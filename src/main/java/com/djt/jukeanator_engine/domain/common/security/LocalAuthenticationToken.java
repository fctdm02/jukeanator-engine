package com.djt.jukeanator_engine.domain.common.security;

import java.io.Serial;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * A fully-authenticated {@link Authentication} token for the built-in Swing-UI {@code "LOCAL"}
 * user.
 *
 * <p>
 * This token is installed once at application startup (by {@link LocalSecurityContextConfigurer})
 * into a <em>global</em> {@code SecurityContextHolder}, making it visible to every thread in the
 * JVM — including the {@code song-queue-thread} executor inside {@code SongPlayerServiceImpl} —
 * without requiring any per-thread work.
 *
 * <p>
 * {@link #getPrincipal()} returns {@link LocalPrincipal#INSTANCE}, which is the object resolved by
 * Spring's {@code @AuthenticationPrincipal} annotation.
 *
 * @author tmyers
 */
public final class LocalAuthenticationToken implements Authentication {

  @Serial
  private static final long serialVersionUID = 1L;

  private static final List<GrantedAuthority> AUTHORITIES =
      List.of(new SimpleGrantedAuthority(LocalPrincipal.LOCAL_ROLE));

  /** Singleton — there is exactly one LOCAL authentication for the entire JVM. */
  public static final LocalAuthenticationToken INSTANCE = new LocalAuthenticationToken();

  private LocalAuthenticationToken() {}

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return AUTHORITIES;
  }

  @Override
  public Object getCredentials() {
    // No password for the local operator — physical presence IS the credential.
    return null;
  }

  @Override
  public Object getDetails() {
    return null;
  }

  /**
   * Returns the {@link LocalPrincipal} singleton. This is the object that
   * {@code @AuthenticationPrincipal} will inject into method parameters.
   */
  @Override
  public LocalPrincipal getPrincipal() {
    return LocalPrincipal.INSTANCE;
  }

  /** Always {@code true} — the local operator is trusted by definition. */
  @Override
  public boolean isAuthenticated() {
    return true;
  }

  @Override
  public void setAuthenticated(boolean isAuthenticated) {
    // Immutable — ignore.
  }

  @Override
  public String getName() {
    return LocalPrincipal.LOCAL_USERNAME;
  }
}
