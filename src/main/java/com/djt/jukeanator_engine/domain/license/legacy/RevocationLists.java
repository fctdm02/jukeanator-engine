package com.djt.jukeanator_engine.domain.license.legacy;

import java.util.Locale;
import java.util.Set;

/**
 * Machine ids and customer emails revoked in the legacy client (MP3Jukeboxx, applied right after
 * {@code checkRuntime()}). Kept so these installs stay revoked even when the license server cannot
 * be reached. The server-side equivalent is the {@code revoked} flag on customers and machines,
 * seeded from the same lists by the legacy import script.
 */
public final class RevocationLists {

  public static final Set<String> MACHINE_IDS = Set.of(
      "{1D38215A-A137-407B-ACFF-ED173A1C49D8}",
      "527546751",
      "672237337",
      "{00DE7D4C-5001-408A-A965-DD0A79B94E8D}",
      "501911060",
      "553102140",
      "531207125",
      "683133782",
      "532844453",
      "621041840",
      "590445152");

  public static final Set<String> CUSTOMER_EMAILS = Set.of(
      "eftobias@gmail.com",
      "retroeatery@hotmail.com",
      "aguayluz2life@yahoo.com",
      "zurkeyon@gmail.com",
      "keith.woods@live.com",
      "peach4059@comcast.net",
      "flores.305@hotmail.com",
      "creacarasucia@gmail.com",
      "hsendelbach@gmail.com",
      "kenlemerond@gmail.com",
      "vancort914@yahoo.com");

  private RevocationLists() {}

  public static boolean isRevokedMachine(String machineId) {
    return machineId != null && MACHINE_IDS.contains(machineId.trim());
  }

  public static boolean isRevokedEmail(String email) {
    return email != null && CUSTOMER_EMAILS.contains(email.trim().toLowerCase(Locale.ROOT));
  }
}
