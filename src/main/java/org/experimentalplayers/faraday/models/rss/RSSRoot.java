package org.experimentalplayers.faraday.models.rss;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import lombok.*;

// Lombok
@ToString
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor

// Jackson XML
@JacksonXmlRootElement(localName = "rss")
@JsonIgnoreProperties(ignoreUnknown = true)

public class RSSRoot {

	private RSSMain channel;

}
