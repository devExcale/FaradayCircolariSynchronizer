package org.experimentalplayers.faraday;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

@Log4j2
@Service
public class FirebaseAdminService {

	@Value("${env.FIREBASE_KEY_PATH}")
	private String firebaseKeyPath;

	public FirebaseAdminService() {
		firebaseKeyPath = null;
	}

	@PostConstruct
	public void init() {

		if(firebaseKeyPath == null) {

			log.error("Null FIREBASE_KEY_PATH");
			return;

		}

		try(InputStream is = Files.newInputStream(Paths.get(firebaseKeyPath))) {

			FirebaseOptions options = FirebaseOptions.builder()
					.setCredentials(GoogleCredentials.fromStream(is))
					.build();

			List<FirebaseApp> apps = FirebaseApp.getApps();

			if(apps.isEmpty()) {

				FirebaseApp.initializeApp(options);
				log.info("Firebase application has been initialized");

			}

		} catch(Exception e) {
			log.error("Could not authenticate Firebase", e);
		}

	}

}
