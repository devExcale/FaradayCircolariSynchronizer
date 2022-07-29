package org.experimentalplayers.faraday.models;

import com.google.cloud.firestore.annotation.DocumentId;
import lombok.*;

@ToString
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArchiveEntry {

	@DocumentId
	private String id;

	private String url;

	private DocumentType type;

	private String schoolYear;

	private Integer startYear;

	private Integer endYear;

}
