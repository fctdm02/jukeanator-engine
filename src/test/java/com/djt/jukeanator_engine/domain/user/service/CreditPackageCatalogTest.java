package com.djt.jukeanator_engine.domain.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.djt.jukeanator_engine.domain.user.dto.CreditPackageDto;

/** Tests for {@link CreditPackageCatalog}. */
class CreditPackageCatalogTest {

  private static CreditPackageDto pkg(String id, String price, int credits, int bonus) {
    return new CreditPackageDto(id, credits, bonus, price == null ? null : new BigDecimal(price),
        null);
  }

  @Test
  void defaultPackages_divideEvenlyByTheDefaultWebSongCost() {
    for (CreditPackageDto pkg : CreditPackageCatalog.DEFAULT_PACKAGES) {
      assertEquals(0, (pkg.credits() + pkg.bonusCredits()) % 2, pkg.id());
    }
  }

  @Test
  void validate_acceptsTheDefaults() {
    assertEquals(CreditPackageCatalog.DEFAULT_PACKAGES,
        CreditPackageCatalog.validate(CreditPackageCatalog.DEFAULT_PACKAGES));
  }

  @Test
  void validate_rejectsAnEmptyList() {
    assertThrows(IllegalStateException.class, () -> CreditPackageCatalog.validate(List.of()));
  }

  @Test
  void validate_rejectsADuplicateId() {
    assertThrows(IllegalStateException.class, () -> CreditPackageCatalog
        .validate(List.of(pkg("pkg-7", "7.00", 12, 2), pkg("pkg-7", "8.00", 14, 2))));
  }

  @Test
  void validate_rejectsABlankId() {
    assertThrows(IllegalStateException.class,
        () -> CreditPackageCatalog.validate(List.of(pkg(" ", "7.00", 12, 2))));
  }

  @Test
  void validate_rejectsAMissingOrNonPositivePrice() {
    assertThrows(IllegalStateException.class,
        () -> CreditPackageCatalog.validate(List.of(pkg("pkg-7", null, 12, 2))));
    assertThrows(IllegalStateException.class,
        () -> CreditPackageCatalog.validate(List.of(pkg("pkg-7", "0.00", 12, 2))));
  }

  @Test
  void validate_rejectsNonPositiveCreditsOrNegativeBonus() {
    assertThrows(IllegalStateException.class,
        () -> CreditPackageCatalog.validate(List.of(pkg("pkg-7", "7.00", 0, 2))));
    assertThrows(IllegalStateException.class,
        () -> CreditPackageCatalog.validate(List.of(pkg("pkg-7", "7.00", 12, -1))));
  }
}
