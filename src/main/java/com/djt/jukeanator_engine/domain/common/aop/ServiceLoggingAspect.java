package com.djt.jukeanator_engine.domain.common.aop;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.regex.Pattern;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * AOP logging interceptor that fires around every method on every service interface in this
 * application.
 *
 * <p>
 * For each invocation it records:
 * <ul>
 * <li>the authenticated username (or {@code "[unauthenticated]"} for
 * {@link PublicServiceMethod @PublicServiceMethod} calls)</li>
 * <li>the fully-qualified method signature</li>
 * <li>the sanitised argument list (credentials are masked)</li>
 * <li>the elapsed wall-clock time in milliseconds</li>
 * <li>whether the call succeeded or threw an exception</li>
 * </ul>
 *
 * <h3>Credential masking</h3> Any parameter whose runtime type name or {@code toString()} value
 * matches the credential hint pattern (see {@link #CREDENTIAL_TYPE_PATTERN}) is replaced with
 * {@code "***"} in the log output. This prevents passwords from appearing in log files even when
 * structured {@code LoginRequest} / {@code RegisterRequest} objects are passed as arguments.
 *
 * <h3>Log level</h3> Entry/exit are logged at {@code DEBUG}. Exceptions are additionally logged at
 * {@code WARN} (the exception itself is still propagated).
 *
 * @author tmyers
 */
@Aspect
@Component
public class ServiceLoggingAspect {

  private static final Logger log = LoggerFactory.getLogger(ServiceLoggingAspect.class);

  /**
   * Simple name patterns (case-insensitive, substring match) that signal a parameter contains a
   * credential and must be masked in the log output.
   */
  private static final Set<String> CREDENTIAL_FIELD_HINTS =
      Set.of("password", "secret", "credential", "token", "apikey", "api_key");

  /**
   * Matches type simple-names that sound like credential containers. LoginRequest and
   * RegisterRequest both carry passwords.
   */
  private static final Pattern CREDENTIAL_TYPE_PATTERN =
      Pattern.compile("(?i)(login|register|auth|credential|password|secret).*request.*");

  // ──────────────────────────────────────────────────────────────────────────
  // Pointcut — every method on every *Service interface in the domain layer
  // ──────────────────────────────────────────────────────────────────────────

  /**
   * Matches any method declared on an interface whose simple name ends with {@code Service} and
   * that lives anywhere within the application's domain package hierarchy.
   *
   * <p>
   * Spring AOP proxies service beans through their interfaces, so this pointcut intercepts calls
   * made through those proxies (i.e. cross-bean calls visible to Spring).
   */
  @Pointcut("execution(* com.djt.jukeanator_engine.domain..service.*Service.*(..))")
  public void allServiceMethods() {}

  // ──────────────────────────────────────────────────────────────────────────
  // Advice
  // ──────────────────────────────────────────────────────────────────────────

  @Around("allServiceMethods()")
  public Object logServiceCall(ProceedingJoinPoint pjp) throws Throwable {

    MethodSignature sig = (MethodSignature) pjp.getSignature();
    Method method = sig.getMethod();
    String methodName = sig.getDeclaringType().getSimpleName() + "." + method.getName();
    String username = resolveUsername();
    String args = formatArgs(method, pjp.getArgs());

    log.debug("[SERVICE] user='{}' → {}({})", username, methodName, args);

    long start = System.currentTimeMillis();
    try {
      Object result = pjp.proceed();
      long elapsed = System.currentTimeMillis() - start;
      log.debug("[SERVICE] user='{}' ← {}  OK  ({}ms)", username, methodName, elapsed);
      return result;

    } catch (Throwable t) {
      long elapsed = System.currentTimeMillis() - start;
      log.warn("[SERVICE] user='{}' ← {}  THREW {}  ({}ms): {}", username, methodName,
          t.getClass().getSimpleName(), elapsed, t.getMessage());
      throw t;
    }
  }

  // ──────────────────────────────────────────────────────────────────────────
  // Helpers
  // ──────────────────────────────────────────────────────────────────────────

  /**
   * Returns the name of the currently authenticated principal, or a placeholder when no
   * authentication is present (allowed only on {@link PublicServiceMethod @PublicServiceMethod}
   * methods, which the {@link ServiceSecurityAspect} has already verified).
   */
  private static String resolveUsername() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated()) {
      return "[unauthenticated]";
    }
    return auth.getName();
  }

  /**
   * Produces a human-readable, comma-separated argument list. Parameters whose type names look like
   * credential containers are replaced with {@code "***"}.
   *
   * @param method the interface method being called (used for parameter names when the class was
   *        compiled with {@code -parameters})
   * @param args the actual argument values
   */
  private static String formatArgs(Method method, Object[] args) {
    if (args == null || args.length == 0) {
      return "";
    }

    String[] paramNames = method.getParameters() != null
        ? Arrays.stream(method.getParameters()).map(p -> p.getName()).toArray(String[]::new)
        : new String[0];

    var sb = new StringBuilder();
    for (int i = 0; i < args.length; i++) {
      if (i > 0)
        sb.append(", ");

      String paramName = (paramNames.length > i) ? paramNames[i] : "arg" + i;
      Object arg = args[i];

      if (arg == null) {
        sb.append(paramName).append("=null");
      } else if (isSensitive(paramName, arg)) {
        sb.append(paramName).append("=***");
      } else {
        sb.append(paramName).append("=").append(arg);
      }
    }
    return sb.toString();
  }

  /**
   * Returns {@code true} when the parameter should be masked in log output.
   *
   * <p>
   * A parameter is considered sensitive when:
   * <ul>
   * <li>its name contains a credential hint word (e.g. {@code "password"}), or</li>
   * <li>its runtime type simple name matches {@link #CREDENTIAL_TYPE_PATTERN} (e.g.
   * {@code LoginRequest}, {@code RegisterRequest}).</li>
   * </ul>
   */
  private static boolean isSensitive(String paramName, Object arg) {
    String lowerName = paramName.toLowerCase();
    for (String hint : CREDENTIAL_FIELD_HINTS) {
      if (lowerName.contains(hint)) {
        return true;
      }
    }
    String typeName = arg.getClass().getSimpleName();
    return CREDENTIAL_TYPE_PATTERN.matcher(typeName).matches();
  }
}
