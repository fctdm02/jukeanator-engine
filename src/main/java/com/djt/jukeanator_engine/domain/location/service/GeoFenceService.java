package com.djt.jukeanator_engine.domain.location.service;

import com.djt.jukeanator_engine.domain.location.dto.GeoFenceStatusDto;
import com.djt.jukeanator_engine.domain.location.exception.GeoFenceViolationException;
import com.djt.jukeanator_engine.domain.location.model.GeoPosition;

/**
 * Restricts Web/Mobile UI queue operations at a geo-fenced location to patrons who are physically
 * there. See {@link com.djt.jukeanator_engine.domain.location.config.GeoFenceProperties} for when
 * the fence is enforced.
 */
public interface GeoFenceService {

  /**
   * @return true if queue operations at the given location require a device position inside the
   *         fence
   */
  boolean isEnforced(Integer locationId);

  /** @return the fence settings the Web/Mobile UI needs for the given location */
  GeoFenceStatusDto getStatus(Integer locationId);

  /**
   * Returns normally if the fence is not enforced at the given location, or if the position is
   * recent, accurate enough and inside the fence.
   *
   * @param position the device position sent by the client, or null if none was sent
   * @throws GeoFenceViolationException otherwise
   */
  void verifyWithinFence(Integer locationId, GeoPosition position);
}
