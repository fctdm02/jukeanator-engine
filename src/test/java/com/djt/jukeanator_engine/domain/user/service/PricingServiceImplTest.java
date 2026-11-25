package com.djt.jukeanator_engine.domain.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.djt.jukeanator_engine.config.AppProperties;
import com.djt.jukeanator_engine.domain.location.model.LocationEntity;
import com.djt.jukeanator_engine.domain.location.service.LocationService;
import com.djt.jukeanator_engine.domain.user.config.AddFundsProperties;
import com.djt.jukeanator_engine.domain.user.dto.CreditPackageDto;
import com.djt.jukeanator_engine.ui.config.JukeANatorUserInterfaceProperties;

/**
 * Covers {@link PricingServiceImpl#resolveCreditPackages}: each location sells its own Add Funds
 * packages.
 */
class PricingServiceImplTest {

  private static final Integer LOCATION_ID = Integer.valueOf(42);

  private static final CreditPackageDto OWN_PACKAGE =
      new CreditPackageDto("own-5", 10, 0, new BigDecimal("5.00"), null);
  private static final CreditPackageDto LOCATIONS_PACKAGE =
      new CreditPackageDto("bar-10", 20, 4, new BigDecimal("10.00"), "Best Value");

  private final LocationService locationService = mock(LocationService.class);

  private PricingServiceImpl service(boolean master, List<CreditPackageDto> ownPackages) {

    AppProperties appProperties = mock(AppProperties.class);
    when(appProperties.isMaster()).thenReturn(master);
    AddFundsProperties addFundsProperties = mock(AddFundsProperties.class);
    when(addFundsProperties.toCreditPackages()).thenReturn(ownPackages);
    return new PricingServiceImpl(appProperties, new JukeANatorUserInterfaceProperties(),
        locationService, addFundsProperties);
  }

  @Test
  void onAStandaloneOrSlave_theInstancesOwnPackagesAreSold() {

    PricingServiceImpl service = service(false, List.of(OWN_PACKAGE));

    assertEquals(List.of(OWN_PACKAGE), service.resolveCreditPackages(LOCATION_ID));
    assertEquals(List.of(OWN_PACKAGE), service.resolveCreditPackages(null));
  }

  @Test
  void onMaster_aLocationSellsThePackagesItsSlavePushed() {

    LocationEntity location = new LocationEntity(LOCATION_ID, "The Bar", null, null, "hash");
    location.setCreditPackages(List.of(LOCATIONS_PACKAGE));
    when(locationService.getLocationByIdNullIfNotExists(LOCATION_ID)).thenReturn(location);

    assertEquals(List.of(LOCATIONS_PACKAGE),
        service(true, List.of(OWN_PACKAGE)).resolveCreditPackages(LOCATION_ID));
  }

  @Test
  void onMaster_aLocationWithNoPushedPackagesFallsBackToMastersOwn() {

    when(locationService.getLocationByIdNullIfNotExists(LOCATION_ID))
        .thenReturn(new LocationEntity(LOCATION_ID, "The Bar", null, null, "hash"));
    PricingServiceImpl service = service(true, List.of(OWN_PACKAGE));

    assertEquals(List.of(OWN_PACKAGE), service.resolveCreditPackages(LOCATION_ID));
    assertEquals(List.of(OWN_PACKAGE), service.resolveCreditPackages(Integer.valueOf(999)),
        "an unknown location falls back too");
  }

  @Test
  void anInvalidOwnPackageList_failsStartup() {

    assertThrows(IllegalStateException.class,
        () -> service(false, List.of(OWN_PACKAGE, OWN_PACKAGE)));
  }
}
