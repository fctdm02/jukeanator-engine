package com.djt.jukeanator_engine.domain.common.security;

import java.util.Date;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtUtil {

  private static final Logger LOGGER = LoggerFactory.getLogger(JwtUtil.class);

  private final SecretKey secretKey;
  private final long expirationMs;

  public JwtUtil(@Value("${app.jwt.secret:}") String secret,
      @Value("${app.jwt.expiration-ms:86400000}") long expirationMs) {

    if (secret == null || secret.isBlank()) {
      // No JWT_SECRET configured: sign with a random per-run key. Logins still work, but every
      // token becomes invalid when the application restarts, and no other instance (or
      // GeneratePostmanJwtToken) can produce tokens this one accepts. Fine for a standalone kiosk;
      // a master must set JWT_SECRET.
      LOGGER.warn("app.jwt.secret is not set (JWT_SECRET environment variable). Using a random "
          + "signing key for this run: web logins will be invalidated on restart.");
      this.secretKey = Jwts.SIG.HS256.key().build();
    } else {
      // Secret must be ≥ 256 bits for HS256
      this.secretKey = Keys.hmacShaKeyFor(secret.getBytes());
    }
    this.expirationMs = expirationMs;
  }

  public String generateToken(String emailAddress, String role) {
    return Jwts.builder().subject(emailAddress).claim("role", role).issuedAt(new Date())
        .expiration(new Date(System.currentTimeMillis() + expirationMs)).signWith(secretKey)
        .compact();
  }

  public String extractEmail(String token) {
    return parseClaims(token).getSubject();
  }

  public String extractRole(String token) {
    return (String) parseClaims(token).get("role");
  }

  public boolean isTokenValid(String token) {
    try {
      parseClaims(token);
      return true;
    } catch (JwtException | IllegalArgumentException e) {
      return false;
    }
  }

  private Claims parseClaims(String token) {
    return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
  }
}
