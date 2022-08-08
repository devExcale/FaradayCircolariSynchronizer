package org.experimentalplayers.faraday.scraper;

import com.google.cloud.Timestamp;
import lombok.extern.log4j.Log4j2;
import org.experimentalplayers.faraday.beans.WebRef;
import org.experimentalplayers.faraday.models.ArchiveEntry;
import org.experimentalplayers.faraday.models.DocumentType;
import org.experimentalplayers.faraday.models.SiteDocument;
import org.experimentalplayers.faraday.models.rss.RSSItem;
import org.experimentalplayers.faraday.models.rss.RSSMain;
import org.experimentalplayers.faraday.services.CacheService;
import org.experimentalplayers.faraday.services.FirestoreHelper;
import org.experimentalplayers.faraday.utils.AwareCache;
import org.intellij.lang.annotations.Language;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.experimentalplayers.faraday.models.DocumentType.AVVISI;
import static org.experimentalplayers.faraday.models.DocumentType.CIRCOLARI;

@SuppressWarnings("ScheduledMethodInspection")

@Log4j2
@Service
public class WebsitePoller {

	@Language("SpEL")
	public static final String CRON_EXP_CIRCOLARI = "#{ ${env.ENABLE_POLL_CIRCOLARI:true} ? ${application.cron.circolari} : '-'}";

	@Language("SpEL")
	public static final String CRON_EXP_AVVISI = "#{ ${env.ENABLE_POLL_AVVISI:true} ? ${application.cron.avvisi} : '-'}";

	@Language("SpEL")
	public static final String CRON_EXP_ARCHIVE = "#{ ${env.ENABLE_POLL_ARCHIVE:true} ? ${application.cron.archive} : '-'}";

	private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("EEE, dd MMM yy HH:mm:ss X");

	// === START COMPONENTS ===

	private final CacheService cacheService;

	private final WebsiteScraper scraper;

	private final FirestoreHelper dbHelper;

	private final WebRef webref;

	// === END COMPONENTS ===

	public WebsitePoller(CacheService cacheService, WebsiteScraper scraper, FirestoreHelper dbHelper, WebRef webref) {
		this.cacheService = cacheService;
		this.scraper = scraper;
		this.dbHelper = dbHelper;
		this.webref = webref;
	}

	// TODO: move segments in methods
	public int updateSiteDocuments(DocumentType type, String feedUrl, String schoolYear) {

		if(type != CIRCOLARI && type != AVVISI)
			throw new IllegalArgumentException("Illegal SiteDocument type");

		RSSMain rss;

		// TODO: base_url check

		// Get feed
		try {

			// TODO: query parameters check
			rss = scraper.feed(feedUrl + "?format=feed&amp;type=rss");

		} catch(Exception e) {
			log.warn("Couldn't update SiteDocuments<{}>", type, e);
			return 0;
		}

		List<RSSItem> feedMetas = rss.getItems();

		// Retrieve doc ids from feed
		List<String> feedIds = feedMetas.stream()
				.map(RSSItem::getLink)
				.map(SiteDocument::idFromUrl)
				.collect(Collectors.toList());

		// There should be no problem in casting, CIRCOLARE and AVVISO types are always SiteDocument
		//noinspection unchecked
		AwareCache<SiteDocument> cache = (AwareCache<SiteDocument>) cacheService.getCache(type);

		log.info("Checking feed's SiteDocument(s)<{}> against cache", type);

		// Get ids which are not in cache
		List<String> newIds = feedIds.stream()
				.filter(cache::miss)
				.collect(Collectors.toList());

		if(newIds.isEmpty()) {
			log.info("No new SiteDocuments<{}>", type);
			return 0;
		}

		Map<String, SiteDocument> dbNewDocs;

		log.info("Checking {} SiteDocument(s)<{}> against db", newIds.size(), type);

		// Get doc urls from db that match the feed ones
		try {

			dbNewDocs = dbHelper.getSiteDocumentsWithIds(newIds)
					.stream()
					.collect(Collectors.toMap(SiteDocument::getId, Function.identity()));

		} catch(Exception e) {
			log.warn("Couldn't get SiteDocuments<{}> from db", type, e);
			return 0;
		}

		Set<SiteDocument> newSiteDocs = new HashSet<>();

		// For doc urls not in db
		for(RSSItem docMeta : feedMetas)
			if(!dbNewDocs.containsKey(docMeta.getLink()))
				try {

					// Get document from site and fill missing
					SiteDocument d = scraper.document(docMeta.getLink())
							.title(docMeta.getTitle())
							.type(type)
							.category(docMeta.getCategory())
							.schoolYear(schoolYear)
							.originalPublishDate(Timestamp.of(DATE_FORMAT.parse(docMeta.getPubDate())))
							.build();

					newSiteDocs.add(d);

				} catch(Exception e) {
					log.warn("Couldn't scrape SiteDocument<{}> from site", type, e);
				}

		if(newSiteDocs.isEmpty()) {
			log.info("No new SiteDocuments<{}>", type);
			return 0;
		}

		// Remove previously added documents from cache (other than latest 10)
		cache.clearAdded();

		// Write new SiteDocuments on db
		dbHelper.writeSiteDocuments(newSiteDocs);
		// And on cache
		cache.addAll(newSiteDocs);

		int updated = newSiteDocs.size();

		log.info("Saved (hopefully) {} new SiteDocument(s)<{}>", updated, type);

		return updated;
	}

	@Scheduled(cron = CRON_EXP_CIRCOLARI, zone = "Europe/Rome")
	public int updateCircolari() {

		return updateSiteDocuments(CIRCOLARI, webref.getFeedCircolari(), webref.getSchoolYearCircolari());

	}

	@Scheduled(cron = CRON_EXP_AVVISI, zone = "Europe/Rome")
	public int updateAvvisi() {

		return updateSiteDocuments(AVVISI, webref.getFeedAvvisi(), webref.getSchoolYearAvvisi());

	}

	@Scheduled(cron = CRON_EXP_ARCHIVE, zone = "Europe/Rome")
	public int updateArchive() {

		//noinspection unchecked
		AwareCache<ArchiveEntry> cache = (AwareCache<ArchiveEntry>) cacheService.getCache(DocumentType.ARCHIVE);
		String archiveUrl = webref.getArchiveUrl();
		List<ArchiveEntry> webEntries;

		try {

			// Get archives from site
			webEntries = scraper.archive(archiveUrl);

		} catch(IOException e) {
			log.warn("Couldn't update archive", e);
			return 0;
		}

		// Check cache for new entries
		Map<String, ArchiveEntry> newEntries = webEntries.stream()
				.filter(entry -> cache.miss(entry.getId()))
				.collect(Collectors.toMap(ArchiveEntry::getId, Function.identity()));

		if(newEntries.isEmpty()) {
			log.info("No new ArchiveEntries");
			return 0;
		}

		Set<String> dbKeys;

		try {

			// Get "new" entries that are on db (but not on cache)
			dbKeys = dbHelper.getArchivesWithIds(new ArrayList<>(newEntries.keySet()))
					.stream()
					.map(ArchiveEntry::getId)
					.collect(Collectors.toSet());

		} catch(Exception e) {
			log.warn("Couldn't update archive", e);
			return 0;
		}

		// Remove all new entries that are already on db
		for(String key : dbKeys)
			newEntries.remove(key);

		if(newEntries.isEmpty()) {

			log.info("No new Archives");
			return 0;

		} else {

			// Save on db and update cache
			dbHelper.writeArchives(newEntries.values());
			cache.clearAdded();
			cache.addAll(newEntries.values());

			log.info("Saved {} new ArchiveEntries", newEntries.size());

		}

		// Whether to upload WebRef
		AtomicBoolean updated = new AtomicBoolean(false);

		String feedCircolari = webref.getFeedCircolari();
		String feedAvvisi = webref.getFeedAvvisi();
		String schoolYearCircolari = webref.getSchoolYearCircolari();
		String schoolYearAvvisi = webref.getSchoolYearAvvisi();

		// Get latest archive for circolari
		cache.getValues()
				.stream()
				.filter(entry -> entry.getType() == CIRCOLARI)
				.max(Comparator.comparingInt(ArchiveEntry::getStartYear))
				// Check if WebRef values differ
				.filter(arc -> !arc.getUrl()
						.equals(feedCircolari) || !arc.getSchoolYear()
						.equals(schoolYearCircolari))
				// Update WebRef if values differ
				.ifPresent(arc -> {
					webref.setFeedCircolari(arc.getUrl());
					webref.setSchoolYearCircolari(arc.getSchoolYear());
					updated.set(true);
				});

		// Get latest archive for avvisi
		cache.getValues()
				.stream()
				.filter(entry -> entry.getType() == AVVISI)
				.max(Comparator.comparingInt(ArchiveEntry::getStartYear))
				// Check if WebRef values differ
				.filter(arc -> !arc.getUrl()
						.equals(feedAvvisi) || !arc.getSchoolYear()
						.equals(schoolYearAvvisi))
				// Update WebRef if values differ
				.ifPresent(arc -> {
					webref.setFeedAvvisi(arc.getUrl());
					webref.setSchoolYearAvvisi(arc.getSchoolYear());
					updated.set(true);
				});


		if(updated.get())
			dbHelper.mergeWebRef();

		return newEntries.size();
	}

}
