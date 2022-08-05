package org.experimentalplayers.faraday.models;

import com.google.cloud.Timestamp;

public interface FireDocument {

	String getId();

	DocumentType getType();

	Timestamp getLastUpdated();

}
