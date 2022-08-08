package org.experimentalplayers.faraday.models.rest;

import lombok.*;
import org.experimentalplayers.faraday.models.FireDocument;

import java.util.Collection;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateResponse {

	private String collection;

	private Long opTime;

	private Integer documentsUpdated;

	private Collection<FireDocument> documents;

}
