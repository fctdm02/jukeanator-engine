package com.djt.jukeanator_engine.domain.user.model;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongIdentifier;

/**
 * Covers {@link PlaylistEntity#changeLocationId}. Lives in the model package since the method is
 * package-private (only {@link UserEntity#changeLocationId} calls it).
 *
 * @author tmyers
 */
class PlaylistEntityTest {

  private static final Integer PREVIOUS_LOCATION_ID = Integer.valueOf(7);
  private static final Integer CONFIRMED_LOCATION_ID = Integer.valueOf(42);
  private static final Integer OTHER_LOCATION_ID = Integer.valueOf(99);

  private static PlaylistEntity playlistOf(List<SongIdentifier> songs) {
    return new PlaylistEntity(Integer.valueOf(1), "alice@example.com", "Party Mix", songs);
  }

  @Test
  void changeLocationId_retagsSongsUnderPreviousIdOnly_preservingOrder() {

    PlaylistEntity playlist = playlistOf(new ArrayList<>(List.of(
        new SongIdentifier(PREVIOUS_LOCATION_ID, 1, 2),
        new SongIdentifier(OTHER_LOCATION_ID, 3, 4),
        new SongIdentifier(null, 5, 6),
        new SongIdentifier(PREVIOUS_LOCATION_ID, 7, 8))));

    playlist.changeLocationId(PREVIOUS_LOCATION_ID, CONFIRMED_LOCATION_ID);

    assertEquals(List.of(new SongIdentifier(CONFIRMED_LOCATION_ID, 1, 2),
        new SongIdentifier(OTHER_LOCATION_ID, 3, 4),
        new SongIdentifier(null, 5, 6),
        new SongIdentifier(CONFIRMED_LOCATION_ID, 7, 8)), playlist.getSongs());
  }

  @Test
  void changeLocationId_updatesTheSameListInPlace() {

    // In place (replaceAll), not a swapped-in new list, so the JPA-managed @ElementCollection
    // instance Hibernate is tracking is the one that picks up the change.
    List<SongIdentifier> songs =
        new ArrayList<>(List.of(new SongIdentifier(PREVIOUS_LOCATION_ID, 1, 2)));
    PlaylistEntity playlist = playlistOf(songs);

    playlist.changeLocationId(PREVIOUS_LOCATION_ID, CONFIRMED_LOCATION_ID);

    assertSame(songs, playlist.getSongs());
    assertEquals(new SongIdentifier(CONFIRMED_LOCATION_ID, 1, 2), songs.get(0));
  }

  @Test
  void changeLocationId_leavesAnEmptyPlaylistEmpty() {

    PlaylistEntity playlist = playlistOf(new ArrayList<>());

    playlist.changeLocationId(PREVIOUS_LOCATION_ID, CONFIRMED_LOCATION_ID);

    assertEquals(List.of(), playlist.getSongs());
  }

  @Test
  void changeLocationId_toleratesNullSongs() {

    // Can be null after deserializing a playlist whose JSON omitted its songs (see UserMapper).
    PlaylistEntity playlist = playlistOf(null);

    assertDoesNotThrow(
        () -> playlist.changeLocationId(PREVIOUS_LOCATION_ID, CONFIRMED_LOCATION_ID));
    assertNull(playlist.getSongs());
  }
}
