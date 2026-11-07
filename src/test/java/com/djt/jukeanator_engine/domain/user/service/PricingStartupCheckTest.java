package com.djt.jukeanator_engine.domain.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.djt.jukeanator_engine.domain.user.dto.CreditPackageDto;
import com.djt.jukeanator_engine.domain.user.service.PricingStartupCheck.CardPricing;
import com.djt.jukeanator_engine.domain.user.service.PricingStartupCheck.Report;

/** Tests for {@link PricingStartupCheck#analyze}. */
class PricingStartupCheckTest {

  /** The default card pricing: 3 credits per dollar, bonuses 3/10, card multiplier 2. */
  private static final CardPricing DEFAULT_CARD = cardPricing("2");

  private static CardPricing cardPricing(String multiplier) {
    return new CardPricing(3, 3, 10, new BigDecimal(multiplier));
  }

  private static CreditPackageDto pkg(String id, String price, int credits, int bonus) {
    return new CreditPackageDto(id, credits, bonus, new BigDecimal(price), null);
  }

  @Test
  void theDefaults_produceNoWarnings() {
    Report report =
        PricingStartupCheck.analyze(CreditPackageCatalog.DEFAULT_PACKAGES, 2, DEFAULT_CARD);

    assertEquals(List.of(), report.warnings());
    assertTrue(report.info().contains("Add Funds package pkg-28: $28.00 for 72 credits = 36 songs, "
        + "$0.78 per song"), report.info().toString());
    assertTrue(report.info().contains("Card reader $20: 40 songs, $0.50 per song"),
        report.info().toString());
  }

  @Test
  void aPackageThatStrandsCredits_isWarnedAbout() {
    Report report =
        PricingStartupCheck.analyze(List.of(pkg("pkg-7", "7.00", 12, 1)), 2, null);

    assertEquals(1, report.warnings().size());
    assertTrue(report.warnings().get(0).contains("13 credits leaves 1 credit(s)"),
        report.warnings().get(0));
  }

  @Test
  void aWebPackageNoDearerThanTheCardReader_isWarnedAbout() {
    // $20 for 80 credits = 40 songs at $0.50 -- cheaper than the card reader's $5 button ($0.56).
    Report report =
        PricingStartupCheck.analyze(List.of(pkg("pkg-20", "20.00", 80, 0)), 2, DEFAULT_CARD);

    assertEquals(1, report.warnings().size());
    assertTrue(report.warnings().get(0).startsWith("Add Funds package pkg-20 costs $0.50 per song"),
        report.warnings().get(0));
  }

  @Test
  void aCardReaderCheaperThanCash_isWarnedAbout() {
    Report report = PricingStartupCheck.analyze(CreditPackageCatalog.DEFAULT_PACKAGES, 2,
        cardPricing("0.5"));

    assertTrue(report.warnings().stream().anyMatch(w -> w.contains("cheaper than cash")),
        report.warnings().toString());
  }

  @Test
  void withCardProcessingOff_onlyThePackagesAreChecked() {
    Report report =
        PricingStartupCheck.analyze(CreditPackageCatalog.DEFAULT_PACKAGES, 2, null);

    assertEquals(List.of(), report.warnings());
    assertTrue(report.info().stream().noneMatch(line -> line.startsWith("Card reader")));
  }
}
