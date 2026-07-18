package com.djt.jukeanator_engine.domain.location.model;

import java.util.Collection;
import java.util.TreeMap;
import com.djt.jukeanator_engine.domain.common.model.AbstractPersistentEntity;

/**
 * Master-only aggregate root holding every provisioned location, keyed by locationId.
 *
 * @author tmyers
 */
public class LocationRootEntity extends AbstractPersistentEntity {

  private static final long serialVersionUID = 1L;

  public static final String LOCATION_LIST_FILENAME = "JukeANator_Locations.oos";

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
