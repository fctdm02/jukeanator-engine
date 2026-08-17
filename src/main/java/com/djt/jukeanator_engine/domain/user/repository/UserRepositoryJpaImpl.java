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
import com.djt.jukeanator_engine.domain.user.model.UserEntity;
import com.djt.jukeanator_engine.domain.user.model.UserRootEntity;

/**
 * JPA/Hibernate-backed implementation of {@link UserRepository}. Unlike a hand-written JDBC/SQL
 * implementation, this class is server/database-type agnostic: the actual vendor (Postgres,
 * MySQL, ...) is selected entirely via {@code spring.datasource.url}/{@code driver-class-name},
 * and Hibernate translates the JPQL/criteria queries and id-generation strategy below into
 * whatever dialect that vendor needs -- including emulating {@code GenerationType.SEQUENCE} with
 * a backing table on databases (like MySQL) that don't support native sequences. See
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

      for (UserEntity user : root.getUsers()) {
        entityManager.merge(user);
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
