package org.experimentalplayers.faraday.services;

import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.cloud.FirestoreClient;
import org.experimentalplayers.faraday.models.ArchiveEntry;
import org.experimentalplayers.faraday.models.DocumentType;
import org.experimentalplayers.faraday.models.FireDocument;
import org.experimentalplayers.faraday.models.SiteDocument;
import org.experimentalplayers.faraday.utils.AwareCache;
import org.experimentalplayers.faraday.utils.CollectionMappings;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.google.cloud.firestore.Query.Direction.DESCENDING;
import static org.experimentalplayers.faraday.models.DocumentType.*;
import static org.experimentalplayers.faraday.utils.CollectionMappings.DOCUMENTS;

@DependsOn("firebaseAdminService")
@Service
public class CacheService {

	private final Map<DocumentType, AwareCache<? extends FireDocument>> caches;

	private final AwareCache<ArchiveEntry> archiveCache;

	private final AwareCache<SiteDocument> circolariCache;

	private final AwareCache<SiteDocument> avvisiCache;

	private final Firestore db;

	public CacheService(Firestore db) {
		this.db = db;

		archiveCache = new AwareCache<>(ArchiveEntry.class, ArchiveEntry::getId);
		circolariCache = new AwareCache<>(SiteDocument.class, SiteDocument::getId);
		avvisiCache = new AwareCache<>(SiteDocument.class, SiteDocument::getId);

		caches = new ConcurrentHashMap<>();
		caches.put(ARCHIVE, archiveCache);
		caches.put(CIRCOLARE, circolariCache);
		caches.put(AVVISO, avvisiCache);

	}

	@PostConstruct
	public void initCaches() {

		archiveCache.open(db.collection(CollectionMappings.ARCHIVE));

		circolariCache.open(db.collection(DOCUMENTS)
				.whereEqualTo("type", CIRCOLARE)
				.orderBy("publishDate", DESCENDING)
				.limit(10));

		avvisiCache.open(db.collection(DOCUMENTS)
				.whereEqualTo("type", AVVISO)
				.orderBy("publishDate", DESCENDING)
				.limit(10));

	}

	public AwareCache<? extends FireDocument> getCache(DocumentType type) {
		return caches.get(type);
	}

}
