package org.experimentalplayers.faraday.scraper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.google.cloud.Timestamp;
import lombok.extern.log4j.Log4j2;
import org.experimentalplayers.faraday.models.ArchiveEntry;
import org.experimentalplayers.faraday.models.Attachment;
import org.experimentalplayers.faraday.models.DocumentType;
import org.experimentalplayers.faraday.models.SiteDocument;
import org.experimentalplayers.faraday.models.SiteDocument.SiteDocumentBuilder;
import org.experimentalplayers.faraday.models.rss.RSSMain;
import org.experimentalplayers.faraday.models.rss.RSSRoot;
import org.experimentalplayers.faraday.utils.Statics;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.Integer.parseInt;
import static java.lang.Math.max;
import static org.experimentalplayers.faraday.models.DocumentType.UNKNOWN;

@Log4j2
@Service
public class WebsiteScraper {

	private static final String HEADER = "div.page-header > h2";

	private static final String ATTACHMENT_CONTAINER = "div.attachmentsContainer";

	private static final String ATTACHMENT_ITEM = "tr";

	private static final String CWATTACHMENT_CONTAINER = "div.cwattachments";

	private static final String CWATTACHMENT_ITEM = "div.cwaitem";

	private static final String ARTICLE_BODY = "div.item-page > div";

	private static final Pattern ARTICLE_ID_REGEX = Pattern.compile("^\\d+");

	/**
	 * Sets only pageUrl, snippet and setDerefAttachments!
	 * Everything else needs to be set by builder.
	 *
	 * @param url
	 * @return
	 */
	public SiteDocumentBuilder document(String url) throws IOException {

		// TODO: exceptions
		Document page = Jsoup.connect(url)
				.get();

		SiteDocumentBuilder builder = SiteDocument.builder()
				.pageUrl(url);

		documentSetArticleId(builder, url);
		documentSetTitleAndPublishDate(builder, page);
		documentSetAttachments(builder, page);
		documentSetSnippet(builder, page);

		return builder;
	}

	public void documentSetTitleAndPublishDate(@NotNull SiteDocumentBuilder builder, @NotNull Document page) {

		Element header = page.selectFirst(HEADER);

		if(header == null)
			return;

		String title = header.html()
				.trim();

		if(title.isEmpty())
			return;

		builder.title(title);

		Statics.findDate(title)
				.map(Timestamp::of)
				.ifPresent(builder::publishDate);

	}

	public void documentSetAttachments(@NotNull SiteDocumentBuilder builder, @NotNull Document page) {

		List<Attachment> attachments = new LinkedList<>();
		Element attachmentContainer = page.selectFirst(ATTACHMENT_CONTAINER);
		Element cwattachmentContainer = page.selectFirst(CWATTACHMENT_CONTAINER);

		// Parse attachments
		if(attachmentContainer != null) {

			attachmentContainer.select(ATTACHMENT_ITEM)
					.stream()
					.map(ParseElement::attachment)
					.filter(Objects::nonNull)
					.forEach(attachments::add);

			// Remove attachments from article snippet
			attachmentContainer.remove();
		}

		// Parse cwattachments
		if(cwattachmentContainer != null) {

			cwattachmentContainer.select(CWATTACHMENT_ITEM)
					.stream()
					.map(ParseElement::cwattachment)
					.filter(Objects::nonNull)
					.forEach(attachments::add);

			// Remove attachments from article snippet
			cwattachmentContainer.remove();
		}

		builder.attachments(attachments);

	}

	public void documentSetSnippet(@NotNull SiteDocumentBuilder builder, @NotNull Document page) {

		String snippet = page.select(ARTICLE_BODY)
				.stream()
				.filter(element -> "articleBody".equals(element.attr("itemprop")))
				.findFirst()
				.map(Element::html)
				.orElse("");

		builder.snippet(snippet);

	}

	public void documentSetArticleId(@NotNull SiteDocumentBuilder builder, @NotNull String url) {

		String[] split = url.split("/");

		if(split[0].isEmpty())
			return;

		Matcher matcher = ARTICLE_ID_REGEX.matcher(split[max(0, split.length - 1)]);
		if(!matcher.find())
			return;

		builder.articleId(parseInt(matcher.group()));

	}

	public RSSMain feed(String baseUri) throws JsonProcessingException {

		// TODO: constant base_url?
		String body = WebClient.create("https://www.itifaraday.edu.it")
				.get()
				.uri(baseUri + "?format=feed&type=rss")
				.retrieve()
				.bodyToMono(String.class)
				.block();

		// TODO: checks
		ObjectMapper mapper = new XmlMapper();
		RSSRoot root = mapper.readValue(body, RSSRoot.class);

		return root.getChannel();
	}

	public List<ArchiveEntry> archive(String url) throws IOException {

		Document page = Jsoup.connect(url)
				.get();

		// Remove attachments from body
		Optional.ofNullable(page.selectFirst(ATTACHMENT_CONTAINER))
				.ifPresent(Node::remove);

		// Get articleBody
		Element articleBody = page.select(ARTICLE_BODY)
				.stream()
				.filter(element -> "articleBody".equals(element.attr("itemprop")))
				.findFirst()
				.orElse(null);

		if(articleBody == null) {
			log.warn("Couldn't get articleBody while parsing page, follows html\n" + page);
			return Collections.emptyList();
		}

		return articleBody.select("p")
				.stream()
				.flatMap(p -> {

					// Deduce type from paragraph content
					DocumentType type = DocumentType.deduce(p.html());

					return (type == UNKNOWN)
							? Stream.empty()
							: Optional.ofNullable(p.nextElementSibling())
									.map(ul -> ul.select("a"))
									.map(Collection::stream)
									.orElseGet(Stream::empty)
									.map(a -> ParseElement.archive(a, type));
				})
				.collect(Collectors.toList());
	}

}
