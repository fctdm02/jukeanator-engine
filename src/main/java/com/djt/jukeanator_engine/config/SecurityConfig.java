package com.djt.jukeanator_engine.config;

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
import com.djt.jukeanator_engine.domain.common.security.JwtAuthenticationFilter;

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
    http.csrf(csrf -> csrf.disable())
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth

            // ── Public: auth endpoints ────────────────────────────────────────
            .requestMatchers("/api/users/register", "/api/users/login").permitAll()
            .requestMatchers(HttpMethod.GET, "/api/users/credit-packages").permitAll()

            // ── Public: static web UI assets and the websocket handshake ─────
            .requestMatchers("/", "/index.html", "/css/**", "/js/**", "/images/**",
                "/favicon.ico", "/ws/**")
            .permitAll()

            // ── Public: read-only music browsing ─────────────────────────────
            .requestMatchers(HttpMethod.GET, "/api/song-library/popular",
                "/api/song-library/search", "/api/song-library/genres",
                "/api/song-library/genres/**", "/api/song-library/artists",
                "/api/song-library/artists/**", "/api/song-library/albums",
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

            // ── Authenticated users: add songs to the queue ───────────────────
            // Any logged-in patron can queue songs; they cannot manage the queue.
            .requestMatchers(HttpMethod.POST, "/api/song-queue/addSong", "/api/song-queue/addAlbum",
                "/api/song-queue/addMultipleSongs")
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
                "/api/song-queue/randomizeQueue", "/api/song-queue/moveSongUpInQueue",
                "/api/song-queue/moveSongDownInQueue", "/api/song-queue/removeSongDownFromQueue",
                "/api/song-queue/saveQueueAsPlaylist", "/api/song-queue/loadPlaylistIntoQueue")
            .hasRole("ADMIN")

            // ── Admin only: player controls ───────────────────────────────────
            .requestMatchers(HttpMethod.POST, "/api/song-player/next", "/api/song-player/pause",
                "/api/song-player/stop")
            .hasRole("ADMIN")

            // ── Catch-all: anything not listed above requires authentication ──
            .anyRequest().authenticated())
        .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }
}
