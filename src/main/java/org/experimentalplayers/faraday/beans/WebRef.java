package org.experimentalplayers.faraday.beans;

import lombok.*;

@ToString
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WebRef {

	// Site's base url
	private String baseUrl;

	// Site's archive url
	private String archiveUrl;

	// Current year's feed for circolari
	private String feedCircolari;

	// Current year's feed for avvisi
	private String feedAvvisi;

	// Current schoolYear for circolari
	private String schoolYearCircolari;

	// Current schoolYear for avvisi
	private String schoolYearAvvisi;

}
