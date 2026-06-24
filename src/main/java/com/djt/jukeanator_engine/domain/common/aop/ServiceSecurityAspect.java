package com.djt.jukeanator_engine.domain.common.aop;

import java.lang.reflect.Method;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * AOP security interceptor that fires <em>before</em> every service method and verifies that the
 * calling thread has a valid, authenticated principal in the {@code SecurityContextHolder}.
 *
 * <h3>Bypass</h3> Methods annotated with {@link PublicServiceMethod @PublicServiceMethod} on the
 * service <em>interface</em> are exempt from this check. Use that annotation for methods that must
 * be callable before authentication (e.g. {@code UserService.login()},
 * {@code UserService.register()}).
 *
 * <h3>Order</h3> This aspect is ordered <strong>before</strong> {@link ServiceLoggingAspect}
 * ({@code @Order(1)} vs {@code @Order(2)}) so that a security rejection is never logged as a normal
 * method call.
 *
 * <h3>Thread safety</h3>
 * <ul>
 * <li>In <em>local / Swing-UI mode</em>, {@code SecurityContextHolder.MODE_GLOBAL} is active (set
 * by {@code LocalSecurityContextConfigurer}), so every thread automatically sees the
 * {@code LocalAuthenticationToken}. No per-thread work is needed.</li>
 * <li>In <em>REST / remote mode</em>, the default {@code ThreadLocal} strategy is in effect. HTTP
 * request threads are populated by the JWT filter chain. Background threads (e.g.
 * {@code song-queue-thread}) must wrap their {@code Runnable}s with a
 * {@code SecurityContextPropagatingRunnable} seeded with
 * {@code SystemPrincipal.SystemAuthenticationToken.INSTANCE}.</li>
 * </ul>
 *
 * @author tmyers
 */
@Aspect
@Component
@Order(1) // run before ServiceLoggingAspect (@Order(2))
public class ServiceSecurityAspect {

  private static final Logger log = LoggerFactory.getLogger(ServiceSecurityAspect.class);

  // ──────────────────────────────────────────────────────────────────────────
  // Pointcut — same surface area as ServiceLoggingAspect
  // ──────────────────────────────────────────────────────────────────────────

  @Pointcut("execution(* com.djt.jukeanator_engine.domain..service.*Service.*(..))")
  public void allServiceMethods() {}

  // ──────────────────────────────────────────────────────────────────────────
  // Advice
  // ──────────────────────────────────────────────────────────────────────────

  /**
   * Checks that the current thread has a valid {@link Authentication} before allowing the service
   * method to execute.
   *
   * @throws InsufficientAuthenticationException if no authenticated principal is present and the
   *         method is not annotated with {@link PublicServiceMethod @PublicServiceMethod}
   */
  @Before("allServiceMethods()")
  public void enforceAuthentication(JoinPoint jp) {

    MethodSignature sig = (MethodSignature) jp.getSignature();
    Method method = sig.getMethod();

    /*
     * Public methods (login, register, etc.) bypass the security check entirely.
     */
    if (method.isAnnotationPresent(PublicServiceMethod.class)) {
      log.debug("[SECURITY] @PublicServiceMethod — skipping auth check for {}.{}",
          sig.getDeclaringType().getSimpleName(), method.getName());
      return;
    }

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();

    if (auth == null || !auth.isAuthenticated()) {
      String target = sig.getDeclaringType().getSimpleName() + "." + method.getName();
      log.warn("[SECURITY] Unauthenticated call rejected: {}", target);
      throw new InsufficientAuthenticationException(
          "No authenticated principal found in SecurityContextHolder for service call: " + target
              + ". In REST mode ensure the JWT filter has run; in Swing-UI mode ensure "
              + "LocalSecurityContextConfigurer has executed.");
    }

    log.debug("[SECURITY] Authenticated as '{}' for {}.{}", auth.getName(),
        sig.getDeclaringType().getSimpleName(), method.getName());
  }
}
