package org.experimentalplayers.faraday.utils;

import com.google.cloud.firestore.DocumentChange;
import com.google.cloud.firestore.ListenerRegistration;
import com.google.cloud.firestore.Query;
import lombok.extern.log4j.Log4j2;

import java.io.Closeable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@Log4j2
public class AwareCache<K, T> implements Closeable {

	private final Map<K, T> objects;
	private final Map<K, T> additions;
	private final Class<T> type;
	private final Function<T, K> keyExtractor;

	private ListenerRegistration listener;
	private Query query;

	// Go generics!
	public AwareCache(Class<T> type, Function<T, K> keyExtractor) {
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
	public boolean hit(K key) {
		return objects.containsKey(key) || additions.containsKey(key);
	}

	/**
	 * Basically !{@link Map#containsKey(Object key)}
	 *
	 * @param key Key to match
	 * @return True if key is not present, false otherwise
	 */
	public boolean miss(K key) {
		return !objects.containsKey(key) && !additions.containsKey(key);
	}

	public void add(T obj) {
		additions.put(keyExtractor.apply(obj), obj);
	}

	public void clear() {
		objects.clear();
		additions.clear();
	}

	public void clearAdded() {
		additions.clear();
	}

	public Map<K, T> getState() {

		Map<K, T> map = new HashMap<>(objects);
		map.putAll(additions);

		return map;
	}

	public Set<K> getKeys() {

		Set<K> set = new HashSet<>(objects.keySet());
		set.addAll(additions.keySet());

		return set;
	}

	public Set<T> getValues() {

		Set<T> set = new HashSet<>(objects.values());
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

				T obj = change.getDocument()
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

		List<T> fetched;

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

		for(T obj : fetched)
			objects.put(keyExtractor.apply(obj), obj);

	}

}
