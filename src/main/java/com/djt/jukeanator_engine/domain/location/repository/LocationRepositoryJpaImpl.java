package com.djt.jukeanator_engine.domain.location.repository;

import static java.util.Objects.requireNonNull;
import java.sql.Statement;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.Session;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;
import com.djt.jukeanator_engine.domain.location.model.LocationEntity;
import com.djt.jukeanator_engine.domain.location.model.LocationRootEntity;

/**
 * JPA/Hibernate-backed implementation of {@link LocationRepository}. Server/database-type
 * agnostic, exactly like {@code UserRepositoryJpaImpl}: the vendor is selected entirely via
 * {@code spring.datasource.url}/{@code driver-class-name} (MySQL is the only supported vendor).
 *
 * <p>{@link LocationRootEntity} is <strong>not</strong> JPA-mapped -- there is no {@code
 * location_root} table. It exists purely as an in-memory aggregate, exactly like {@link
 * LocationRepositoryFileSystemImpl} treats it. {@link #loadOrCreateRoot()} loads every {@link
 * LocationEntity} row directly and assembles the root around them; {@link
 * #storeAggregateRoot(LocationRootEntity)} persists every location still in the root and
 * explicitly deletes any row that dropped out of it, since there's no parent-owned {@code
 * orphanRemoval} relationship to detect that for us.
 *
 * @author tmyers
 */
public final class LocationRepositoryJpaImpl implements LocationRepository {

  private static final Integer LOCATION_ROOT_ID = Integer.valueOf(0);

  private final EntityManager entityManager;
  private final TransactionTemplate transactionTemplate;

  public LocationRepositoryJpaImpl(EntityManagerFactory entityManagerFactory,
      PlatformTransactionManager transactionManager) {

    requireNonNull(entityManagerFactory, "entityManagerFactory cannot be null");
    requireNonNull(transactionManager, "transactionManager cannot be null");

    this.entityManager = SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory);
    this.transactionTemplate = new TransactionTemplate(transactionManager);
  }

  @Override
  public LocationRootEntity loadAggregateRoot(String naturalIdentity)
      throws EntityDoesNotExistException {

    // naturalIdentity is unused: there is exactly one (in-memory) LocationRootEntity, same as
    // LocationRepositoryFileSystemImpl ignoring naturalIdentity in favor of its one file.
    return loadOrCreateRoot();
  }

  @Override
  public LocationRootEntity loadAggregateRoot(int persistentIdentity)
      throws EntityDoesNotExistException {

    if (!LOCATION_ROOT_ID.equals(Integer.valueOf(persistentIdentity))) {
      throw new EntityDoesNotExistException(
          "LocationRootEntity is a singleton aggregate; no root exists with persistentIdentity: ["
              + persistentIdentity + "].");
    }
    return loadOrCreateRoot();
  }

  @Override
  public void storeAggregateRoot(LocationRootEntity root) {

    requireNonNull(root, "root cannot be null");

    transactionTemplate.executeWithoutResult(status -> {

      Set<Integer> currentIds = new HashSet<>();
      for (LocationEntity location : root.getLocations()) {
        currentIds.add(location.getPersistentIdentity());
      }

      List<Integer> persistedIds = entityManager
          .createQuery("select l.persistentIdentity from LocationEntity l", Integer.class)
          .getResultList();

      for (Integer persistedId : persistedIds) {
        if (!currentIds.contains(persistedId)) {
          LocationEntity toRemove = entityManager.find(LocationEntity.class, persistedId);
          if (toRemove != null) {
            entityManager.remove(toRemove);
          }
        }
      }

      // persistentIdentity is minted up front by nextPersistentIdentity() (see its own javadoc --
      // needed before the entity exists so registerLocation() can return the id immediately),
      // bypassing this @GeneratedValue(SEQUENCE) entity's own generator. Both merge() and
      // persist() refuse that: merge() assumes a pre-set id means "update an existing row" (a
      // no-op update on a row that was never persisted surfaces as a concurrent-modification
      // conflict, not an insert), and persist() rejects a @GeneratedValue entity that already has
      // an id outright ("detached entity passed to persist"). Only merge() rows already confirmed
      // present (per persistedIds, queried above); INSERT anything new natively, the same
      // workaround nextPersistentIdentity() already uses for allocating the id itself.
      Set<Integer> persistedIdSet = new HashSet<>(persistedIds);
      for (LocationEntity location : root.getLocations()) {
        if (persistedIdSet.contains(location.getPersistentIdentity())) {
          entityManager.merge(location);
        } else {
          insertNewLocation(location);
        }
      }
    });
  }

  @Override
  public Integer nextPersistentIdentity() {

    // Emulates Hibernate's own org.hibernate.id.enhanced.TableStructure read-then-increment
    // allocation against the same persistent_identity_seq backing table every
    // AbstractPersistentEntity subclass shares (see PERSISTENT_IDENTITY_SEQUENCE) -- needed here
    // because registerLocation() must know the assigned id immediately (to return it to the
    // caller and add it to the in-memory root), before the entity itself is ever persisted.
    return transactionTemplate.execute(status -> {

      entityManager.createNativeQuery("update persistent_identity_seq set next_val = next_val + 1")
          .executeUpdate();
      Number nextVal = (Number) entityManager
          .createNativeQuery("select next_val from persistent_identity_seq").getSingleResult();
      return Integer.valueOf(nextVal.intValue() - 1);
    });
  }

  /**
   * storeAggregateRoot() alone would persist a re-keyed location as a delete of the old row plus an
   * insert of the new one, and that delete is blocked by every foreign key referencing it. Instead
   * this updates the primary key in place and re-points each referencing table explicitly, all in
   * one transaction with foreign-key checks briefly disabled. That's required for song_library:
   * its self-referencing fk_song_library_parent includes parent_location_id, so no row-by-row
   * update order can satisfy it, and MySQL treats a cascade back into the same table as RESTRICT.
   * With checks disabled MySQL performs no ON UPDATE CASCADE actions either, so every referencing
   * table is updated explicitly here rather than relying on them.
   */
  @Override
  public void changeLocationId(Integer oldLocationId, Integer newLocationId) {

    requireNonNull(oldLocationId, "oldLocationId cannot be null");
    requireNonNull(newLocationId, "newLocationId cannot be null");

    if (oldLocationId.equals(newLocationId)) {
      return;
    }

    transactionTemplate.executeWithoutResult(status -> {

      // FOREIGN_KEY_CHECKS is session-scoped, and every native query in this transaction runs on
      // the same connection -- restore it in finally so the pooled connection is never returned
      // with checks still off. Both toggles go through plain JDBC on that connection (see
      // setForeignKeyChecks), since Hibernate may refuse further queries once one of the updates
      // below has failed.
      setForeignKeyChecks(false);
      try {
        rekey("update location set id = :newId where id = :oldId", oldLocationId,
            newLocationId);
        rekey("update song_library set parent_location_id = :newId "
            + "where parent_location_id = :oldId", oldLocationId, newLocationId);
        rekey("update location_jukebox_split set parent_location_id = :newId "
            + "where parent_location_id = :oldId", oldLocationId, newLocationId);
        rekey("update song_queue_entries set location_id = :newId where location_id = :oldId",
            oldLocationId, newLocationId);
        // The remaining tables have no foreign key to location, but their rows are still tagged
        // with this instance's own location id, so they must follow it too. Services holding
        // copies of these rows in memory re-tag them on OwnLocationIdChangedEvent, so their next
        // store doesn't write the previous id back over these updates.
        for (String table : List.of("location_transaction", "user_song_play_history",
            "user_playlist_song", "user_song_credit_usage", "user_activity",
            "song_background_music")) {
          rekey("update " + table + " set location_id = :newId where location_id = :oldId",
              oldLocationId, newLocationId);
        }
        rekey("update song_background_music set source_location_id = :newId "
            + "where source_location_id = :oldId", oldLocationId, newLocationId);
      } finally {
        setForeignKeyChecks(true);
      }
    });
  }

  // Runs directly on the JDBC connection bound to the current transaction (Session.doWork), so it
  // always hits the same session the re-keying updates run on, and still works after one of them
  // has failed.
  private void setForeignKeyChecks(boolean enabled) {

    entityManager.unwrap(Session.class).doWork(connection -> {
      try (Statement statement = connection.createStatement()) {
        statement.execute("set foreign_key_checks = " + (enabled ? 1 : 0));
      }
    });
  }

  private void rekey(String sql, Integer oldLocationId, Integer newLocationId) {

    entityManager.createNativeQuery(sql)
        .setParameter("oldId", oldLocationId)
        .setParameter("newId", newLocationId)
        .executeUpdate();
  }

  private void insertNewLocation(LocationEntity location) {

    entityManager.createNativeQuery("insert into location "
        + "(id, version, name, latitude, longitude, api_key_hash, status, last_seen_at, "
        + "library_last_synced_at, logo_name, is_geo_fenced, priority_cost_multiplier, "
        + "credits_per_dollar, five_dollar_bonus_credits, ten_dollar_bonus_credits, "
        + "web_cost_multiplier, display_currency_for_cost) "
        + "values (:id, :version, :name, :latitude, :longitude, :apiKeyHash, :status, "
        + ":lastSeenAt, :libraryLastSyncedAt, :logoName, :isGeoFenced, :priorityCostMultiplier, "
        + ":creditsPerDollar, :fiveDollarBonusCredits, :tenDollarBonusCredits, "
        + ":webCostMultiplier, :displayCurrencyForCost)")
        .setParameter("id", location.getPersistentIdentity())
        .setParameter("version", location.getVersion())
        .setParameter("name", location.getName())
        .setParameter("latitude", location.getLatitude())
        .setParameter("longitude", location.getLongitude())
        .setParameter("apiKeyHash", location.getApiKeyHash())
        .setParameter("status", location.getStatus().name())
        .setParameter("lastSeenAt", location.getLastSeenAt())
        .setParameter("libraryLastSyncedAt", location.getLibraryLastSyncedAt())
        .setParameter("logoName", location.getLogoName())
        .setParameter("isGeoFenced", location.isGeoFenced())
        .setParameter("priorityCostMultiplier", location.getPriorityCostMultiplier())
        .setParameter("creditsPerDollar", location.getCreditsPerDollar())
        .setParameter("fiveDollarBonusCredits", location.getFiveDollarBonusCredits())
        .setParameter("tenDollarBonusCredits", location.getTenDollarBonusCredits())
        .setParameter("webCostMultiplier", location.getWebCostMultiplier())
        .setParameter("displayCurrencyForCost", location.getDisplayCurrencyForCost())
        .executeUpdate();
  }

  private LocationRootEntity loadOrCreateRoot() {

    return transactionTemplate.execute(status -> {

      List<LocationEntity> locations =
          entityManager.createQuery("from LocationEntity", LocationEntity.class).getResultList();

      LocationRootEntity root = new LocationRootEntity();
      for (LocationEntity location : locations) {
        root.addLocation(location);
      }
      return root;
    });
  }
}
