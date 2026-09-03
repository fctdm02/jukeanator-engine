package com.djt.jukeanator_engine.config;

import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Serves {@code /images/**} from the operator-configurable {@code <dataDir>/images/} directory
 * first, falling back to the built-in images bundled on the classpath. This is the web UI's
 * counterpart to {@link com.djt.jukeanator_engine.ui.components.ImageLoader}, which lets the
 * Swing desktop UI pick up an operator-supplied logo override (e.g. a location's
 * {@code logoName}) from the same directory without a rebuild.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

  private static final Logger log = LoggerFactory.getLogger(WebConfig.class);

  private final AppProperties appProperties;

  public WebConfig(AppProperties appProperties) {
    this.appProperties = appProperties;
  }

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {

    // Path.toUri() (rather than string-concatenating "file:" + dataDir) produces a properly
    // encoded file: URL regardless of platform separator -- string concatenation on Windows
    // leaves raw backslashes in the location, which Spring's UrlResource silently fails to
    // resolve to any file.
    Path dataDirImagesPath = Path.of(appProperties.getDataDir(), "images");
    String dataDirImages = dataDirImagesPath.toUri().toString();

    log.info("Serving /images/** from {} (exists={}), falling back to classpath:/static/images/",
        dataDirImagesPath.toAbsolutePath(), dataDirImagesPath.toFile().isDirectory());

    registry.addResourceHandler("/images/**")
        .addResourceLocations(dataDirImages, "classpath:/static/images/");
  }
}
