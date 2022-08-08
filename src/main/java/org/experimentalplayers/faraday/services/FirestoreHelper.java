package org.experimentalplayers.faraday.services;

import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.WriteBatch;
import lombok.extern.log4j.Log4j2;
import org.experimentalplayers.faraday.beans.WebRef;
import org.experimentalplayers.faraday.models.ArchiveEntry;
import org.experimentalplayers.faraday.models.SiteDocument;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static com.google.cloud.firestore.FieldPath.documentId;
import static com.google.cloud.firestore.SetOptions.mergeFields;
import static java.lang.Math.min;
import static org.experimentalplayers.faraday.utils.CollectionMappings.*;
import static org.experimentalplayers.faraday.utils.Statics.nonNullFields;

@Log4j2
@DependsOn("firebaseAdminService")
@Service
public class FirestoreHelper {

	private final Firestore db;
	private final CollectionReference docsRef;
	private final CollectionReference archiveRef;

	private WebRef webref;

	public FirestoreHelper(Firestore db) {
		this.db = db;
		docsRef = db.collection(DOCUMENTS);
		archiveRef = db.collection(ARCHIVE);
		webref = null;
	}

	@Bean
	public WebRef getWebRef() throws ExecutionException, InterruptedException {
		return getWebRef(false);
	}

	public WebRef getWebRef(boolean update) throws ExecutionException, InterruptedException {

		if(webref == null || update)
			webref = db.document(WEB_REF)
					.get()
					.get()
					.toObject(WebRef.class);

		return webref;
	}

	public void writeWebRef() {

		if(webref == null) {
			log.warn("Can't upload null WebRef");
			return;
		}

		db.document(WEB_REF)
				.set(webref);

	}

	public void mergeWebRef() {

		if(webref == null) {
			log.warn("Can't save null WebRef");
			return;
		}

		db.document(WEB_REF)
				.set(webref, mergeFields(nonNullFields(webref)));

	}

	public void writeSiteDocument(SiteDocument document) {

		document.setLastUpdated(null);

		docsRef.document(document.getId())
				.set(document);

	}

	public void writeSiteDocuments(Collection<SiteDocument> documents) {

		WriteBatch batch = db.batch();

		for(SiteDocument doc : documents) {

			doc.setLastUpdated(null);

			batch.set(docsRef.document(doc.getId()), doc);

		}

		batch.commit();

	}

	public void mergeSiteDocument(SiteDocument document) {

		document.setLastUpdated(null);

		docsRef.document(document.getId())
				.set(document, mergeFields(nonNullFields(document, "lastUpdated")));

	}

	public void mergeSiteDocuments(Collection<SiteDocument> documents) {

		WriteBatch batch = db.batch();

		for(SiteDocument doc : documents) {

			doc.setLastUpdated(null);

			batch.set(docsRef.document(doc.getId()), doc, mergeFields(nonNullFields(doc, "lastUpdated")));

		}

		batch.commit();

	}

	public void writeArchive(ArchiveEntry archive) {

		archive.setLastUpdated(null);

		archiveRef.document(archive.getId())
				.set(archive);

	}

	public void writeArchives(Collection<ArchiveEntry> archives) {

		WriteBatch batch = db.batch();

		for(ArchiveEntry archive : archives) {

			archive.setLastUpdated(null);

			batch.set(archiveRef.document(archive.getId()), archive);

		}

		batch.commit();

	}

	public List<SiteDocument> getSiteDocumentsWithIds(List<String> ids)
			throws ExecutionException, InterruptedException {

		return db.collection(DOCUMENTS)
				.whereIn(documentId(), ids)
				.get()
				.get()
				.toObjects(SiteDocument.class);

	}

	public List<ArchiveEntry> getArchivesWithIds(List<String> ids) throws ExecutionException, InterruptedException {

		List<String> queryParams = ids.subList(0, min(ids.size(), 10));
		List<ArchiveEntry> results = archiveRef.whereIn(documentId(), queryParams)
				.get()
				.get()
				.toObjects(ArchiveEntry.class);

		if(ids.size() > 10)
			results.addAll(getArchivesWithIds(ids.subList(10, ids.size())));

		return results;
	}

}


