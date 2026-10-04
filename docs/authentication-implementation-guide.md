# Authentication Implementation Guide
## JukeANator Engine — Spring Boot 4.0.4 / Java 21

---

## Overview and Strategy

The recommended approach is **JWT (JSON Web Token) stateless authentication** via Spring Security. This fits the existing architecture well:

- The `SongLibraryController` becomes a secured REST resource
- The `SongLibraryServiceHttpClient` sends a `Bearer` token header on every request
- A new `UserEntity` (JPA) stores user accounts with hashed passwords
- A new `UserController` / `UserService` handles register, login, and profile endpoints
- The existing `authenticateForAdminPanel` method can be refactored or left in place for the admin panel specifically

---

## Step 1 — Add Dependencies to `pom.xml`

Add these inside `<dependencies>`:

```xml
<!-- Spring Security + JWT -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.6</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>

<!-- For test -->
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-test</artifactId>
    <scope>test</scope>
</dependency>
```

---

## Step 2 — `UserEntity` (JPA)

**File:** `com/djt/jukeanator_engine/domain/user/model/UserEntity.java`

```java
package com.djt.jukeanator_engine.domain.user.model;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Column(nullable = false, unique = true)
    private String emailAddress;

    @Column(nullable = false)
    private String passwordHash;           // BCrypt hash; never store plaintext

    @Column(nullable = false)
    private Integer numCredits = 0;

    // Ordered list of album+song IDs played, stored as "albumId:songId" strings.
    // For production consider a separate @Entity; a simple ElementCollection is
    // fine for a first pass and keeps things in one table.
    @ElementCollection
    @CollectionTable(name = "user_song_play_history",
                     joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "song_ref")             // "albumId:songId"
    @OrderColumn(name = "play_order")
    private List<String> songPlayHistory = new ArrayList<>();

    // Role — ROLE_USER or ROLE_ADMIN
    @Column(nullable = false)
    private String role = "ROLE_USER";

    // ── Getters / Setters ────────────────────────────────────────────────────

    public Long getId() { return id; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getEmailAddress() { return emailAddress; }
    public void setEmailAddress(String emailAddress) { this.emailAddress = emailAddress; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public Integer getNumCredits() { return numCredits; }
    public void setNumCredits(Integer numCredits) { this.numCredits = numCredits; }
    public List<String> getSongPlayHistory() { return songPlayHistory; }
    public void setSongPlayHistory(List<String> songPlayHistory) { this.songPlayHistory = songPlayHistory; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}
```

---

## Step 3 — `UserRepository`

**File:** `com/djt/jukeanator_engine/domain/user/repository/UserRepository.java`

```java
package com.djt.jukeanator_engine.domain.user.repository;

import com.djt.jukeanator_engine.domain.user.model.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Long> {
    Optional<UserEntity> findByEmailAddress(String emailAddress);
    boolean existsByEmailAddress(String emailAddress);
}
```

---

## Step 4 — DTOs

**File:** `com/djt/jukeanator_engine/domain/user/dto/RegisterRequest.java`

```java
public record RegisterRequest(
    String firstName,
    String lastName,
    String emailAddress,
    String password
) {}
```

**File:** `com/djt/jukeanator_engine/domain/user/dto/LoginRequest.java`

```java
public record LoginRequest(String emailAddress, String password) {}
```

**File:** `com/djt/jukeanator_engine/domain/user/dto/AuthResponse.java`

```java
public record AuthResponse(String token, String emailAddress, String role) {}
```

**File:** `com/djt/jukeanator_engine/domain/user/dto/UserProfileDto.java`

```java
public record UserProfileDto(
    Long id,
    String firstName,
    String lastName,
    String emailAddress,
    Integer numCredits,
    List<String> songPlayHistory
) {}
```

---

## Step 5 — JWT Utility

**File:** `com/djt/jukeanator_engine/security/JwtUtil.java`

```java
package com.djt.jukeanator_engine.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
public class JwtUtil {

    private final SecretKey secretKey;
    private final long expirationMs;

    public JwtUtil(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms:86400000}") long expirationMs) {

        // Secret must be ≥ 256 bits for HS256
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes());
        this.expirationMs = expirationMs;
    }

    public String generateToken(String emailAddress, String role) {
        return Jwts.builder()
                .subject(emailAddress)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(secretKey)
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
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
```

---

## Step 6 — JWT Filter

**File:** `com/djt/jukeanator_engine/security/JwtAuthenticationFilter.java`

```java
package com.djt.jukeanator_engine.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    public JwtAuthenticationFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            if (jwtUtil.isTokenValid(token)) {
                String email = jwtUtil.extractEmail(token);
                String role  = jwtUtil.extractRole(token);

                var auth = new UsernamePasswordAuthenticationToken(
                        email,
                        null,
                        List.of(new SimpleGrantedAuthority(role))
                );
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }

        filterChain.doFilter(request, response);
    }
}
```

---

## Step 7 — Security Configuration

**File:** `com/djt/jukeanator_engine/security/SecurityConfig.java`

```java
package com.djt.jukeanator_engine.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtFilter) {
        this.jwtFilter = jwtFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Public — auth endpoints
                .requestMatchers("/api/users/register", "/api/users/login").permitAll()
                // Public — read-only music browsing (adjust to taste)
                .requestMatchers(HttpMethod.GET,
                    "/api/song-library/popular",
                    "/api/song-library/search",
                    "/api/song-library/genres/**",
                    "/api/song-library/artists/**",
                    "/api/song-library/albums/**",
                    "/api/song-library/songs/**").permitAll()
                // Admin-only — mutation / scan operations
                .requestMatchers(HttpMethod.POST,
                    "/api/song-library/scan",
                    "/api/song-library/scanNoPath",
                    "/api/song-library/resetSongStatistics",
                    "/api/song-library/albums/*/updateAlbumMetadata",
                    "/api/song-library/downloadAlbumCoverArt",
                    "/api/song-library/authenticateForAdminPanel").hasRole("ADMIN")
                // Everything else requires at least a logged-in user
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
```

---

## Step 8 — `UserService`

**File:** `com/djt/jukeanator_engine/domain/user/service/UserService.java`

```java
package com.djt.jukeanator_engine.domain.user.service;

import com.djt.jukeanator_engine.domain.user.dto.*;
import com.djt.jukeanator_engine.domain.user.model.UserEntity;
import com.djt.jukeanator_engine.domain.user.repository.UserRepository;
import com.djt.jukeanator_engine.security.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmailAddress(request.emailAddress())) {
            throw new IllegalArgumentException("Email already registered");
        }

        UserEntity user = new UserEntity();
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setEmailAddress(request.emailAddress());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole("ROLE_USER");
        userRepository.save(user);

        String token = jwtUtil.generateToken(user.getEmailAddress(), user.getRole());
        return new AuthResponse(token, user.getEmailAddress(), user.getRole());
    }

    public AuthResponse login(LoginRequest request) {
        UserEntity user = userRepository.findByEmailAddress(request.emailAddress())
            .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid credentials");
        }

        String token = jwtUtil.generateToken(user.getEmailAddress(), user.getRole());
        return new AuthResponse(token, user.getEmailAddress(), user.getRole());
    }

    public UserProfileDto getProfile(String emailAddress) {
        UserEntity user = userRepository.findByEmailAddress(emailAddress)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        return new UserProfileDto(
            user.getId(),
            user.getFirstName(),
            user.getLastName(),
            user.getEmailAddress(),
            user.getNumCredits(),
            user.getSongPlayHistory()
        );
    }

    /** Called by SongLibraryService (or an event listener) when a song is played */
    public void recordSongPlay(String emailAddress, Integer albumId, Integer songId) {
        userRepository.findByEmailAddress(emailAddress).ifPresent(user -> {
            user.getSongPlayHistory().add(albumId + ":" + songId);
            userRepository.save(user);
        });
    }
}
```

---

## Step 9 — `UserController`

**File:** `com/djt/jukeanator_engine/domain/user/controller/UserController.java`

```java
package com.djt.jukeanator_engine.domain.user.controller;

import com.djt.jukeanator_engine.domain.user.dto.*;
import com.djt.jukeanator_engine.domain.user.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest request) {
        return ResponseEntity.ok(userService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(userService.login(request));
    }

    /** Returns the profile for the currently authenticated user */
    @GetMapping("/me")
    public ResponseEntity<UserProfileDto> me(@AuthenticationPrincipal String emailAddress) {
        return ResponseEntity.ok(userService.getProfile(emailAddress));
    }
}
```

---

## Step 10 — `application.yml` / `application-test.yml` Changes

Add to `application.yml`:

```yaml
app:
  jwt:
    secret: "replace-this-with-a-32-plus-char-random-secret-for-production!!"
    expiration-ms: 86400000   # 24 hours
```

In `application-test.yml`, add the same block (or reuse via Spring profiles). Also disable security for test contexts if desired:

```yaml
# application-test.yml
app:
  jwt:
    secret: "test-secret-key-must-be-at-least-32-characters-long-for-hs256"
    expiration-ms: 3600000
```

---

## Step 11 — Changes to `SongLibraryController`

The controller itself barely changes — Spring Security's filter does all the work before the request arrives. The only change needed is annotating admin-only methods for clarity and adding an optional `@AuthenticationPrincipal` where you want to capture the calling user (e.g. to record a play):

```java
// Add this import
import org.springframework.security.core.annotation.AuthenticationPrincipal;

// Example: getSongById — record which user played it
@Override
@GetMapping("/songs/{albumId}/{songId}")
public SongDto getSongById(@PathVariable Integer albumId,
                           @PathVariable Integer songId,
                           @AuthenticationPrincipal String emailAddress) {

    SongDto song = songLibraryService.getSongById(albumId, songId);

    // Optionally record the play on the user's history
    if (emailAddress != null) {
        userService.recordSongPlay(emailAddress, albumId, songId);
    }

    return song;
}
```

Note: `getSongById` in the `SongLibraryService` interface does NOT change. Only the controller method signature widens — Spring resolves `@AuthenticationPrincipal` parameters separately from the interface contract.

---

## Step 12 — Changes to `SongLibraryServiceHttpClient`

The HTTP client needs to accept and forward a JWT token. The cleanest pattern is to inject the token at construction time (or per-request via a supplier):

```java
public class SongLibraryServiceHttpClient implements SongLibraryService {

    private final RestClient restClient;

    /** Use this constructor when a static token is known upfront (service-to-service) */
    public SongLibraryServiceHttpClient(String baseUrl, String bearerToken) {
        this.restClient = RestClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader("Authorization", "Bearer " + bearerToken)
            .build();
    }

    /** Use this constructor + call login() first to obtain a token dynamically */
    public SongLibraryServiceHttpClient(String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    /** Convenience: log in and return a pre-authenticated client */
    public static SongLibraryServiceHttpClient authenticated(
            String baseUrl, String emailAddress, String password) {

        RestClient bootstrap = RestClient.builder().baseUrl(baseUrl).build();
        // POST /api/users/login
        var response = bootstrap.post()
            .uri("/api/users/login")
            .body(Map.of("emailAddress", emailAddress, "password", password))
            .retrieve()
            .body(Map.class);          // or a LoginResponse record

        String token = (String) response.get("token");
        return new SongLibraryServiceHttpClient(baseUrl, token);
    }

    // All existing methods remain exactly the same — the Authorization header
    // is injected at the RestClient level and sent on every request automatically.
}
```

---

## Step 13 — Changes to `SongLibraryServiceTest`

```java
@SpringBootTest
@ActiveProfiles("test")
public class SongLibraryServiceTest {

    @Autowired
    private SongLibraryService songLibraryService;

    @Autowired
    private UserService userService;        // NEW

    // Helper: create a test user before tests that need one
    private static final String TEST_EMAIL    = "testuser@example.com";
    private static final String TEST_PASSWORD = "Test@1234";

    @BeforeAll
    static void beforeAll() throws IOException {
        cleanup();
    }

    @AfterAll
    static void afterAll() throws IOException {
        cleanup();
    }

    // Existing cleanup() stays the same ...

    @Test
    @WithMockUser(username = TEST_EMAIL, roles = "USER")   // Spring Security test support
    void scanFileSystemForSongs() throws IOException {
        // Existing test body is unchanged — the @WithMockUser annotation
        // injects a mock principal so the security filter doesn't block it.
        ScanRequest scanRequest = new ScanRequest("src/test/resources/...");
        Integer numAlbums = songLibraryService.scanFileSystemForSongs(scanRequest);
        assertNotNull(numAlbums);
        // ...
    }

    @Test
    void registerAndLogin() {
        // Register
        AuthResponse reg = userService.register(
            new RegisterRequest("Jane", "Doe", TEST_EMAIL, TEST_PASSWORD));
        assertNotNull(reg.token());

        // Login
        AuthResponse login = userService.login(
            new LoginRequest(TEST_EMAIL, TEST_PASSWORD));
        assertNotNull(login.token());
        assertEquals(TEST_EMAIL, login.emailAddress());
    }

    @Test
    @WithMockUser(username = TEST_EMAIL, roles = "ADMIN")
    void adminCanScan() throws IOException {
        ScanRequest scanRequest = new ScanRequest("src/test/resources/...");
        Integer numAlbums = songLibraryService.scanFileSystemForSongs(scanRequest);
        assertNotNull(numAlbums);
    }
}
```

Key additions:
- Add `@WithMockUser` from `spring-security-test` to all existing tests so the security layer doesn't reject them during unit/integration testing.
- Add dedicated auth tests (register, login, bad password) in a separate `UserServiceTest`.

---

## Step 14 — Flyway Migration

Create `src/main/resources/db/migration/V2__create_users.sql`:

```sql
CREATE TABLE users (
    id             BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    first_name     VARCHAR(100)  NOT NULL,
    last_name      VARCHAR(100)  NOT NULL,
    email_address  VARCHAR(255)  NOT NULL UNIQUE,
    password_hash  VARCHAR(255)  NOT NULL,
    num_credits    INT           NOT NULL DEFAULT 0,
    role           VARCHAR(50)   NOT NULL DEFAULT 'ROLE_USER'
);

CREATE TABLE user_song_play_history (
    user_id     BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    song_ref    VARCHAR(50)  NOT NULL,
    play_order  INT          NOT NULL
);
```

---

## Step 15 — Postman Collection Changes

### Environment Variables (add these)

- `baseUrl`: `http://localhost:8080`
- `authToken`: *(populated automatically by the login script below)*

### New Requests

**POST `{{baseUrl}}/api/users/register`**
```json
{
  "firstName": "Jane",
  "lastName": "Doe",
  "emailAddress": "jane@example.com",
  "password": "MySecret123!"
}
```
In the **Tests** tab:
```javascript
const json = pm.response.json();
pm.environment.set("authToken", json.token);
```

**POST `{{baseUrl}}/api/users/login`**
```json
{
  "emailAddress": "jane@example.com",
  "password": "MySecret123!"
}
```
Same **Tests** script — this keeps `authToken` fresh on every login run.

**GET `{{baseUrl}}/api/users/me`**  
No body. Auth header added by the collection-level script below.

### Collection-Level Authorization (applies to all existing requests automatically)

In the Postman Collection settings → **Authorization** tab:
- Type: `Bearer Token`
- Token: `{{authToken}}`

This propagates to every request in the collection. No need to edit each request individually.

### Existing Requests — What Changes

- All `GET /api/song-library/*`: Nothing — they're public, but the Bearer token is harmless to
  include
- `POST /api/song-library/scan`: Now requires `ROLE_ADMIN` — use an admin account's token
- `POST /api/song-library/scanNoPath`: Same
- `POST /api/song-library/resetSongStatistics`: Same
- `POST /api/song-library/authenticateForAdminPanel`: Still works as before (no Spring Security
  rule blocks it since it's under `.hasRole("ADMIN")` — or you can leave it `.permitAll()` if it's
  the admin panel's own login mechanism)

### Testing the full flow in Postman

1. Run **POST /register** or **POST /login** → `authToken` is set in environment
2. Run any protected endpoint — the collection-level Bearer token is sent automatically
3. To test admin endpoints, log in with an account that has `role = ROLE_ADMIN` in the database

---

## Summary of All Files to Create/Modify

- **Create**: `UserEntity.java`
- **Create**: `UserRepository.java`
- **Create**: `RegisterRequest.java`, `LoginRequest.java`, `AuthResponse.java`, `UserProfileDto.java`
- **Create**: `JwtUtil.java`
- **Create**: `JwtAuthenticationFilter.java`
- **Create**: `SecurityConfig.java`
- **Create**: `UserService.java`
- **Create**: `UserController.java`
- **Create**: `V2__create_users.sql`
- **Modify**: `pom.xml` — add 4 JWT + security dependencies
- **Modify**: `application.yml` — add `app.jwt.*` properties
- **Modify**: `application-test.yml` — add test JWT secret
- **Modify**: `SongLibraryController.java` — optionally add `@AuthenticationPrincipal` where plays
  should be recorded
- **Modify**: `SongLibraryServiceHttpClient.java` — add token-bearing constructor / static factory
- **Modify**: `SongLibraryServiceTest.java` — add `@WithMockUser` to existing tests, add auth tests
- **Modify**: Postman collection — add env vars, register/login requests, collection-level Bearer
  auth

The `SongLibraryService` **interface** and `SongLibraryServiceImpl` do **not** need to change for authentication to work — Spring Security handles everything at the HTTP boundary before requests reach those classes.
