package org.experimentalplayers.faraday.models.rest;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateRequest {

	// Archive info

	private String documentType;

	private String schoolYear;

	// Single document

	private String url;

}
