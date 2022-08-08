package org.experimentalplayers.faraday.controllers;

import lombok.extern.log4j.Log4j2;
import org.experimentalplayers.faraday.beans.WebRef;
import org.experimentalplayers.faraday.models.rest.UpdateResponse;
import org.experimentalplayers.faraday.scraper.WebsiteScraper;
import org.experimentalplayers.faraday.services.FirestoreHelper;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

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
	public UpdateResponse singleDocument() {

	}

	@PostMapping(DOCUMENTS_BATCH)
	public UpdateResponse batchDocuments() {

	}

}
