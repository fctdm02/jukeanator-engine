package com.djt.jukeanator_engine.domain.financialledger.mapper;

import java.util.ArrayList;
import java.util.List;
import com.djt.jukeanator_engine.domain.financialledger.dto.FinancialLedgerRootDto;
import com.djt.jukeanator_engine.domain.financialledger.dto.JukeboxSplitPeriodDto;
import com.djt.jukeanator_engine.domain.financialledger.dto.JukeboxSplitPeriodSyncDto;
import com.djt.jukeanator_engine.domain.financialledger.dto.LocalTransactionDto;
import com.djt.jukeanator_engine.domain.financialledger.dto.LocalTransactionSyncDto;
import com.djt.jukeanator_engine.domain.financialledger.dto.LocationMobileCreditUsageDto;
import com.djt.jukeanator_engine.domain.financialledger.model.AbstractLocationTransactionEntity;
import com.djt.jukeanator_engine.domain.financialledger.model.FinancialLedgerRootEntity;
import com.djt.jukeanator_engine.domain.financialledger.model.JukeboxSplitPeriodEntity;
import com.djt.jukeanator_engine.domain.financialledger.model.LocalCashTransactionEntity;
import com.djt.jukeanator_engine.domain.financialledger.model.LocalCreditTransactionEntity;
import com.djt.jukeanator_engine.domain.financialledger.model.LocationMobileCreditUsageEntity;

public final class FinancialLedgerMapper {

  private FinancialLedgerMapper() {}

  public static FinancialLedgerRootDto toDto(FinancialLedgerRootEntity root) {

    List<JukeboxSplitPeriodDto> periodDtos = new ArrayList<>();
    for (JukeboxSplitPeriodEntity period : root.getSplitPeriods()) {
      periodDtos.add(toDto(period));
    }

    List<LocalTransactionDto> cashDtos = new ArrayList<>();
    for (LocalCashTransactionEntity transaction : root.getLocalCashTransactions()) {
      cashDtos.add(toDto(transaction));
    }

    List<LocalTransactionDto> creditCardDtos = new ArrayList<>();
    for (LocalCreditTransactionEntity transaction : root.getLocalCreditCardTransactions()) {
      creditCardDtos.add(toDto(transaction));
    }

    List<LocationMobileCreditUsageDto> mobileDtos = new ArrayList<>();
    for (LocationMobileCreditUsageEntity usage : root.getMobileCreditUsages()) {
      mobileDtos.add(toDto(usage));
    }

    return new FinancialLedgerRootDto(periodDtos, cashDtos, creditCardDtos, mobileDtos);
  }

  public static JukeboxSplitPeriodDto toDto(JukeboxSplitPeriodEntity entity) {

    return new JukeboxSplitPeriodDto(
        entity.getPersistentIdentity(),
        entity.getStartDate(),
        entity.getEndDate(),
        entity.getSplitPercentageToOwner(),
        entity.getCashTotal(),
        entity.getCardTotal(),
        entity.getMobileTotal(),
        entity.getTotalEarned(),
        entity.getAmountDueOwner(),
        entity.getAmountDueOperator(),
        entity.getLocationId(),
        entity.getSourcePeriodId(),
        entity.getSyncedToMasterAt());
  }

  public static LocalTransactionDto toDto(AbstractLocationTransactionEntity entity) {

    return new LocalTransactionDto(
        entity.getPersistentIdentity(),
        entity.getAmountDollars(),
        entity.getTimestamp(),
        entity.getLocationId(),
        entity.getSourceTransactionId(),
        entity.getSyncedToMasterAt());
  }

  public static LocationMobileCreditUsageDto toDto(LocationMobileCreditUsageEntity entity) {

    return new LocationMobileCreditUsageDto(
        entity.getPersistentIdentity(),
        entity.getLocationId(),
        entity.getSourceSyncId(),
        entity.getUserEmail(),
        entity.getAmount(),
        entity.getType(),
        entity.getTimestamp(),
        entity.getSongAlbumId(),
        entity.getSongId());
  }

  /** The slave-to-master mirror's wire shape for one of this instance's own transactions. */
  public static LocalTransactionSyncDto toSyncDto(AbstractLocationTransactionEntity entity) {

    return new LocalTransactionSyncDto(entity.getPersistentIdentity(), entity.getAmountDollars(),
        entity.getTimestamp());
  }

  /** The slave-to-master mirror's wire shape for one of this instance's own finalized periods. */
  public static JukeboxSplitPeriodSyncDto toSyncDto(JukeboxSplitPeriodEntity entity) {

    return new JukeboxSplitPeriodSyncDto(
        entity.getPersistentIdentity(),
        entity.getStartDate(),
        entity.getEndDate(),
        entity.getSplitPercentageToOwner(),
        entity.getCashTotal(),
        entity.getCardTotal(),
        entity.getMobileTotal(),
        entity.getTotalEarned(),
        entity.getAmountDueOwner(),
        entity.getAmountDueOperator());
  }

  public static FinancialLedgerRootEntity toEntity(FinancialLedgerRootDto dto) {

    FinancialLedgerRootEntity root = new FinancialLedgerRootEntity();

    for (JukeboxSplitPeriodDto periodDto : dto.splitPeriods()) {
      root.addSplitPeriod(toEntity(periodDto));
    }
    for (LocalTransactionDto cashDto : dto.localCashTransactions()) {
      root.addLocalCashTransaction(toCashEntity(cashDto));
    }
    for (LocalTransactionDto creditCardDto : dto.localCreditCardTransactions()) {
      root.addLocalCreditCardTransaction(toCreditCardEntity(creditCardDto));
    }
    if (dto.mobileCreditUsages() != null) {
      for (LocationMobileCreditUsageDto mobileDto : dto.mobileCreditUsages()) {
        root.addMobileCreditUsage(toEntity(mobileDto));
      }
    }

    return root;
  }

  public static JukeboxSplitPeriodEntity toEntity(JukeboxSplitPeriodDto dto) {

    return new JukeboxSplitPeriodEntity(
        dto.persistentIdentity(),
        dto.startDate(),
        dto.endDate(),
        dto.splitPercentageToOwner(),
        dto.cashTotal(),
        dto.cardTotal(),
        dto.mobileTotal(),
        dto.totalEarned(),
        dto.amountDueOwner(),
        dto.amountDueOperator(),
        dto.locationId(),
        dto.sourcePeriodId(),
        dto.syncedToMasterAt());
  }

  public static LocalCashTransactionEntity toCashEntity(LocalTransactionDto dto) {

    LocalCashTransactionEntity entity = new LocalCashTransactionEntity(
        dto.persistentIdentity(),
        dto.amountDollars(),
        dto.timestamp(),
        dto.locationId(),
        dto.sourceTransactionId());
    entity.markSyncedToMaster(dto.syncedToMasterAt());
    return entity;
  }

  public static LocalCreditTransactionEntity toCreditCardEntity(LocalTransactionDto dto) {

    LocalCreditTransactionEntity entity = new LocalCreditTransactionEntity(
        dto.persistentIdentity(),
        dto.amountDollars(),
        dto.timestamp(),
        dto.locationId(),
        dto.sourceTransactionId());
    entity.markSyncedToMaster(dto.syncedToMasterAt());
    return entity;
  }

  public static LocationMobileCreditUsageEntity toEntity(LocationMobileCreditUsageDto dto) {

    return new LocationMobileCreditUsageEntity(
        dto.persistentIdentity(),
        dto.locationId(),
        dto.sourceSyncId(),
        dto.userEmail(),
        dto.amount(),
        dto.type(),
        dto.timestamp(),
        dto.songAlbumId(),
        dto.songId());
  }
}
