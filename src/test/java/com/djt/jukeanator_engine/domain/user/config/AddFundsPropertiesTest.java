package com.djt.jukeanator_engine.domain.user.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.djt.jukeanator_engine.domain.user.dto.CreditPackageDto;
import com.djt.jukeanator_engine.domain.user.service.CreditPackageCatalog;

/** Tests for {@link AddFundsProperties#toCreditPackages()}. */
class AddFundsPropertiesTest {

  @Test
  void noConfiguredPackages_meansTheBuiltInDefaults() {
    assertEquals(CreditPackageCatalog.DEFAULT_PACKAGES, new AddFundsProperties().toCreditPackages());
  }

  @Test
  void configuredPackages_areConvertedInOrder_withABlankBadgeAsNone() {
    AddFundsProperties properties = new AddFundsProperties();
    properties.setPackages(List.of(entry("pkg-10", "10.00", 20, 4, "Popular"),
        entry("pkg-5", "5.00", 10, 0, " ")));

    List<CreditPackageDto> packages = properties.toCreditPackages();

    assertEquals(new CreditPackageDto("pkg-10", 20, 4, new BigDecimal("10.00"), "Popular"),
        packages.get(0));
    assertEquals("pkg-5", packages.get(1).id());
    assertNull(packages.get(1).badge());
  }

  private static AddFundsProperties.CreditPackage entry(String id, String price, int credits,
      int bonus, String badge) {
    AddFundsProperties.CreditPackage entry = new AddFundsProperties.CreditPackage();
    entry.setId(id);
    entry.setPriceUsd(new BigDecimal(price));
    entry.setCredits(credits);
    entry.setBonusCredits(bonus);
    entry.setBadge(badge);
    return entry;
  }
}
