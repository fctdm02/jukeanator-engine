package com.djt.jukeanator_engine.domain.location.service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Master-only. Tracks which locations currently have a live {@code /ws-slave} STOMP session, so
 * {@link LocationService#listLocations()} can report a real "online" status instead of a
 * time-since-last-sync heuristic, and so {@link SlaveCommandGateway} can fail fast with a clean
 * "location offline" error instead of waiting out a full timeout for a location that is obviously
 * not connected.
 *
 * @author tmyers
 */
public class ConnectedSlaveRegistry {

  private final ConcurrentHashMap<String, String> sessionIdByLocationId = new ConcurrentHashMap<>();

  public void markConnected(String locationId, String sessionId) {
    sessionIdByLocationId.put(locationId, sessionId);
  }

  public void markDisconnected(String sessionId) {
    sessionIdByLocationId.values().removeIf(sessionId::equals);
  }

  public boolean isConnected(String locationId) {
    return sessionIdByLocationId.containsKey(locationId);
  }
}
