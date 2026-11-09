package com.djt.jukeanator_engine.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Routes between the two static front ends bundled in {@code classpath:/static}:
 *
 * <ul>
 * <li>the public jukeanator.com marketing pages at the site root ({@code /index.html},
 * {@code /features.html}, ...), which are the site a master instance serves to the public;</li>
 * <li>the Web/Mobile jukebox UI (single-page app) under {@code /app/}.</li>
 * </ul>
 *
 * <p>On standalone and slave instances (kiosks) the root has no public audience, so {@code /}
 * goes straight to the jukebox UI.
 */
@Configuration
public class StaticSiteConfig implements WebMvcConfigurer {

  private final AppProperties appProperties;

  public StaticSiteConfig(AppProperties appProperties) {
    this.appProperties = appProperties;
  }

  @Override
  public void addViewControllers(ViewControllerRegistry registry) {
    registry.addRedirectViewController("/app", "/app/");
    registry.addViewController("/app/").setViewName("forward:/app/index.html");
    if (!appProperties.isMaster()) {
      registry.addRedirectViewController("/", "/app/");
    }
  }
}
