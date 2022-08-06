package org.experimentalplayers.faraday.services;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;
import lombok.extern.log4j.Log4j2;
import org.experimentalplayers.faraday.beans.WebRef;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.experimentalplayers.faraday.utils.CollectionMappings.WEB_REF;

@Log4j2
@Service
public class FirebaseAdminService {

	@Value("${env.FIREBASE_KEY_PATH}")
	private String firebaseKeyPath;

	private Firestore db;

	private WebRef webref;

	public FirebaseAdminService() {
		firebaseKeyPath = "";
		db = null;
		webref = null;
	}

	@PostConstruct
	public void init() throws IOException, IllegalArgumentException {

		if(firebaseKeyPath.isEmpty())
			throw new IllegalArgumentException("Null or empty FIREBASE_KEY_PATH variable");

		try(InputStream is = Files.newInputStream(Paths.get(firebaseKeyPath))) {

			FirebaseOptions options = FirebaseOptions.builder()
					.setCredentials(GoogleCredentials.fromStream(is))
					.build();

			List<FirebaseApp> apps = FirebaseApp.getApps();

			if(apps.isEmpty()) {

				FirebaseApp.initializeApp(options);
				log.info("Firebase application has been initialized");

			}

			db = FirestoreClient.getFirestore();

		} catch(Exception e) {
			log.error("Could not authenticate Firebase", e);
			throw e;
		}

	}

	@Bean
	public Firestore getDB() {
		return db;
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

	public void uploadWebRef() {

		if(webref == null) {
			log.warn("Can't upload null WebRef");
			return;
		}

		try {

			db.document(WEB_REF)
					.set(webref)
					.get();

		} catch(Exception e) {
			log.warn("Couldn't upload WebRef", e);
			return;
		}

		log.info("Uploaded WebRef");

	}

}
