package com.djt.jukeanator_engine.domain.location.security;

import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import com.djt.jukeanator_engine.domain.location.controller.LocationController;
import com.djt.jukeanator_engine.domain.location.service.LocationService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Master-only. Authenticates a slave's library-sync request via its {@code location-id}/
 * {@code location-api-key} headers, mirroring {@link
 * com.djt.jukeanator_engine.domain.common.security.JwtAuthenticationFilter}'s shape for JWT. On
 * success, sets a {@code ROLE_LOCATION} principal (the locationId) so
 * {@code SecurityConfig} can gate the sync endpoints with {@code hasRole("LOCATION")}.
 */
public class LocationApiKeyAuthenticationFilter extends OncePerRequestFilter {

  private final LocationService locationService;

  public LocationApiKeyAuthenticationFilter(LocationService locationService) {
    this.locationService = locationService;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {

    String locationId = request.getHeader(LocationController.LOCATION_ID_HEADER);
    String apiKey = request.getHeader(LocationController.LOCATION_API_KEY_HEADER);

    if (locationId != null && apiKey != null && locationService.verifyApiKey(locationId, apiKey)) {

      var auth = new UsernamePasswordAuthenticationToken(locationId, null,
          List.of(new SimpleGrantedAuthority("ROLE_LOCATION")));

      SecurityContextHolder.getContext().setAuthentication(auth);
    }

    filterChain.doFilter(request, response);
  }
}
