package com.djt.jukeanator_engine.domain.common.security;

import java.io.Serial;
import java.io.Serializable;

/**
 * The principal object placed inside a {@link LocalAuthenticationToken} for the built-in
 * {@code "LOCAL"} Swing-UI user.
 *
 * <p>
 * Spring's {@code @AuthenticationPrincipal} annotation resolves the return value of
 * {@link org.springframework.security.core.Authentication#getPrincipal()}, so any controller or
 * service that declares {@code @AuthenticationPrincipal LocalPrincipal principal} will receive this
 * record directly.
 *
 * @param username Always {@value #LOCAL_USERNAME} for Swing-UI sessions.
 * @param role Always {@value #LOCAL_ROLE} for the desktop operator.
 *
 * @author tmyers
 */
public record LocalPrincipal(String username, String role) implements Serializable {

  @Serial
  private static final long serialVersionUID = 1L;

  /** The fixed username used for all local / Swing-UI interactions. */
  public static final String LOCAL_USERNAME = "LOCAL";

  /** The role granted to the local operator. */
  public static final String LOCAL_ROLE = "ROLE_LOCAL";

  /** Convenience singleton — only one LOCAL principal ever exists. */
  public static final LocalPrincipal INSTANCE = new LocalPrincipal(LOCAL_USERNAME, LOCAL_ROLE);
}
