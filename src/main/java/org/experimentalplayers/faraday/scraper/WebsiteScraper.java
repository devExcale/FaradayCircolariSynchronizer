package org.experimentalplayers.faraday.scraper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import lombok.extern.log4j.Log4j2;
import org.experimentalplayers.faraday.models.ArchiveEntry;
import org.experimentalplayers.faraday.models.Attachment;
import org.experimentalplayers.faraday.models.DocumentType;
import org.experimentalplayers.faraday.models.SiteDocument;
import org.experimentalplayers.faraday.models.rss.RSSMain;
import org.experimentalplayers.faraday.models.rss.RSSRoot;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.experimentalplayers.faraday.models.DocumentType.UNKNOWN;

@Log4j2
@Service
public class WebsiteScraper {

	private static final String ATTACHMENT_CONTAINER = ".attachmentsContainer";

	private static final String ATTACHMENT_ITEM = "tr";

	private static final String CWATTACHMENT_CONTAINER = "div.cwattachments";

	private static final String CWATTACHMENT_ITEM = "div.cwaitem";

	private static final String ARTICLE_BODY = "div.item-page > div";

	public SiteDocument document(String url) throws IOException {

		// TODO: exceptions
		Document page = Jsoup.connect(url)
				.get();

		Element attachmentContainer = page.selectFirst(ATTACHMENT_CONTAINER);
		Element cwattachmentContainer = page.selectFirst(CWATTACHMENT_CONTAINER);

		List<Attachment> attachments = new LinkedList<>();

		// Parse attachments
		if(attachmentContainer != null) {

			attachmentContainer.select(ATTACHMENT_ITEM)
					.stream()
					.map(ParseElement::attachment)
					.filter(Objects::nonNull)
					.forEach(attachments::add);

			attachmentContainer.remove();
		}

		// Parse cwattachments
		if(cwattachmentContainer != null) {

			cwattachmentContainer.select(CWATTACHMENT_ITEM)
					.stream()
					.map(ParseElement::cwattachment)
					.filter(Objects::nonNull)
					.forEach(attachments::add);

			cwattachmentContainer.remove();
		}

		String snippet = page.select(ARTICLE_BODY)
				.stream()
				.filter(element -> "articleBody".equals(element.attr("itemprop")))
				.findFirst()
				.map(Element::html)
				.orElse("");

		return SiteDocument.builder()
				.pageUrl(url)
				.snippet(snippet)
				.derefAttachments(attachments)
				.build();
	}

	public RSSMain feed(String baseUri) throws JsonProcessingException {

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
