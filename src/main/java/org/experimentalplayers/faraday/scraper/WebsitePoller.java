package org.experimentalplayers.faraday.scraper;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutures;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.WriteResult;
import com.google.firebase.cloud.FirestoreClient;
import lombok.extern.log4j.Log4j2;
import org.experimentalplayers.faraday.beans.WebRef;
import org.experimentalplayers.faraday.models.ArchiveEntry;
import org.experimentalplayers.faraday.services.FirebaseAdminService;
import org.experimentalplayers.faraday.utils.Mappings;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.MINUTES;
import static org.experimentalplayers.faraday.models.DocumentType.AVVISO;
import static org.experimentalplayers.faraday.models.DocumentType.CIRCOLARE;

@Log4j2
@Service
public class WebsitePoller {

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
		CollectionReference archive = db.collection(Mappings.ARCHIVE);

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
		log.info("Added " + newEntries + " new entrie(s) in archive");

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
