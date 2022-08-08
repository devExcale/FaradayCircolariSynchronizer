package org.experimentalplayers.faraday.controllers;

import lombok.extern.log4j.Log4j2;
import org.experimentalplayers.faraday.beans.WebRef;
import org.experimentalplayers.faraday.models.ArchiveEntry;
import org.experimentalplayers.faraday.models.DocumentType;
import org.experimentalplayers.faraday.models.rest.UpdateResponse;
import org.experimentalplayers.faraday.scraper.WebsitePoller;
import org.experimentalplayers.faraday.services.CacheService;
import org.experimentalplayers.faraday.utils.AwareCache;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.experimentalplayers.faraday.models.DocumentType.AVVISI;
import static org.experimentalplayers.faraday.models.DocumentType.CIRCOLARI;
import static org.experimentalplayers.faraday.utils.CollectionMappings.ARCHIVE;
import static org.experimentalplayers.faraday.utils.RestMappings.*;

@Log4j2
@RestController(CONTEXT_POLL)
public class PollerController {

	private final WebsitePoller poller;

	private final WebRef webref;

	// TODO:
	private final AwareCache<ArchiveEntry> archiveCache;

	public PollerController(WebsitePoller poller, WebRef webref, CacheService cacheService) {
		this.poller = poller;
		this.webref = webref;

		//noinspection unchecked
		archiveCache = (AwareCache<ArchiveEntry>) cacheService.getCache(DocumentType.ARCHIVE);
	}

	@GetMapping(POLL_ARCHIVE)
	public UpdateResponse updateArchive() {

		long start = System.currentTimeMillis();
		int updated = poller.updateArchive(); // TODO: return updated documents

		return UpdateResponse.builder()
				.collection(ARCHIVE.toUpperCase())
				.documentsUpdated(updated)
				.opTime(System.currentTimeMillis() - start)
				.build();
	}

	@GetMapping(POLL_CIRCOLARI)
	public UpdateResponse updateCircolari() {

		long start = System.currentTimeMillis();
		int updated = poller.updateCircolari();

		return UpdateResponse.builder()
				.collection(CIRCOLARI.toString())
				.documentsUpdated(updated)
				.opTime(System.currentTimeMillis() - start)
				.build();
	}

	@GetMapping(POLL_AVVISI)
	public UpdateResponse updateAvvisi() {

		long start = System.currentTimeMillis();
		int updated = poller.updateAvvisi();

		return UpdateResponse.builder()
				.collection(AVVISI.toString())
				.documentsUpdated(updated)
				.opTime(System.currentTimeMillis() - start)
				.build();
	}

}
