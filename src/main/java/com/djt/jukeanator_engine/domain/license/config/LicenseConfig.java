package com.djt.jukeanator_engine.domain.license.config;

import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import com.djt.jukeanator_engine.JukeANatorBackendApplication;
import com.djt.jukeanator_engine.config.AppProperties;
import com.djt.jukeanator_engine.config.ExternalConfigUpdater;
import com.djt.jukeanator_engine.config.NotMasterModeCondition;
import com.djt.jukeanator_engine.domain.license.client.LicenseServerClient;
import com.djt.jukeanator_engine.domain.license.service.LegacyMachineIdentityProvider;
import com.djt.jukeanator_engine.domain.license.service.LicenseScheduler;
import com.djt.jukeanator_engine.domain.license.service.LicenseService;
import com.djt.jukeanator_engine.domain.license.service.LicenseServiceImpl;
import com.djt.jukeanator_engine.domain.license.service.LicenseStartupCheck;
import com.djt.jukeanator_engine.domain.license.service.MachineIdentityProvider;

/**
 * Wires the legacy license checks for standalone and slave instances. A master instance is the
 * license server itself (see {@code LicenseServerConfig}) and never constructs these beans.
 */
@Configuration
@Conditional(NotMasterModeCondition.class)
public class LicenseConfig {

  @Bean
  public MachineIdentityProvider machineIdentityProvider(AppProperties appProperties) {
    List<Path> diskId32Locations = new ArrayList<>();
    diskId32Locations.add(Path.of(appProperties.getDataDir()));
    diskId32Locations.add(Path.of("C:\\kiosk"));
    return new LegacyMachineIdentityProvider(diskId32Locations);
  }

  @Bean
  public LicenseServerClient licenseServerClient(LicenseProperties properties,
      AppProperties appProperties) {
    return new LicenseServerClient(properties, appVersion(), appProperties.getMode());
  }

  @Bean
  public LicenseService licenseService(LicenseProperties properties,
      MachineIdentityProvider machineIdentityProvider, LicenseServerClient licenseServerClient,
      ApplicationEventPublisher events, AppProperties appProperties, Environment environment) {
    return new LicenseServiceImpl(properties, machineIdentityProvider, licenseServerClient,
        events, Path.of(appProperties.getDataDir()),
        ExternalConfigUpdater.resolveExternalConfigFile(environment), Clock.systemDefaultZone(),
        (file, values) -> ExternalConfigUpdater.persistSectionValues(file, "license", values));
  }

  @Bean
  public LicenseScheduler licenseScheduler(LicenseService licenseService,
      LicenseProperties properties) {
    return new LicenseScheduler(licenseService, properties);
  }

  @Bean
  public LicenseStartupCheck licenseStartupCheck(LicenseService licenseService,
      LicenseScheduler licenseScheduler) {
    return new LicenseStartupCheck(licenseService, licenseScheduler);
  }

  /** Implementation-Version from the manifest of a packaged build, else "dev". */
  static String appVersion() {
    String v = JukeANatorBackendApplication.class.getPackage().getImplementationVersion();
    return v == null || v.isBlank() ? "dev" : v;
  }
}
