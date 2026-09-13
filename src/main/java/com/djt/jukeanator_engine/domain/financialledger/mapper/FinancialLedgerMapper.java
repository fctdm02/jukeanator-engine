package com.djt.jukeanator_engine.domain.financialledger.mapper;

import java.util.ArrayList;
import java.util.List;
import com.djt.jukeanator_engine.domain.financialledger.dto.FinancialLedgerRootDto;
import com.djt.jukeanator_engine.domain.financialledger.dto.JukeboxSplitPeriodDto;
import com.djt.jukeanator_engine.domain.financialledger.dto.LocalCreditTransactionDto;
import com.djt.jukeanator_engine.domain.financialledger.model.FinancialLedgerRootEntity;
import com.djt.jukeanator_engine.domain.financialledger.model.JukeboxSplitPeriodEntity;
import com.djt.jukeanator_engine.domain.financialledger.model.LocalCreditSource;
import com.djt.jukeanator_engine.domain.financialledger.model.LocalCreditTransactionEntity;

public final class FinancialLedgerMapper {

  private FinancialLedgerMapper() {}

  public static FinancialLedgerRootDto toDto(FinancialLedgerRootEntity root) {

    List<JukeboxSplitPeriodDto> periodDtos = new ArrayList<>();
    for (JukeboxSplitPeriodEntity period : root.getSplitPeriods()) {
      periodDtos.add(toDto(period));
    }

    List<LocalCreditTransactionDto> transactionDtos = new ArrayList<>();
    for (LocalCreditTransactionEntity transaction : root.getLocalCreditTransactions()) {
      transactionDtos.add(toDto(transaction));
    }

    return new FinancialLedgerRootDto(periodDtos, transactionDtos);
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
        entity.getAmountDueOperator());
  }

  public static LocalCreditTransactionDto toDto(LocalCreditTransactionEntity entity) {

    return new LocalCreditTransactionDto(
        entity.getPersistentIdentity(),
        entity.getSource().name(),
        entity.getAmountDollars(),
        entity.getTimestamp(),
        entity.getLocationId());
  }

  public static FinancialLedgerRootEntity toEntity(FinancialLedgerRootDto dto) {

    FinancialLedgerRootEntity root = new FinancialLedgerRootEntity();

    for (JukeboxSplitPeriodDto periodDto : dto.splitPeriods()) {
      root.addSplitPeriod(toEntity(periodDto));
    }
    for (LocalCreditTransactionDto transactionDto : dto.localCreditTransactions()) {
      root.addLocalCreditTransaction(toEntity(transactionDto));
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
        dto.amountDueOperator());
  }

  public static LocalCreditTransactionEntity toEntity(LocalCreditTransactionDto dto) {

    return new LocalCreditTransactionEntity(
        dto.persistentIdentity(),
        LocalCreditSource.valueOf(dto.source()),
        dto.amountDollars(),
        dto.timestamp(),
        dto.locationId());
  }
}
