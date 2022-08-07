package org.experimentalplayers.faraday.utils;

import com.google.cloud.firestore.annotation.Exclude;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Statics {

	// Convert byte array to human readable String

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

	// Try to find a Date in a String

	private static final Map<String, String> MONTHS_TRANSLATIONS;
	private static final Pattern DATE_PATTERN = Pattern.compile("\\d{1,2}-\\d{1,2}-\\d{4}");
	private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd-MM-yyyy");

	static {

		// Italian support
		MONTHS_TRANSLATIONS = new HashMap<>();
		MONTHS_TRANSLATIONS.put("\\s*gennaio\\s*", "-01-");
		MONTHS_TRANSLATIONS.put("\\s*febbraio\\s*", "-02-");
		MONTHS_TRANSLATIONS.put("\\s*marzo\\s*", "-03-");
		MONTHS_TRANSLATIONS.put("\\s*aprile\\s*", "-04-");
		MONTHS_TRANSLATIONS.put("\\s*maggio\\s*", "-05-");
		MONTHS_TRANSLATIONS.put("\\s*giugno\\s*", "-06-");
		MONTHS_TRANSLATIONS.put("\\s*luglio\\s*", "-07-");
		MONTHS_TRANSLATIONS.put("\\s*agosto\\s*", "-08-");
		MONTHS_TRANSLATIONS.put("\\s*settembre\\s*", "-09-");
		MONTHS_TRANSLATIONS.put("\\s*ottobre\\s*", "-10-");
		MONTHS_TRANSLATIONS.put("\\s*novembre\\s*", "-11-");
		MONTHS_TRANSLATIONS.put("\\s*dicembre\\s*", "-12-");

	}

	public static Optional<? extends Date> findDate(String str) {

		str = str.toLowerCase(Locale.ROOT)
				.replaceAll("/", "-");

		for(Map.Entry<String, String> month : MONTHS_TRANSLATIONS.entrySet())
			str = str.replaceAll(month.getKey(), month.getValue());

		Matcher matcher = DATE_PATTERN.matcher(str);
		if(!matcher.find())
			return Optional.empty();

		Optional<Date> opt = Optional.empty();

		try {

			Date date = DATE_FORMAT.parse(matcher.group());
			opt = Optional.of(date);

		} catch(ParseException ignored) {
		}

		return opt;
	}

	public static List<String> nonNullFields(Object obj, String... otherFields) {

		Set<String> others = new HashSet<>(Arrays.asList(otherFields));
		Field[] fields = obj.getClass()
				.getDeclaredFields();

		return Arrays.stream(fields)
				.filter(field -> !field.isAnnotationPresent(Exclude.class))
				.peek(field -> field.setAccessible(true))
				.filter(field -> !Modifier.isStatic(field.getModifiers()))
				.filter(field -> {
					try {

						return Objects.nonNull(field.get(obj)) || others.contains(field.getName());

					} catch(IllegalAccessException e) {
						return false;
					}
				})
				.map(Field::getName)
				.collect(Collectors.toList());
	}

}
