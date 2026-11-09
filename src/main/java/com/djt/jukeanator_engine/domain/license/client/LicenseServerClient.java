package com.djt.jukeanator_engine.domain.license.client;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.djt.jukeanator_engine.domain.license.config.LicenseProperties;
import com.djt.jukeanator_engine.domain.license.legacy.LegacyResponses;

/**
 * Talks to the license server with the legacy {@code /FrontController} protocol (the same one
 * MP3Jukeboxx uses), so a single server code path serves both applications.
 *
 * <p>The server URL-decodes every parameter once more after the servlet container has, so each
 * value is encoded twice here. Engine-only parameters ({@code appVersion}, {@code appMode}) are
 * ignored by older servers.
 */
public class LicenseServerClient {

  private final LicenseProperties properties;
  private final RestClient restClient;
  private final String appVersion;
  private final String appMode;
  private final String buildDate;

  public LicenseServerClient(LicenseProperties properties, String appVersion, String appMode) {
    this.properties = properties;
    this.appVersion = appVersion;
    this.appMode = appMode;
    this.buildDate = legacyBuildDate();
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(properties.getConnectTimeout());
    factory.setReadTimeout(properties.getReadTimeout());
    this.restClient = RestClient.builder().requestFactory(factory).build();
  }

  /** Common identification parameters, in the legacy client's order. */
  public record ClientIdentity(
      int licenseTypeWireCode,
      String serialNumber,
      String customerEmail,
      String customerName,
      String locationName) {}

  /** Provisioning reply: the 16 key bytes, or the server's refusal text. */
  public record ProvisionReply(byte[] licenseKey, String message) {
    public boolean hasKey() {
      return licenseKey != null && licenseKey.length == 16;
    }
  }

  /** {@code command=provision}. */
  public ProvisionReply provision(String customerEmail, String passcodeSha1, String serialNumber,
      String locationName) {

    Map<String, String> params = new LinkedHashMap<>();
    params.put("customerEmail", customerEmail);
    params.put("passcode", passcodeSha1);
    params.put("serialNumber", serialNumber);
    params.put("buildDate", buildDate);
    params.put("locationName", locationName);
    addEngineParams(params);

    ResponseEntity<byte[]> response = restClient.get()
        .uri(buildUri("provision", params))
        .retrieve()
        .toEntity(byte[].class);
    byte[] body = response.getBody() == null ? new byte[0] : response.getBody();
    MediaType type = response.getHeaders().getContentType();
    if (type != null && MediaType.APPLICATION_OCTET_STREAM.includes(type)) {
      return new ProvisionReply(body, null);
    }
    return new ProvisionReply(null,
        LegacyResponses.decodeText(new String(body, StandardCharsets.ISO_8859_1)));
  }

  /** {@code command=ascap} (subscription check). Returns the decoded reply text. */
  public String checkSubscription(ClientIdentity identity) {
    return getText("ascap", identityParams(identity));
  }

  /** {@code command=heartbeat}. Returns the decoded reply text. */
  public String heartbeat(ClientIdentity identity) {
    return getText("heartbeat", identityParams(identity));
  }

  /** {@code command=getcustomername}. Returns the decoded reply text. */
  public String getCustomerName(ClientIdentity identity) {
    return getText("getcustomername", identityParams(identity));
  }

  /** {@code command=getengineversion}: {@code <version>|<url>}, or empty. */
  public String getEngineVersion() {
    Map<String, String> params = new LinkedHashMap<>();
    addEngineParams(params);
    return getText("getengineversion", params);
  }

  // ---------------------------------------------------------------- helpers

  private static final DateTimeFormatter LEGACY_BUILD_DATE =
      DateTimeFormatter.ofPattern("MM-dd-yyyy", Locale.US);

  /**
   * The legacy {@code buildDate} parameter: {@code MM-dd-yyyy}, as MP3Jukeboxx sends it. The legacy
   * server stores it in {@code CUSTOMER_SERIAL.BUILD_DATE VARCHAR(10) NOT NULL}, so a longer value
   * (such as the engine version) could make its insert fail. Uses the date of the running jar / war,
   * else today. The engine version travels separately as {@code appVersion}.
   */
  static String legacyBuildDate() {
    LocalDate date = LocalDate.now();
    try {
      var source = LicenseServerClient.class.getProtectionDomain().getCodeSource();
      if (source != null && source.getLocation() != null
          && "file".equals(source.getLocation().getProtocol())) {
        Path path = Path.of(source.getLocation().toURI());
        if (Files.isRegularFile(path)) {
          date = LocalDate.ofInstant(Files.getLastModifiedTime(path).toInstant(),
              ZoneId.systemDefault());
        }
      }
    } catch (Exception e) {
      // Fall back to today's date.
    }
    return LEGACY_BUILD_DATE.format(date);
  }

  private Map<String, String> identityParams(ClientIdentity identity) {
    Map<String, String> params = new LinkedHashMap<>();
    params.put("licenseType", Integer.toString(identity.licenseTypeWireCode()));
    params.put("serialNumber", identity.serialNumber());
    params.put("buildDate", buildDate);
    params.put("customerEmail", identity.customerEmail());
    params.put("customerName", identity.customerName());
    params.put("locationName", identity.locationName());
    addEngineParams(params);
    return params;
  }

  private void addEngineParams(Map<String, String> params) {
    params.put("appVersion", appVersion);
    params.put("appMode", appMode);
  }

  private String getText(String command, Map<String, String> params) {
    String body = restClient.get()
        .uri(buildUri(command, params))
        .retrieve()
        .body(String.class);
    return LegacyResponses.decodeText(body);
  }

  /**
   * Builds the URL by hand (a {@link URI}, so RestClient applies no template encoding of its
   * own): blank parameters are omitted, as the legacy client did.
   */
  URI buildUri(String command, Map<String, String> params) {
    String base = properties.getServerUrl() == null ? "" : properties.getServerUrl().trim();
    if (base.isEmpty()) {
      throw new RestClientException("license.server-url is not configured");
    }
    if (base.endsWith("/")) {
      base = base.substring(0, base.length() - 1);
    }
    StringJoiner query = new StringJoiner("&");
    query.add("command=" + command);
    params.forEach((name, value) -> {
      if (value != null && !value.isBlank()) {
        query.add(name + "=" + LegacyResponses.urlEncode(LegacyResponses.urlEncode(value)));
      }
    });
    return URI.create(base + "/FrontController?" + query);
  }
}
