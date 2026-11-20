package com.djt.jukeanator_engine.web.getapp;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where {@code /get-app} (the address printed in the jukebox QR codes) sends a phone, bound to the
 * {@code mobile-app:} YAML prefix. A blank store URL means that app is not published yet, so those
 * phones go to the Web/Mobile UI at {@link #getWebAppUrl()} instead.
 */
@ConfigurationProperties(prefix = "mobile-app")
public class MobileAppProperties {

  // Apple App Store listing, e.g. https://apps.apple.com/us/app/jukeanator/id1234567890
  private String iosStoreUrl;

  // Google Play listing, e.g. https://play.google.com/store/apps/details?id=com.djt.jukeanator
  private String androidStoreUrl;

  // Everyone else (desktop browsers, other phones, and any phone whose app is not published yet).
  private String webAppUrl = "/app/";

  public String getIosStoreUrl() {
    return iosStoreUrl;
  }

  public void setIosStoreUrl(String iosStoreUrl) {
    this.iosStoreUrl = iosStoreUrl;
  }

  public String getAndroidStoreUrl() {
    return androidStoreUrl;
  }

  public void setAndroidStoreUrl(String androidStoreUrl) {
    this.androidStoreUrl = androidStoreUrl;
  }

  public String getWebAppUrl() {
    return webAppUrl;
  }

  public void setWebAppUrl(String webAppUrl) {
    this.webAppUrl = webAppUrl;
  }
}
