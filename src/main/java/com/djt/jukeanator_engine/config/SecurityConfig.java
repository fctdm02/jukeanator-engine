package com.djt.jukeanator_engine.config;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import com.djt.jukeanator_engine.domain.common.security.JwtAuthenticationFilter;
import com.djt.jukeanator_engine.domain.location.security.LocationApiKeyAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

  private final JwtAuthenticationFilter jwtFilter;

  // Only present in master mode (see LocationConfig) — standalone/slave deployments never
  // construct this bean, so this is empty and the sync routes below simply never see traffic.
  private final Optional<LocationApiKeyAuthenticationFilter> locationApiKeyFilter;

  public SecurityConfig(JwtAuthenticationFilter jwtFilter,
      Optional<LocationApiKeyAuthenticationFilter> locationApiKeyFilter) {
    this.jwtFilter = jwtFilter;
    this.locationApiKeyFilter = locationApiKeyFilter;
  }

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(ex -> ex
            // No/invalid/expired JWT on a protected endpoint → 401, not the default 403, so the
            // web UI can tell "you need to log in" apart from "you're logged in but not allowed".
            .authenticationEntryPoint((request, response, authException) -> {
              log.warn("[SECURITY] 401 Unauthorized: {} {} — {}", request.getMethod(),
                  request.getRequestURI(), authException.getMessage());
              response.sendError(HttpStatus.UNAUTHORIZED.value(), authException.getMessage());
            })
            // Authenticated but lacking the required role (e.g. non-admin hitting an admin route).
            .accessDeniedHandler((request, response, accessDeniedException) -> {
              log.warn("[SECURITY] 403 Forbidden: {} {} — {}", request.getMethod(),
                  request.getRequestURI(), accessDeniedException.getMessage());
              response.sendError(HttpStatus.FORBIDDEN.value(), accessDeniedException.getMessage());
            }))
        .authorizeHttpRequests(auth -> auth

            // ── Public: auth endpoints ────────────────────────────────────────
            .requestMatchers("/api/users/register", "/api/users/login").permitAll()
            .requestMatchers(HttpMethod.GET, "/api/users/credit-packages", "/api/users/home-public",
                "/api/users/own-location-id", "/api/users/own-location",
                "/api/users/pricing-config").permitAll()

            // ── Public: static web UI assets and the websocket handshake ─────
            // /ws-slave/** (master-mode only) is the same story as /ws/**: the HTTP handshake
            // itself can't carry STOMP headers, so real auth happens one layer up, at the STOMP
            // CONNECT frame (StompJwtChannelInterceptor / StompLocationApiKeyChannelInterceptor).
            .requestMatchers("/", "/index.html", "/css/**", "/js/**", "/images/**",
                "/favicon.ico", "/favicon-16x16.png", "/favicon-32x32.png",
                "/apple-touch-icon.png", "/site.webmanifest", "/ws/**", "/ws-slave/**")
            .permitAll()

            // ── Public: container error-page forward ──────────────────────────
            // response.sendError() triggers an internal forward to /error, which re-enters this
            // filter chain as a new request. Without this, that forward gets blocked too and
            // silently overwrites the real 401/403 status set by the handlers below.
            .requestMatchers("/error").permitAll()

            // Every song-library/song-player/song-queue route is location-scoped
            // (/api/locations/{locationId}/...) in every app.mode -- on standalone/slave the
            // locationId is the instance's own location.

            // ── Public: read-only music browsing ─────────────────────────────
            .requestMatchers(HttpMethod.GET, "/api/locations/*/song-library/popular",
                "/api/locations/*/song-library/search", "/api/locations/*/song-library/genres",
                "/api/locations/*/song-library/genres/**", "/api/locations/*/song-library/artists",
                "/api/locations/*/song-library/artists/**",
                "/api/locations/*/song-library/artistByAlbum/**",
                "/api/locations/*/song-library/albums",
                "/api/locations/*/song-library/albums/**",
                "/api/locations/*/song-library/songs/**", "/api/locations/*/song-library/artist")
            .permitAll()

            // ── Public: playback status and the queue (read-only) ────────────
            .requestMatchers(HttpMethod.GET, "/api/locations/*/song-player/nowPlayingSong",
                "/api/locations/*/song-player/playbackStatus")
            .permitAll()

            .requestMatchers(HttpMethod.GET, "/api/locations/*/song-queue/queuedSongs",
                "/api/locations/*/song-queue/highestPriority")
            .permitAll()

            // ── Public: whether queue operations at a location require the device position ─
            .requestMatchers(HttpMethod.GET, "/api/locations/*/geo-fence").permitAll()

            // ── Master-mode only: public location picker, admin-only provisioning ─
            .requestMatchers(HttpMethod.GET, "/api/locations").permitAll()
            .requestMatchers(HttpMethod.POST, "/api/locations").hasRole("ADMIN")

            // ── Master-mode only: bar-owner accounting, admin-only ─────────────
            .requestMatchers(HttpMethod.GET, "/api/locations/*/credit-ledger").hasRole("ADMIN")

            // ── Master-mode only: slave library sync + financial-ledger and user-activity
            // mirror sync, authenticated via the location-id/location-api-key headers
            // (LocationApiKeyAuthenticationFilter) ─
            .requestMatchers(HttpMethod.POST, "/api/locations/*/library-sync/**",
                "/api/locations/*/financial-ledger/**", "/api/locations/*/user-activity/**")
            .hasRole("LOCATION")
            // The slave's catch-up pull of the mobile/web spends master recorded against it.
            .requestMatchers(HttpMethod.GET, "/api/locations/*/financial-ledger/**")
            .hasRole("LOCATION")

            // ── Admin only: queue a whole album ───────────────────────────────
            // Patrons queue songs individually (or a playlist via addMultipleSongs).
            .requestMatchers(HttpMethod.POST, "/api/locations/*/song-queue/addAlbum")
            .hasRole("ADMIN")

            // ── Authenticated users: add, reorder and remove songs in the queue ──
            // Charged per action (see SongQueueController); move-up/move-down/remove mirror the
            // JFC/Swing Queue tab's actions.
            .requestMatchers(HttpMethod.POST, "/api/locations/*/song-queue/addSong",
                "/api/locations/*/song-queue/addMultipleSongs",
                "/api/locations/*/song-queue/checkSongsEligibility",
                "/api/locations/*/song-queue/moveSongUpInQueue",
                "/api/locations/*/song-queue/moveSongDownInQueue",
                "/api/locations/*/song-queue/removeSongDownFromQueue")
            .authenticated()

            // ── Admin only: song library mutations -- without these, any logged-in patron
            // could rescan the library or reset its statistics ─
            .requestMatchers(HttpMethod.POST, "/api/locations/*/song-library/scan",
                "/api/locations/*/song-library/scanNoPath",
                "/api/locations/*/song-library/resetSongStatistics",
                "/api/locations/*/song-library/restoreSongStatistics",
                "/api/locations/*/song-library/storeSongLibraryAndStatistics",
                "/api/locations/*/song-library/downloadAlbumCoverArt",
                "/api/locations/*/song-library/authenticateForAdminPanel",
                "/api/locations/*/song-library/albums/*/updateAlbumMetadata")
            .hasRole("ADMIN")

            // ── Admin only: queue management and player controls ──────────────
            .requestMatchers(HttpMethod.POST, "/api/locations/*/song-queue/flushQueue",
                "/api/locations/*/song-queue/randomizeQueue",
                "/api/locations/*/song-queue/saveQueueAsPlaylist",
                "/api/locations/*/song-queue/loadPlaylistIntoQueue")
            .hasRole("ADMIN")

            .requestMatchers(HttpMethod.POST, "/api/locations/*/song-player/next",
                "/api/locations/*/song-player/pause", "/api/locations/*/song-player/stop",
                "/api/locations/*/song-player/lockQueue", "/api/locations/*/song-player/unlockQueue")
            .hasRole("ADMIN")

            // ── Catch-all: anything not listed above requires authentication ──
            .anyRequest().authenticated())
        .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

    locationApiKeyFilter.ifPresent(
        filter -> http.addFilterAfter(filter, JwtAuthenticationFilter.class));

    return http.build();
  }
}
