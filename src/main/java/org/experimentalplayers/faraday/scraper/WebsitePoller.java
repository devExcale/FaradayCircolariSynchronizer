package org.experimentalplayers.faraday.scraper;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutures;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.WriteBatch;
import com.google.cloud.firestore.WriteResult;
import com.google.firebase.cloud.FirestoreClient;
import lombok.extern.log4j.Log4j2;
import org.experimentalplayers.faraday.beans.WebRef;
import org.experimentalplayers.faraday.models.ArchiveEntry;
import org.experimentalplayers.faraday.models.DocumentType;
import org.experimentalplayers.faraday.models.SiteDocument;
import org.experimentalplayers.faraday.models.rss.RSSItem;
import org.experimentalplayers.faraday.models.rss.RSSMain;
import org.experimentalplayers.faraday.services.FirebaseAdminService;
import org.experimentalplayers.faraday.utils.AwareCache;
import org.intellij.lang.annotations.Language;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.google.cloud.firestore.FieldPath.documentId;
import static com.google.cloud.firestore.Query.Direction.DESCENDING;
import static org.experimentalplayers.faraday.models.DocumentType.*;
import static org.experimentalplayers.faraday.utils.Mappings.ARCHIVE;
import static org.experimentalplayers.faraday.utils.Mappings.DOCUMENTS;

@Log4j2
@Service
public class WebsitePoller {

	@Language("SpEL")
	public static final String CRON_EXP_CIRCOLARI = "#{ ${env.ENABLE_POLL_CIRCOLARI:true} ? ${application.cron.circolari} : '-'}";

	@Language("SpEL")
	public static final String CRON_EXP_AVVISI = "#{ ${env.ENABLE_POLL_AVVISI:true} ? ${application.cron.avvisi} : '-'}";

	@Language("SpEL")
	public static final String CRON_EXP_ARCHIVE = "#{ ${env.ENABLE_POLL_ARCHIVE:true} ? ${application.cron.archive} : '-'}";

	private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("EEE, dd MMM yy HH:mm:ss X");

	// === START COMPONENTS ===

	private final FirebaseAdminService fbAdmin;

	private final WebsiteScraper scraper;

	private final WebRef webref;

	// === END COMPONENTS ===

	private final AwareCache<String, SiteDocument> circolariCache;
	private final AwareCache<String, SiteDocument> avvisiCache;

	public WebsitePoller(FirebaseAdminService fbAdmin, WebsiteScraper scraper, WebRef webref) {
		this.fbAdmin = fbAdmin;
		this.scraper = scraper;
		this.webref = webref;
		circolariCache = new AwareCache<>(SiteDocument.class, SiteDocument::getId);
		avvisiCache = new AwareCache<>(SiteDocument.class, SiteDocument::getId);
	}

	@PostConstruct
	public void initCaches() {

		CollectionReference collection = FirestoreClient.getFirestore()
				.collection(DOCUMENTS);

		circolariCache.open(collection.whereEqualTo("type", CIRCOLARE)
				.orderBy("publishDate", DESCENDING)
				.limit(10));

		avvisiCache.open(collection.whereEqualTo("type", AVVISO)
				.orderBy("publishDate", DESCENDING)
				.limit(10));

	}

	public AwareCache<String, SiteDocument> getCache(DocumentType type) {

		switch(type) {

			case CIRCOLARE:
				return circolariCache;

			case AVVISO:
				return avvisiCache;

			default:

				String msg = "Unknown DocumentType, providing no cache";
				log.warn(msg, new RuntimeException(msg));

				return null;
		}

	}

	//	/**
	//	 * Refreshes type's cache with the latest 10 documents,
	//	 * plus any additional document specified (if it exists)
	//	 *
	//	 * @param type
	//	 * @param additionalIds
	//	 */
	//	public void refreshCache(DocumentType type, List<String> additionalIds) {
	//
	//		log.info("Refreshing cache");
	//
	//		Firestore db = FirestoreClient.getFirestore();
	//		CollectionReference collection = db.collection(DOCUMENTS);
	//
	//		ApiFuture<QuerySnapshot> queryLatest = collection.whereEqualTo("type", type)
	//				.orderBy("publishDate", DESCENDING)
	//				.limit(10)
	//				.get();
	//
	//		ApiFuture<QuerySnapshot> queryIds = collection.whereEqualTo("type", type)
	//				.whereIn(FieldPath.documentId(), additionalIds)
	//				.get();
	//
	//		final Set<String> cache = getCache(type);
	//
	//		if(type == CIRCOLARE || type == AVVISO)
	//			try {
	//
	//				ApiFutures.allAsList(Arrays.asList(queryLatest, queryIds))
	//						.get()
	//						.stream()
	//						.map(snap -> snap.toObjects(SiteDocument.class))
	//						.flatMap(Collection::stream)
	//						.map(SiteDocument::getId)
	//						.forEach(cache::add);
	//
	//			} catch(Exception e) {
	//				log.warn("Failed to refresh cache<" + type + ">", e);
	//				// TODO: exception handling
	//			}
	//
	//	}

	// TODO: move segments in methods
	public void updateSiteDocuments(DocumentType type, String feedUrl) {

		if(type == UNKNOWN)
			throw new IllegalArgumentException("Unknown SiteDocument type");

		RSSMain rss;

		// TODO: base_url check

		// Get feed
		try {

			// TODO: query parameters check
			rss = scraper.feed(feedUrl + "?format=feed&amp;type=rss");

		} catch(Exception e) {
			log.warn("Couldn't update SiteDocuments<" + type + ">", e);
			return;
		}

		List<RSSItem> feedMetas = rss.getItems();

		// Retrieve doc ids from feed
		List<String> feedIds = feedMetas.stream()
				.map(RSSItem::getLink)
				.map(SiteDocument::idFromUrl)
				.collect(Collectors.toList());

		// Cache shouldn't be null, unless a new type other than CIRCOLARE and AVVISO is added
		AwareCache<String, SiteDocument> cache = getCache(type);
		Firestore db = FirestoreClient.getFirestore();

		log.info("Checking feed's SiteDocument(s)<" + type + "> against cache");

		// Get ids which are not in cache
		List<String> newIds = feedIds.stream()
				.filter(cache::miss)
				.collect(Collectors.toList());

		if(newIds.isEmpty()) {
			log.info("No new SiteDocuments<{}>", type);
			return;
		}

		Map<String, SiteDocument> dbNewDocs;

		log.info("Checking {} SiteDocument(s)<{}> against db", newIds.size(), type);

		// Get doc urls from db that match the feed ones
		try {

			dbNewDocs = db.collection(DOCUMENTS)
					.whereIn(documentId(), newIds)
					.get()
					.get()
					.toObjects(SiteDocument.class)
					.stream()
					.collect(Collectors.toMap(SiteDocument::getId, Function.identity()));

		} catch(Exception e) {
			log.warn("Couldn't get SiteDocuments<{}> from db", type, e);
			return;
		}

		Set<SiteDocument> newSiteDocs = new HashSet<>();

		// For doc urls not in db
		for(RSSItem docMeta : feedMetas)
			if(!dbNewDocs.containsKey(docMeta.getLink()))
				try {

					// Get document from site and fill missing
					SiteDocument d = scraper.document(docMeta.getLink())
							.title(docMeta.getTitle())
							.type(type)
							.category(docMeta.getCategory())
							.publishDate(Timestamp.of(DATE_FORMAT.parse(docMeta.getPubDate())))
							.build();

					newSiteDocs.add(d);

				} catch(Exception e) {
					log.warn("Couldn't scrape SiteDocument<" + type + "> from site", e);
				}

		if(newSiteDocs.isEmpty()) {
			log.info("No new SiteDocuments<" + type + ">");
			return;
		}

		// Remove previously added documents from cache (other than latest 10)
		cache.clearAdded();

		// Open new batch to save new SiteDocuments
		WriteBatch batch = db.batch();
		CollectionReference documentsCollection = db.collection(DOCUMENTS);

		// Save all new SiteDocuments (db and cache)
		for(SiteDocument doc : newSiteDocs) {
			batch.set(documentsCollection.document(doc.getId()), doc);
			cache.add(doc);
		}

		// Commit batch and pray
		batch.commit();

		// TODO: handle failure

		log.info("Saved (hopefully) " + newSiteDocs.size() + " new SiteDocument(s)<" + type + ">");

	}

	@Scheduled(cron = CRON_EXP_CIRCOLARI, zone = "Europe/Rome")
	public void updateCircolari() {

		updateSiteDocuments(CIRCOLARE, webref.getFeedCircolari());

	}

	@Scheduled(cron = CRON_EXP_AVVISI, zone = "Europe/Rome")
	public void updateAvvisi() {

		updateSiteDocuments(AVVISO, webref.getFeedAvvisi());

	}

	@Scheduled(cron = CRON_EXP_ARCHIVE, zone = "Europe/Rome")
	public void updateArchive() {

		String archiveUrl = webref.getArchiveUrl();
		List<ArchiveEntry> webEntries;

		try {

			webEntries = scraper.archive(archiveUrl);

		} catch(IOException e) {
			log.warn("Couldn't update archive", e);
			return;
		}

		Firestore db = FirestoreClient.getFirestore();
		CollectionReference archive = db.collection(ARCHIVE);

		Map<String, ArchiveEntry> dbEntries;

		try {

			dbEntries = archive.get()
					.get()
					.toObjects(ArchiveEntry.class)
					.stream()
					.collect(Collectors.toMap(ArchiveEntry::getId, Function.identity()));

		} catch(Exception e) {
			log.warn("Couldn't get ArchiveEntries from db", e);
			return;
		}

		// Find new entries
		List<ApiFuture<WriteResult>> newEntriesQueries = webEntries.stream()
				.filter(entry -> !dbEntries.containsKey(entry.getId()))
				// Write new entries to archive
				.map(entry -> archive.document(entry.getId())
						.set(entry))
				.collect(Collectors.toList());

		if(newEntriesQueries.isEmpty()) {
			log.info("No need to update archive, no new entries");
			return;
		}

		try {

			// Execute all writes
			ApiFutures.allAsList(newEntriesQueries)
					.get();

		} catch(Exception e) {
			log.warn("Couldn't update archive", e);
			return;
		}

		int newEntries = newEntriesQueries.size();
		log.info("Added " + newEntries + " new entry(s) in archive");

		for(ArchiveEntry webEntry : webEntries)
			if(!dbEntries.containsKey(webEntry.getId()))
				dbEntries.put(webEntry.getId(), webEntry);

		// Whether to upload WebRef
		AtomicBoolean updated = new AtomicBoolean(false);

		// Get latest archive for circolari
		dbEntries.values()
				.stream()
				.filter(entry -> entry.getType() == CIRCOLARE)
				.max(Comparator.comparingInt(ArchiveEntry::getStartYear))
				.map(ArchiveEntry::getUrl)
				.filter(url -> !url.equals(webref.getFeedCircolari()))
				.ifPresent(url -> {
					webref.setFeedCircolari(url);
					updated.set(true);
				});

		// Get latest archive for avvisi
		dbEntries.values()
				.stream()
				.filter(entry -> entry.getType() == AVVISO)
				.max(Comparator.comparingInt(ArchiveEntry::getStartYear))
				.map(ArchiveEntry::getUrl)
				.filter(url -> !url.equals(webref.getFeedAvvisi()))
				.ifPresent(url -> {
					webref.setFeedAvvisi(url);
					updated.set(true);
				});

		if(updated.get())
			fbAdmin.uploadWebRef();

	}

}
