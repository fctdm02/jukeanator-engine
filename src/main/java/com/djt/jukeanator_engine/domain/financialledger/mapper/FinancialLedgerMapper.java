package com.djt.jukeanator_engine.domain.financialledger.mapper;

import java.util.ArrayList;
import java.util.List;
import com.djt.jukeanator_engine.domain.financialledger.dto.FinancialLedgerRootDto;
import com.djt.jukeanator_engine.domain.financialledger.dto.JukeboxSplitPeriodDto;
import com.djt.jukeanator_engine.domain.financialledger.dto.LocalTransactionDto;
import com.djt.jukeanator_engine.domain.financialledger.model.FinancialLedgerRootEntity;
import com.djt.jukeanator_engine.domain.financialledger.model.JukeboxSplitPeriodEntity;
import com.djt.jukeanator_engine.domain.financialledger.model.LocalCashTransactionEntity;
import com.djt.jukeanator_engine.domain.financialledger.model.LocalCreditTransactionEntity;

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

    return new FinancialLedgerRootDto(periodDtos, cashDtos, creditCardDtos);
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

  public static LocalTransactionDto toDto(LocalCashTransactionEntity entity) {

    return new LocalTransactionDto(
        entity.getPersistentIdentity(),
        entity.getAmountDollars(),
        entity.getTimestamp(),
        entity.getLocationId());
  }

  public static LocalTransactionDto toDto(LocalCreditTransactionEntity entity) {

    return new LocalTransactionDto(
        entity.getPersistentIdentity(),
        entity.getAmountDollars(),
        entity.getTimestamp(),
        entity.getLocationId());
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

  public static LocalCashTransactionEntity toCashEntity(LocalTransactionDto dto) {

    return new LocalCashTransactionEntity(
        dto.persistentIdentity(),
        dto.amountDollars(),
        dto.timestamp(),
        dto.locationId());
  }

  public static LocalCreditTransactionEntity toCreditCardEntity(LocalTransactionDto dto) {

    return new LocalCreditTransactionEntity(
        dto.persistentIdentity(),
        dto.amountDollars(),
        dto.timestamp(),
        dto.locationId());
  }
}
