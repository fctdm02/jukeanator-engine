package com.djt.jukeanator_engine.domain.licenseserver.repository;

import static java.util.Objects.requireNonNull;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.djt.jukeanator_engine.domain.licenseserver.model.LicenseCustomerEntity;
import com.djt.jukeanator_engine.domain.licenseserver.model.LicenseMachineEntity;

/**
 * JPA implementation of {@link LicenseServerRepository}, following the shared-EntityManager plus
 * {@link TransactionTemplate} pattern of the other {@code *RepositoryJpaImpl} classes.
 */
public final class LicenseServerRepositoryJpaImpl implements LicenseServerRepository {

  private final EntityManager entityManager;
  private final TransactionTemplate transactionTemplate;

  public LicenseServerRepositoryJpaImpl(EntityManagerFactory entityManagerFactory,
      PlatformTransactionManager transactionManager) {

    requireNonNull(entityManagerFactory, "entityManagerFactory cannot be null");
    requireNonNull(transactionManager, "transactionManager cannot be null");

    this.entityManager = SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory);
    this.transactionTemplate = new TransactionTemplate(transactionManager);
  }

  @Override
  public <T> T inTransaction(Supplier<T> work) {
    return transactionTemplate.execute(status -> work.get());
  }

  @Override
  public Optional<LicenseCustomerEntity> findCustomerById(int customerId) {
    return inTransaction(() -> Optional.ofNullable(
        entityManager.find(LicenseCustomerEntity.class, customerId)));
  }

  @Override
  public Optional<LicenseCustomerEntity> findCustomerByEmail(String email) {
    if (email == null) {
      return Optional.empty();
    }
    return inTransaction(() -> entityManager
        .createQuery("select c from LicenseCustomerEntity c where lower(c.email) = :email",
            LicenseCustomerEntity.class)
        .setParameter("email", email.trim().toLowerCase(Locale.ROOT))
        .getResultStream()
        .findFirst());
  }

  @Override
  public List<LicenseCustomerEntity> searchCustomers(String q, int limit) {
    String pattern = "%" + (q == null ? "" : q.trim().toLowerCase(Locale.ROOT)) + "%";
    return inTransaction(() -> entityManager
        .createQuery("select c from LicenseCustomerEntity c"
            + " where lower(c.customerName) like :p or lower(c.email) like :p"
            + " or c.persistentIdentity in (select m.customerId from LicenseMachineEntity m"
            + "   where lower(m.serialNumber) like :p or lower(m.locationName) like :p)"
            + " order by c.customerName", LicenseCustomerEntity.class)
        .setParameter("p", pattern)
        .setMaxResults(limit)
        .getResultList());
  }

  @Override
  public Optional<LicenseMachineEntity> findMachineById(int machineId) {
    return inTransaction(() -> Optional.ofNullable(
        entityManager.find(LicenseMachineEntity.class, machineId)));
  }

  @Override
  public Optional<LicenseMachineEntity> findMachineBySerial(String serialNumber) {
    if (serialNumber == null) {
      return Optional.empty();
    }
    return inTransaction(() -> entityManager
        .createQuery("select m from LicenseMachineEntity m where m.serialNumber = :s",
            LicenseMachineEntity.class)
        .setParameter("s", serialNumber)
        .getResultStream()
        .findFirst());
  }

  @Override
  public List<LicenseMachineEntity> findMachinesByCustomer(int customerId) {
    return inTransaction(() -> entityManager
        .createQuery("select m from LicenseMachineEntity m where m.customerId = :c"
            + " order by m.serialNumber", LicenseMachineEntity.class)
        .setParameter("c", customerId)
        .getResultList());
  }

  @Override
  public LicenseCustomerEntity saveCustomer(LicenseCustomerEntity customer) {
    return inTransaction(() -> {
      if (customer.getPersistentIdentity() == null) {
        entityManager.persist(customer);
        return customer;
      }
      return entityManager.merge(customer);
    });
  }

  @Override
  public LicenseMachineEntity saveMachine(LicenseMachineEntity machine) {
    return inTransaction(() -> {
      if (machine.getPersistentIdentity() == null) {
        entityManager.persist(machine);
        return machine;
      }
      return entityManager.merge(machine);
    });
  }

  @Override
  public boolean consumeLicense(int customerId) {
    Integer updated = inTransaction(() -> entityManager
        .createQuery("update LicenseCustomerEntity c"
            + " set c.availableLicenses = c.availableLicenses - 1"
            + " where c.persistentIdentity = :id and c.availableLicenses > 0")
        .setParameter("id", customerId)
        .executeUpdate());
    return updated != null && updated > 0;
  }
}
