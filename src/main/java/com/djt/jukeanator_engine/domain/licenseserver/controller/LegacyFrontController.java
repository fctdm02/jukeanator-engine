package com.djt.jukeanator_engine.domain.licenseserver.controller;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import com.djt.jukeanator_engine.domain.email.service.EmailService;
import com.djt.jukeanator_engine.domain.license.legacy.LegacyResponses;
import com.djt.jukeanator_engine.domain.license.legacy.LicenseTier;
import com.djt.jukeanator_engine.domain.licenseserver.config.LicenseServerProperties;
import com.djt.jukeanator_engine.domain.licenseserver.dto.LegacyClientRequest;
import com.djt.jukeanator_engine.domain.licenseserver.dto.ProvisionOutcome;
import com.djt.jukeanator_engine.domain.licenseserver.service.LicenseServerService;

/**
 * Drop-in replacement for the legacy jukeanator.com {@code FrontController} servlet (master mode).
 * Legacy MP3Jukeboxx installs hardcode {@code http://www.jukeanator.com/FrontController?command=…}
 * and compare replies byte for byte, so this controller reproduces the legacy URLs, parameter
 * decoding, content types and bodies exactly. jukeanator-engine clients use the same protocol.
 *
 * <p>Replies are written straight to the servlet response (no message converters) so Spring
 * cannot alter the content type, charset or body.
 */
@Controller
@ConditionalOnProperty(name = "app.mode", havingValue = "master")
public class LegacyFrontController {

  private static final Logger log = LoggerFactory.getLogger(LegacyFrontController.class);

  static final String LEGACY_JAR_NAME = "MP3Jukeboxx.jar";

  /** Legacy website page commands, now static pages ({@code command=order} → /order.html). */
  private static final Set<String> PAGE_COMMANDS = Set.of("features", "faq", "order", "contact",
      "licensing", "screenshots", "photos", "conversionkits", "comparison_matrix", "howto",
      "performing_upgrade", "privacy_policy", "return_policy", "terms_and_conditions",
      "webstart");

  private final LicenseServerService licenseServerService;
  private final LicenseServerProperties properties;
  private final EmailService emailService;

  public LegacyFrontController(LicenseServerService licenseServerService,
      LicenseServerProperties properties, EmailService emailService) {
    this.licenseServerService = licenseServerService;
    this.properties = properties;
    this.emailService = emailService;
  }

  @RequestMapping(path = {"/FrontController", "/servlet/FrontController"},
      method = {RequestMethod.GET, RequestMethod.POST})
  public void handle(HttpServletRequest request, HttpServletResponse response)
      throws IOException, ServletException {

    // Legacy quirk: a query string that itself embeds "/FrontController" is forwarded to it.
    String queryString = request.getQueryString();
    if (queryString != null) {
      int idx = queryString.indexOf("/FrontController");
      if (idx >= 0) {
        request.getRequestDispatcher(queryString.substring(idx)).forward(request, response);
        return;
      }
    }

    String rawCommand = request.getParameter("command");
    String command = rawCommand == null ? "home" : rawCommand.trim().toLowerCase(Locale.ROOT);

    switch (command) {
      case "provision" -> provision(request, response);
      case "ascap" -> writeText(response,
          licenseServerService.checkSubscription(clientRequest(request)));
      case "heartbeat" -> writeText(response,
          licenseServerService.heartbeat(clientRequest(request)));
      case "getcustomername" -> writeText(response,
          licenseServerService.getCustomerName(clientRequest(request)));
      case "getjukeanatorbuilddate" -> writeText(response, legacyJarBuildDate());
      case "getjukeanatorbuildsize" -> writeText(response, legacyJarBuildSize());
      case "getengineversion" -> writeText(response, engineVersion());
      case "feedback" -> feedback(request, response);
      default -> response.sendRedirect(PAGE_COMMANDS.contains(command) ? "/" + command + ".html"
          : "/");
    }
  }

  /** Old bookmarks and search results: {@code /features.jsp} → {@code /features.html}. */
  @GetMapping("/{page:[A-Za-z_]+}.jsp")
  public void legacyJsp(@PathVariable String page, HttpServletResponse response) {
    String p = page.toLowerCase(Locale.ROOT);
    String target = PAGE_COMMANDS.contains(p) ? "/" + p + ".html"
        : "feedback".equals(p) ? "/contact-thanks.html" : "/";
    response.setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
    response.setHeader("Location", target);
  }

  // ---------------------------------------------------------------- commands

  private void provision(HttpServletRequest request, HttpServletResponse response)
      throws IOException {

    // The legacy server URL-decoded the passcode twice more after the container's decode.
    String passcode = legacyDecode(legacyParam(request, "passcode"));
    ProvisionOutcome outcome = licenseServerService.provision(clientRequest(request), passcode);

    if (!outcome.isSuccess()) {
      writeText(response, outcome.message());
      return;
    }
    byte[] key = outcome.licenseKey();
    response.setContentType(LegacyResponses.BINARY_CONTENT_TYPE);
    response.setContentLength(key.length);
    try (OutputStream out = response.getOutputStream()) {
      out.write(key);
      out.flush();
    }
  }

  private void feedback(HttpServletRequest request, HttpServletResponse response)
      throws IOException {

    StringBuilder comments = new StringBuilder();
    Enumeration<String> names = request.getParameterNames();
    while (names.hasMoreElements()) {
      String name = names.nextElement();
      if ("command".equals(name)) {
        continue;
      }
      for (String value : request.getParameterValues(name)) {
        comments.append(name).append(": ").append(value).append('\n');
      }
    }
    emailService.sendContactFeedbackEmail(properties.getFeedbackRecipients(),
        request.getParameter("customerEmail"), comments.toString());
    response.sendRedirect("/contact-thanks.html");
  }

  /**
   * Legacy {@code GetJukeANatorBuildDateServerCommand}: {@code MM-dd-yyyy} of the legacy jar's
   * last-modified time ({@code 12-31-1969} if missing, as {@code new Date(0)} rendered in a US
   * zone).
   */
  String legacyJarBuildDate() {
    Path jar = legacyJarPath();
    long modified = 0;
    try {
      if (jar != null && Files.isRegularFile(jar)) {
        modified = Files.getLastModifiedTime(jar).toMillis();
      }
    } catch (IOException e) {
      log.warn("Could not read legacy jar timestamp {}: {}", jar, e.getMessage());
    }
    SimpleDateFormat format = new SimpleDateFormat("MM-dd-yyyy");
    format.setTimeZone(TimeZone.getTimeZone(properties.getTimeZone()));
    return format.format(new Date(modified));
  }

  /** Legacy {@code GetJukeANatorBuildSizeServerCommand}: the jar's length, or 0 if missing. */
  String legacyJarBuildSize() {
    Path jar = legacyJarPath();
    try {
      return jar != null && Files.isRegularFile(jar) ? Long.toString(Files.size(jar)) : "0";
    } catch (IOException e) {
      return "0";
    }
  }

  /** New command for engine clients: {@code <version>|<downloadUrl>}, empty if not configured. */
  private String engineVersion() {
    String version = properties.getLatestEngineVersion();
    if (version == null || version.isBlank()) {
      return "";
    }
    String url = properties.getEngineDownloadUrl() == null ? "" : properties.getEngineDownloadUrl();
    return version.trim() + "|" + url.trim();
  }

  private Path legacyJarPath() {
    if (properties.getLegacyJarPath() != null && !properties.getLegacyJarPath().isBlank()) {
      return Path.of(properties.getLegacyJarPath());
    }
    if (properties.getDownloadsDir() != null && !properties.getDownloadsDir().isBlank()) {
      return Path.of(properties.getDownloadsDir(), "temp", LEGACY_JAR_NAME);
    }
    return null;
  }

  // ---------------------------------------------------------------- legacy request/response

  private static LegacyClientRequest clientRequest(HttpServletRequest request) {
    return new LegacyClientRequest(
        legacyParam(request, "serialNumber"),
        LicenseTier.normalizeWireLicenseType(legacyParam(request, "licenseType")),
        legacyParam(request, "customerEmail"),
        legacyParam(request, "customerName"),
        orEmpty(legacyParam(request, "locationName")),
        orEmpty(legacyParam(request, "buildDate")),
        legacyParam(request, "appVersion"),
        legacyParam(request, "appMode"));
  }

  /**
   * Legacy {@code AbstractServerCommand}: every parameter was URL-decoded once more after the
   * container's own decoding. Engine clients double-encode their parameters to match.
   */
  static String legacyParam(HttpServletRequest request, String name) {
    return legacyDecode(request.getParameter(name));
  }

  static String legacyDecode(String value) {
    if (value == null) {
      return null;
    }
    try {
      return URLDecoder.decode(value, StandardCharsets.UTF_8);
    } catch (IllegalArgumentException e) {
      return value;
    }
  }

  private static String orEmpty(String s) {
    return s == null ? "" : s;
  }

  /**
   * Legacy text reply: {@code text/html;charset=ISO-8859-1}, body {@code URLEncoder.encode(text)}
   * plus {@code \n}.
   */
  static void writeText(HttpServletResponse response, String text) throws IOException {
    byte[] body = LegacyResponses.encodeText(text).getBytes(StandardCharsets.ISO_8859_1);
    response.setContentType(LegacyResponses.TEXT_CONTENT_TYPE);
    response.setContentLength(body.length);
    try (OutputStream out = response.getOutputStream()) {
      out.write(body);
      out.flush();
    }
  }
}
