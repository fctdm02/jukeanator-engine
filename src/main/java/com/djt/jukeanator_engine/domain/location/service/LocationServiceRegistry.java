package com.djt.jukeanator_engine.domain.location.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.djt.jukeanator_engine.domain.songlibrary.service.SongLibraryService;
import com.djt.jukeanator_engine.domain.songlibrary.service.SongLibraryServiceLocationProxy;
import com.djt.jukeanator_engine.domain.songplayer.service.SongPlayerService;
import com.djt.jukeanator_engine.domain.songplayer.service.SongPlayerServiceLocationProxy;
import com.djt.jukeanator_engine.domain.songqueue.service.SongQueueService;
import com.djt.jukeanator_engine.domain.songqueue.service.SongQueueServiceLocationProxy;

/**
 * Master-only. Resolves the right per-location service instance for a given locationId, lazily —
 * locations are provisioned at runtime (not at application-context-startup), so a fixed set of
 * Spring beans can't represent them; a lazily-populated registry is the only approach compatible
 * with that.
 *
 * @author tmyers
 */
public class LocationServiceRegistry {

  private final SlaveCommandGateway slaveCommandGateway;
  private final LocationService locationService;

  private final Map<String, SongQueueService> songQueueProxies = new ConcurrentHashMap<>();
  private final Map<String, SongPlayerService> songPlayerProxies = new ConcurrentHashMap<>();
  private final Map<String, SongLibraryService> songLibraryProxies = new ConcurrentHashMap<>();

  public LocationServiceRegistry(SlaveCommandGateway slaveCommandGateway,
      LocationService locationService) {
    this.slaveCommandGateway = slaveCommandGateway;
    this.locationService = locationService;
  }

  public SongQueueService resolveSongQueueService(String locationId) {
    return songQueueProxies.computeIfAbsent(locationId,
        id -> new SongQueueServiceLocationProxy(id, slaveCommandGateway));
  }

  public SongPlayerService resolveSongPlayerService(String locationId) {
    return songPlayerProxies.computeIfAbsent(locationId,
        id -> new SongPlayerServiceLocationProxy(id, slaveCommandGateway));
  }

  public SongLibraryService resolveSongLibraryService(String locationId) {
    return songLibraryProxies.computeIfAbsent(locationId,
        id -> new SongLibraryServiceLocationProxy(id, locationService));
  }
}
