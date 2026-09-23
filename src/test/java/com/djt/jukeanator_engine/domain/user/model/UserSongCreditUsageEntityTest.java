package com.djt.jukeanator_engine.domain.user.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Covers {@link UserSongCreditUsageEntity#changeLocationId}. Lives in the model package since the
 * method is package-private (only {@link UserEntity#changeLocationId} calls it).
 *
 * @author tmyers
 */
class UserSongCreditUsageEntityTest {

  private static final Integer PREVIOUS_LOCATION_ID = Integer.valueOf(7);
  private static final Integer CONFIRMED_LOCATION_ID = Integer.valueOf(42);
  private static final Integer OTHER_LOCATION_ID = Integer.valueOf(99);

  private static final Instant TIMESTAMP = Instant.parse("2026-09-23T12:00:00Z");

  private static UserSongCreditUsageEntity usage(Integer locationId) {
    return new UserSongCreditUsageEntity(Integer.valueOf(10), locationId, -2,
        UserSongCreditUsageType.QUEUE_ADD, TIMESTAMP, Integer.valueOf(1), Integer.valueOf(2), 4);
  }

  @Test
  void changeLocationId_replacesLocationId_andPreservesEveryOtherField_whenLocationMatches() {

    UserSongCreditUsageEntity usage = usage(PREVIOUS_LOCATION_ID);

    usage.changeLocationId(PREVIOUS_LOCATION_ID, CONFIRMED_LOCATION_ID);

    assertEquals(CONFIRMED_LOCATION_ID, usage.getLocationId());
    assertEquals(Integer.valueOf(10), usage.getPersistentIdentity());
    assertEquals(-2, usage.getAmount());
    assertEquals(UserSongCreditUsageType.QUEUE_ADD, usage.getType());
    assertEquals(TIMESTAMP, usage.getTimestamp());
    assertEquals(Integer.valueOf(1), usage.getSongAlbumId());
    assertEquals(Integer.valueOf(2), usage.getSongId());
    assertEquals(4, usage.getResultingBalance());
  }

  @Test
  void changeLocationId_leavesLocationIdAlone_whenLocationDoesNotMatch() {

    UserSongCreditUsageEntity usage = usage(OTHER_LOCATION_ID);

    usage.changeLocationId(PREVIOUS_LOCATION_ID, CONFIRMED_LOCATION_ID);

    assertEquals(OTHER_LOCATION_ID, usage.getLocationId());
  }

  @Test
  void changeLocationId_leavesAnUntaggedUsageUntagged() {

    // Standalone-mode spends are never location-attributed (see the entity's javadoc) and must
    // stay that way through an id correction.
    UserSongCreditUsageEntity usage = usage(null);

    usage.changeLocationId(PREVIOUS_LOCATION_ID, CONFIRMED_LOCATION_ID);

    assertNull(usage.getLocationId());
  }
}
