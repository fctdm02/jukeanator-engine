package com.djt.jukeanator_engine.domain.songqueue.dto;

import java.util.List;

/**
 * {@code skipIneligibleSongs} and {@code maxSongs} are both optional ({@code null} = off /
 * unlimited). With {@code skipIneligibleSongs}, the instance that owns the queue checks each song
 * against {@code isSongEligibleForQueue} (as the queue grows) and skips ineligible or missing songs
 * instead of queueing them; {@code maxSongs} stops queueing once that many songs were added. Both
 * are evaluated where the queue lives, so a remote location still needs only one command -- used to
 * play a web user's playlist, where {@code maxSongs} is the number of songs the user can afford.
 */
public record AddMultipleSongsToQueueRequest(String username, List<SongIdentifier> songIdentifiers,
    Integer priority, Boolean skipIneligibleSongs, Integer maxSongs) {

  public AddMultipleSongsToQueueRequest(String username, List<SongIdentifier> songIdentifiers,
      Integer priority) {
    this(username, songIdentifiers, priority, null, null);
  }
}
