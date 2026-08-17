package com.djt.jukeanator_engine.domain.location.model;

import java.util.Collection;
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

  private TreeMap<String, LocationEntity> locations = new TreeMap<>();

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
    return this.locations.put(location.getLocationId(), location);
  }

  public LocationEntity getLocationByIdNullIfNotExists(String locationId) {
    return this.locations.get(locationId);
  }
}
