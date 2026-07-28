package com.djt.jukeanator_engine.domain.user.repository;

import static java.util.Objects.requireNonNull;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;
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
 * <p>The whole user list is one aggregate (see {@link UserRootEntity}), so, exactly like
 * {@link UserRepositoryFileSystemImpl}, every {@code storeAggregateRoot} call writes the entire
 * tree: root, users, playlists, and their element collections all cascade from a single
 * {@code merge()} call.
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

    // naturalIdentity is unused: there is exactly one UserRootEntity row (id=0), same as
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
    transactionTemplate.executeWithoutResult(status -> entityManager.merge(root));
  }

  private UserRootEntity loadOrCreateRoot() {

    return transactionTemplate.execute(status -> {
      UserRootEntity root = entityManager.find(UserRootEntity.class, USER_ROOT_ID);
      return root != null ? root : new UserRootEntity();
    });
  }
}
