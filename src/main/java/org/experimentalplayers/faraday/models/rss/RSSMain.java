package org.experimentalplayers.faraday.models.rss;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.*;

import java.util.LinkedList;
import java.util.List;

// Lombok
@ToString
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor

// Jackson XML
@JsonIgnoreProperties(ignoreUnknown = true)

public class RSSMain {

	private String title;

	private String description;

	private String link;

	private String lastBuildDate;

	private String language;

	@JacksonXmlElementWrapper(useWrapping = false)
	@JacksonXmlProperty(localName = "item")
	private List<RSSItem> items = new LinkedList<>();

	public void setItems(List<RSSItem> items) {
		// https://stackoverflow.com/questions/36345278/jackson-map-different-unwrapped-elements-in-same-pojo
		// Thanks jackson
		this.items.addAll(items);
	}

}
