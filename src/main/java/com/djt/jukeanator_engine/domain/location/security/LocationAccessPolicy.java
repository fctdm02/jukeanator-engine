package com.djt.jukeanator_engine.domain.location.security;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import com.djt.jukeanator_engine.domain.common.security.SystemPrincipal;

/**
 * Decides which Web/Mobile UI accounts may see and use a private location (master only), from
 * {@code location.private-locations} (location id to the email addresses allowed to use it). A
 * private location is left out of the location list and refuses slave commands (queue, player)
 * for every other account and for visitors who are not signed in. Locations not listed there are
 * public, as before.
 *
 * <p>
 * Callers that are not a Web/Mobile UI user always pass: background work with no authentication
 * in the security context, the location's own kiosk ({@code ROLE_LOCATION}) and the system
 * principal ({@code ROLE_SYSTEM}).
 */
public final class LocationAccessPolicy {

  private static final String LOCATION_ROLE = "ROLE_LOCATION";

  private final Map<Integer, Set<String>> privateLocations;

  public LocationAccessPolicy(Map<Integer, List<String>> privateLocations) {

    Map<Integer, Set<String>> normalized = new HashMap<>();
    if (privateLocations != null) {
      privateLocations.forEach((locationId, emails) -> {
        if (locationId != null) {
          normalized.put(locationId, normalizeEmails(emails));
        }
      });
    }
    this.privateLocations = Map.copyOf(normalized);
  }

  public boolean isPrivate(Integer locationId) {
    return locationId != null && privateLocations.containsKey(locationId);
  }

  /** {@link #isAccessible(Integer, Authentication)} for the current request's authentication. */
  public boolean isAccessibleToCurrentUser(Integer locationId) {
    return isAccessible(locationId, SecurityContextHolder.getContext().getAuthentication());
  }

  public boolean isAccessible(Integer locationId, Authentication authentication) {

    Set<String> allowed = locationId == null ? null : privateLocations.get(locationId);
    if (allowed == null) {
      return true;
    }
    if (authentication == null) {
      return true; // background work outside any request
    }
    if (authentication instanceof AnonymousAuthenticationToken
        || !authentication.isAuthenticated()) {
      return false;
    }
    if (hasAuthority(authentication, LOCATION_ROLE)
        || hasAuthority(authentication, SystemPrincipal.SYSTEM_ROLE)) {
      return true;
    }
    String name = authentication.getName();
    return name != null && allowed.contains(name.trim().toLowerCase(Locale.ROOT));
  }

  private static boolean hasAuthority(Authentication authentication, String authority) {
    Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
    return authorities != null
        && authorities.stream().anyMatch(a -> authority.equals(a.getAuthority()));
  }

  private static Set<String> normalizeEmails(List<String> emails) {
    if (emails == null) {
      return Set.of();
    }
    return emails.stream()
        .filter(e -> e != null && !e.isBlank())
        .map(e -> e.trim().toLowerCase(Locale.ROOT))
        .collect(Collectors.toUnmodifiableSet());
  }
}
