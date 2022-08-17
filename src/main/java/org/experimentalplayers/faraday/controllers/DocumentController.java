package org.experimentalplayers.faraday.controllers;

import lombok.extern.log4j.Log4j2;
import org.experimentalplayers.faraday.beans.WebRef;
import org.experimentalplayers.faraday.models.ArchiveEntry;
import org.experimentalplayers.faraday.models.DocumentType;
import org.experimentalplayers.faraday.models.SiteDocument;
import org.experimentalplayers.faraday.models.rest.UpdateRequest;
import org.experimentalplayers.faraday.models.rest.UpdateResponse;
import org.experimentalplayers.faraday.scraper.WebsiteScraper;
import org.experimentalplayers.faraday.services.CacheService;
import org.experimentalplayers.faraday.services.FirestoreHelper;
import org.experimentalplayers.faraday.utils.AwareCache;
import org.experimentalplayers.faraday.utils.CollectionMappings;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Optional;

import static org.experimentalplayers.faraday.models.DocumentType.ARCHIVE;
import static org.experimentalplayers.faraday.models.DocumentType.UNKNOWN;
import static org.experimentalplayers.faraday.utils.RestMappings.*;

@Log4j2
@RestController(CONTEXT_DOCUMENTS)
public class DocumentController {

	private final WebsiteScraper scraper;

	private final FirestoreHelper dbHelper;

	private final WebRef webref;

	private final AwareCache<ArchiveEntry> archiveCache;

	public DocumentController(WebsiteScraper scraper, FirestoreHelper dbHelper, WebRef webref,
			CacheService cacheService) {
		this.scraper = scraper;
		this.dbHelper = dbHelper;
		this.webref = webref;
		//noinspection unchecked
		archiveCache = (AwareCache<ArchiveEntry>) cacheService.getCache(ARCHIVE);
	}

	@PostMapping(DOCUMENTS_SINGLE)
	public ResponseEntity<UpdateResponse> singleDocument(@RequestBody UpdateRequest request) {

		long start = System.currentTimeMillis();

		DocumentType type = Optional.ofNullable(request.getDocumentType())
				.map(DocumentType::deduce)
				.orElse(UNKNOWN);

		if(type == UNKNOWN)
			return ResponseEntity.badRequest()
					.body(UpdateResponse.builder()
							.message("Unknown document type")
							.build());

		String schoolYear = request.getSchoolYear();

		if(schoolYear == null || schoolYear.isEmpty())
			return ResponseEntity.badRequest()
					.body(UpdateResponse.builder()
							.message("No school year provided")
							.build());

		String url = request.getUrl();

		if(url == null || url.isEmpty())
			return ResponseEntity.badRequest()
					.body(UpdateResponse.builder()
							.message("No SiteDocument url provided")
							.build());

		ArchiveEntry archive = archiveCache.getValues()
				.stream()
				.filter(entry -> entry.getSchoolYear()
						.equalsIgnoreCase(schoolYear))
				.filter(entry -> entry.getType() == type)
				.findFirst()
				.orElse(null);

		if(archive == null)
			return ResponseEntity.badRequest()
					.body(UpdateResponse.builder()
							.message("Unknown SchoolYear")
							.build());

		SiteDocument doc;

		try {

			doc = scraper.document(url)
					.type(type)
					.category(archive.getCategory())
					.schoolYear(archive.getSchoolYear())
					.build();

		} catch(Exception e) {
			return ResponseEntity.unprocessableEntity()
					.body(UpdateResponse.builder()
							.message("Couldn't scrape SiteDocument")
							.build());
		}

		dbHelper.mergeSiteDocument(doc);

		return ResponseEntity.ok(UpdateResponse.builder()
				.collection(CollectionMappings.DOCUMENTS)
				.documents(Collections.singletonList(doc))
				.documentsUpdated(1)
				.opTime(System.currentTimeMillis() - start)
				.build());
	}

	@PostMapping(DOCUMENTS_BATCH)
	public UpdateResponse batchDocuments() {
		return null;
	}

}
