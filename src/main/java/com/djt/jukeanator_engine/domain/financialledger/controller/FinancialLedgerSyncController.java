package com.djt.jukeanator_engine.domain.financialledger.controller;

import static java.util.Objects.requireNonNull;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.djt.jukeanator_engine.domain.financialledger.dto.JukeboxSplitPeriodSyncDto;
import com.djt.jukeanator_engine.domain.financialledger.dto.LocalTransactionSyncDto;
import com.djt.jukeanator_engine.domain.financialledger.service.FinancialLedgerService;
import com.djt.jukeanator_engine.domain.location.controller.LocationController;
import com.djt.jukeanator_engine.domain.user.dto.UserSongCreditUsageDto;

/**
 * Master-only. Slave financial-ledger mirror endpoints -- the local (bill-acceptor /
 * credit-card-reader) and jukebox-split counterpart to {@code LocationController}'s library-sync
 * endpoints, plus the slave's catch-up pull of the mobile/web spends master recorded against its
 * location. Authenticated the exact same way (the {@code location-id}/{@code location-api-key}
 * headers, via {@code LocationApiKeyAuthenticationFilter}).
 *
 * @author tmyers
 */
@RestController
@RequestMapping("/api/locations")
@ConditionalOnProperty(name = "app.mode", havingValue = "master")
public class FinancialLedgerSyncController {

  private final FinancialLedgerService financialLedgerService;

  public FinancialLedgerSyncController(FinancialLedgerService financialLedgerService) {

    requireNonNull(financialLedgerService, "financialLedgerService cannot be null");
    this.financialLedgerService = financialLedgerService;
  }

  @PostMapping("/{locationId}/financial-ledger/local-cash")
  public ResponseEntity<Void> syncLocalCash(
      @PathVariable Integer locationId,
      @RequestHeader(LocationController.LOCATION_API_KEY_HEADER) String apiKey,
      @RequestBody LocalTransactionSyncDto dto) {

    financialLedgerService.receiveLocalCashSync(locationId, apiKey, dto);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{locationId}/financial-ledger/local-credit-card")
  public ResponseEntity<Void> syncLocalCreditCard(
      @PathVariable Integer locationId,
      @RequestHeader(LocationController.LOCATION_API_KEY_HEADER) String apiKey,
      @RequestBody LocalTransactionSyncDto dto) {

    financialLedgerService.receiveLocalCreditCardSync(locationId, apiKey, dto);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{locationId}/financial-ledger/split-period")
  public ResponseEntity<Void> syncSplitPeriod(
      @PathVariable Integer locationId,
      @RequestHeader(LocationController.LOCATION_API_KEY_HEADER) String apiKey,
      @RequestBody JukeboxSplitPeriodSyncDto dto) {

    financialLedgerService.receiveSplitPeriodSync(locationId, apiKey, dto);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/{locationId}/financial-ledger/mobile-credit-usage")
  public List<UserSongCreditUsageDto> getMobileCreditUsage(
      @PathVariable Integer locationId,
      @RequestHeader(LocationController.LOCATION_API_KEY_HEADER) String apiKey,
      @RequestParam Instant since) {

    return financialLedgerService.getMobileCreditUsageForSync(locationId, apiKey, since);
  }
}
