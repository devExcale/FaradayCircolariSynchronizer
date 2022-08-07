package org.experimentalplayers.faraday.services;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

@Log4j2
@Service
public class FirebaseAdminService {

	@Value("${env.FIREBASE_KEY_PATH}")
	private String firebaseKeyPath;

	private Firestore db;

	public FirebaseAdminService() {
		firebaseKeyPath = "";
		db = null;
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

}
