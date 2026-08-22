package com.djt.jukeanator_engine.domain.backgroundmusic.repository;

import static java.util.Objects.requireNonNull;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.djt.jukeanator_engine.domain.backgroundmusic.model.SmartBackgroundMusicSongEntity;
import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;

/**
 * JPA/Hibernate-backed implementation of {@link SmartBackgroundMusicRepository}, mapped to its
 * own {@code smart_background_music_songs} table -- see {@link
 * BackgroundMusicRepositoryJpaImpl}'s class javadoc for how the {@code TABLE_PER_CLASS} split
 * with {@link
 * com.djt.jukeanator_engine.domain.backgroundmusic.model.BackgroundMusicSongEntity} works.
 * {@link SmartBackgroundMusicSongEntity} has no subtypes of its own, so unlike the base-class
 * queries there, no {@code TYPE(...)} restriction is needed here -- every row in this table
 * belongs to this repository.
 *
 * <p>{@link #storeAll(List)} uses the same diff/orphan-delete shape {@code UserRepositoryJpaImpl}
 * uses for {@code storeAggregateRoot}. {@link #exists()} answers "has this table ever been
 * populated" the same way {@code SmartBackgroundMusicRepositoryFileSystemImpl.exists()} answers
 * "does the persisted file exist" -- used by {@code BackgroundMusicServiceImpl} to decide whether
 * the smart-additions pool needs to be generated from scratch.
 *
 * <p>{@link #updatePlayStats(List, SmartBackgroundMusicSongEntity)}/{@link
 * #resetAllPlayedTimestamps(List)} are targeted {@code UPDATE} statements for the common cases
 * where {@code BackgroundMusicServiceImpl} only actually changed one song's, or every song's,
 * play-tracking columns -- avoiding {@link #storeAll(List)}'s full per-row diff/merge when
 * nothing about the pool's membership changed. Mirrors {@code
 * SongLibraryRepositoryJpaImpl.updateNumPlaysForSong()}.
 *
 * @author tmyers
 */
public final class SmartBackgroundMusicRepositoryJpaImpl implements SmartBackgroundMusicRepository {

  private final EntityManager entityManager;
  private final TransactionTemplate transactionTemplate;

  public SmartBackgroundMusicRepositoryJpaImpl(EntityManagerFactory entityManagerFactory,
      PlatformTransactionManager transactionManager) {

    requireNonNull(entityManagerFactory, "entityManagerFactory cannot be null");
    requireNonNull(transactionManager, "transactionManager cannot be null");

    this.entityManager = SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory);
    this.transactionTemplate = new TransactionTemplate(transactionManager);
  }

  @Override
  public List<SmartBackgroundMusicSongEntity> loadAll() {

    return transactionTemplate.execute(status -> entityManager
        .createQuery("from SmartBackgroundMusicSongEntity s order by s.persistentIdentity asc",
            SmartBackgroundMusicSongEntity.class)
        .getResultList());
  }

  @Override
  public void storeAll(List<SmartBackgroundMusicSongEntity> songs) {

    requireNonNull(songs, "songs cannot be null");

    transactionTemplate.executeWithoutResult(status -> {

      Set<Integer> currentIds = new HashSet<>();
      for (SmartBackgroundMusicSongEntity song : songs) {
        if (song.getPersistentIdentity() != null) {
          currentIds.add(song.getPersistentIdentity());
        }
      }

      List<Integer> persistedIds = entityManager
          .createQuery("select s.persistentIdentity from SmartBackgroundMusicSongEntity s",
              Integer.class)
          .getResultList();

      for (Integer persistedId : persistedIds) {
        if (!currentIds.contains(persistedId)) {
          SmartBackgroundMusicSongEntity toRemove =
              entityManager.find(SmartBackgroundMusicSongEntity.class, persistedId);
          if (toRemove != null) {
            entityManager.remove(toRemove);
          }
        }
      }

      for (SmartBackgroundMusicSongEntity song : songs) {
        entityManager.merge(song);
      }
    });
  }

  @Override
  public boolean exists() {

    return transactionTemplate.execute(status -> {

      Long count = entityManager
          .createQuery("select count(s) from SmartBackgroundMusicSongEntity s", Long.class)
          .getSingleResult();
      return count != null && count.longValue() > 0;
    });
  }

  @Override
  public void updatePlayStats(List<SmartBackgroundMusicSongEntity> smartPool,
      SmartBackgroundMusicSongEntity song) throws EntityDoesNotExistException {

    requireNonNull(song, "song cannot be null");
    Integer persistentIdentity = requireNonNull(song.getPersistentIdentity(),
        "song.getPersistentIdentity() cannot be null");

    int rowsUpdated = transactionTemplate.execute(status -> entityManager
        .createQuery(
            "update SmartBackgroundMusicSongEntity s set s.timeLastPlayed = :timeLastPlayed, "
                + "s.numberOfPlays = :numberOfPlays where s.persistentIdentity = :id")
        .setParameter("timeLastPlayed", song.getTimeLastPlayed())
        .setParameter("numberOfPlays", song.getNumberOfPlays())
        .setParameter("id", persistentIdentity)
        .executeUpdate());

    if (rowsUpdated == 0) {
      throw new EntityDoesNotExistException(
          "No smart background music song found with persistentIdentity: [" + persistentIdentity
              + "].");
    }
  }

  @Override
  public void resetAllPlayedTimestamps(List<SmartBackgroundMusicSongEntity> smartPool) {

    transactionTemplate.executeWithoutResult(status -> entityManager
        .createQuery("update SmartBackgroundMusicSongEntity s set s.timeLastPlayed = null "
            + "where s.timeLastPlayed is not null")
        .executeUpdate());
  }
}
