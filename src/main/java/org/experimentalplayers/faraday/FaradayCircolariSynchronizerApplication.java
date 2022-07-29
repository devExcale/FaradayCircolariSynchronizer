package org.experimentalplayers.faraday;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class FaradayCircolariSynchronizerApplication {

	public static void main(String[] args) {
		SpringApplication.run(FaradayCircolariSynchronizerApplication.class, args);
	}

}
