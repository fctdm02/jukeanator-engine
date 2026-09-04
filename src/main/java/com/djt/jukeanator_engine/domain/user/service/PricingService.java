package com.djt.jukeanator_engine.domain.user.service;

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
}
