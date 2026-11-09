package com.djt.jukeanator_engine.domain.license.legacy;

import java.util.Optional;

/**
 * License tiers of the legacy MP3Jukeboxx licensing scheme. The numeric wire code, the
 * single-letter database code and the delimiter used to build the {@code JukeANator.CTL} hash are
 * frozen: legacy clients in the field and existing license files depend on them.
 *
 * <p>Lower wire codes mean more functionality, which is why the legacy client gates features with
 * numeric comparisons ({@code lt <= C} for commercial, {@code lt <= CD} for subscription).
 *
 * <p>Note: the legacy code and database call {@link #CR} and {@link #CD} "ASCAP mode". The
 * business no longer deals with performance licensing, so this codebase calls them
 * <em>subscription</em> tiers.
 */
public enum LicenseTier {

  /** Subscription mode, remote ("downloading") data source variant. Database letter R. */
  CR(-1, "R", "&@", "Commercial-Subscription"),

  /** Subscription mode, standalone variant. Database letter A. */
  CD(0, "A", "__", "Commercial-Subscription"),

  /** Commercial (perpetual). Database letter C. */
  C(1, "C", "##", "Commercial"),

  /** Professional, i.e. non-commercial. Database letter P. */
  NC(5, "P", "@#", "Professional"),

  /** Lite. Database letter L. */
  L(6, "L", "&&", "Lite"),

  /** Trial / demo. No license file matched. Has no database letter or delimiter. */
  D(10, null, null, "Trial");

  /** Order in which the legacy client tests a license file against each tier. */
  public static final LicenseTier[] MATCH_ORDER = {CR, CD, C, NC, L};

  private final int wireCode;
  private final String letter;
  private final String delimiter;
  private final String displayName;

  LicenseTier(int wireCode, String letter, String delimiter, String displayName) {
    this.wireCode = wireCode;
    this.letter = letter;
    this.delimiter = delimiter;
    this.displayName = displayName;
  }

  /** Integer sent as the {@code licenseType} request parameter, e.g. {@code -1}, {@code 0}. */
  public int getWireCode() {
    return wireCode;
  }

  /** Single-letter code stored in the server's customer table (R/A/C/P/L), or null for D. */
  public String getLetter() {
    return letter;
  }

  /** Delimiter wrapped around the machine id before hashing, or null for D. */
  public String getDelimiter() {
    return delimiter;
  }

  public String getDisplayName() {
    return displayName;
  }

  /** True for the subscription tiers that require a valid {@code ExtendCode.CTL}. */
  public boolean isSubscription() {
    return wireCode <= CD.wireCode;
  }

  /** True for tiers that may accept money (bill acceptor, card reader). */
  public boolean isCommercial() {
    return wireCode <= C.wireCode;
  }

  /**
   * Maps a database letter to a tier. Unknown letters map to {@link #L}, matching the legacy
   * server's {@code else} branch when it builds a license key.
   */
  public static LicenseTier fromLetterForKeyGeneration(String letter) {
    return fromLetter(letter).orElse(L);
  }

  public static Optional<LicenseTier> fromLetter(String letter) {
    if (letter == null) {
      return Optional.empty();
    }
    for (LicenseTier tier : values()) {
      if (letter.equals(tier.letter)) {
        return Optional.of(tier);
      }
    }
    return Optional.empty();
  }

  /**
   * Normalizes the {@code licenseType} request parameter the same way the legacy server did:
   * numeric codes become letters, anything else is passed through unchanged.
   */
  public static String normalizeWireLicenseType(String raw) {
    if (raw == null) {
      return null;
    }
    return switch (raw) {
      case "1" -> "C";
      case "5" -> "P";
      case "6" -> "L";
      case "0" -> "A";
      case "-1" -> "R";
      default -> raw;
    };
  }
}
