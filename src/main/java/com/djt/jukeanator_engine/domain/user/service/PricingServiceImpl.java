package com.djt.jukeanator_engine.domain.user.service;

import java.util.List;
import com.djt.jukeanator_engine.config.AppProperties;
import com.djt.jukeanator_engine.domain.location.model.LocationEntity;
import com.djt.jukeanator_engine.domain.location.service.LocationService;
import com.djt.jukeanator_engine.domain.user.config.AddFundsProperties;
import com.djt.jukeanator_engine.domain.user.dto.CreditPackageDto;
import com.djt.jukeanator_engine.ui.config.JukeANatorUserInterfaceProperties;

public class PricingServiceImpl implements PricingService {

  private final AppProperties appProperties;
  private final JukeANatorUserInterfaceProperties userInterfaceProperties;
  private final LocationService locationService;
  private final List<CreditPackageDto> ownCreditPackages;

  public PricingServiceImpl(AppProperties appProperties,
      JukeANatorUserInterfaceProperties userInterfaceProperties, LocationService locationService,
      AddFundsProperties addFundsProperties) {
    this.appProperties = appProperties;
    this.userInterfaceProperties = userInterfaceProperties;
    this.locationService = locationService;
    this.ownCreditPackages = CreditPackageCatalog.validate(addFundsProperties.toCreditPackages());
  }

  @Override
  public List<CreditPackageDto> resolveCreditPackages(Integer locationId) {

    if (!appProperties.isMaster()) {
      return ownCreditPackages;
    }
    LocationEntity location =
        locationId == null ? null : locationService.getLocationByIdNullIfNotExists(locationId);
    if (location == null || location.getCreditPackages().isEmpty()) {
      return ownCreditPackages;
    }
    return List.copyOf(location.getCreditPackages());
  }

  @Override
  public PricingConfig resolvePricingConfig(Integer locationId) {

    if (!appProperties.isMaster()) {
      // Same JVM as the JFC/Swing UI -- its own YAML is authoritative.
      return new PricingConfig(userInterfaceProperties.getPriorityCostMultiplier(),
          userInterfaceProperties.getCreditsPerDollar(),
          userInterfaceProperties.getFiveDollarBonusCredits(),
          userInterfaceProperties.getTenDollarBonusCredits(),
          userInterfaceProperties.getWebCostMultiplier(),
          userInterfaceProperties.isDisplayCurrencyForCost());
    }

    LocationEntity location =
        locationId == null ? null : locationService.getLocationByIdNullIfNotExists(locationId);

    // Falls back to JukeANatorUserInterfaceProperties' own field defaults for any value not yet
    // synced from that location's slave (see SlaveConnectionManager.sendPricingConfig) -- not
    // master's own YAML as "the" answer, just a sane default until the real sync arrives.
    return new PricingConfig(
        orDefault(location, LocationEntity::getPriorityCostMultiplier,
            userInterfaceProperties.getPriorityCostMultiplier()),
        orDefault(location, LocationEntity::getCreditsPerDollar,
            userInterfaceProperties.getCreditsPerDollar()),
        orDefault(location, LocationEntity::getFiveDollarBonusCredits,
            userInterfaceProperties.getFiveDollarBonusCredits()),
        orDefault(location, LocationEntity::getTenDollarBonusCredits,
            userInterfaceProperties.getTenDollarBonusCredits()),
        orDefault(location, LocationEntity::getWebCostMultiplier,
            userInterfaceProperties.getWebCostMultiplier()),
        location != null && location.getDisplayCurrencyForCost() != null
            ? location.getDisplayCurrencyForCost()
            : userInterfaceProperties.isDisplayCurrencyForCost());
  }

  private static int orDefault(LocationEntity location,
      java.util.function.Function<LocationEntity, Integer> getter, int defaultValue) {
    Integer value = location == null ? null : getter.apply(location);
    return value != null ? value : defaultValue;
  }
}
