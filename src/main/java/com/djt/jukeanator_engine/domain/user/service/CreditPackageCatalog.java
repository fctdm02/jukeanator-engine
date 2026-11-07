package com.djt.jukeanator_engine.domain.user.service;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.djt.jukeanator_engine.domain.user.dto.CreditPackageDto;

/**
 * The Web/Mobile UI's Add Funds credit packages: the built-in defaults, used when
 * {@code add-funds.packages} is not configured, and the validation every configured list must
 * pass. See {@code docs/pricing.md}.
 */
public final class CreditPackageCatalog {

  /**
   * Each total (credits + bonus) is an even number, so it divides exactly by the default Web/Mobile
   * song cost of 2 credits (1 kiosk credit x {@code web-cost-multiplier: 2}): 36, 16 and 7 songs.
   */
  public static final List<CreditPackageDto> DEFAULT_PACKAGES = List.of(
      new CreditPackageDto("pkg-28", 48, 24, new BigDecimal("28.00"), "Best Value"),
      new CreditPackageDto("pkg-14", 24, 8, new BigDecimal("14.00"), null),
      new CreditPackageDto("pkg-7", 12, 2, new BigDecimal("7.00"), null));

  private CreditPackageCatalog() {}

  /**
   * Checks a package list, failing startup on a mistake that would break or misprice purchases.
   *
   * @return an unmodifiable copy of {@code packages}
   * @throws IllegalStateException if the list is empty, or a package has a blank or duplicate id,
   *         a missing or non-positive price, non-positive credits, or negative bonus credits
   */
  public static List<CreditPackageDto> validate(List<CreditPackageDto> packages) {
    if (packages == null || packages.isEmpty()) {
      throw new IllegalStateException("add-funds.packages must list at least one package");
    }
    Set<String> ids = new HashSet<>();
    for (CreditPackageDto pkg : packages) {
      String id = pkg.id();
      if (id == null || id.isBlank()) {
        throw new IllegalStateException("add-funds.packages: every package needs an id");
      }
      if (!ids.add(id)) {
        throw new IllegalStateException("add-funds.packages: duplicate package id '" + id + "'");
      }
      if (pkg.priceUsd() == null || pkg.priceUsd().signum() <= 0) {
        throw new IllegalStateException(
            "add-funds.packages: package '" + id + "' needs a price-usd greater than 0");
      }
      if (pkg.credits() <= 0) {
        throw new IllegalStateException(
            "add-funds.packages: package '" + id + "' needs credits greater than 0");
      }
      if (pkg.bonusCredits() < 0) {
        throw new IllegalStateException(
            "add-funds.packages: package '" + id + "' cannot have negative bonus-credits");
      }
    }
    return List.copyOf(packages);
  }
}
