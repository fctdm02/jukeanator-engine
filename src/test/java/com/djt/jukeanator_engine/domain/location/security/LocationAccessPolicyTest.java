package com.djt.jukeanator_engine.domain.location.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import com.djt.jukeanator_engine.domain.common.security.SystemPrincipal;

class LocationAccessPolicyTest {

  private static final Integer PRIVATE_ID = 1;
  private static final Integer PUBLIC_ID = 2;

  private final LocationAccessPolicy policy = new LocationAccessPolicy(
      Map.of(PRIVATE_ID, List.of(" Owner@Example.com", "tester@example.com", "")));

  @Test
  void publicLocation_isAccessibleToEveryone() {
    assertTrue(policy.isAccessible(PUBLIC_ID, anonymous()));
    assertTrue(policy.isAccessible(PUBLIC_ID, user("stranger@example.com", "ROLE_USER")));
    assertFalse(policy.isPrivate(PUBLIC_ID));
  }

  @Test
  void privateLocation_isAccessibleOnlyToListedAccounts_ignoringCaseAndSpaces() {
    assertTrue(policy.isPrivate(PRIVATE_ID));
    assertTrue(policy.isAccessible(PRIVATE_ID, user("owner@example.com", "ROLE_ADMIN")));
    assertTrue(policy.isAccessible(PRIVATE_ID, user("TESTER@example.com ", "ROLE_USER")));
    assertFalse(policy.isAccessible(PRIVATE_ID, user("stranger@example.com", "ROLE_USER")));
    assertFalse(policy.isAccessible(PRIVATE_ID, user("stranger@example.com", "ROLE_ADMIN")));
  }

  @Test
  void privateLocation_isHiddenFromVisitorsWhoAreNotSignedIn() {
    assertFalse(policy.isAccessible(PRIVATE_ID, anonymous()));
  }

  @Test
  void privateLocation_staysUsableByBackgroundWork_itsKiosk_andTheSystem() {
    assertTrue(policy.isAccessible(PRIVATE_ID, null));
    assertTrue(policy.isAccessible(PRIVATE_ID, user(PRIVATE_ID, "ROLE_LOCATION")));
    assertTrue(policy.isAccessible(PRIVATE_ID, user("system", SystemPrincipal.SYSTEM_ROLE)));
  }

  @Test
  void noConfiguration_meansEveryLocationIsPublic() {
    LocationAccessPolicy none = new LocationAccessPolicy(null);
    assertTrue(none.isAccessible(PRIVATE_ID, anonymous()));
    assertFalse(none.isPrivate(PRIVATE_ID));
  }

  private static Authentication user(Object principal, String role) {
    return new UsernamePasswordAuthenticationToken(principal, null,
        List.of(new SimpleGrantedAuthority(role)));
  }

  private static Authentication anonymous() {
    return new AnonymousAuthenticationToken("key", "anonymousUser",
        List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
  }
}
