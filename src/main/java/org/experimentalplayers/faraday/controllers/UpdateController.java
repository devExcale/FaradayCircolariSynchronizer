package org.experimentalplayers.faraday.controllers;

import com.google.cloud.firestore.Firestore;
import com.google.firebase.cloud.FirestoreClient;
import org.experimentalplayers.faraday.beans.WebRef;
import org.experimentalplayers.faraday.models.ArchiveEntry;
import org.experimentalplayers.faraday.scraper.WebsiteScraper;
import org.experimentalplayers.faraday.utils.AwareCache;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.experimentalplayers.faraday.utils.Mappings.ARCHIVE;

@RestController("/update")
public class UpdateController {

	private final WebsiteScraper scraper;

	private final WebRef webref;

	private final AwareCache<String, ArchiveEntry> archiveCache;

	public UpdateController(WebsiteScraper scraper, WebRef webref) {
		this.scraper = scraper;
		this.webref = webref;
		archiveCache = new AwareCache<>(ArchiveEntry.class, ArchiveEntry::getId);
	}

	@EventListener(ApplicationReadyEvent.class)
	public void onApplicationReady() {

		archiveCache.open(FirestoreClient.getFirestore()
				.collection(ARCHIVE));

	}

	@GetMapping("/archive")
	public void updateArchive() {

	}

	@GetMapping("/circolari")
	public void updateCircolari() {

	}

	@GetMapping("/avvisi")
	public void updateAvvisi() {

	}

	public void updateSiteDocuments() {

	}

}
