package com.djt.jukeanator_engine.domain.common.aop;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a service-interface method as publicly accessible — i.e., the {@link ServiceSecurityAspect}
 * will NOT require a populated {@code SecurityContextHolder} before allowing the call through.
 *
 * <p>
 * Apply this to any method that must be callable before a user has authenticated, for example:
 *
 * <pre>
 * {
 *   &#64;code
 *   public interface UserService {
 *
 *     &#64;PublicServiceMethod
 *     AuthResponse login(LoginRequest request);
 *
 *     @PublicServiceMethod
 *     AuthResponse register(RegisterRequest request);
 *   }
 * }
 * </pre>
 *
 * @author tmyers
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PublicServiceMethod {
}
