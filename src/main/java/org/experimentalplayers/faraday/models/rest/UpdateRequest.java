package org.experimentalplayers.faraday.models.rest;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateRequest {

	private String documentType;

	private Integer startYear;

	private String url;

}
