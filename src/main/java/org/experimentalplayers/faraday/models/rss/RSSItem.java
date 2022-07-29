package org.experimentalplayers.faraday.models.rss;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

// Lombok
@ToString
@Getter
@NoArgsConstructor
@AllArgsConstructor

// Jackson XML
@JsonIgnoreProperties(ignoreUnknown = true)

public class RSSItem {

	private String title;

	private String link;

	private String description;

	private String category;

	private String pubDate;

}
