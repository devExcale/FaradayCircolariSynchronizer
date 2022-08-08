package org.experimentalplayers.faraday.models;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.annotation.DocumentId;
import com.google.cloud.firestore.annotation.Exclude;
import com.google.cloud.firestore.annotation.ServerTimestamp;
import lombok.*;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

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
			try {

				id = URLEncoder.encode(getCategory(), "UTF-8");

			} catch(UnsupportedEncodingException ignored) {
			}

		return id;
	}

	@Exclude
	public String getCategory() {
		return String.format("%s %d/%d", type.pascalCase(), startYear, endYear - 2000);
	}

}
