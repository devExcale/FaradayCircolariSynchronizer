package org.experimentalplayers.faraday.scraper;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutures;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.*;
import com.google.firebase.cloud.FirestoreClient;
import lombok.extern.log4j.Log4j2;
import org.experimentalplayers.faraday.beans.WebRef;
import org.experimentalplayers.faraday.models.ArchiveEntry;
import org.experimentalplayers.faraday.models.Attachment;
import org.experimentalplayers.faraday.models.SiteDocument;
import org.experimentalplayers.faraday.models.rss.RSSItem;
import org.experimentalplayers.faraday.models.rss.RSSMain;
import org.experimentalplayers.faraday.services.FirebaseAdminService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.MINUTES;
import static org.experimentalplayers.faraday.models.DocumentType.AVVISO;
import static org.experimentalplayers.faraday.models.DocumentType.CIRCOLARE;
import static org.experimentalplayers.faraday.utils.Mappings.*;

@Log4j2
@Service
public class WebsitePoller {

	private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("EEE, dd MMM yy HH:mm:ss X");

	private final FirebaseAdminService fbAdmin;

	private final WebsiteScraper scraper;

	private final WebRef webref;

	public WebsitePoller(FirebaseAdminService fbAdmin, WebsiteScraper scraper, WebRef webref) {
		this.fbAdmin = fbAdmin;
		this.scraper = scraper;
		this.webref = webref;
	}

	/**
	 * Update circolari every 15 minutes.
	 */
	@Scheduled(fixedRate = 15, timeUnit = MINUTES)
	public void updateCircolari() {

		String circolariFeed = webref.getFeedCircolari();
		RSSMain rss;

		// TODO: base_url check

		// Get feed
		try {

			rss = scraper.feed(circolariFeed + "?format=feed&amp;type=rss");

		} catch(IOException e) {
			log.warn("Couldn't update circolari", e);
			return;
		}

		List<RSSItem> feedDocs = rss.getItems();

		// Retrieve doc urls from feed
		List<String> docUrls = feedDocs.stream()
				.map(RSSItem::getLink)
				.collect(Collectors.toList());

		Firestore db = FirestoreClient.getFirestore();
		Set<String> dbDocUrls;

		// Get doc urls from db that match the feed ones
		try {

			dbDocUrls = db.collection(DOCUMENTS)
					.whereIn("pageUrl", docUrls)
					.get()
					.get()
					.toObjects(SiteDocument.class)
					.stream()
					.map(SiteDocument::getPageUrl)
					.collect(Collectors.toSet());

		} catch(Exception e) {
			log.warn("Couldn't get SiteDocuments from db", e);
			return;
		}

		List<SiteDocument> siteDocs = new LinkedList<>();

		// For doc urls not in db
		for(RSSItem feedDoc : feedDocs)
			if(!dbDocUrls.contains(feedDoc.getLink()))
				try {

					// Get document from site and fill missing
					SiteDocument d = scraper.document(feedDoc.getLink())
							.title(feedDoc.getTitle())
							.type(CIRCOLARE)
							.category(feedDoc.getCategory())
							.publishDate(Timestamp.of(DATE_FORMAT.parse(feedDoc.getPubDate())))
							.build();

					siteDocs.add(d);

				} catch(Exception e) {
					log.warn("Couldn't scrape SiteDocument from site", e);
				}

		if(siteDocs.isEmpty()) {
			log.info("No new circolari");
			return;
		}

		// Open batch to save all new attachments
		WriteBatch batch = db.batch();

		CollectionReference attachmentsCollection = db.collection(ATTACHMENTS);

		// Save attachments in dedicated collection
		for(SiteDocument siteDoc : siteDocs) {

			List<DocumentReference> atRefs = new LinkedList<>();

			for(Attachment attachment : siteDoc.getDerefAttachments()) {

				// Create new document (autogen. id)
				DocumentReference atRef = attachmentsCollection.document();

				// Save new id
				attachment.setId(atRef.getId());
				// Save reference in list
				atRefs.add(atRef);

				// Write attachment in batch
				batch.set(atRef, attachment);

			}

			// Save all attachments references in SiteDocument
			siteDoc.setAttachments(atRefs);

		}

		// Commit batch and pray
		batch.commit();

		CollectionReference documentsCollection = db.collection(DOCUMENTS);

		// Open new batch for SiteDocuments
		batch = db.batch();

		// Save all new SiteDocuments
		for(SiteDocument siteDoc : siteDocs)
			batch.set(documentsCollection.document(), siteDoc);

		// Commit batch and pray
		batch.commit();

		log.info("Saved (hopefully) " + siteDocs.size() + " new circolari");

	}

	/**
	 * Update avvisi every 15 minutes.
	 */
	@Scheduled(fixedRate = 15, timeUnit = MINUTES)
	public void updateAvvisi() {

	}

	/**
	 * Update archive every day at midnight.
	 */
	@Scheduled(cron = "0 0 0 * * *", zone = "Europe/Rome")
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
