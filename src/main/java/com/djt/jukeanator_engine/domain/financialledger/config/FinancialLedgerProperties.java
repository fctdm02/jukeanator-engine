package com.djt.jukeanator_engine.domain.financialledger.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "financial-ledger")
public class FinancialLedgerProperties {

  // Percentage of total period earnings (cash + credit-card + mobile) due to the location owner
  // when a split is finalized; the operator keeps the remainder. Snapshotted onto each
  // JukeboxSplitPeriodEntity at the moment it's finalized, so a later change to this setting never
  // retroactively changes an already-finalized period's amounts.
  private int jukeboxSplitPercentage = 50;

  public int getJukeboxSplitPercentage() {
    return jukeboxSplitPercentage;
  }

  public void setJukeboxSplitPercentage(int jukeboxSplitPercentage) {
    this.jukeboxSplitPercentage = jukeboxSplitPercentage;
  }
}
