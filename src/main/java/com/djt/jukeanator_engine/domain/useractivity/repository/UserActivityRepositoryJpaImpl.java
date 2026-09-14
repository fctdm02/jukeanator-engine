package com.djt.jukeanator_engine.domain.useractivity.repository;

import static java.util.Objects.requireNonNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.djt.jukeanator_engine.domain.common.model.utils.ObjectMappers;
import com.djt.jukeanator_engine.domain.useractivity.model.UserActivityEntity;
import com.djt.jukeanator_engine.domain.useractivity.model.UserActivityRecord;
import com.djt.jukeanator_engine.domain.useractivity.model.UserActivitySource;
import com.djt.jukeanator_engine.domain.useractivity.model.UserActivityType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * JPA/Hibernate-backed implementation of {@link UserActivityRepository}. Purely insert-only --
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
        record.occurredAt(), toJson(record.details()));

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

  private static UserActivityRecord toRecord(UserActivityEntity entity) {
    return new UserActivityRecord(entity.getLocationId(),
        UserActivitySource.valueOf(entity.getSource()), entity.getUsername(),
        UserActivityType.valueOf(entity.getActivityType()), entity.getOccurredAt(),
        fromJson(entity.getDetails()));
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
