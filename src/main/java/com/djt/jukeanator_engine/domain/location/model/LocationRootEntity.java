package com.djt.jukeanator_engine.domain.location.model;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;
import com.djt.jukeanator_engine.domain.common.model.AbstractPersistentEntity;

/**
 * Master-only in-memory aggregate root holding every provisioned location, keyed by locationId.
 * Not itself JPA-mapped: there is no {@code location_root} table -- {@code
 * LocationRepositoryJpaImpl} loads every {@link LocationEntity} row directly and assembles this
 * aggregate around them in memory, since a relational schema has no need for a singleton "root"
 * row to own a one-table collection. Same pattern as {@code UserRootEntity}.
 *
 * @author tmyers
 */
public class LocationRootEntity extends AbstractPersistentEntity {

  private static final long serialVersionUID = 1L;

  public static final String LOCATION_LIST_FILENAME = "JukeANator_Locations.json";

  private TreeMap<Integer, LocationEntity> locations = new TreeMap<>();

  // Ids of locations removed through removeLocation() since the last store. A JPA store deletes
  // exactly these rows, never "every row not in memory": master loads the location table once at
  // startup, so a row inserted by hand while it runs is absent from memory, and must survive the
  // next store (see LocationServiceImpl#resolveAndVerifyByApiKey, which picks such rows up).
  // Not serialized: LocationMapper maps only the locations themselves.
  private transient Set<Integer> removedLocationIds = new HashSet<>();

  public LocationRootEntity() {
    super(Integer.valueOf(0));
  }

  @Override
  public String getNaturalIdentity() {
    return "LocationRootEntity";
  }

  public Collection<LocationEntity> getLocations() {
    return this.locations.values();
  }

  public LocationEntity addLocation(LocationEntity location) {
    removedLocationIds().remove(location.getPersistentIdentity());
    return this.locations.put(location.getPersistentIdentity(), location);
  }

  public LocationEntity removeLocation(Integer locationId) {
    LocationEntity removed = this.locations.remove(locationId);
    if (removed != null) {
      removedLocationIds().add(locationId);
    }
    return removed;
  }

  /** The ids removed since the last store; see {@link #forgetRemovedLocationIds}. */
  public Set<Integer> pendingRemovedLocationIds() {
    return Set.copyOf(removedLocationIds());
  }

  /** Called once a store has deleted {@code storedIds}, so they are not deleted again. */
  public void forgetRemovedLocationIds(Set<Integer> storedIds) {
    removedLocationIds().removeAll(storedIds);
  }

  // Lazily (re)created: a transient field is null after Java deserialization.
  private Set<Integer> removedLocationIds() {
    if (this.removedLocationIds == null) {
      this.removedLocationIds = new HashSet<>();
    }
    return this.removedLocationIds;
  }

  public LocationEntity getLocationByIdNullIfNotExists(Integer locationId) {
    return this.locations.get(locationId);
  }
}
