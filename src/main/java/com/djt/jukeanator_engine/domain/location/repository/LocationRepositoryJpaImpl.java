package com.djt.jukeanator_engine.domain.location.repository;

import static java.util.Objects.requireNonNull;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.Session;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;
import com.djt.jukeanator_engine.domain.location.model.LocationCreditPackageEntity;
import com.djt.jukeanator_engine.domain.location.model.LocationEntity;
import com.djt.jukeanator_engine.domain.location.model.LocationRootEntity;
import com.djt.jukeanator_engine.domain.location.model.LocationStatus;
import com.djt.jukeanator_engine.domain.user.dto.CreditPackageDto;

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
 * explicitly deletes the rows the root removed (see {@link
 * LocationRootEntity#pendingRemovedLocationIds()}), since there's no parent-owned {@code
 * orphanRemoval} relationship to detect that for us. Rows the root never loaded, such as one
 * inserted by hand while master runs, are left alone. For a row it did load, a store writes only
 * the fields this instance changed since, and copies every other field back from the database
 * into memory, so hand edits made while it runs are kept and picked up rather than overwritten.
 *
 * @author tmyers
 */
public final class LocationRepositoryJpaImpl implements LocationRepository {

  private static final Integer LOCATION_ROOT_ID = Integer.valueOf(0);

  /** One stored, operator- or slave-editable field of a location row. */
  private record Field(String name, Function<LocationEntity, Object> getter,
      BiConsumer<LocationEntity, Object> setter) {
  }

  // Every mapped field a store may write, in a fixed order (the Object[] values below follow it).
  // Not version/dateAdded/dateUpdated: nothing in this application changes them on a location.
  private static final List<Field> FIELDS = List.of(
      new Field("name", LocationEntity::getName, (l, v) -> l.setName((String) v)),
      new Field("latitude", LocationEntity::getLatitude, (l, v) -> l.setLatitude((Double) v)),
      new Field("longitude", LocationEntity::getLongitude, (l, v) -> l.setLongitude((Double) v)),
      new Field("logoName", LocationEntity::getLogoName, (l, v) -> l.setLogoName((String) v)),
      new Field("geoFenced", LocationEntity::isGeoFenced,
          (l, v) -> l.setGeoFenced(Boolean.TRUE.equals(v))),
      new Field("apiKeyHash", LocationEntity::getApiKeyHash,
          (l, v) -> l.setApiKeyHash((String) v)),
      new Field("apiKeyLookup", LocationEntity::getApiKeyLookup,
          (l, v) -> l.setApiKeyLookup((String) v)),
      new Field("status", LocationEntity::getStatus, (l, v) -> l.setStatus((LocationStatus) v)),
      new Field("lastSeenAt", LocationEntity::getLastSeenAt,
          (l, v) -> l.setLastSeenAt((Instant) v)),
      new Field("libraryLastSyncedAt", LocationEntity::getLibraryLastSyncedAt,
          (l, v) -> l.setLibraryLastSyncedAt((Instant) v)),
      new Field("priorityCostMultiplier", LocationEntity::getPriorityCostMultiplier,
          (l, v) -> l.setPriorityCostMultiplier((Integer) v)),
      new Field("creditsPerDollar", LocationEntity::getCreditsPerDollar,
          (l, v) -> l.setCreditsPerDollar((Integer) v)),
      new Field("fiveDollarBonusCredits", LocationEntity::getFiveDollarBonusCredits,
          (l, v) -> l.setFiveDollarBonusCredits((Integer) v)),
      new Field("tenDollarBonusCredits", LocationEntity::getTenDollarBonusCredits,
          (l, v) -> l.setTenDollarBonusCredits((Integer) v)),
      new Field("webCostMultiplier", LocationEntity::getWebCostMultiplier,
          (l, v) -> l.setWebCostMultiplier((Integer) v)),
      new Field("displayCurrencyForCost", LocationEntity::getDisplayCurrencyForCost,
          (l, v) -> l.setDisplayCurrencyForCost((Boolean) v)));

  private final EntityManager entityManager;
  private final TransactionTemplate transactionTemplate;

  // Each location row's field values as this repository last loaded or stored them, by location
  // id. A store compares memory against these to tell what this instance changed (and so writes)
  // from what it didn't (and so leaves to the database, which may hold a hand edit).
  private final Map<Integer, Object[]> lastKnownValues = new ConcurrentHashMap<>();

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

    // Only rows the root itself removed are deleted -- never "every row missing from the root".
    // Master loads this table once at startup, so a location row inserted by hand (phpMyAdmin)
    // while it runs is missing from its in-memory root, and deleting rows by absence used to wipe
    // that row the next time any slave connected and triggered a store.
    Set<Integer> removedIds = root.pendingRemovedLocationIds();
    Map<Integer, Object[]> inMemoryAtStore = new HashMap<>();
    Map<Integer, Object[]> storedValues = new HashMap<>();

    transactionTemplate.executeWithoutResult(status -> {

      for (Integer removedId : removedIds) {
        LocationEntity toRemove = entityManager.find(LocationEntity.class, removedId);
        if (toRemove != null) {
          entityManager.remove(toRemove);
        }
      }

      List<Integer> persistedIds = entityManager
          .createQuery("select l.persistentIdentity from LocationEntity l", Integer.class)
          .getResultList();

      // persistentIdentity is minted up front by nextPersistentIdentity() (see its own javadoc --
      // needed before the entity exists so registerLocation() can return the id immediately),
      // bypassing this @GeneratedValue(SEQUENCE) entity's own generator. Both merge() and
      // persist() refuse that: merge() assumes a pre-set id means "update an existing row" (a
      // no-op update on a row that was never persisted surfaces as a concurrent-modification
      // conflict, not an insert), and persist() rejects a @GeneratedValue entity that already has
      // an id outright ("detached entity passed to persist"). So anything new is INSERTed
      // natively, the same workaround nextPersistentIdentity() already uses for allocating the id.
      //
      // A row already present (per persistedIds, queried above) is not merge()d wholesale
      // either: only the fields that changed in memory since this repository last loaded or
      // stored the row are written (see writeFieldsChangedInMemory), so a hand edit made to any
      // other field while this instance runs survives.
      Set<Integer> persistedIdSet = new HashSet<>(persistedIds);
      for (LocationEntity location : root.getLocations()) {
        Integer locationId = location.getPersistentIdentity();
        Object[] inMemory = fieldValues(location);
        inMemoryAtStore.put(locationId, inMemory);
        if (persistedIdSet.contains(locationId)) {
          writeFieldsChangedInMemory(location, inMemory);
        } else {
          insertNewLocation(location);
        }
      }

      // Read every row back as stored, hand edits included, so memory can mirror it below.
      entityManager.flush();
      for (LocationEntity location : root.getLocations()) {
        LocationEntity stored =
            entityManager.find(LocationEntity.class, location.getPersistentIdentity());
        if (stored != null) {
          entityManager.refresh(stored);
          storedValues.put(location.getPersistentIdentity(), fieldValues(stored));
        }
      }
    });

    // Committed: these rows are gone, so the next store need not delete them again.
    root.forgetRemovedLocationIds(removedIds);
    removedIds.forEach(this.lastKnownValues::remove);

    // Bring memory in line with the database: this is how master picks up a hand edit made while
    // it runs. A field that changed in memory while this store was running is left as it is (and,
    // differing from the new last-known value, is written by the next store).
    for (LocationEntity location : root.getLocations()) {
      Integer locationId = location.getPersistentIdentity();
      Object[] stored = storedValues.get(locationId);
      if (stored == null) {
        continue;
      }
      Object[] atStore = inMemoryAtStore.get(locationId);
      for (int i = 0; i < FIELDS.size(); i++) {
        Field field = FIELDS.get(i);
        if (Objects.equals(field.getter().apply(location), atStore[i])
            && !Objects.equals(atStore[i], stored[i])) {
          field.setter().accept(location, stored[i]);
        }
      }
      this.lastKnownValues.put(locationId, stored);
    }
  }

  /**
   * Writes to {@code location}'s row only the fields whose in-memory value differs from the value
   * this repository last loaded or stored for it. Every other column keeps whatever the database
   * holds now, which may be a hand edit (e.g. phpMyAdmin) made while this instance runs. With no
   * last-known values for the row, every field counts as changed, as merge() used to.
   */
  private void writeFieldsChangedInMemory(LocationEntity location, Object[] inMemory) {

    LocationEntity stored = entityManager.find(LocationEntity.class, location.getPersistentIdentity());
    if (stored == null) {
      return;
    }
    Object[] lastKnown = this.lastKnownValues.get(location.getPersistentIdentity());
    for (int i = 0; i < FIELDS.size(); i++) {
      Field field = FIELDS.get(i);
      boolean changedInMemory = lastKnown == null || !Objects.equals(inMemory[i], lastKnown[i]);
      if (changedInMemory && !Objects.equals(inMemory[i], field.getter().apply(stored))) {
        field.setter().accept(stored, inMemory[i]);
      }
    }
  }

  private static Object[] fieldValues(LocationEntity location) {

    Object[] values = new Object[FIELDS.size()];
    for (int i = 0; i < FIELDS.size(); i++) {
      values[i] = FIELDS.get(i).getter().apply(location);
    }
    return values;
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
        rekey("update song_queue set location_id = :newId where location_id = :oldId",
            oldLocationId, newLocationId);
        rekey("update location_credit_package set location_id = :newId "
            + "where location_id = :oldId", oldLocationId, newLocationId);
        // The remaining tables have no foreign key to location, but their rows are still tagged
        // with this instance's own location id, so they must follow it too. Services holding
        // copies of these rows in memory re-tag them on OwnLocationIdChangedEvent, so their next
        // store doesn't write the previous id back over these updates.
        for (String table : List.of("location_transaction", "location_mobile_credit_usage",
            "user_song_play_history", "user_playlist_song", "user_song_credit_usage",
            "user_add_funds_transaction", "user_location_account", "user_activity",
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

    Object[] lastKnown = this.lastKnownValues.remove(oldLocationId);
    if (lastKnown != null) {
      this.lastKnownValues.put(newLocationId, lastKnown);
    }
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
        + "(id, version, name, latitude, longitude, api_key_hash, api_key_lookup, status, "
        + "last_seen_at, library_last_synced_at, logo_name, is_geo_fenced, "
        + "priority_cost_multiplier, credits_per_dollar, five_dollar_bonus_credits, "
        + "ten_dollar_bonus_credits, web_cost_multiplier, display_currency_for_cost) "
        + "values (:id, :version, :name, :latitude, :longitude, :apiKeyHash, :apiKeyLookup, "
        + ":status, :lastSeenAt, :libraryLastSyncedAt, :logoName, :isGeoFenced, "
        + ":priorityCostMultiplier, "
        + ":creditsPerDollar, :fiveDollarBonusCredits, :tenDollarBonusCredits, "
        + ":webCostMultiplier, :displayCurrencyForCost)")
        .setParameter("id", location.getPersistentIdentity())
        .setParameter("version", location.getVersion())
        .setParameter("name", location.getName())
        .setParameter("latitude", location.getLatitude())
        .setParameter("longitude", location.getLongitude())
        .setParameter("apiKeyHash", location.getApiKeyHash())
        .setParameter("apiKeyLookup", location.getApiKeyLookup())
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

  @Override
  public void storeCreditPackages(Integer locationId, List<CreditPackageDto> packages) {

    requireNonNull(locationId, "locationId cannot be null");
    transactionTemplate.executeWithoutResult(status -> {
      entityManager
          .createQuery("delete from LocationCreditPackageEntity p where p.locationId = :locationId")
          .setParameter("locationId", locationId)
          .executeUpdate();
      // Flush the delete before inserting: a re-pushed list usually reuses the same package
      // codes, and uq_location_credit_package would otherwise reject the new rows.
      entityManager.flush();
      if (packages != null) {
        for (int i = 0; i < packages.size(); i++) {
          entityManager.persist(new LocationCreditPackageEntity(locationId, packages.get(i), i));
        }
      }
    });
  }

  private LocationRootEntity loadOrCreateRoot() {

    return transactionTemplate.execute(status -> {

      List<LocationEntity> locations =
          entityManager.createQuery("from LocationEntity", LocationEntity.class).getResultList();

      Map<Integer, List<CreditPackageDto>> packagesByLocation = new HashMap<>();
      for (LocationCreditPackageEntity pkg : entityManager
          .createQuery("from LocationCreditPackageEntity p order by p.locationId, p.displayOrder",
              LocationCreditPackageEntity.class)
          .getResultList()) {
        packagesByLocation.computeIfAbsent(pkg.getLocationId(), id -> new ArrayList<>())
            .add(pkg.toDto());
      }

      LocationRootEntity root = new LocationRootEntity();
      for (LocationEntity location : locations) {
        location.setCreditPackages(packagesByLocation.get(location.getPersistentIdentity()));
        root.addLocation(location);
        // putIfAbsent: a re-load (e.g. LocationServiceImpl picking up rows inserted by hand)
        // must not replace the last-known values of a row already held in memory -- that would
        // make a hand edit since then look like an in-memory change, and the next store would
        // write the stale in-memory value back over it.
        this.lastKnownValues.putIfAbsent(location.getPersistentIdentity(), fieldValues(location));
      }
      return root;
    });
  }
}
