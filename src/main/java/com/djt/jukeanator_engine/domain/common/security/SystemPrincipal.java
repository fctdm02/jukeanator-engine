package com.djt.jukeanator_engine.domain.common.security;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import com.djt.jukeanator_engine.domain.common.aop.ServiceSecurityAspect;

/**
 * Principal for JVM-internal background threads (e.g. {@code song-queue-thread}) that drive
 * operations not attributable to any specific human user.
 *
 * <p>
 * When the application runs in REST/remote mode (i.e. NOT local/Swing-UI mode), the
 * {@code SecurityContextHolder} uses the default {@code ThreadLocal} strategy, so background
 * threads start with no security context. Wrapping their {@link Runnable}s in a
 * {@link SecurityContextPropagatingRunnable} seeded with a {@link SystemAuthenticationToken}
 * ensures the {@link ServiceSecurityAspect} never rejects an internal system call.
 *
 * @author tmyers
 */
public final class SystemPrincipal implements Serializable {

  @Serial
  private static final long serialVersionUID = 1L;

  public static final String SYSTEM_USERNAME = "SYSTEM";
  public static final String SYSTEM_ROLE = "ROLE_SYSTEM";

  public static final SystemPrincipal INSTANCE = new SystemPrincipal();

  private SystemPrincipal() {}

  @Override
  public String toString() {
    return SYSTEM_USERNAME;
  }

  // ──────────────────────────────────────────────────────────────────────────
  // Nested Authentication token
  // ──────────────────────────────────────────────────────────────────────────

  /**
   * Fully-authenticated token that represents an internal system operation.
   *
   * <p>
   * Use this token as the argument to
   * {@link SecurityContextPropagatingRunnable#SecurityContextPropagatingRunnable(Runnable, Authentication)}
   * when submitting tasks to executor services that are not driven by a human user request.
   */
  public static final class SystemAuthenticationToken implements Authentication {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final List<GrantedAuthority> AUTHORITIES =
        List.of(new SimpleGrantedAuthority(SYSTEM_ROLE));

    /** Singleton — every background thread can share this immutable token. */
    public static final SystemAuthenticationToken INSTANCE = new SystemAuthenticationToken();

    private SystemAuthenticationToken() {}

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
      return AUTHORITIES;
    }

    @Override
    public Object getCredentials() {
      return null;
    }

    @Override
    public Object getDetails() {
      return null;
    }

    @Override
    public SystemPrincipal getPrincipal() {
      return SystemPrincipal.INSTANCE;
    }

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
      return SYSTEM_USERNAME;
    }
  }
}
