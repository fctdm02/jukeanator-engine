package com.djt.jukeanator_engine.domain.license.model;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * The JukeANator End User License Agreement shown by the jukebox UI. The text lives in the
 * classpath resource {@value #RESOURCE}; the website's {@code license_agreement.html} carries the
 * same text.
 *
 * <p>Changing the agreement's substance means bumping {@link #VERSION}: an operator whose accepted
 * version differs is asked to accept again at the next start.
 */
public final class LicenseAgreement {

  /** Current agreement version, stored in {@code license.agreement-accepted-version}. */
  public static final String VERSION = "1.0";

  /** Classpath location of the agreement (simple HTML that Swing can render). */
  public static final String RESOURCE = "/legal/license-agreement.html";

  /** Anchor name of the Commercial Use section within the agreement. */
  public static final String COMMERCIAL_ANCHOR = "commercial";

  private LicenseAgreement() {
  }

  /** Loads the agreement HTML from the classpath. */
  public static String loadHtml() {
    try (InputStream in = LicenseAgreement.class.getResourceAsStream(RESOURCE)) {
      if (in == null) {
        throw new IllegalStateException("License agreement resource not found: " + RESOURCE);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("Could not read the license agreement", e);
    }
  }
}
