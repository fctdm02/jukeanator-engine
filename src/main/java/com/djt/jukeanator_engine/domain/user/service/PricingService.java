package com.djt.jukeanator_engine.domain.user.service;

import java.util.List;
import com.djt.jukeanator_engine.domain.user.dto.CreditPackageDto;

/**
 * Resolves the effective credit-pricing config for a location, so credit-charging/display logic
 * never has to know whether it's running standalone/slave (same JVM as the JFC/Swing UI, whose
 * own {@code user-interface.*} YAML is authoritative) or master (headless; each location's own
 * synced {@code LocationEntity} columns govern its Web/Mobile pricing instead).
 */
public interface PricingService {

  /**
   * {@code locationId} is ignored on standalone/slave (there is only ever one location, this
   * instance's own). On master it selects which location's synced config to use; if that location
   * hasn't synced a config yet (or {@code locationId} is {@code null}), falls back to this
   * instance's own {@code user-interface.*} YAML defaults.
   */
  PricingConfig resolvePricingConfig(Integer locationId);

  /**
   * The Add Funds credit packages sold for {@code locationId}, in display order. On
   * standalone/slave, this instance's own {@code add-funds.packages}. On master, the packages that
   * location's slave last pushed, falling back to master's own {@code add-funds.packages} (or the
   * built-in defaults) until it has.
   */
  List<CreditPackageDto> resolveCreditPackages(Integer locationId);
}
