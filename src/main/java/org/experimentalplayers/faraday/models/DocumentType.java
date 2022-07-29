package org.experimentalplayers.faraday.models;

import org.jetbrains.annotations.NotNull;

public enum DocumentType {

	CIRCOLARE,
	AVVISO,
	UNKNOWN;

	@NotNull
	public static DocumentType deduce(String word) {

		word = word.toLowerCase();
		DocumentType type = UNKNOWN;

		if(word.contains("circolar"))
			type = CIRCOLARE;

		else if(word.contains("avvis"))
			type = AVVISO;

		return type;
	}

}
