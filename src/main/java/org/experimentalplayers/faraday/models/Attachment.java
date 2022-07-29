package org.experimentalplayers.faraday.models;

import com.google.cloud.firestore.annotation.DocumentId;
import lombok.*;

@ToString
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Attachment {

	@DocumentId
	private String id;

	private String filename;

	private String url;

	private String size;

	private String type;

}
