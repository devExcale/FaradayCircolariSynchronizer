package org.experimentalplayers.faraday.utils;

import java.nio.charset.StandardCharsets;

public class Statics {

	private static final byte[] HEX_ARRAY = "0123456789abcdef".getBytes(StandardCharsets.UTF_8);

	public static String bytesToHex(byte[] bytes) {

		byte[] hexChars = new byte[bytes.length * 2];

		for(int j = 0; j < bytes.length; j++) {
			int v = bytes[j] & 0xFF;
			hexChars[j * 2] = HEX_ARRAY[v >>> 4];
			hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
		}

		return new String(hexChars, StandardCharsets.UTF_8);
	}

}
