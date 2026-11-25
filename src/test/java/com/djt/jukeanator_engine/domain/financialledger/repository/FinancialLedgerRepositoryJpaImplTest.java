package com.djt.jukeanator_engine.domain.financialledger.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import jakarta.persistence.EntityManagerFactory;
import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;
import com.djt.jukeanator_engine.domain.financialledger.model.FinancialLedgerRootEntity;
import com.djt.jukeanator_engine.domain.financialledger.model.JukeboxSplitPeriodEntity;
import com.djt.jukeanator_engine.domain.financialledger.model.LocalCashTransactionEntity;
import com.djt.jukeanator_engine.domain.financialledger.model.LocalCreditTransactionEntity;
import com.djt.jukeanator_engine.domain.financialledger.model.LocalMobileTransactionEntity;
import com.djt.jukeanator_engine.domain.songlibrary.service.SongLibraryService;

/**
 * Integration tests for {@link FinancialLedgerRepositoryJpaImpl}, run against a live local MySQL
 * instance -- same setup as {@code SongQueueRepositoryJpaImplTest}/
 * {@code SongLibraryRepositoryJpaImplTest} (see {@code src/test/resources/application-test.yml}).
 * Requires a real MySQL server with a {@code jukeanator_test} database the {@code jukeanator} user
 * can access.
 *
 * <p>Unlike those tests, {@link FinancialLedgerRootEntity} is a true global singleton -- there is
 * no per-tenant key (such as {@code locationId}) to scope fixtures by, so every test run shares
 * the same three tables. Rather than relying on unique fixture names to avoid collisions, {@link
 * #cleanTables()} truncates all three before each test; safe here because {@code jukeanator_test}
 * is a disposable, test-only database (never the {@code jukeanator} dev/QA database -- see
 * {@code application-test.yml}'s own javadoc on that separation).
 *
 * <p>Placed alongside {@link FinancialLedgerRepositoryFileSystemImplTest} in this domain's own
 * {@code repository} package.
 *
 * @author tmyers
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = { "app.repository-type=jpa" })
class FinancialLedgerRepositoryJpaImplTest {

  @Autowired
  private DataSource dataSource;

  @Autowired
  private EntityManagerFactory entityManagerFactory;

  @Autowired
  private PlatformTransactionManager transactionManager;

  @Autowired
  private SongLibraryService songLibraryService;

  @BeforeEach
  void cleanTables() throws SQLException {

    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement()) {
      statement.executeUpdate("delete from location_transaction");
      statement.executeUpdate("delete from location_jukebox_split");
    }
  }

  private FinancialLedgerRepositoryJpaImpl newRepository() {
    return new FinancialLedgerRepositoryJpaImpl(entityManagerFactory, transactionManager);
  }

  // location_jukebox_split.parent_location_id is a NOT NULL foreign key to location, so every
  // period needs a real location row as its parent -- the standalone context's own location
  // (created by SongLibraryServiceImpl at startup) serves.
  private JukeboxSplitPeriodEntity newPeriod(FinancialLedgerRepositoryJpaImpl repository,
      Instant startDate) {

    JukeboxSplitPeriodEntity period =
        new JukeboxSplitPeriodEntity(repository.nextPersistentIdentity(), startDate);
    period.setParentLocation(songLibraryService.getOwnLocation());
    return period;
  }

  // ── round-trip ───────────────────────────────────────────────────────────

  @Test
  void storeAggregateRoot_thenLoadAggregateRoot_roundTripsPeriodsAndLocalTransactions()
      throws Exception {

    FinancialLedgerRepositoryJpaImpl repository = newRepository();

    FinancialLedgerRootEntity root = new FinancialLedgerRootEntity();

    Instant periodStart = Instant.now().minus(30, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
    Instant periodEnd = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    JukeboxSplitPeriodEntity finalizedPeriod =
        newPeriod(repository, periodStart);
    finalizedPeriod.finalizePeriod(periodEnd, 50, new BigDecimal("12.00"), new BigDecimal("8.00"),
        new BigDecimal("5.00"), new BigDecimal("25.00"), new BigDecimal("12.50"),
        new BigDecimal("12.50"));
    root.addSplitPeriod(finalizedPeriod);

    JukeboxSplitPeriodEntity openPeriod =
        newPeriod(repository, periodEnd);
    root.addSplitPeriod(openPeriod);

    root.addLocalCashTransaction(new LocalCashTransactionEntity(
        repository.nextPersistentIdentity(), 1, periodEnd, Integer.valueOf(9)));
    root.addLocalCreditCardTransaction(new LocalCreditTransactionEntity(
        repository.nextPersistentIdentity(), 1, periodEnd, null));

    repository.storeAggregateRoot(root);

    FinancialLedgerRootEntity reloaded =
        repository.loadAggregateRoot("FinancialLedgerRootEntity");

    assertEquals(2, reloaded.getSplitPeriods().size());
    JukeboxSplitPeriodEntity reloadedFinalized = reloaded.getSplitPeriods().get(0);
    assertEquals(periodStart, reloadedFinalized.getStartDate());
    assertEquals(periodEnd, reloadedFinalized.getEndDate());
    assertEquals(Integer.valueOf(50), reloadedFinalized.getSplitPercentageToOwner());
    assertEquals(0, new BigDecimal("12.00").compareTo(reloadedFinalized.getCashTotal()));
    assertEquals(0, new BigDecimal("8.00").compareTo(reloadedFinalized.getCardTotal()));
    assertEquals(0, new BigDecimal("5.00").compareTo(reloadedFinalized.getMobileTotal()));
    assertEquals(0, new BigDecimal("25.00").compareTo(reloadedFinalized.getTotalEarned()));
    assertEquals(0, new BigDecimal("12.50").compareTo(reloadedFinalized.getAmountDueOwner()));
    assertEquals(0, new BigDecimal("12.50").compareTo(reloadedFinalized.getAmountDueOperator()));

    JukeboxSplitPeriodEntity reloadedOpen = reloaded.getSplitPeriods().get(1);
    assertTrue(reloadedOpen.isOpen());
    assertEquals(reloaded.getCurrentPeriod().getPersistentIdentity(),
        reloadedOpen.getPersistentIdentity());

    assertEquals(1, reloaded.getLocalCashTransactions().size());
    LocalCashTransactionEntity cashTx = reloaded.getLocalCashTransactions().get(0);
    assertEquals(1, cashTx.getAmountDollars());
    assertEquals(Integer.valueOf(9), cashTx.getLocationId());

    assertEquals(1, reloaded.getLocalCreditCardTransactions().size());
    LocalCreditTransactionEntity cardTx = reloaded.getLocalCreditCardTransactions().get(0);
    assertEquals(1, cardTx.getAmountDollars());
    assertNull(cardTx.getLocationId());
  }

  // ── merge (finalize) vs. insert ──────────────────────────────────────────

  @Test
  void storeAggregateRoot_mergesAnAlreadyPersistedPeriod_insteadOfDuplicatingIt() throws Exception {

    FinancialLedgerRepositoryJpaImpl repository = newRepository();

    Instant start = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    FinancialLedgerRootEntity root = new FinancialLedgerRootEntity();
    JukeboxSplitPeriodEntity openPeriod =
        newPeriod(repository, start);
    root.addSplitPeriod(openPeriod);
    repository.storeAggregateRoot(root);

    // Finalize the same in-memory period and store again -- this must UPDATE the existing row,
    // not insert a second one, since its persistentIdentity is already on disk.
    Instant end = start.plus(1, ChronoUnit.DAYS);
    openPeriod.finalizePeriod(end, 50, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO,
        BigDecimal.ONE, new BigDecimal("0.50"), new BigDecimal("0.50"));
    repository.storeAggregateRoot(root);

    FinancialLedgerRootEntity reloaded =
        repository.loadAggregateRoot("FinancialLedgerRootEntity");

    assertEquals(1, reloaded.getSplitPeriods().size(),
        "Re-storing an already-persisted period should update it in place, not duplicate it");
    JukeboxSplitPeriodEntity persisted = reloaded.getSplitPeriods().get(0);
    assertEquals(end, persisted.getEndDate());
    assertEquals(0, BigDecimal.ONE.compareTo(persisted.getTotalEarned()));
  }

  // ── nextPersistentIdentity ───────────────────────────────────────────────

  @Test
  void nextPersistentIdentity_returnsIncreasingUniqueIds() {

    FinancialLedgerRepositoryJpaImpl repository = newRepository();

    Integer first = repository.nextPersistentIdentity();
    Integer second = repository.nextPersistentIdentity();

    assertTrue(second.intValue() > first.intValue());
  }

  // ── loadAggregateRoot(int) singleton semantics ──────────────────────────

  @Test
  void loadAggregateRoot_byPersistentIdentityZero_returnsTheSameRootAsByNaturalIdentity()
      throws Exception {

    FinancialLedgerRepositoryJpaImpl repository = newRepository();
    FinancialLedgerRootEntity root = new FinancialLedgerRootEntity();
    root.addSplitPeriod(newPeriod(repository, Instant.now().truncatedTo(ChronoUnit.SECONDS)));
    repository.storeAggregateRoot(root);

    FinancialLedgerRootEntity reloaded = repository.loadAggregateRoot(0);
    assertEquals(1, reloaded.getSplitPeriods().size());
  }

  @Test
  void loadAggregateRoot_byAnyOtherPersistentIdentity_throws() {

    FinancialLedgerRepositoryJpaImpl repository = newRepository();

    assertThrows(EntityDoesNotExistException.class, () -> repository.loadAggregateRoot(1));
  }

  @Test
  void mobilePurchases_storeAsMobileWebRows_andReloadWithEveryField() throws Exception {

    FinancialLedgerRepositoryJpaImpl repository = newRepository();
    FinancialLedgerRootEntity root = new FinancialLedgerRootEntity();
    Instant timestamp = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    Integer locationId = songLibraryService.getOwnLocationId();
    root.addLocalMobileTransaction(new LocalMobileTransactionEntity(
        repository.nextPersistentIdentity(), new BigDecimal("7.50"), timestamp, locationId, null,
        "alice@example.com", "txn-1", "pkg-7", 12, 2));
    root.addLocalCashTransaction(new LocalCashTransactionEntity(
        repository.nextPersistentIdentity(), 1, timestamp, locationId));

    repository.storeAggregateRoot(root);
    // A second store merges the existing purchase rather than inserting it again.
    repository.storeAggregateRoot(root);

    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement();
        java.sql.ResultSet rs = statement.executeQuery("select transaction_type, amount, "
            + "payment_reference from location_transaction order by transaction_type")) {
      assertTrue(rs.next());
      assertEquals("CASH", rs.getString(1));
      assertEquals(0, BigDecimal.ONE.compareTo(rs.getBigDecimal(2)));
      assertTrue(rs.next());
      assertEquals("MOBILE_WEB", rs.getString(1));
      assertEquals(0, new BigDecimal("7.50").compareTo(rs.getBigDecimal(2)));
      assertEquals("txn-1", rs.getString(3));
      assertTrue(!rs.next(), "one row per transaction");
    }

    FinancialLedgerRootEntity reloaded = newRepository().loadAggregateRoot("FinancialLedgerRootEntity");
    assertEquals(1, reloaded.getLocalMobileTransactions().size());
    assertEquals(1, reloaded.getLocalCashTransactions().size(),
        "a MOBILE_WEB row is never loaded as cash or card");
    assertTrue(reloaded.getLocalCreditCardTransactions().isEmpty());
    LocalMobileTransactionEntity purchase = reloaded.getLocalMobileTransactions().get(0);
    assertEquals(0, new BigDecimal("7.50").compareTo(purchase.getAmount()));
    assertEquals(timestamp, purchase.getTimestamp());
    assertEquals(locationId, purchase.getLocationId());
    assertEquals("alice@example.com", purchase.getUserEmail());
    assertEquals("pkg-7", purchase.getPackageCode());
    assertEquals(Integer.valueOf(12), purchase.getCreditsAwarded());
    assertEquals(Integer.valueOf(2), purchase.getBonusCredits());
  }

  @Test
  void loadOrCreateRoot_returnsEmptyRoot_whenNothingPersistedYet() throws Exception {

    FinancialLedgerRepositoryJpaImpl repository = newRepository();

    FinancialLedgerRootEntity root = repository.loadAggregateRoot("FinancialLedgerRootEntity");

    assertEquals(List.of(), root.getSplitPeriods());
    assertEquals(List.of(), root.getLocalCashTransactions());
    assertEquals(List.of(), root.getLocalCreditCardTransactions());
  }
}
