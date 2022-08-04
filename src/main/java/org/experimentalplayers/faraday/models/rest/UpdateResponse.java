package org.experimentalplayers.faraday.models.rest;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateResponse {

	private String collection;

	private Integer updated;

	private Long opTime;

}
