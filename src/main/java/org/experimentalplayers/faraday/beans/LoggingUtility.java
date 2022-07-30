package org.experimentalplayers.faraday.beans;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import static org.experimentalplayers.faraday.scraper.WebsitePoller.*;

@Log4j2
@Component
public class LoggingUtility {

	@Autowired
	public void logCronArchive(@Value(CRON_EXP_ARCHIVE) String value) {
		log.info("Defined cron for Archive: " + value);
	}

	@Autowired
	public void logCronCircolari(@Value(CRON_EXP_CIRCOLARI) String value) {
		log.info("Defined cron for Circolari: " + value);
	}

	@Autowired
	public void logCronAvvisi(@Value(CRON_EXP_AVVISI) String value) {
		log.info("Defined cron for Avvisi: " + value);
	}

}
