package org.experimentalplayers.faraday.models;

import org.jetbrains.annotations.NotNull;

public enum DocumentType {

	CIRCOLARI,
	AVVISI,
	ARCHIVE,
	UNKNOWN;

	@NotNull
	public static DocumentType deduce(String word) {

		word = word.toLowerCase();
		DocumentType type = UNKNOWN;

		if(word.contains("circolar"))
			type = CIRCOLARI;

		else if(word.contains("avvis"))
			type = AVVISI;

		return type;
	}

	public @NotNull String pascalCase() {

		String up = name().substring(0, 1)
				.toUpperCase();
		String low = name().substring(1)
				.toLowerCase();

		return up + low;
	}

}
