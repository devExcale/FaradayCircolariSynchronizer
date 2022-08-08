package org.experimentalplayers.faraday.models;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.annotation.DocumentId;
import com.google.cloud.firestore.annotation.ServerTimestamp;
import lombok.*;

@ToString
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArchiveEntry implements FireDocument {

	@DocumentId
	private String id;

	private String url;

	private DocumentType type;

	private String schoolYear;

	private Integer startYear;

	private Integer endYear;

	@ServerTimestamp
	private Timestamp lastUpdated;

	public String getId() {

		if(id == null || id.isEmpty())
			id = String.format("%s %d/%d", type.pascalCase(), startYear, endYear - 2000);

		return id;
	}

}
