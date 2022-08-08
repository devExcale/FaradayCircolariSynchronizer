package org.experimentalplayers.faraday.controllers;

import lombok.extern.log4j.Log4j2;
import org.experimentalplayers.faraday.beans.WebRef;
import org.experimentalplayers.faraday.models.DocumentType;
import org.experimentalplayers.faraday.models.rest.UpdateRequest;
import org.experimentalplayers.faraday.models.rest.UpdateResponse;
import org.experimentalplayers.faraday.scraper.WebsiteScraper;
import org.experimentalplayers.faraday.services.FirestoreHelper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

import static org.experimentalplayers.faraday.models.DocumentType.UNKNOWN;
import static org.experimentalplayers.faraday.utils.RestMappings.*;

@Log4j2
@RestController(CONTEXT_DOCUMENTS)
public class DocumentController {

	private final WebsiteScraper scraper;

	private final FirestoreHelper dbHelper;

	private final WebRef webref;

	public DocumentController(WebsiteScraper scraper, FirestoreHelper dbHelper, WebRef webref) {
		this.scraper = scraper;
		this.dbHelper = dbHelper;
		this.webref = webref;
	}

	@PostMapping(DOCUMENTS_SINGLE)
	public ResponseEntity<UpdateResponse> singleDocument(@RequestBody UpdateRequest request) {

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

	}

	@PostMapping(DOCUMENTS_BATCH)
	public UpdateResponse batchDocuments() {

	}

}
