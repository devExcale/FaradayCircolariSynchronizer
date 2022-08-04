package org.experimentalplayers.faraday.models;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.annotation.DocumentId;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

import java.net.MalformedURLException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.Integer.parseInt;
import static java.lang.Math.max;
import static org.experimentalplayers.faraday.utils.Statics.bytesToHex;

@Log4j2
@Data
@Builder
@AllArgsConstructor
public class SiteDocument {

	private static final Pattern ARTICLE_ID_REGEX = Pattern.compile("^\\d+");

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

	public static Integer articleIdFromUrl(String url) {

		String[] split = url.split("/");
		if(split[0].isEmpty())
			return null;

		Matcher matcher = ARTICLE_ID_REGEX.matcher(split[max(0, split.length - 1)]);
		if(!matcher.find())
			return null;

		return parseInt(matcher.group());
	}

	@DocumentId
	private String id;

	private Integer articleId;

	private String title;

	private String snippet;

	private String pageUrl;

	@EqualsAndHashCode.Exclude
	private List<Attachment> attachments;

	private Timestamp publishDate;

	private DocumentType type;

	private String category;

	private String schoolYear;

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

	public Integer getArticleId() {

		if(articleId != null)
			return articleId;

		articleId = articleIdFromUrl(pageUrl);

		return articleId;
	}

}
