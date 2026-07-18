package com.djt.jukeanator_engine.domain.location.dto;

import java.util.List;

/**
 * Returned to the slave after a metadata sync — tells it which albums' cover art master doesn't
 * have yet (or has a stale hash for), so the slave only uploads the delta instead of re-sending
 * every album's art on every scan.
 */
public record LibrarySyncAckDto(List<Integer> sourceAlbumIdsNeedingCoverArt) {
}
