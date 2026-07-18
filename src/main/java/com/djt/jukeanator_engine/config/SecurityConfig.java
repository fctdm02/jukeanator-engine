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
            .requestMatchers(HttpMethod.GET, "/api/users/credit-packages", "/api/users/home-public").permitAll()

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

            // ── Public: read-only music browsing ─────────────────────────────
            .requestMatchers(HttpMethod.GET, "/api/song-library/popular",
                "/api/song-library/search", "/api/song-library/genres",
                "/api/song-library/genres/**", "/api/song-library/artists",
                "/api/song-library/artists/**", "/api/song-library/artistByAlbum/**",
                "/api/song-library/albums",
                "/api/song-library/albums/**", "/api/song-library/songs/**",
                "/api/song-library/artist", "/api/song-library/searchInternetForAlbumMetadata")
            .permitAll()

            // ── Public: playback status (read-only, display on jukebox UI) ───
            .requestMatchers(HttpMethod.GET, "/api/song-player/nowPlayingSong",
                "/api/song-player/playbackStatus")
            .permitAll()

            // ── Public: view the queue ────────────────────────────────────────
            .requestMatchers(HttpMethod.GET, "/api/song-queue/queuedSongs",
                "/api/song-queue/highestPriority")
            .permitAll()

            // ── Master-mode only: location-scoped mirrors of the three rule
            // categories above, for web/mobile users browsing/queuing at a chosen location ─
            .requestMatchers(HttpMethod.GET, "/api/locations/*/song-library/popular",
                "/api/locations/*/song-library/search", "/api/locations/*/song-library/genres",
                "/api/locations/*/song-library/genres/**", "/api/locations/*/song-library/artists",
                "/api/locations/*/song-library/artists/**",
                "/api/locations/*/song-library/artistByAlbum/**",
                "/api/locations/*/song-library/albums",
                "/api/locations/*/song-library/albums/**",
                "/api/locations/*/song-library/songs/**", "/api/locations/*/song-library/artist")
            .permitAll()

            .requestMatchers(HttpMethod.GET, "/api/locations/*/song-player/nowPlayingSong",
                "/api/locations/*/song-player/playbackStatus")
            .permitAll()

            .requestMatchers(HttpMethod.GET, "/api/locations/*/song-queue/queuedSongs",
                "/api/locations/*/song-queue/highestPriority")
            .permitAll()

            // ── Master-mode only: public location picker, admin-only provisioning ─
            .requestMatchers(HttpMethod.GET, "/api/locations").permitAll()
            .requestMatchers(HttpMethod.POST, "/api/locations").hasRole("ADMIN")

            // ── Master-mode only: bar-owner accounting, admin-only ─────────────
            .requestMatchers(HttpMethod.GET, "/api/locations/*/credit-ledger").hasRole("ADMIN")

            // ── Master-mode only: slave library sync, authenticated via the
            // location-id/location-api-key headers (LocationApiKeyAuthenticationFilter) ─
            .requestMatchers(HttpMethod.POST, "/api/locations/*/library-sync/**")
            .hasRole("LOCATION")

            // ── Authenticated users: add songs to the queue ───────────────────
            // Any logged-in patron can queue songs.
            .requestMatchers(HttpMethod.POST, "/api/song-queue/addSong", "/api/song-queue/addAlbum",
                "/api/song-queue/addMultipleSongs")
            .authenticated()

            // ── Authenticated users: reorder/remove songs from the queue ──────
            // Mirrors the JFC/Swing Queue tab's move-up/move-down/remove actions,
            // priced the same way addSong is (see UserServiceImpl WEB_QUEUE_ACTION_COST).
            .requestMatchers(HttpMethod.POST, "/api/song-queue/moveSongUpInQueue",
                "/api/song-queue/moveSongDownInQueue", "/api/song-queue/removeSongDownFromQueue")
            .authenticated()

            // ── Master-mode only: location-scoped mirror of the queue-add/reorder rules ─
            .requestMatchers(HttpMethod.POST, "/api/locations/*/song-queue/addSong",
                "/api/locations/*/song-queue/addAlbum", "/api/locations/*/song-queue/addMultipleSongs")
            .authenticated()

            .requestMatchers(HttpMethod.POST, "/api/locations/*/song-queue/moveSongUpInQueue",
                "/api/locations/*/song-queue/moveSongDownInQueue",
                "/api/locations/*/song-queue/removeSongDownFromQueue")
            .authenticated()

            // ── Admin only: song library mutations ────────────────────────────
            .requestMatchers(HttpMethod.POST, "/api/song-library/scan",
                "/api/song-library/scanNoPath", "/api/song-library/resetSongStatistics",
                "/api/song-library/downloadAlbumCoverArt",
                "/api/song-library/authenticateForAdminPanel")
            .hasRole("ADMIN")

            .requestMatchers(HttpMethod.POST, "/api/song-library/albums/*/updateAlbumMetadata")
            .hasRole("ADMIN")

            .requestMatchers(HttpMethod.POST, "/api/song-queue/flushQueue",
                "/api/song-queue/randomizeQueue",
                "/api/song-queue/saveQueueAsPlaylist", "/api/song-queue/loadPlaylistIntoQueue")
            .hasRole("ADMIN")

            // ── Admin only: player controls ───────────────────────────────────
            .requestMatchers(HttpMethod.POST, "/api/song-player/next", "/api/song-player/pause",
                "/api/song-player/stop")
            .hasRole("ADMIN")

            // ── Master-mode only: location-scoped mirror of admin-only queue/player rules ─
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
