package com.djt.jukeanator_engine.domain.common.security;

import java.util.Date;
import javax.crypto.SecretKey;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Standalone utility that generates a signed JWT token suitable for pasting into the Postman
 * {{jwtToken}} collection variable.
 *
 * <p>
 * The token is built with the exact same logic as {@link JwtUtil#generateToken}, so any token
 * produced here will be accepted by the running application without modification.
 *
 * <h3>Usage</h3>
 * <ol>
 * <li>Set SECRET below to the value of {@code app.jwt.secret} in your application.yml / .env</li>
 * <li>Set EMAIL and ROLE to the test identity you want to impersonate</li>
 * <li>Run the class as a plain Java main (no Spring context needed)</li>
 * <li>Copy the printed token into Postman → jukeanator-engine collection → Variables →
 * {{jwtToken}}</li>
 * </ol>
 *
 * <h3>Available roles</h3>
 * <ul>
 * <li>{@code ROLE_ADMIN} — admin-only endpoints (scan, flush queue, player controls, etc.)</li>
 * <li>{@code ROLE_USER} — authenticated patron endpoints (add songs to queue, view profile)</li>
 * </ul>
 *
 * <h3>Dependencies</h3> This file uses only the jjwt libraries already on your classpath:
 * 
 * <pre>
 *   io.jsonwebtoken:jjwt-api
 *   io.jsonwebtoken:jjwt-impl
 *   io.jsonwebtoken:jjwt-jackson
 * </pre>
 *
 * @author tmyers
 */
public class GeneratePostmanJwtToken {

  // ── Configuration — edit these three values before running ───────────────

  /** Must match app.jwt.secret in application.yml exactly (≥ 32 ASCII chars for HS256). */
  private static final String SECRET = "fT7mK9xQ2pNc8VdL4sHz1YwB6rAe3JkM0uCp5GqX8nRv2ZtF7hLs9Wd3YbKm6QaP";

  /** Email address embedded as the JWT subject claim. */
  private static final String EMAIL = "admin@jukeanator.local";

  /**
   * Role claim. Use "ROLE_ADMIN" for full access or "ROLE_USER" for patron access. Spring
   * Security's hasRole("ADMIN") matcher automatically prepends "ROLE_", so the stored value in the
   * token must include the prefix.
   */
  private static final String ROLE = "ROLE_ADMIN";

  /**
   * Token lifetime in milliseconds. Default matches app.jwt.expiration-ms (24 hours). Increase this
   * for long-running Postman sessions, e.g. 7 * 24 * 60 * 60 * 1000L for 7 days.
   */
  private static final long EXPIRATION_MS = 86_400_000L; // 24 hours

  // ─────────────────────────────────────────────────────────────────────────

  public static void main(String[] args) {

    if (SECRET.equals("PASTE_YOUR_APP_JWT_SECRET_HERE")) {
      System.err
          .println("ERROR: Set the SECRET constant to your app.jwt.secret value before running.");
      System.exit(1);
    }

    SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes());

    Date now = new Date();
    Date expiry = new Date(now.getTime() + EXPIRATION_MS);

    String token = Jwts.builder().subject(EMAIL).claim("role", ROLE).issuedAt(now)
        .expiration(expiry).signWith(key).compact();

    System.out.println("=".repeat(72));
    System.out.println("  Jukeanator Postman JWT Token Generator");
    System.out.println("=".repeat(72));
    System.out.println("  Email   : " + EMAIL);
    System.out.println("  Role    : " + ROLE);
    System.out.println("  Issued  : " + now);
    System.out.println("  Expires : " + expiry);
    System.out.println("=".repeat(72));
    System.out.println();
    System.out.println(token);
    System.out.println();
    System.out.println("Paste the token above into Postman:");
    System.out
        .println("  Collection 'jukeanator-engine' → Variables tab → {{jwtToken}} → Current Value");
    System.out.println("=".repeat(72));
  }
}
