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
import com.djt.jukeanator_engine.domain.backgroundmusic.model.BackgroundMusicSongEntity;
import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;

/**
 * JPA/Hibernate-backed implementation of {@link BackgroundMusicRepository}. Server/database-type
 * agnostic, exactly like {@code UserRepositoryJpaImpl}: the vendor (Postgres, MySQL, ...) is
 * selected entirely via {@code spring.datasource.url}/{@code driver-class-name}.
 *
 * <p>Unlike {@code UserRepository}, {@link BackgroundMusicRepository} has no root aggregate --
 * it already deals directly in {@code List<BackgroundMusicSongEntity>}, so {@link #loadAll()}/
 * {@link #storeAll(List)} map straight onto JPA queries against the {@code
 * background_music_songs} table, using the same diff/orphan-delete {@code storeAll} shape {@code
 * UserRepositoryJpaImpl} uses for {@code storeAggregateRoot}: every song still in the list is
 * merged, and any previously-persisted row that dropped out of it is explicitly deleted.
 *
 * <p>{@link BackgroundMusicSongEntity} is the root of a {@code TABLE_PER_CLASS} inheritance
 * hierarchy shared with {@link
 * com.djt.jukeanator_engine.domain.backgroundmusic.model.SmartBackgroundMusicSongEntity} (own
 * table: {@code smart_background_music_songs}) -- each concrete subtype gets its own complete
 * table, mirroring the pre-existing split between {@code BackgroundMusicSongs.json} and {@code
 * SmartBackgroundMusicSongs.json}. Because {@code SmartBackgroundMusicSongEntity} is-a {@code
 * BackgroundMusicSongEntity}, a plain {@code from BackgroundMusicSongEntity} query would
 * polymorphically union in {@code smart_background_music_songs} rows too -- every query here is
 * explicitly restricted with {@code TYPE(s) = BackgroundMusicSongEntity} to avoid that.
 * {@link SmartBackgroundMusicRepositoryJpaImpl} owns the smart table exclusively.
 *
 * <p>{@link #updatePlayStats(List, BackgroundMusicSongEntity)}/{@link
 * #resetAllPlayedTimestamps(List)} are targeted {@code UPDATE} statements for the common cases
 * where {@code BackgroundMusicServiceImpl} only actually changed one song's, or every song's,
 * play-tracking columns -- avoiding {@link #storeAll(List)}'s full per-row diff/merge when
 * nothing about the list's membership changed. Mirrors {@code
 * SongLibraryRepositoryJpaImpl.updateNumPlaysForSong()}.
 *
 * @author tmyers
 */
public final class BackgroundMusicRepositoryJpaImpl implements BackgroundMusicRepository {

  private final EntityManager entityManager;
  private final TransactionTemplate transactionTemplate;

  public BackgroundMusicRepositoryJpaImpl(EntityManagerFactory entityManagerFactory,
      PlatformTransactionManager transactionManager) {

    requireNonNull(entityManagerFactory, "entityManagerFactory cannot be null");
    requireNonNull(transactionManager, "transactionManager cannot be null");

    this.entityManager = SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory);
    this.transactionTemplate = new TransactionTemplate(transactionManager);
  }

  @Override
  public List<BackgroundMusicSongEntity> loadAll() {

    return transactionTemplate.execute(status -> entityManager
        .createQuery(
            "from BackgroundMusicSongEntity s where TYPE(s) = BackgroundMusicSongEntity order by s.persistentIdentity asc",
            BackgroundMusicSongEntity.class)
        .getResultList());
  }

  @Override
  public void storeAll(List<BackgroundMusicSongEntity> songs) {

    requireNonNull(songs, "songs cannot be null");

    transactionTemplate.executeWithoutResult(status -> {

      Set<Integer> currentIds = new HashSet<>();
      for (BackgroundMusicSongEntity song : songs) {
        if (song.getPersistentIdentity() != null) {
          currentIds.add(song.getPersistentIdentity());
        }
      }

      List<Integer> persistedIds = entityManager
          .createQuery(
              "select s.persistentIdentity from BackgroundMusicSongEntity s where TYPE(s) = BackgroundMusicSongEntity",
              Integer.class)
          .getResultList();

      for (Integer persistedId : persistedIds) {
        if (!currentIds.contains(persistedId)) {
          BackgroundMusicSongEntity toRemove =
              entityManager.find(BackgroundMusicSongEntity.class, persistedId);
          if (toRemove != null) {
            entityManager.remove(toRemove);
          }
        }
      }

      for (BackgroundMusicSongEntity song : songs) {
        entityManager.merge(song);
      }
    });
  }

  @Override
  public void updatePlayStats(List<BackgroundMusicSongEntity> allSongs,
      BackgroundMusicSongEntity song) throws EntityDoesNotExistException {

    requireNonNull(song, "song cannot be null");
    Integer persistentIdentity = requireNonNull(song.getPersistentIdentity(),
        "song.getPersistentIdentity() cannot be null");

    int rowsUpdated = transactionTemplate.execute(status -> entityManager
        .createQuery("update BackgroundMusicSongEntity s set s.timeLastPlayed = :timeLastPlayed, "
            + "s.numberOfPlays = :numberOfPlays "
            + "where TYPE(s) = BackgroundMusicSongEntity and s.persistentIdentity = :id")
        .setParameter("timeLastPlayed", song.getTimeLastPlayed())
        .setParameter("numberOfPlays", song.getNumberOfPlays())
        .setParameter("id", persistentIdentity)
        .executeUpdate());

    if (rowsUpdated == 0) {
      throw new EntityDoesNotExistException(
          "No background music song found with persistentIdentity: [" + persistentIdentity
              + "].");
    }
  }

  @Override
  public void resetAllPlayedTimestamps(List<BackgroundMusicSongEntity> allSongs) {

    transactionTemplate.executeWithoutResult(status -> entityManager
        .createQuery("update BackgroundMusicSongEntity s set s.timeLastPlayed = null "
            + "where TYPE(s) = BackgroundMusicSongEntity and s.timeLastPlayed is not null")
        .executeUpdate());
  }
}
