package com.djt.jukeanator_engine.domain.user.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import com.djt.jukeanator_engine.domain.common.security.UserRole;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongIdentifier;

/**
 * Covers {@link UserRootEntity#changeLocationId}, the in-memory counterpart of {@code
 * LocationRepositoryJpaImpl.changeLocationId}'s user table updates.
 *
 * @author tmyers
 */
class UserRootEntityTest {

  private static final Integer PREVIOUS_LOCATION_ID = Integer.valueOf(7);
  private static final Integer CONFIRMED_LOCATION_ID = Integer.valueOf(42);
  private static final Integer OTHER_LOCATION_ID = Integer.valueOf(99);

  @Test
  void changeLocationId_retagsPlayHistoryPlaylistSongsAndCreditUsages_underPreviousIdOnly() {

    UserEntity user = new UserEntity(Integer.valueOf(1), "Alice", "Smith", "alice@example.com",
        "hash", Integer.valueOf(6), UserRole.ROLE_USER);

    user.addSongToSongPlayHistory(new SongIdentifier(PREVIOUS_LOCATION_ID, 1, 2));
    user.addSongToSongPlayHistory(new SongIdentifier(OTHER_LOCATION_ID, 3, 4));

    PlaylistEntity favorites = user.createMyFavoritesPlaylist();
    favorites.addSong(new SongIdentifier(PREVIOUS_LOCATION_ID, 5, 6));
    favorites.addSong(new SongIdentifier(OTHER_LOCATION_ID, 7, 8));

    UserSongCreditUsageEntity ownUsage = user.addUserSongCreditUsage(
        new UserSongCreditUsageEntity(Integer.valueOf(10), PREVIOUS_LOCATION_ID, -2,
            UserSongCreditUsageType.QUEUE_ADD, Instant.now(), 5, 6, 4));
    UserSongCreditUsageEntity otherUsage = user.addUserSongCreditUsage(
        new UserSongCreditUsageEntity(Integer.valueOf(11), OTHER_LOCATION_ID, -2,
            UserSongCreditUsageType.QUEUE_ADD, Instant.now(), 7, 8, 2));

    UserRootEntity root = new UserRootEntity();
    root.addUser(user);

    root.changeLocationId(PREVIOUS_LOCATION_ID, CONFIRMED_LOCATION_ID);

    assertEquals(new SongIdentifier(CONFIRMED_LOCATION_ID, 1, 2), user.getSongPlayHistory().get(0));
    assertEquals(new SongIdentifier(OTHER_LOCATION_ID, 3, 4), user.getSongPlayHistory().get(1));
    assertEquals(new SongIdentifier(CONFIRMED_LOCATION_ID, 5, 6), favorites.getSongs().get(0));
    assertEquals(new SongIdentifier(OTHER_LOCATION_ID, 7, 8), favorites.getSongs().get(1));
    assertEquals(CONFIRMED_LOCATION_ID, ownUsage.getLocationId());
    assertEquals(OTHER_LOCATION_ID, otherUsage.getLocationId());
  }
}
