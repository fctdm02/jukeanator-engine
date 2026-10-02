package com.djt.jukeanator_engine.domain.user.model;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.djt.jukeanator_engine.domain.common.exception.EntityAlreadyExistsException;
import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;
import com.djt.jukeanator_engine.domain.common.security.UserRole;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongIdentifier;

/**
 * Covers {@link UserEntity#changeLocationId}. Lives in the model package since the method is
 * package-private (only {@link UserRootEntity#changeLocationId} calls it) -- see {@link
 * UserRootEntityTest} for the same behavior exercised through the root.
 *
 * @author tmyers
 */
class UserEntityTest {

  private static final Integer PREVIOUS_LOCATION_ID = Integer.valueOf(7);
  private static final Integer CONFIRMED_LOCATION_ID = Integer.valueOf(42);
  private static final Integer OTHER_LOCATION_ID = Integer.valueOf(99);

  private static UserEntity newUser() {
    return new UserEntity(Integer.valueOf(1), "Alice", "Smith", "alice@example.com", "hash",
        Integer.valueOf(6), UserRole.ROLE_USER);
  }

  private static UserSongCreditUsageEntity usage(int persistentIdentity, Integer locationId) {
    return new UserSongCreditUsageEntity(Integer.valueOf(persistentIdentity), locationId, -2,
        UserSongCreditUsageType.QUEUE_ADD, Instant.now(), 1, 2, 4);
  }

  @Test
  void changeLocationId_retagsPlayHistoryInOrder_underPreviousIdOnly() {

    UserEntity user = newUser();
    user.addSongToSongPlayHistory(new SongIdentifier(PREVIOUS_LOCATION_ID, 1, 2));
    user.addSongToSongPlayHistory(new SongIdentifier(OTHER_LOCATION_ID, 3, 4));
    user.addSongToSongPlayHistory(new SongIdentifier(PREVIOUS_LOCATION_ID, 5, 6));

    user.changeLocationId(PREVIOUS_LOCATION_ID, CONFIRMED_LOCATION_ID);

    assertEquals(List.of(new SongIdentifier(CONFIRMED_LOCATION_ID, 1, 2),
        new SongIdentifier(OTHER_LOCATION_ID, 3, 4),
        new SongIdentifier(CONFIRMED_LOCATION_ID, 5, 6)), user.getSongPlayHistory());
  }

  @Test
  void changeLocationId_retagsSongsInEveryPlaylist() {

    UserEntity user = newUser();
    PlaylistEntity favorites = user.createMyFavoritesPlaylist();
    favorites.addSong(new SongIdentifier(PREVIOUS_LOCATION_ID, 1, 2));
    PlaylistEntity partyMix = user.restorePlaylist(new PlaylistEntity(Integer.valueOf(2),
        "alice@example.com", "Party Mix", new ArrayList<>(List.of(
            new SongIdentifier(OTHER_LOCATION_ID, 3, 4),
            new SongIdentifier(PREVIOUS_LOCATION_ID, 5, 6)))));

    user.changeLocationId(PREVIOUS_LOCATION_ID, CONFIRMED_LOCATION_ID);

    assertEquals(List.of(new SongIdentifier(CONFIRMED_LOCATION_ID, 1, 2)), favorites.getSongs());
    assertEquals(List.of(new SongIdentifier(OTHER_LOCATION_ID, 3, 4),
        new SongIdentifier(CONFIRMED_LOCATION_ID, 5, 6)), partyMix.getSongs());
  }

  @Test
  void changeLocationId_retagsCreditUsagesUnderPreviousIdOnly() {

    UserEntity user = newUser();
    UserSongCreditUsageEntity ownUsage =
        user.addUserSongCreditUsage(usage(10, PREVIOUS_LOCATION_ID));
    UserSongCreditUsageEntity otherUsage =
        user.addUserSongCreditUsage(usage(11, OTHER_LOCATION_ID));
    UserSongCreditUsageEntity untaggedUsage = user.addUserSongCreditUsage(usage(12, null));

    user.changeLocationId(PREVIOUS_LOCATION_ID, CONFIRMED_LOCATION_ID);

    assertEquals(CONFIRMED_LOCATION_ID, ownUsage.getLocationId());
    assertEquals(OTHER_LOCATION_ID, otherUsage.getLocationId());
    assertNull(untaggedUsage.getLocationId(),
        "A standalone-mode (non-location-attributed) usage should stay untagged");
  }

  @Test
  void changeLocationId_toleratesNullPlayHistoryAndNullPlaylistSongs() {

    // Both can be null after deserializing a user whose JSON omitted them (see UserMapper).
    UserEntity user = newUser();
    user.setSongPlayHistory(null);
    PlaylistEntity emptyPlaylist = user.restorePlaylist(
        new PlaylistEntity(Integer.valueOf(2), "alice@example.com", "Empty", null));

    assertDoesNotThrow(() -> user.changeLocationId(PREVIOUS_LOCATION_ID, CONFIRMED_LOCATION_ID));

    assertNull(user.getSongPlayHistory());
    assertNull(emptyPlaylist.getSongs());
  }

  @Test
  void renamePlaylist_renamesInPlace_keepingIdentityAndSongs() throws Exception {

    UserEntity user = newUser();
    PlaylistEntity partyMix = user.createPlaylist("Party Mix");
    partyMix.addSong(new SongIdentifier(PREVIOUS_LOCATION_ID, 1, 2));
    Integer persistentIdentity = partyMix.getPersistentIdentity();

    assertTrue(user.renamePlaylist("Party Mix", "Dance Mix"));

    assertNull(user.getPlaylistByNameNullIfNotExists("Party Mix"));
    PlaylistEntity renamed = user.getPlaylistByName("Dance Mix");
    assertSame(partyMix, renamed);
    assertEquals(persistentIdentity, renamed.getPersistentIdentity());
    assertEquals(List.of(new SongIdentifier(PREVIOUS_LOCATION_ID, 1, 2)), renamed.getSongs());
  }

  @Test
  void renamePlaylist_toSameName_isNoOp() throws Exception {

    UserEntity user = newUser();
    user.createPlaylist("Party Mix");

    assertFalse(user.renamePlaylist("Party Mix", "Party Mix"));
    assertNotNull(user.getPlaylistByNameNullIfNotExists("Party Mix"));
  }

  @Test
  void renamePlaylist_toExistingName_throws() throws Exception {

    UserEntity user = newUser();
    user.createPlaylist("Party Mix");
    user.createPlaylist("Dance Mix");

    assertThrows(EntityAlreadyExistsException.class,
        () -> user.renamePlaylist("Party Mix", "Dance Mix"));
    assertNotNull(user.getPlaylistByNameNullIfNotExists("Party Mix"));
  }

  @Test
  void renamePlaylist_toOrFromMyFavorites_throws() throws Exception {

    UserEntity user = newUser();
    user.createPlaylist("Party Mix");

    assertThrows(IllegalArgumentException.class, () -> user
        .renamePlaylist(PlaylistEntity.MY_FAVORITES_PLAYLIST_NAME, "Not Favorites"));
    assertThrows(IllegalArgumentException.class,
        () -> user.renamePlaylist("Party Mix", PlaylistEntity.MY_FAVORITES_PLAYLIST_NAME));
  }

  @Test
  void renamePlaylist_missingPlaylist_throws() {

    UserEntity user = newUser();

    assertThrows(EntityDoesNotExistException.class,
        () -> user.renamePlaylist("No Such Playlist", "Anything"));
  }
}
