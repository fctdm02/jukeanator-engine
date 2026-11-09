package com.djt.jukeanator_engine.domain.license.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.NetworkInterface;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reproduces the legacy MP3Jukeboxx machine ids exactly (their text is hashed into the license
 * key, so a single differing character would invalidate existing license files):
 *
 * <ol>
 * <li><b>Network-card GUID</b>: {@code ServiceName} of the lowest-numbered (0-20) subkey of
 * {@code HKLM\SOFTWARE\Microsoft\Windows NT\CurrentVersion\NetworkCards}. The legacy client read
 * this through a JNI DLL, only on a 32-bit JVM; here it is read with {@code reg query} from both
 * registry views.</li>
 * <li><b>MAC address</b>: the first "Physical Address" in {@code ipconfig /all}, cut at the same
 * fixed offsets (legacy 64-bit JVM behavior).</li>
 * <li><b>Motherboard id</b>: the "Computer ID" printed by {@code diskid32.exe} (only the two
 * known builds, by CRC32), or its first MAC address when the id is {@code 0} or
 * {@code 600000000}.</li>
 * </ol>
 *
 * <p>Ids are computed once and cached; external commands are slow and the ids do not change while
 * the application runs.
 */
public class LegacyMachineIdentityProvider implements MachineIdentityProvider {

  private static final Logger log = LoggerFactory.getLogger(LegacyMachineIdentityProvider.class);

  static final String NETWORK_CARDS_KEY =
      "HKLM\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\\NetworkCards";

  private static final long DISKID32_CRC_1 = 4224116446L;
  private static final long DISKID32_CRC_2 = 396496930L;

  private static final String IPCONFIG_MARKER = "Physical Address. . . . . . . . . :";

  private static final Pattern REG_KEY_LINE =
      Pattern.compile("^HKEY_LOCAL_MACHINE\\\\.*\\\\NetworkCards\\\\(\\d+)\\s*$");
  private static final Pattern REG_SERVICE_NAME_LINE =
      Pattern.compile("^\\s+ServiceName\\s+REG_SZ\\s+(.+?)\\s*$");

  private final List<Path> diskId32Locations;
  private final boolean windows;

  private volatile Ids ids;

  /**
   * @param diskId32Locations where to look for {@code diskid32.exe}, in order (legacy: the data
   *        directory, then {@code C:\kiosk})
   */
  public LegacyMachineIdentityProvider(List<Path> diskId32Locations) {
    this.diskId32Locations = List.copyOf(diskId32Locations);
    this.windows = System.getProperty("os.name", "").startsWith("Windows");
  }

  @Override
  public List<String> candidateIds() {
    Ids current = ids();
    Set<String> ordered = new LinkedHashSet<>();
    addIfPresent(ordered, current.networkCardGuid);
    addIfPresent(ordered, current.ipconfigMac);
    addIfPresent(ordered, current.motherboardId);
    return List.copyOf(ordered);
  }

  @Override
  public String provisioningId() {
    Ids current = ids();
    if (current.motherboardId != null) {
      return current.motherboardId;
    }
    if (current.networkCardGuid != null) {
      return current.networkCardGuid;
    }
    return current.ipconfigMac;
  }

  private Ids ids() {
    Ids current = ids;
    if (current == null) {
      synchronized (this) {
        current = ids;
        if (current == null) {
          current = compute();
          ids = current;
          log.info("Machine ids: networkCard={}, mac={}, motherboard={}",
              current.networkCardGuid, current.ipconfigMac, current.motherboardId);
        }
      }
    }
    return current;
  }

  private Ids compute() {
    if (!windows) {
      return new Ids(null, javaMacAddress(), null);
    }
    return new Ids(
        safely("network card GUID", this::networkCardGuid),
        safely("ipconfig MAC address", this::ipconfigMac),
        safely("diskid32 motherboard id", this::motherboardId));
  }

  // ---------------------------------------------------------------- network card GUID

  private String networkCardGuid() {
    for (String view : new String[] {"/reg:32", "/reg:64"}) {
      String output = run(List.of("reg", "query", NETWORK_CARDS_KEY, "/s", view),
          Charset.defaultCharset());
      String guid = parseNetworkCardGuid(output);
      if (guid != null) {
        return guid;
      }
    }
    return null;
  }

  /** The {@code ServiceName} of the lowest-numbered subkey 0-20 in {@code reg query /s} output. */
  static String parseNetworkCardGuid(String regOutput) {
    if (regOutput == null) {
      return null;
    }
    TreeMap<Integer, String> byNumber = new TreeMap<>();
    Integer currentKey = null;
    for (String line : regOutput.split("\\R")) {
      Matcher key = REG_KEY_LINE.matcher(line);
      if (key.matches()) {
        currentKey = Integer.valueOf(key.group(1));
        continue;
      }
      Matcher value = REG_SERVICE_NAME_LINE.matcher(line);
      if (value.matches() && currentKey != null && currentKey <= 20) {
        byNumber.putIfAbsent(currentKey, value.group(1));
      }
    }
    return byNumber.isEmpty() ? null : byNumber.firstEntry().getValue();
  }

  // ---------------------------------------------------------------- ipconfig MAC

  private String ipconfigMac() {
    return parseIpconfigMac(run(List.of("ipconfig", "/all"), StandardCharsets.ISO_8859_1));
  }

  /** Legacy {@code MacAddressFinder.windowsParseMacAddress}: fixed offsets after the marker. */
  static String parseIpconfigMac(String output) {
    if (output == null) {
      return null;
    }
    int index = output.indexOf(IPCONFIG_MARKER);
    if (index == -1 || output.length() < index + 53) {
      return null;
    }
    return output.substring(index + 36, index + 53);
  }

  // ---------------------------------------------------------------- diskid32 motherboard id

  private String motherboardId() {
    Path exe = diskId32Locations.stream()
        .map(dir -> dir.resolve("diskid32.exe"))
        .filter(Files::isRegularFile)
        .findFirst()
        .orElse(null);
    if (exe == null) {
      return null;
    }
    long crc = crc32(exe);
    if (crc != DISKID32_CRC_1 && crc != DISKID32_CRC_2) {
      log.warn("Ignoring {}: unrecognized build (CRC32 {})", exe, crc);
      return null;
    }
    String output = null;
    for (int attempt = 0; attempt < 5; attempt++) {
      output = runJoinedLines(List.of("cmd.exe", "/C", exe.toAbsolutePath().toString()));
      if (output != null && output.contains("Computer ID")) {
        break;
      }
      sleep(500);
    }
    return parseDiskId32(output);
  }

  /**
   * Legacy {@code getMotherboardSid()} parsing of diskid32 output (lines joined by CRLF, no
   * trailing line break), including its fallback to the MAC address for invalid ids.
   */
  static String parseDiskId32(String output) {
    if (output == null || !output.contains("Computer ID")) {
      return null;
    }
    int idx = output.indexOf("Computer ID");
    if (output.length() < idx + 36) {
      return null;
    }
    String computerId = output.substring(idx + 36);
    idx = computerId.indexOf("MAC Address");
    if (idx != -1) {
      computerId = computerId.substring(0, idx).trim();
    }
    String macAddress = null;
    idx = output.indexOf("MAC Address: ");
    if (idx != -1) {
      macAddress = output.substring(idx + 13);
      idx = macAddress.indexOf("MAC Address: ");
      if (idx != -1) {
        macAddress = macAddress.substring(0, idx).trim();
      }
    }
    if (computerId.trim().equals("0") || computerId.trim().equals("600000000")) {
      return macAddress;
    }
    return computerId;
  }

  // ---------------------------------------------------------------- non-Windows fallback

  /** Development fallback off Windows: first hardware address, formatted like ipconfig. */
  private static String javaMacAddress() {
    try {
      for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
        byte[] mac = ni.getHardwareAddress();
        if (mac != null && mac.length == 6 && !ni.isLoopback()) {
          List<String> parts = new ArrayList<>();
          for (byte b : mac) {
            parts.add(String.format(Locale.ROOT, "%02X", b));
          }
          return String.join("-", parts);
        }
      }
    } catch (IOException e) {
      log.warn("Could not read network interfaces: {}", e.getMessage());
    }
    return null;
  }

  // ---------------------------------------------------------------- helpers

  private static String safely(String what, Supplier<String> supplier) {
    try {
      String value = supplier.get();
      return value == null || value.isBlank() ? null : value;
    } catch (RuntimeException e) {
      log.warn("Could not determine {}: {}", what, e.getMessage());
      return null;
    }
  }

  private static void addIfPresent(Set<String> set, String value) {
    if (value != null && !value.isBlank()) {
      set.add(value);
    }
  }

  private static String run(List<String> command, Charset charset) {
    try {
      Process p = new ProcessBuilder(command).redirectErrorStream(true).start();
      byte[] out;
      try (InputStream in = p.getInputStream()) {
        out = in.readAllBytes();
      }
      p.waitFor(10, TimeUnit.SECONDS);
      return new String(out, charset);
    } catch (IOException e) {
      log.debug("Command {} failed: {}", command, e.getMessage());
      return null;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return null;
    }
  }

  /** Legacy {@code Exec.getOutputString()}: stdout lines joined with CRLF. */
  private static String runJoinedLines(List<String> command) {
    try {
      Process p = new ProcessBuilder(command).start();
      List<String> lines = new ArrayList<>();
      try (BufferedReader reader = new BufferedReader(
          new InputStreamReader(p.getInputStream(), Charset.defaultCharset()))) {
        String line;
        while ((line = reader.readLine()) != null) {
          lines.add(line);
        }
      }
      p.waitFor(10, TimeUnit.SECONDS);
      return String.join("\r\n", lines);
    } catch (IOException e) {
      log.debug("Command {} failed: {}", command, e.getMessage());
      return null;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return null;
    }
  }

  private static long crc32(Path file) {
    try {
      CRC32 crc = new CRC32();
      crc.update(Files.readAllBytes(file));
      return crc.getValue();
    } catch (IOException e) {
      return -1;
    }
  }

  private static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private record Ids(String networkCardGuid, String ipconfigMac, String motherboardId) {}
}
