package com.djt.jukeanator_engine.domain.useractivity.model;

import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import com.djt.jukeanator_engine.domain.common.model.AbstractPersistentEntity;

/**
 * Flat JPA persistence row for the {@code user_activity} table -- follows the same
 * self-contained-entity shape as {@code SongQueueEntryJpaEntity} rather than extending {@link
 * AbstractPersistentEntity}, since activity records are append-only log rows with no aggregate-root
 * semantics (no load/store round trip, no child collections). {@code details} holds the
 * activity-specific payload (tab name, search query, artist/album/song id, ...) serialized as a
 * JSON string by {@code UserActivityRepositoryJpaImpl}; kept as a plain {@code String} column here
 * rather than a Hibernate-specific JSON type so this entity stays consistent with the rest of the
 * codebase's plain-Jakarta-persistence style.
 */
@Entity
@Table(name = "user_activity")
public class UserActivityEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE,
      generator = AbstractPersistentEntity.PERSISTENT_IDENTITY_SEQUENCE)
  @SequenceGenerator(name = AbstractPersistentEntity.PERSISTENT_IDENTITY_SEQUENCE,
      sequenceName = AbstractPersistentEntity.PERSISTENT_IDENTITY_SEQUENCE, allocationSize = 1)
  @Column(name = "persistent_identity")
  private Integer persistentIdentity;

  @Column(name = "location_id")
  private Integer locationId;

  @Column(name = "source", nullable = false)
  private String source;

  @Column(name = "username", nullable = false)
  private String username;

  @Column(name = "activity_type", nullable = false)
  private String activityType;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  @Column(name = "details", columnDefinition = "json")
  private String details;

  @CreationTimestamp
  @Column(name = "date_added", nullable = false, updatable = false)
  private Instant dateAdded;

  @UpdateTimestamp
  @Column(name = "date_updated", nullable = false)
  private Instant dateUpdated;

  protected UserActivityEntity() {} // for JPA

  public UserActivityEntity(Integer locationId, String source, String username,
      String activityType, Instant occurredAt, String details) {
    this.locationId = locationId;
    this.source = source;
    this.username = username;
    this.activityType = activityType;
    this.occurredAt = occurredAt;
    this.details = details;
  }

  public Integer getPersistentIdentity() {
    return persistentIdentity;
  }

  public Integer getLocationId() {
    return locationId;
  }

  public String getSource() {
    return source;
  }

  public String getUsername() {
    return username;
  }

  public String getActivityType() {
    return activityType;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }

  public String getDetails() {
    return details;
  }

  public Instant getDateAdded() {
    return dateAdded;
  }

  public Instant getDateUpdated() {
    return dateUpdated;
  }
}
