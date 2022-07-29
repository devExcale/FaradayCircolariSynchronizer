package org.experimentalplayers.faraday.scraper;

import lombok.extern.log4j.Log4j2;
import org.apache.logging.log4j.util.Strings;
import org.experimentalplayers.faraday.models.ArchiveEntry;
import org.experimentalplayers.faraday.models.Attachment;
import org.experimentalplayers.faraday.models.DocumentType;
import org.jetbrains.annotations.Nullable;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.experimentalplayers.faraday.models.DocumentType.UNKNOWN;

@Log4j2
public class ParseElement {

	private static class CWAttachment {

		private static final String URL = "a.cattachment";

		private static final String INFO = "div.cwa_info";

		private static final Pattern FILENAME_REGEX = Pattern.compile("Filename: (.+)");

		private static final Pattern SIZE_REGEX = Pattern.compile("Size: (.+)");

	}

	private static class NormalAttachment {

		private static final String URL = "a.at_url";

		private static final String SIZE = "td.at_file_size";

		private static final Pattern FILENAME_REGEX = Pattern.compile("Scarica questo file \\((.+)\\)");

	}

	private static final Pattern YEAR_MATCHER = Pattern.compile("\\D+?(\\d+)\\D+(\\d+)\\D*?");

	/**
	 * @param item &lt;tr&gt;
	 * @return Normal attachment
	 */
	@Nullable
	public static Attachment attachment(Element item) {

		Element urlElement = item.selectFirst(NormalAttachment.URL);

		if(urlElement == null) {
			log.warn("Couldn't get urlElement while parsing attachment");
			return null;
		}

		String url = urlElement.attr("abs:href");

		if(url.isEmpty()) {
			log.warn("Couldn't get url while parsing attachment, follows html\n" + urlElement);
			return null;
		}

		Matcher filenameMatcher = NormalAttachment.FILENAME_REGEX.matcher(urlElement.attr("title"));

		if(!filenameMatcher.matches()) {
			log.warn("Couldn't get filename while parsing attachment, follows html\n" + urlElement);
			return null;
		}

		String filename = filenameMatcher.group(1);

		String size = Optional.ofNullable(item.selectFirst(NormalAttachment.SIZE))
				.map(Element::html)
				.orElse(Strings.EMPTY);

		return Attachment.builder()
				.url(url)
				.filename(filename)
				.size(size)
				.type("attachment")
				.build();
	}

	@Nullable
	public static Attachment cwattachment(Element cwitem) {

		Element urlElement = cwitem.selectFirst(CWAttachment.URL);

		if(urlElement == null) {
			log.warn("Couldn't get urlElement while parsing cwattachment");
			return null;
		}

		String url = urlElement.attr("abs:href");

		if(url.isEmpty()) {
			log.warn("Couldn't get url while parsing cwattachment, follows html\n" + urlElement);
			return null;
		}

		Elements cwinfos = cwitem.select(CWAttachment.INFO);

		List<String> infos = cwinfos.stream()
				.map(Element::html)
				.collect(Collectors.toList());

		String filename = infos.stream()
				.map(CWAttachment.FILENAME_REGEX::matcher)
				.filter(Matcher::matches)
				.map(matcher -> matcher.group(1))
				.filter(s -> s != null && !s.isEmpty())
				.findFirst()
				.orElse(Strings.EMPTY);

		String size = infos.stream()
				.map(CWAttachment.SIZE_REGEX::matcher)
				.filter(Matcher::matches)
				.map(matcher -> matcher.group(1))
				.filter(s -> s != null && !s.isEmpty())
				.findFirst()
				.orElse(Strings.EMPTY);

		if(filename.isEmpty()) {
			log.warn("Couldn't get filename while parsing cwattachment, follows html\n" + urlElement);
			return null;
		}

		return Attachment.builder()
				.url(url)
				.filename(filename)
				.size(size)
				.type("cwattachment")
				.build();
	}

	@Nullable
	public static ArchiveEntry archive(Element a, DocumentType type) {

		String url = a.attr("abs:href");

		if(url.isEmpty()) {
			log.warn("Couldn't get url while parsing ArchiveEntry, follow html\n" + a);
			return null;
		}

		String schoolYear = a.html();

		Matcher years = YEAR_MATCHER.matcher(schoolYear);

		if(!years.matches()) {
			log.warn("Couldn't get schoolYear while parsing ArchiveEntry, follows html\n" + a);
			return null;
		}

		int startYear = Integer.parseInt(years.group(1));
		int endYear = Integer.parseInt(years.group(2));

		return ArchiveEntry.builder()
				.id(String.format("%s %d-%d", type, startYear, endYear))
				.type(type)
				.url(url)
				.schoolYear(schoolYear)
				.startYear(startYear)
				.endYear(endYear)
				.build();
	}

}
