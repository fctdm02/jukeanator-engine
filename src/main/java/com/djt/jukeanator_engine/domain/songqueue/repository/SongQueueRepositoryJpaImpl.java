package com.djt.jukeanator_engine.domain.songqueue.repository;

import static java.util.Objects.requireNonNull;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;
import com.djt.jukeanator_engine.domain.songlibrary.model.RootFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.SongFileEntity;
import com.djt.jukeanator_engine.domain.songlibrary.service.SongLibraryService;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongIdentifier;
import com.djt.jukeanator_engine.domain.songqueue.model.SongQueueEntryEntity;
import com.djt.jukeanator_engine.domain.songqueue.model.SongQueueRootEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

/**
 * JPA/Hibernate-backed implementation of {@link SongQueueRepository}. Server/database-type
 * agnostic, exactly like {@code UserRepositoryJpaImpl}: the vendor is selected entirely via
 * {@code spring.datasource.url}/{@code driver-class-name} (MySQL is the only supported vendor).
 *
 * <p>{@link SongQueueRootEntity} is <strong>not</strong> JPA-mapped -- there is no {@code
 * song_queue_root} table. It exists purely as an in-memory aggregate, exactly like {@link
 * SongQueueRepositoryFileSystemImpl} treats it: {@link #loadAggregateRoot(String)} loads every
 * {@link SongQueueEntryJpaEntity} row for this instance's own {@code locationId} (see {@link
 * SongLibraryService#getOwnLocationId()}) and reassembles the root around them, resolving each
 * row's song against the live song library exactly like the filesystem implementation does --
 * including skipping (with a warning) any entry whose song no longer exists there.
 *
 * <p>Unlike {@code UserRepositoryJpaImpl}'s diff/orphan-delete {@code storeAggregateRoot}, a
 * queued song has no identity of its own to diff against previously persisted rows -- there's no
 * equivalent of {@code UserEntity}'s unique email address, since the same song can legitimately be
 * queued more than once -- and {@code SongQueueServiceImpl} already calls {@code
 * storeAggregateRoot} with the entire current queue immediately after every single mutation
 * (add/remove/move/randomize). So {@link #storeAggregateRoot(SongQueueRootEntity)} simply deletes
 * every row for this instance's {@code locationId} and reinserts the current in-memory list,
 * recording each entry's list index into {@code queue_order} so a reload reproduces the exact
 * order -- order that can otherwise change (via {@code moveSongUpInQueue}/{@code
 * moveSongDownInQueue}/{@code randomizeQueue}) independent of {@code priority}/{@code
 * queuedAtTime}.
 *
 * @author tmyers
 */
public final class SongQueueRepositoryJpaImpl implements SongQueueRepository {

  private static final Logger log = LoggerFactory.getLogger(SongQueueRepositoryJpaImpl.class);

  private static final Integer SONG_QUEUE_ROOT_ID = Integer.valueOf(0);

  private final EntityManager entityManager;
  private final TransactionTemplate transactionTemplate;
  private final SongLibraryService songLibraryService;

  public SongQueueRepositoryJpaImpl(EntityManagerFactory entityManagerFactory,
      PlatformTransactionManager transactionManager, SongLibraryService songLibraryService) {

    requireNonNull(entityManagerFactory, "entityManagerFactory cannot be null");
    requireNonNull(transactionManager, "transactionManager cannot be null");
    requireNonNull(songLibraryService, "songLibraryService cannot be null");

    this.entityManager = SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory);
    this.transactionTemplate = new TransactionTemplate(transactionManager);
    this.songLibraryService = songLibraryService;
  }

  @Override
  public SongQueueRootEntity loadAggregateRoot(String naturalIdentity)
      throws EntityDoesNotExistException {

    // naturalIdentity is unused beyond seeding rootPath: there is exactly one queue per
    // locationId, same as SongQueueRepositoryFileSystemImpl treating its one file as the queue.
    return loadRoot(naturalIdentity);
  }

  @Override
  public SongQueueRootEntity loadAggregateRoot(int persistentIdentity)
      throws EntityDoesNotExistException {

    if (!SONG_QUEUE_ROOT_ID.equals(Integer.valueOf(persistentIdentity))) {
      throw new EntityDoesNotExistException(
          "SongQueueRootEntity is a singleton aggregate; no root exists with persistentIdentity: ["
              + persistentIdentity + "].");
    }
    return loadRoot(SongQueueRootEntity.SONG_QUEUE_FILENAME);
  }

  @Override
  public void storeAggregateRoot(SongQueueRootEntity root) {

    requireNonNull(root, "root cannot be null");

    Integer locationId = songLibraryService.getOwnLocationId();
    List<SongQueueEntryEntity> entries = root.getSongs();

    transactionTemplate.executeWithoutResult(status -> {

      entityManager
          .createQuery(
              "delete from SongQueueEntryJpaEntity e where e.songIdentifier.locationId = :locationId")
          .setParameter("locationId", locationId)
          .executeUpdate();

      for (int i = 0; i < entries.size(); i++) {

        SongQueueEntryEntity entry = entries.get(i);
        SongFileEntity song = entry.getSong();

        SongIdentifier songIdentifier = new SongIdentifier(locationId,
            song.getAlbum().getId(), song.getId());

        entityManager.persist(new SongQueueEntryJpaEntity(songIdentifier, entry.getUsername(),
            entry.getPriority(), entry.getQueuedAtTime(), Integer.valueOf(i)));
      }
    });
  }

  private SongQueueRootEntity loadRoot(String rootPath) {

    Integer locationId = songLibraryService.getOwnLocationId();

    List<SongQueueEntryJpaEntity> rows = transactionTemplate.execute(status -> entityManager
        .createQuery(
            "from SongQueueEntryJpaEntity e where e.songIdentifier.locationId = :locationId order by e.queueOrder asc",
            SongQueueEntryJpaEntity.class)
        .setParameter("locationId", locationId)
        .getResultList());

    RootFolderEntity songLibraryRoot = songLibraryService.getSongLibraryRoot(locationId);

    SongQueueRootEntity root = new SongQueueRootEntity(rootPath);
    for (SongQueueEntryJpaEntity row : rows) {

      SongIdentifier songIdentifier = row.getSongIdentifier();
      try {
        SongFileEntity song =
            songLibraryRoot.getSongById(songIdentifier.getAlbumId(), songIdentifier.getSongId());
        SongQueueEntryEntity entry =
            new SongQueueEntryEntity(row.getUsername(), song, row.getPriority());
        entry.setQueuedAtTime(row.getQueuedAtTime());
        root.getSongs().add(entry);
      } catch (EntityDoesNotExistException ednee) {
        log.warn(
            "Skipping persisted song queue entry whose song no longer exists in the library: albumId={}, songId={}",
            songIdentifier.getAlbumId(), songIdentifier.getSongId());
      }
    }

    return root;
  }
}
