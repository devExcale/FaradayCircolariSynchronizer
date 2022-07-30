package org.experimentalplayers.faraday.models;

import lombok.*;

@ToString
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Attachment {

	private String filename;

	private String url;

	private String size;

	private String type;

}
