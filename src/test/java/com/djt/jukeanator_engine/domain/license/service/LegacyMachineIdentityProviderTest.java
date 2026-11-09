package com.djt.jukeanator_engine.domain.license.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/** Parsing must reproduce the legacy client's machine id text exactly. */
class LegacyMachineIdentityProviderTest {

  @Test
  void diskId32_computerId() {
    String out = String.join("\r\n", "", "Computer ID_______________________: 661793536", "",
        "MAC Address: 00-1F-E1-51-A7-A5", "", "MAC Address: 00-1E-68-7C-22-91");
    assertEquals("661793536", LegacyMachineIdentityProvider.parseDiskId32(out));
  }

  @Test
  void diskId32_invalidId_fallsBackToFirstMac() {
    String out = String.join("\r\n", "Computer ID_______________________: 600000000", "",
        "MAC Address: 00-1F-E1-51-A7-A5", "", "MAC Address: 00-1E-68-7C-22-91");
    assertEquals("00-1F-E1-51-A7-A5", LegacyMachineIdentityProvider.parseDiskId32(out));
  }

  @Test
  void diskId32_noComputerId() {
    assertNull(LegacyMachineIdentityProvider.parseDiskId32("nothing here"));
  }

  @Test
  void ipconfig_firstPhysicalAddress() {
    String out = "Windows IP Configuration\r\n\r\n"
        + "        Physical Address. . . . . . . . . : 00-1A-70-F9-9D-DE\r\n"
        + "        Physical Address. . . . . . . . . : 11-22-33-44-55-66\r\n";
    assertEquals("00-1A-70-F9-9D-DE", LegacyMachineIdentityProvider.parseIpconfigMac(out));
  }

  @Test
  void registry_lowestNumberedNetworkCard() {
    String out = String.join("\r\n", "",
        "HKEY_LOCAL_MACHINE\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\\NetworkCards\\12",
        "    ServiceName    REG_SZ    {BBBB}",
        "    Description    REG_SZ    Second",
        "",
        "HKEY_LOCAL_MACHINE\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\\NetworkCards\\3",
        "    ServiceName    REG_SZ    {AAAA}",
        "    Description    REG_SZ    First",
        "",
        "HKEY_LOCAL_MACHINE\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\\NetworkCards\\25",
        "    ServiceName    REG_SZ    {OUT-OF-LEGACY-RANGE}");
    assertEquals("{AAAA}", LegacyMachineIdentityProvider.parseNetworkCardGuid(out));
  }
}
