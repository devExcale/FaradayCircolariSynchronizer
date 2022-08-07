package org.experimentalplayers.faraday.utils;

import com.google.cloud.firestore.DocumentChange;
import com.google.cloud.firestore.ListenerRegistration;
import com.google.cloud.firestore.Query;
import lombok.extern.log4j.Log4j2;
import org.experimentalplayers.faraday.models.FireDocument;
import org.jetbrains.annotations.NotNull;

import java.io.Closeable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@Log4j2
public class AwareCache<Doc extends FireDocument> implements Closeable {

	private final Map<String, Doc> objects;
	private final Map<String, Doc> additions;
	private final Class<Doc> type;
	private final Function<Doc, String> keyExtractor;

	private ListenerRegistration listener;
	private Query query;

	// Go generics!
	public AwareCache(Class<Doc> type, Function<Doc, String> keyExtractor) {
		this.type = type;
		this.keyExtractor = keyExtractor;
		objects = new ConcurrentHashMap<>();
		additions = new ConcurrentHashMap<>();
		listener = null;
	}

	/**
	 * Basically {@link Map#containsKey(Object key)}
	 *
	 * @param key Key to match
	 * @return True if key is present, false otherwise
	 */
	public boolean hit(String key) {
		return objects.containsKey(key) || additions.containsKey(key);
	}

	/**
	 * Basically !{@link Map#containsKey(Object key)}
	 *
	 * @param key Key to match
	 * @return True if key is not present, false otherwise
	 */
	public boolean miss(String key) {
		return !objects.containsKey(key) && !additions.containsKey(key);
	}

	public void add(@NotNull Doc doc) {
		additions.put(keyExtractor.apply(doc), doc);
	}

	public void addAll(@NotNull Collection<Doc> docs) {
		for(Doc doc : docs)
			additions.put(keyExtractor.apply(doc), doc);
	}

	public void clear() {
		objects.clear();
		additions.clear();
	}

	public void clearAdded() {
		additions.clear();
	}

	public Class<? extends FireDocument> getType() {
		return type;
	}

	public Map<String, Doc> getState() {

		Map<String, Doc> map = new HashMap<>(objects);
		map.putAll(additions);

		return map;
	}

	public Set<String> getKeys() {

		Set<String> set = new HashSet<>(objects.keySet());
		set.addAll(additions.keySet());

		return set;
	}

	public Set<Doc> getValues() {

		Set<Doc> set = new HashSet<>(objects.values());
		set.addAll(additions.values());

		return set;
	}

	public void open(Query query) {

		close();
		objects.clear();

		this.query = query;
		this.listener = query.addSnapshotListener((snapshots, e) -> {

			if(e != null) {
				log.warn("Couldn't get snapshot updates", e);
				return;
			}

			//noinspection ConstantConditions
			for(DocumentChange change : snapshots.getDocumentChanges()) {

				Doc obj = change.getDocument()
						.toObject(type);

				switch(change.getType()) {

					case ADDED:
					case MODIFIED:
						objects.put(keyExtractor.apply(obj), obj);
						break;

					case REMOVED:
						objects.remove(keyExtractor.apply(obj));

				}
			}

		});

	}

	@Override
	public void close() {

		if(this.listener != null)
			this.listener.remove();

	}

	public void forceRefresh(boolean clearAdditions) {

		if(query == null)
			return;

		List<Doc> fetched;

		try {

			fetched = query.get()
					.get()
					.toObjects(type);

		} catch(Exception ignored) {
			return;
		}

		if(clearAdditions)
			additions.clear();
		objects.clear();

		for(Doc obj : fetched)
			objects.put(keyExtractor.apply(obj), obj);

	}

}
