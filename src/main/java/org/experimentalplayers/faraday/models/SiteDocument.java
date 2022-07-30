package org.experimentalplayers.faraday.models;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.annotation.DocumentId;
import com.google.cloud.firestore.annotation.Exclude;
import com.google.firebase.cloud.FirestoreClient;
import lombok.*;
import lombok.extern.log4j.Log4j2;

import java.util.LinkedList;
import java.util.List;

@Log4j2
@ToString
@Getter
@Setter
@Builder
@AllArgsConstructor
public class SiteDocument {

	@DocumentId
	private String id;

	private String title;

	private String snippet;

	private String pageUrl;

	private List<DocumentReference> attachments;

	@ToString.Exclude
	@Exclude
	private List<Attachment> derefAttachments;

	private Timestamp publishDate;

	private DocumentType type;

	private String category;

	public SiteDocument() {
		attachments = new LinkedList<>();
		derefAttachments = new LinkedList<>();
	}

	public List<DocumentReference> getAttachments() {

		if(attachments == null)
			attachments = new LinkedList<>();

		return attachments;
	}

	@Exclude
	public void setDerefAttachments(List<Attachment> derefAttachments) {
		this.derefAttachments = derefAttachments;
	}

	@Exclude
	public List<Attachment> getDerefAttachments() {

		if(derefAttachments == null)
			derefAttachments = new LinkedList<>();

		if(!getAttachments().isEmpty() && derefAttachments.isEmpty())
			updateAttachmentsFromReferences();

		return derefAttachments;
	}

	@Exclude
	public List<Attachment> updateAttachmentsFromReferences() {

		if(derefAttachments == null)
			derefAttachments = new LinkedList<>();
		else
			derefAttachments.clear();

		if(attachments.isEmpty())
			return derefAttachments;

		try {

			FirestoreClient.getFirestore()
					.getAll(this.attachments.toArray(new DocumentReference[0]))
					.get()
					.stream()
					.map(snap -> snap.toObject(Attachment.class))
					.forEach(derefAttachments::add);

		} catch(Exception e) {

			log.warn("Couldn't get Attachments from references", e);

		}

		return derefAttachments;
	}

}
