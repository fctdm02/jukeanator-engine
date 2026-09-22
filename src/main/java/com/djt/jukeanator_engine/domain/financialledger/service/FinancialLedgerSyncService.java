package com.djt.jukeanator_engine.domain.financialledger.service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import com.djt.jukeanator_engine.config.AppProperties;
import com.djt.jukeanator_engine.domain.financialledger.dto.LocalTransactionSyncDto;
import com.djt.jukeanator_engine.domain.financialledger.event.LocalFinancialTransactionRecordedEvent;

/**
 * Slave-only. Mirrors every local (bill-acceptor / credit-card-reader) transaction up to master as
 * it happens, modeled directly on {@code LibrarySyncService}: a background daemon executor so a
 * slow/unreachable master never delays the Swing UI's own credit-award flow, and best-effort --
 * any failure is caught and logged, never retried. A slave's own local ledger already has the
 * transaction regardless of whether the mirror push succeeds, so this is a durability tradeoff
 * (not a data-loss risk for the slave itself), matching the same tradeoff
 * {@code LibrarySyncService} already accepts for library-metadata sync.
 *
 * @author tmyers
 */
@Component
@ConditionalOnProperty(name = "app.mode", havingValue = "slave")
public class FinancialLedgerSyncService {

  private static final Logger log = LoggerFactory.getLogger(FinancialLedgerSyncService.class);

  private final AppProperties appProperties;
  private final RestClient restClient;

  private final ExecutorService syncExecutor = Executors.newSingleThreadExecutor(r -> {
    Thread t = new Thread(r, "financial-ledger-sync-thread");
    t.setDaemon(true);
    return t;
  });

  public FinancialLedgerSyncService(AppProperties appProperties) {

    this.appProperties = appProperties;
    this.restClient = RestClient.builder().baseUrl(appProperties.getMasterInstanceUrl()).build();
  }

  @EventListener
  public void handleLocalFinancialTransactionRecordedEvent(
      LocalFinancialTransactionRecordedEvent event) {

    syncExecutor.submit(() -> {
      try {
        syncTransaction(event);
      } catch (Throwable t) {
        // Must never let a sync failure propagate anywhere that could affect the slave's own
        // credit-award flow -- see the class javadoc: this is best-effort only.
        log.warn("Could not mirror local " + event.kind() + " transaction to master at "
            + appProperties.getMasterInstanceUrl(), t);
      }
    });
  }

  private void syncTransaction(LocalFinancialTransactionRecordedEvent event) {

    if (event.locationId() == null) {
      log.warn("Skipping financial-ledger mirror for a transaction with no locationId -- kind: "
          + event.kind() + ", sourceTransactionId: " + event.sourceTransactionId());
      return;
    }

    String pathSegment = event.kind() == LocalFinancialTransactionRecordedEvent.Kind.CASH
        ? "local-cash"
        : "local-credit-card";

    LocalTransactionSyncDto dto = new LocalTransactionSyncDto(event.sourceTransactionId(),
        event.amountDollars(), event.timestamp());

    restClient.post()
        .uri("/api/locations/{locationId}/financial-ledger/{pathSegment}", event.locationId(),
            pathSegment)
        .header("location-id", String.valueOf(event.locationId()))
        .header("location-api-key", appProperties.getLocationApiKey())
        .contentType(MediaType.APPLICATION_JSON)
        .body(dto)
        .retrieve()
        .toBodilessEntity();
  }
}
