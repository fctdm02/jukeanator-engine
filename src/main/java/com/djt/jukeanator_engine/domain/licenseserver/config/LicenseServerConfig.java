package com.djt.jukeanator_engine.domain.licenseserver.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneId;

import jakarta.persistence.EntityManagerFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.djt.jukeanator_engine.domain.licenseserver.repository.LicenseServerRepository;
import com.djt.jukeanator_engine.domain.licenseserver.repository.LicenseServerRepositoryJpaImpl;
import com.djt.jukeanator_engine.domain.licenseserver.service.LicenseServerService;
import com.djt.jukeanator_engine.domain.licenseserver.service.LicenseServerServiceImpl;

/**
 * Wires the license server (master mode only): repository, service, and the {@code /temp/**}
 * and {@code /webstart/**} download paths that legacy clients use for auto-update.
 */
@Configuration
@ConditionalOnProperty(name = "app.mode", havingValue = "master")
public class LicenseServerConfig implements WebMvcConfigurer {

  private static final Logger log = LoggerFactory.getLogger(LicenseServerConfig.class);

  private final LicenseServerProperties properties;

  public LicenseServerConfig(LicenseServerProperties properties) {
    this.properties = properties;
  }

  @Bean
  public LicenseServerRepository licenseServerRepository(
      EntityManagerFactory entityManagerFactory, PlatformTransactionManager transactionManager) {
    return new LicenseServerRepositoryJpaImpl(entityManagerFactory, transactionManager);
  }

  @Bean
  public LicenseServerService licenseServerService(LicenseServerRepository repository) {
    return new LicenseServerServiceImpl(repository, ZoneId.of(properties.getTimeZone()),
        Clock.systemUTC());
  }

  /**
   * Serves {@code <downloads-dir>/temp/**} and {@code <downloads-dir>/webstart/**}. These hold the
   * legacy auto-update jar, {@code kiosk.zip}, third-party jars and JNLP files, which are too large
   * (and too release-specific) to ship inside the WAR.
   */
  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    String dir = properties.getDownloadsDir();
    if (dir == null || dir.isBlank()) {
      log.warn("license-server.downloads-dir is not set: /temp/** and /webstart/** are disabled,"
          + " so legacy MP3Jukeboxx auto-update downloads will fail");
      return;
    }
    Path root = Path.of(dir);
    log.info("Serving /temp/** and /webstart/** from {} (exists={})", root.toAbsolutePath(),
        Files.isDirectory(root));
    registry.addResourceHandler("/temp/**").addResourceLocations(dirUri(root.resolve("temp")));
    registry.addResourceHandler("/webstart/**")
        .addResourceLocations(dirUri(root.resolve("webstart")));
  }

  // Path.toUri() only appends the trailing slash Spring needs when the directory already exists.
  private static String dirUri(Path dir) {
    String uri = dir.toUri().toString();
    return uri.endsWith("/") ? uri : uri + "/";
  }
}
