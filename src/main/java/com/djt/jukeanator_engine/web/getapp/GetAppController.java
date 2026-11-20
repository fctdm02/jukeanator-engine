package com.djt.jukeanator_engine.web.getapp;

import java.net.URI;
import java.util.Locale;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /get-app}: the one permanent address encoded in the jukebox QR codes. It sends an iPhone
 * or iPad to the App Store, an Android phone to Google Play, and everyone else to the Web/Mobile
 * UI, using the links in {@link MobileAppProperties}. Until an app is published (its store URL
 * left blank), those phones go to the Web/Mobile UI too, so printed QR codes never need to change.
 *
 * <p>The redirect is temporary (302) and never cached, so changing a store URL takes effect on the
 * next scan.
 */
@RestController
public class GetAppController {

  private final MobileAppProperties properties;

  public GetAppController(MobileAppProperties properties) {
    this.properties = properties;
  }

  @GetMapping("/get-app")
  public ResponseEntity<Void> getApp(
      @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
    return ResponseEntity.status(HttpStatus.FOUND)
        .location(URI.create(destinationFor(userAgent)))
        .cacheControl(CacheControl.noStore())
        .header(HttpHeaders.VARY, HttpHeaders.USER_AGENT)
        .build();
  }

  /** The store or page a browser with this User-Agent should go to. */
  String destinationFor(String userAgent) {
    String agent = userAgent == null ? "" : userAgent.toLowerCase(Locale.ROOT);
    // iPadOS 13+ Safari reports itself as a Mac by default, so such iPads get the web app.
    if ((agent.contains("iphone") || agent.contains("ipad") || agent.contains("ipod"))
        && StringUtils.hasText(properties.getIosStoreUrl())) {
      return properties.getIosStoreUrl().trim();
    }
    if (agent.contains("android") && StringUtils.hasText(properties.getAndroidStoreUrl())) {
      return properties.getAndroidStoreUrl().trim();
    }
    return properties.getWebAppUrl();
  }
}
