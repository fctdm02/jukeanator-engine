package com.djt.jukeanator_engine.domain.songqueue.repository;

import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import com.djt.jukeanator_engine.domain.common.model.AbstractPersistentEntity;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongIdentifier;

/**
 * Flat JPA persistence row for the {@code song_queue_entries} table -- see {@link
 * SongQueueRepositoryJpaImpl}'s class javadoc for why {@link
 * com.djt.jukeanator_engine.domain.songqueue.model.SongQueueEntryEntity} isn't JPA-mapped
 * directly. Reuses {@link SongIdentifier} (already {@code @Embeddable}, and already used by {@code
 * UserEntity.songPlayHistory}/{@code PlaylistEntity.songs}) for the {@code (locationId, albumId,
 * songId)} triple rather than duplicating those three columns here.
 *
 * @author tmyers
 */
@Entity
@Table(name = "song_queue_entries")
public class SongQueueEntryJpaEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE,
      generator = AbstractPersistentEntity.PERSISTENT_IDENTITY_SEQUENCE)
  @SequenceGenerator(name = AbstractPersistentEntity.PERSISTENT_IDENTITY_SEQUENCE,
      sequenceName = AbstractPersistentEntity.PERSISTENT_IDENTITY_SEQUENCE, allocationSize = 1)
  @Column(name = "persistent_identity")
  private Integer persistentIdentity;

  @Embedded
  private SongIdentifier songIdentifier;

  @Column(name = "username", nullable = false)
  private String username;

  @Column(name = "priority", nullable = false)
  private Integer priority;

  @Column(name = "queued_at_time", nullable = false)
  private Instant queuedAtTime;

  // Explicit persisted order: a queue entry has no identity of its own to diff against previously
  // persisted rows (unlike UserEntity's unique email address), and its position in the list can
  // change independent of priority/queuedAtTime (moveSongUpInQueue/moveSongDownInQueue,
  // randomizeQueue), so SongQueueRepositoryJpaImpl records the in-memory list index directly
  // rather than trying to recompute order from other fields on load.
  @Column(name = "queue_order", nullable = false)
  private Integer queueOrder;

  protected SongQueueEntryJpaEntity() {} // for JPA

  public SongQueueEntryJpaEntity(SongIdentifier songIdentifier, String username, Integer priority,
      Instant queuedAtTime, Integer queueOrder) {
    this.songIdentifier = songIdentifier;
    this.username = username;
    this.priority = priority;
    this.queuedAtTime = queuedAtTime;
    this.queueOrder = queueOrder;
  }

  public Integer getPersistentIdentity() {
    return persistentIdentity;
  }

  public SongIdentifier getSongIdentifier() {
    return songIdentifier;
  }

  public String getUsername() {
    return username;
  }

  public Integer getPriority() {
    return priority;
  }

  public Instant getQueuedAtTime() {
    return queuedAtTime;
  }

  public Integer getQueueOrder() {
    return queueOrder;
  }
}
