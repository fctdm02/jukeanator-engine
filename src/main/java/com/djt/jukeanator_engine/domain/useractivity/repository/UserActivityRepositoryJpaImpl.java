package com.djt.jukeanator_engine.domain.useractivity.repository;

import static java.util.Objects.requireNonNull;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.djt.jukeanator_engine.domain.common.model.utils.ObjectMappers;
import com.djt.jukeanator_engine.domain.common.security.SystemPrincipal;
import com.djt.jukeanator_engine.domain.useractivity.model.PendingUserActivity;
import com.djt.jukeanator_engine.domain.useractivity.model.UserActivityEntity;
import com.djt.jukeanator_engine.domain.useractivity.model.UserActivityRecord;
import com.djt.jukeanator_engine.domain.useractivity.model.UserActivitySource;
import com.djt.jukeanator_engine.domain.useractivity.model.UserActivityType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * JPA/Hibernate-backed implementation of {@link UserActivityRepository}. Insert-only apart from the
 * slave-side outbox marker ({@link #markSyncedToMaster}) and the retention purge --
 * unlike aggregate-root repositories (e.g. {@code FinancialLedgerRepositoryJpaImpl}), activity
 * records are never updated or re-loaded, so there's no {@code nextPersistentIdentity()}/native-insert
 * dance here: {@link UserActivityEntity#getPersistentIdentity()} is left {@code null} and Hibernate
 * mints it from {@code persistent_identity_seq} on {@code persist()}, the same simple pattern {@code
 * SongQueueRepositoryJpaImpl} uses for its own append-only {@code SongQueueEntryJpaEntity} rows.
 */
public final class UserActivityRepositoryJpaImpl implements UserActivityRepository {

  private static final ObjectMapper MAPPER = ObjectMappers.create();

  private final EntityManager entityManager;
  private final TransactionTemplate transactionTemplate;

  public UserActivityRepositoryJpaImpl(EntityManagerFactory entityManagerFactory,
      PlatformTransactionManager transactionManager) {

    requireNonNull(entityManagerFactory, "entityManagerFactory cannot be null");
    requireNonNull(transactionManager, "transactionManager cannot be null");

    this.entityManager = SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory);
    this.transactionTemplate = new TransactionTemplate(transactionManager);
  }

  @Override
  public void record(UserActivityRecord record) {

    requireNonNull(record, "record cannot be null");

    UserActivityEntity entity = new UserActivityEntity(record.locationId(),
        record.source().name(), record.username(), record.activityType().name(),
        record.occurredAt(), toJson(record.details()), record.activityId());

    transactionTemplate.executeWithoutResult(status -> entityManager.persist(entity));
  }

  @Override
  public List<UserActivityRecord> findRecentActivity(Integer locationId, int limit) {

    requireNonNull(locationId, "locationId cannot be null");

    return transactionTemplate.execute(status -> entityManager
        .createQuery(
            "from UserActivityEntity where locationId = :locationId order by occurredAt desc",
            UserActivityEntity.class)
        .setParameter("locationId", locationId)
        .setMaxResults(limit)
        .getResultList()
        .stream()
        .map(UserActivityRepositoryJpaImpl::toRecord)
        .toList());
  }

  @Override
  public void purgeOlderThan(Instant cutoff) {

    requireNonNull(cutoff, "cutoff cannot be null");

    transactionTemplate.executeWithoutResult(status -> entityManager
        .createQuery("delete from UserActivityEntity where occurredAt < :cutoff")
        .setParameter("cutoff", cutoff)
        .executeUpdate());
  }

  @Override
  public List<PendingUserActivity> findPendingMasterSync(int limit) {

    return transactionTemplate.execute(status -> entityManager
        .createQuery("from UserActivityEntity where syncedToMasterAt is null "
            + "and username <> :systemUsername order by persistentIdentity",
            UserActivityEntity.class)
        .setParameter("systemUsername", SystemPrincipal.SYSTEM_USERNAME)
        .setMaxResults(limit)
        .getResultList()
        .stream()
        .map(UserActivityRepositoryJpaImpl::toPending)
        .toList());
  }

  @Override
  public void markSyncedToMaster(List<PendingUserActivity> acknowledged) {

    if (acknowledged.isEmpty()) {
      return;
    }

    List<Integer> ids = acknowledged.stream().map(p -> Integer.valueOf(p.cursor())).toList();
    Instant now = Instant.now();

    transactionTemplate.executeWithoutResult(status -> entityManager
        .createQuery("update UserActivityEntity set syncedToMasterAt = :now "
            + "where persistentIdentity in :ids")
        .setParameter("now", now)
        .setParameter("ids", ids)
        .executeUpdate());
  }

  @Override
  public boolean recordIfAbsent(UserActivityRecord record) {

    requireNonNull(record, "record cannot be null");
    requireNonNull(record.activityId(), "record.activityId() cannot be null");

    return Boolean.TRUE.equals(transactionTemplate.execute(status -> {

      Long existing = entityManager
          .createQuery("select count(a) from UserActivityEntity a "
              + "where a.locationId = :locationId and a.activityId = :activityId", Long.class)
          .setParameter("locationId", record.locationId())
          .setParameter("activityId", record.activityId())
          .getSingleResult();
      if (existing.longValue() > 0) {
        return Boolean.FALSE;
      }

      entityManager.persist(new UserActivityEntity(record.locationId(), record.source().name(),
          record.username(), record.activityType().name(), record.occurredAt(),
          toJson(record.details()), record.activityId()));
      return Boolean.TRUE;
    }));
  }

  // A row written before activity ids existed gets a stand-in derived from its own row id -- stable
  // across sweeps, so a re-push of it is still idempotent on master.
  private static PendingUserActivity toPending(UserActivityEntity entity) {

    UserActivityRecord record = toRecord(entity);
    String activityId = record.activityId() != null ? record.activityId()
        : UUID.nameUUIDFromBytes(("user_activity/" + entity.getPersistentIdentity())
            .getBytes(StandardCharsets.UTF_8)).toString();
    return new PendingUserActivity(String.valueOf(entity.getPersistentIdentity()),
        record.with(record.locationId(), activityId));
  }

  private static UserActivityRecord toRecord(UserActivityEntity entity) {
    return new UserActivityRecord(entity.getLocationId(),
        UserActivitySource.valueOf(entity.getSource()), entity.getUsername(),
        UserActivityType.valueOf(entity.getActivityType()), entity.getOccurredAt(),
        fromJson(entity.getDetails()), entity.getActivityId());
  }

  private static String toJson(Object details) {
    try {
      return MAPPER.writeValueAsString(details);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Could not serialize user activity details to JSON", e);
    }
  }

  private static Map<String, Object> fromJson(String details) {
    if (details == null) {
      return Map.of();
    }
    try {
      return MAPPER.readValue(details, new TypeReference<Map<String, Object>>() {});
    } catch (JsonProcessingException e) {
      return Map.of();
    }
  }
}
