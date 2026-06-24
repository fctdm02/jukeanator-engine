# Service Interceptors — Integration Guide

## Files Delivered

### New files (create in your source tree)

| File | Package | Purpose |
|---|---|---|
| `PublicServiceMethod.java` | `…domain.common.aop` | Annotation — bypasses security check on a method |
| `ServiceLoggingAspect.java` | `…domain.common.aop` | `@Around` aspect: logs user, method, args, timing |
| `ServiceSecurityAspect.java` | `…domain.common.aop` | `@Before` aspect: rejects calls with no auth |
| `LocalPrincipal.java` | `…domain.common.security` | Record — the principal for the LOCAL/Swing user |
| `LocalAuthenticationToken.java` | `…domain.common.security` | Authentication impl for LOCAL |
| `SystemPrincipal.java` + inner `SystemAuthenticationToken` | `…domain.common.security` | Auth for background/daemon threads |
| `SecurityContextPropagatingRunnable.java` | `…domain.common.security` | Carries a SecurityContext into an executor thread |
| `LocalSecurityContextConfigurer.java` | `…domain.common.security` | ApplicationRunner — MODE_GLOBAL + LOCAL auth install |

### Files you must edit

| File | Change |
|---|---|
| `UserService` (interface) | Add `@PublicServiceMethod` to `login()` and `register()` |
| `SongLibraryService` (interface) | Add `@PublicServiceMethod` to `authenticateForAdminPanel()` if present |
| `SongPlayerServiceImpl.java` | Replace `submitQueueProcessing()` — see `SongPlayerServiceImpl_DIFF.java` |

---

## How it works end-to-end

### LOCAL / Swing-UI mode (`user-interface.enabled=true`)

```
Application startup
  └── LocalSecurityContextConfigurer.run()
        ├── SecurityContextHolder.setStrategyName(MODE_GLOBAL)
        └── installs LocalAuthenticationToken in the global context

Any thread (EDT, song-queue-thread, Spring event thread…)
  └── calls service method
        ├── ServiceSecurityAspect (@Order 1)  — sees LOCAL auth in global context → OK
        └── ServiceLoggingAspect  (@Order 2)  — logs "user='LOCAL'"
```

`MODE_GLOBAL` makes the SecurityContext a JVM singleton, so the
`song-queue-thread` executor automatically sees the LOCAL principal without
any wrapper.

---

### REST / remote mode (`user-interface.enabled=false`)

```
HTTP request thread (JWT populated by Spring Security filter)
  └── calls service method
        ├── ServiceSecurityAspect — sees JWT auth in ThreadLocal context → OK
        └── ServiceLoggingAspect  — logs the email address from the JWT

song-queue-thread (executor, no HTTP context)
  └── SecurityContextPropagatingRunnable.run()
        ├── installs SystemAuthenticationToken in ThreadLocal context
        ├── calls processQueue()
        │     └── songQueueService.dequeueNextSong()
        │           ├── ServiceSecurityAspect — sees SYSTEM auth → OK
        │           └── ServiceLoggingAspect  — logs "user='SYSTEM'"
        └── clears ThreadLocal context (finally block)
```

---

## Maven / Gradle dependency

The aspects require `spring-boot-starter-aop` on the classpath.
Spring Boot auto-configures `@EnableAspectJAutoProxy` when this starter is present.

```xml
<!-- pom.xml -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-aop</artifactId>
</dependency>
```

---

## Logging configuration

The aspects log at `DEBUG` level under their own class names.
Add these lines to `application.yml` to activate them:

```yaml
logging:
  level:
    com.djt.jukeanator_engine.domain.common.aop: DEBUG
    com.djt.jukeanator_engine.domain.common.security: DEBUG
```

Or keep them at `INFO` to suppress per-call chatter in production and only
see exception (`WARN`) entries from `ServiceLoggingAspect`.

---

## AOP pointcut scope

```
execution(* com.djt.jukeanator_engine.domain..service.*Service.*(..))
```

This matches any method on any interface whose name ends with `Service` anywhere
under the `domain` package tree — i.e., `SongLibraryService`, `SongQueueService`,
`SongPlayerService`, and `UserService` all match.

Both aspects share the identical pointcut, so adding a new `*Service` interface
automatically gets logging and security protection with no further wiring.

---

## @AuthenticationPrincipal compatibility

The `LocalPrincipal` record is returned by `LocalAuthenticationToken.getPrincipal()`.
Spring's `@AuthenticationPrincipal` annotation resolves exactly that — the principal
of the current `Authentication`.  So any REST controller (or service helper) can do:

```java
@GetMapping("/profile")
public ResponseEntity<UserProfileDto> profile(
        @AuthenticationPrincipal LocalPrincipal principal) {
    return ResponseEntity.ok(userService.getProfile(principal.username()));
}
```

For JWT-authenticated remote users, your existing `JwtUtil` should return a
`UserDetails` or custom principal from its `Authentication`, and
`@AuthenticationPrincipal` will resolve that instead — no change required.

---

## Security note: password masking

`ServiceLoggingAspect` automatically masks parameters whose:
- **name** contains: `password`, `secret`, `credential`, `token`, `apikey`, `api_key`
- **type simple name** matches: `.*(?i)(login|register|auth|credential|password|secret).*request.*`

So `LoginRequest` and `RegisterRequest` arguments are logged as `***` rather
than their contents.  If you add new credential-carrying DTOs in future, either
follow the naming convention above or extend `CREDENTIAL_TYPE_PATTERN`.
