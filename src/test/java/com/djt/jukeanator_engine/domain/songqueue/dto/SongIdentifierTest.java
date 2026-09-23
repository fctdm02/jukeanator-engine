package com.djt.jukeanator_engine.domain.songqueue.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Covers {@link SongIdentifier#withLocationIdChanged}, used to re-tag in-memory song identifiers
 * when this instance's own location id is corrected post-handshake.
 *
 * @author tmyers
 */
class SongIdentifierTest {

  private static final Integer PREVIOUS_LOCATION_ID = Integer.valueOf(7);
  private static final Integer CONFIRMED_LOCATION_ID = Integer.valueOf(42);
  private static final Integer OTHER_LOCATION_ID = Integer.valueOf(99);

  @Test
  void withLocationIdChanged_returnsNewIdentifierWithNewLocationId_whenLocationMatches() {

    SongIdentifier original = new SongIdentifier(PREVIOUS_LOCATION_ID, 10, 11);

    SongIdentifier rekeyed =
        original.withLocationIdChanged(PREVIOUS_LOCATION_ID, CONFIRMED_LOCATION_ID);

    assertNotSame(original, rekeyed);
    assertEquals(new SongIdentifier(CONFIRMED_LOCATION_ID, 10, 11), rekeyed);
  }

  @Test
  void withLocationIdChanged_leavesTheOriginalUnchanged_whenLocationMatches() {

    SongIdentifier original = new SongIdentifier(PREVIOUS_LOCATION_ID, 10, 11);

    original.withLocationIdChanged(PREVIOUS_LOCATION_ID, CONFIRMED_LOCATION_ID);

    // SongIdentifier is immutable (and used as a value in hashed collections), so re-tagging must
    // never mutate the receiver.
    assertEquals(PREVIOUS_LOCATION_ID, original.getLocationId());
    assertEquals(Integer.valueOf(10), original.getAlbumId());
    assertEquals(Integer.valueOf(11), original.getSongId());
  }

  @Test
  void withLocationIdChanged_returnsTheSameInstance_whenLocationDoesNotMatch() {

    SongIdentifier original = new SongIdentifier(OTHER_LOCATION_ID, 10, 11);

    // Callers (e.g. BackgroundMusicServiceImpl) rely on identity to detect "nothing changed", so a
    // non-match must return this very instance rather than an equal copy.
    assertSame(original,
        original.withLocationIdChanged(PREVIOUS_LOCATION_ID, CONFIRMED_LOCATION_ID));
  }

  @Test
  void withLocationIdChanged_returnsTheSameInstance_whenIdentifierHasNoLocation() {

    SongIdentifier original = new SongIdentifier(null, 10, 11);

    assertSame(original,
        original.withLocationIdChanged(PREVIOUS_LOCATION_ID, CONFIRMED_LOCATION_ID));
  }

  @Test
  void withLocationIdChanged_rejectsNullOldLocationId() {

    // A null oldLocationId would otherwise match (and re-tag) every identifier with no location.
    SongIdentifier original = new SongIdentifier(null, 10, 11);

    assertThrows(NullPointerException.class,
        () -> original.withLocationIdChanged(null, CONFIRMED_LOCATION_ID));
  }
}
