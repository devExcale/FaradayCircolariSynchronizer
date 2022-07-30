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
import org.intellij.lang.annotations.Language;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.google.cloud.firestore.FieldPath.documentId;
import static org.experimentalplayers.faraday.models.DocumentType.AVVISO;
import static org.experimentalplayers.faraday.models.DocumentType.CIRCOLARE;
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

	public WebsitePoller(FirebaseAdminService fbAdmin, WebsiteScraper scraper, WebRef webref) {
		this.fbAdmin = fbAdmin;
		this.scraper = scraper;
		this.webref = webref;
	}

	public void updateSiteDocuments(DocumentType type, String feedUrl) {

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

		Firestore db = FirestoreClient.getFirestore();
		Set<String> dbDocUrls;

		// Get doc urls from db that match the feed ones
		try {

			// TODO: cache (read on db once)

			dbDocUrls = db.collection(DOCUMENTS)
					.whereIn(documentId(), feedIds)
					.get()
					.get()
					.toObjects(SiteDocument.class)
					.stream()
					.map(SiteDocument::getPageUrl)
					.collect(Collectors.toSet());

		} catch(Exception e) {
			log.warn("Couldn't get SiteDocuments<" + type + "> from db", e);
			return;
		}

		List<SiteDocument> newSiteDocs = new LinkedList<>();

		// For doc urls not in db
		for(RSSItem docMeta : feedMetas)
			if(!dbDocUrls.contains(docMeta.getLink()))
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

		CollectionReference documentsCollection = db.collection(DOCUMENTS);

		// Open new batch to save new SiteDocuments
		WriteBatch batch = db.batch();

		// Save all new SiteDocuments
		for(SiteDocument siteDoc : newSiteDocs)
			batch.set(documentsCollection.document(siteDoc.getId()), siteDoc);

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
