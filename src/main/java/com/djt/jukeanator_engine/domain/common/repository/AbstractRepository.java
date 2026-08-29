package com.djt.jukeanator_engine.domain.common.repository;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class AbstractRepository {

	private final static AtomicInteger nextPersistentIdentityValue = new AtomicInteger(1);

	/**
	 * Returns the next unique persistent identity for this repository instance.
	 * Must be seeded via {@link #seedNextPersistentIdentityFrom(Stream)} (typically
	 * at the end of a load) before being used to mint identities for newly-created
	 * entities, otherwise numbering restarts at 1 and can collide with
	 * previously-persisted identities.
	 */
	synchronized public static Integer getNextPersistentIdentityValue() {

		return Integer.valueOf(nextPersistentIdentityValue.incrementAndGet());
	}

	/**
	 * Seeds this repository's identity counter from the maximum identity found
	 * among {@code existingIds} (e.g. entities just loaded from disk), so
	 * subsequently minted identities never collide with ones already persisted.
	 */
	synchronized public static void seedNextPersistentIdentityFrom(Stream<Integer> existingIds) {

		int max = existingIds.filter(Objects::nonNull).mapToInt(Integer::intValue).max().orElse(1);

		nextPersistentIdentityValue.updateAndGet(current -> Math.max(current, max));
	}
}