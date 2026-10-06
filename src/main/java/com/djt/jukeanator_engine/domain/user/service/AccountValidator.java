package com.djt.jukeanator_engine.domain.user.service;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The server's own rules for account details -- the Web/Mobile UI mirrors them for quick feedback,
 * but a client is never trusted to have checked. Every violation is an
 * {@link IllegalArgumentException} (400) whose message is written for the patron.
 *
 * <p>The same rules are mirrored in {@code static/js/app.js} ({@code passwordProblem}); keep both
 * sides in sync.
 */
public final class AccountValidator {

  static final int MIN_PASSWORD_LENGTH = 8;
  static final int MAX_PASSWORD_BYTES = 72;
  static final int MAX_EMAIL_LENGTH = 254;
  static final int MAX_NAME_LENGTH = 50;

  private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
  private static final Pattern LETTER = Pattern.compile("\\p{L}");
  private static final Pattern DIGIT = Pattern.compile("\\d");

  private AccountValidator() {}

  /** Email addresses are compared without regard to case or surrounding spaces. */
  public static String normalizeEmail(String emailAddress) {
    return emailAddress == null ? null : emailAddress.strip().toLowerCase(Locale.ROOT);
  }

  /** @return the normalized email address */
  public static String requireValidEmail(String emailAddress) {

    String normalized = normalizeEmail(emailAddress);
    if (normalized == null || normalized.isEmpty()) {
      throw new IllegalArgumentException("Please enter your email address.");
    }
    if (normalized.length() > MAX_EMAIL_LENGTH || !EMAIL.matcher(normalized).matches()) {
      throw new IllegalArgumentException("Please enter a valid email address.");
    }
    return normalized;
  }

  public static void requireValidPassword(String password) {

    if (password == null || password.length() < MIN_PASSWORD_LENGTH
        || !LETTER.matcher(password).find() || !DIGIT.matcher(password).find()) {
      throw new IllegalArgumentException("Your password must be at least " + MIN_PASSWORD_LENGTH
          + " characters long and include at least one letter and one number.");
    }
    // BCrypt (AppConfig's PasswordEncoder) refuses anything longer than 72 bytes.
    if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
      throw new IllegalArgumentException(
          "Your password must be at most " + MAX_PASSWORD_BYTES + " characters long.");
    }
  }

  /**
   * @param fieldName how the patron knows the field, e.g. "first name"
   * @return the name without surrounding spaces
   */
  public static String requireValidName(String name, String fieldName) {

    String stripped = name == null ? "" : name.strip();
    if (stripped.isEmpty()) {
      throw new IllegalArgumentException("Please enter your " + fieldName + ".");
    }
    if (stripped.length() > MAX_NAME_LENGTH) {
      throw new IllegalArgumentException(
          "Your " + fieldName + " must be at most " + MAX_NAME_LENGTH + " characters long.");
    }
    return stripped;
  }
}
