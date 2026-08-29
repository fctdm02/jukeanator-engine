package com.djt.jukeanator_engine.domain.songlibrary.repository;

import java.io.Serializable;
import java.util.Objects;

/**
 * {@code @IdClass} companion for {@link SongLibraryJpaEntity}'s composite primary key. Field
 * names must match the entity's {@code @Id}-annotated fields exactly (JPA spec requirement) --
 * see {@code locationId}/{@code id} there.
 *
 * @author tmyers
 */
public class SongLibraryJpaEntityId implements Serializable {

  private static final long serialVersionUID = 1L;

  private Integer locationId;
  private Integer id;

  public SongLibraryJpaEntityId() {} // for JPA

  public SongLibraryJpaEntityId(Integer locationId, Integer id) {
    this.locationId = locationId;
    this.id = id;
  }

  public Integer getLocationId() {
    return locationId;
  }

  public Integer getId() {
    return id;
  }

  @Override
  public boolean equals(Object that) {

    if (this == that) {
      return true;
    }
    if (!(that instanceof SongLibraryJpaEntityId other)) {
      return false;
    }
    return Objects.equals(this.locationId, other.locationId) && Objects.equals(this.id, other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(locationId, id);
  }
}
