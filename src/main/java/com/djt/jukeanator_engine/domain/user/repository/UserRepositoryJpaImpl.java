package com.djt.jukeanator_engine.domain.user.repository;

import static java.util.Objects.requireNonNull;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;
import com.djt.jukeanator_engine.domain.user.model.CreditTransactionEntity;
import com.djt.jukeanator_engine.domain.user.model.PlaylistEntity;
import com.djt.jukeanator_engine.domain.user.model.UserEntity;
import com.djt.jukeanator_engine.domain.user.model.UserRootEntity;

/**
 * JPA/Hibernate-backed implementation of {@link UserRepository}. Unlike a hand-written JDBC/SQL
 * implementation, this class is server/database-type agnostic: the actual vendor is selected
 * entirely via {@code spring.datasource.url}/{@code driver-class-name} (MySQL is the only
 * supported vendor), and Hibernate translates the JPQL/criteria queries and id-generation
 * strategy below into whatever dialect that vendor needs -- including emulating {@code
 * GenerationType.SEQUENCE} with a backing table, since MySQL doesn't support native sequences. See
 * {@link com.djt.jukeanator_engine.domain.common.model.AbstractPersistentEntity} for where that
 * id-generation strategy is declared.
 *
 * <p>{@link UserRootEntity} is <strong>not</strong> JPA-mapped -- there is no {@code user_root}
 * table. It exists purely as an in-memory aggregate, exactly like {@link
 * UserRepositoryFileSystemImpl} treats it, so relational storage isn't saddled with a singleton
 * "root" row whose only job is to own the {@code users} table. {@link #loadOrCreateRoot()} loads
 * every {@link UserEntity} row directly and assembles the root around them; {@link
 * #storeAggregateRoot(UserRootEntity)} persists every user still in the root and explicitly
 * deletes any row that dropped out of it (e.g. {@code UserServiceImpl.deleteAccount()}), since
 * there's no longer a parent-owned {@code orphanRemoval} relationship to detect that for us.
 * Playlists/transactions/element collections still cascade automatically -- that cascade config
 * lives on {@link UserEntity} itself and is untouched by any of this.
 *
 * @author tmyers
 */
public final class UserRepositoryJpaImpl implements UserRepository {

  private static final Integer USER_ROOT_ID = Integer.valueOf(0);

  private final EntityManager entityManager;
  private final TransactionTemplate transactionTemplate;

  public UserRepositoryJpaImpl(EntityManagerFactory entityManagerFactory,
      PlatformTransactionManager transactionManager) {

    requireNonNull(entityManagerFactory, "entityManagerFactory cannot be null");
    requireNonNull(transactionManager, "transactionManager cannot be null");

    this.entityManager = SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory);
    this.transactionTemplate = new TransactionTemplate(transactionManager);
  }

  @Override
  public UserRootEntity loadAggregateRoot(String naturalIdentity)
      throws EntityDoesNotExistException {

    // naturalIdentity is unused: there is exactly one (in-memory) UserRootEntity, same as
    // UserRepositoryFileSystemImpl ignoring naturalIdentity in favor of its one file.
    return loadOrCreateRoot();
  }

  @Override
  public UserRootEntity loadAggregateRoot(int persistentIdentity)
      throws EntityDoesNotExistException {

    if (!USER_ROOT_ID.equals(Integer.valueOf(persistentIdentity))) {
      throw new EntityDoesNotExistException(
          "UserRootEntity is a singleton aggregate; no root exists with persistentIdentity: ["
              + persistentIdentity + "].");
    }
    return loadOrCreateRoot();
  }

  @Override
  public void storeAggregateRoot(UserRootEntity root) {

    requireNonNull(root, "root cannot be null");

    transactionTemplate.executeWithoutResult(status -> {

      Set<Integer> currentIds = new HashSet<>();
      for (UserEntity user : root.getUsers()) {
        currentIds.add(user.getPersistentIdentity());
      }

      List<Integer> persistedIds = entityManager
          .createQuery("select u.persistentIdentity from UserEntity u", Integer.class)
          .getResultList();

      for (Integer persistedId : persistedIds) {
        if (!currentIds.contains(persistedId)) {
          UserEntity toRemove = entityManager.find(UserEntity.class, persistedId);
          if (toRemove != null) {
            entityManager.remove(toRemove);
          }
        }
      }

      // A brand-new UserEntity still carries the placeholder persistentIdentity that
      // UserServiceImpl/UserEntity assign for the filesystem-repository's benefit (there's no DB
      // there to generate one) -- e.g. userRoot.getUsers().size() + 1. That placeholder is
      // meaningless here: merge()-ing an entity whose id doesn't yet exist in `users` makes
      // Hibernate assume the row must already exist and issue an UPDATE instead of an INSERT,
      // which matches zero rows and throws OptimisticLockException ("...or unsaved-value mapping
      // was incorrect"). persist() instead lets the real @GeneratedValue(SEQUENCE) on
      // AbstractPersistentEntity.persistentIdentity mint the actual id -- but persist() also
      // requires the entity to look genuinely transient, so any pre-assigned id (on the user
      // itself, and, since a user row can't have pre-existing playlist/transaction rows before it
      // exists, on its cascaded playlists/transactions too) has to be cleared first; SEQUENCE
      // generation then assigns the real ids on `user` and its children in place, so the
      // in-memory userRoot graph and the DB agree without any further plumbing.
      for (UserEntity user : root.getUsers()) {
        if (persistedIds.contains(user.getPersistentIdentity())) {

          // The same placeholder-id problem described above applies just as much to a single new
          // PlaylistEntity/CreditTransactionEntity added onto an otherwise-already-persisted user
          // (e.g. UserServiceImpl.deductCredits() assigning getTransactions().size() + 1): merge()
          // on a non-null id it doesn't recognize issues an UPDATE that matches zero rows instead
          // of an INSERT, throwing OptimisticLockException. Unlike the brand-new-user persist()
          // path below, merge() also returns a copy rather than mutating `user` in place, so its
          // cascade wouldn't write the real generated id back onto our long-lived in-memory
          // userRoot anyway -- persist() each new child directly first (which does mutate in
          // place) so it's already a managed, real-id'd entity by the time merge(user) cascades
          // over the rest of the (unchanged) collection.
          Set<Integer> persistedTransactionIds = new HashSet<>(entityManager
              .createQuery("select t.persistentIdentity from CreditTransactionEntity t "
                  + "where t.user.persistentIdentity = :userId", Integer.class)
              .setParameter("userId", user.getPersistentIdentity())
              .getResultList());
          for (CreditTransactionEntity transaction : user.getTransactions()) {
            if (!persistedTransactionIds.contains(transaction.getPersistentIdentity())) {
              transaction.setPersistentIdentity(null);
              entityManager.persist(transaction);
            }
          }

          Set<Integer> persistedPlaylistIds = new HashSet<>(entityManager
              .createQuery("select p.persistentIdentity from PlaylistEntity p "
                  + "where p.user.persistentIdentity = :userId", Integer.class)
              .setParameter("userId", user.getPersistentIdentity())
              .getResultList());
          for (PlaylistEntity playlist : user.getPlaylists()) {
            if (!persistedPlaylistIds.contains(playlist.getPersistentIdentity())) {
              playlist.setPersistentIdentity(null);
              entityManager.persist(playlist);
            }
          }

          entityManager.merge(user);
        } else {
          user.setPersistentIdentity(null);
          for (PlaylistEntity playlist : user.getPlaylists()) {
            playlist.setPersistentIdentity(null);
          }
          for (CreditTransactionEntity transaction : user.getTransactions()) {
            transaction.setPersistentIdentity(null);
          }
          entityManager.persist(user);
        }
      }
    });
  }

  private UserRootEntity loadOrCreateRoot() {

    return transactionTemplate.execute(status -> {

      List<UserEntity> users =
          entityManager.createQuery("from UserEntity", UserEntity.class).getResultList();

      UserRootEntity root = new UserRootEntity();
      for (UserEntity user : users) {
        root.addUser(user);
      }
      return root;
    });
  }
}
