package org.experimentalplayers.faraday.services;

import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.SetOptions;
import com.google.cloud.firestore.WriteBatch;
import org.experimentalplayers.faraday.models.SiteDocument;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Service;

import java.util.Collection;

import static org.experimentalplayers.faraday.utils.CollectionMappings.DOCUMENTS;

@DependsOn("firebaseAdminService")
@Service
public class FirestoreHelper {

	private final Firestore db;
	private final CollectionReference docsRef;

	public FirestoreHelper(Firestore db) {
		this.db = db;
		docsRef = db.collection(DOCUMENTS);
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
				.set(document, SetOptions.mergeFields(document.nonNullFieldsNames("lastUpdated")));

	}

	public void mergeSiteDocuments(Collection<SiteDocument> documents) {

		WriteBatch batch = db.batch();

		for(SiteDocument doc : documents) {

			doc.setLastUpdated(null);

			batch.set(docsRef.document(doc.getId()), doc,
					SetOptions.mergeFields(doc.nonNullFieldsNames("lastUpdated")));

		}

		batch.commit();

	}

}
