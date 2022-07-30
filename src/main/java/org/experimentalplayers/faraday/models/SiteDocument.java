package org.experimentalplayers.faraday.models;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.annotation.DocumentId;
import lombok.*;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

import java.net.MalformedURLException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedList;
import java.util.List;

import static org.experimentalplayers.faraday.utils.Statics.bytesToHex;

@Log4j2
@ToString
@Getter
@Setter
@Builder
@AllArgsConstructor
public class SiteDocument {

	public static String idFromUrl(String url) {

		try {

			return idFromUrl(new URL(url));

		} catch(Exception ignored) {
			return "";
		}

	}

	@NotNull
	public static String idFromUrl(URL url) {

		String path = url.getPath();

		MessageDigest md;

		try {

			md = MessageDigest.getInstance("SHA-256");
			md.update(path.getBytes());

		} catch(NoSuchAlgorithmException e) {
			// Should happen only if no SHA-256 hashing implementation was found
			// Not a recurring thing, hence the RuntimeException
			throw new RuntimeException(e);
		}


		return bytesToHex(md.digest());
	}

	@DocumentId
	private String id;

	private String title;

	private String snippet;

	private String pageUrl;

	private List<Attachment> attachments;

	private Timestamp publishDate;

	private DocumentType type;

	private String category;

	public SiteDocument() {
		attachments = new LinkedList<>();
	}

	public String getId() {

		if(id != null && !id.isEmpty())
			return id;

		if(pageUrl == null)
			return null;

		try {

			id = idFromUrl(new URL(pageUrl));

		} catch(MalformedURLException ignored) {
		}

		return id;
	}

}
