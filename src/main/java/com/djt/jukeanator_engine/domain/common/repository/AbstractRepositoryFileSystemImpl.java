package com.djt.jukeanator_engine.domain.common.repository;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.djt.jukeanator_engine.domain.common.model.utils.ObjectMappers;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.deser.DeserializationProblemHandler;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;

public class AbstractRepositoryFileSystemImpl {

  private final AtomicInteger nextPersistentIdentityValue = new AtomicInteger(0);

  /**
   * Returns the next unique persistent identity for this repository instance. Must be seeded via
   * {@link #seedNextPersistentIdentityFrom(Stream)} (typically at the end of a load) before being
   * used to mint identities for newly-created entities, otherwise numbering restarts at 1 and can
   * collide with previously-persisted identities.
   */
  synchronized protected Integer getNextPersistentIdentityValue() {
    return Integer.valueOf(nextPersistentIdentityValue.incrementAndGet());
  }

  /**
   * Seeds this repository's identity counter from the maximum identity found among
   * {@code existingIds} (e.g. entities just loaded from disk), so subsequently minted identities
   * never collide with ones already persisted.
   */
  protected void seedNextPersistentIdentityFrom(Stream<Integer> existingIds) {

    int max = existingIds
        .filter(Objects::nonNull)
        .mapToInt(Integer::intValue)
        .max()
        .orElse(0);

    nextPersistentIdentityValue.updateAndGet(current -> Math.max(current, max));
  }

  protected static boolean USE_PRETTY_PRINT = true;
  public static boolean getPrettyPrint() {
    return USE_PRETTY_PRINT;
  }
  public static void setPrettyPrint(boolean prettyPrint) {
    USE_PRETTY_PRINT = prettyPrint;
  }

  private static final Logger LOG = LoggerFactory.getLogger(AbstractRepositoryFileSystemImpl.class);

  /**
   * Unknown properties already reported, keyed by {@code "<class>.<property>"}, so a stale field
   * repeated across thousands of entries in one file is only logged once.
   */
  private static final Set<String> REPORTED_UNKNOWN_PROPERTIES = ConcurrentHashMap.newKeySet();

  /**
   * A dedicated mapper (not the shared {@link ObjectMappers#create()} instance, which also backs
   * the REST layer) for on-disk JSON that may predate the current DTO shapes:
   * <ul>
   * <li>Properties that no longer exist on the target DTO (e.g. after a field is removed or split)
   * are skipped with a warning; the stale property is dropped the next time the file is
   * written.</li>
   * <li>Properties the DTO expects but the file lacks (e.g. after a field is added) fail the read
   * via {@link DeserializationFeature#FAIL_ON_MISSING_CREATOR_PROPERTIES}, so a partially
   * populated aggregate is never loaded. All persisted DTOs are records and nulls are always
   * written explicitly, so every expected property is present in a current-format file.
   * {@link #readJson} and {@link #readJsonList} treat such a file as stale (see
   * {@link #quarantineStaleFile}).</li>
   * </ul>
   */
  protected static final ObjectMapper MAPPER = createFileSystemMapper();

  private static ObjectMapper createFileSystemMapper() {

    ObjectMapper m = ObjectMappers.createNew();
    m.enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES);
    m.addHandler(new DeserializationProblemHandler() {
      @Override
      public boolean handleUnknownProperty(DeserializationContext ctxt, JsonParser p,
          JsonDeserializer<?> deserializer, Object beanOrClass, String propertyName)
          throws IOException {

        Class<?> type = (beanOrClass instanceof Class<?> c) ? c : beanOrClass.getClass();
        if (REPORTED_UNKNOWN_PROPERTIES.add(type.getName() + "." + propertyName)) {
          LOG.warn("Ignoring unknown JSON property '{}' for {} (stale file format?); it will be "
              + "dropped on the next save.", propertyName, type.getSimpleName());
        }
        p.skipChildren();
        return true;
      }
    });
    return m;
  }

  protected static final ObjectWriter OBJECT_WRITER = MAPPER.writer();

  protected static final ObjectWriter OBJECT_WRITER_WITH_PRETTY_PRINTER =
      MAPPER.writerWithDefaultPrettyPrinter();

  protected static final ObjectWriter getObjectWriter() {
    if  (USE_PRETTY_PRINT) {
      return OBJECT_WRITER_WITH_PRETTY_PRINTER;
    }
    return OBJECT_WRITER;
  }

  /**
   * Reads a JSON array file into a list of the given type. Returns an empty list, rather than
   * throwing, when the file does not yet exist so first-run bootstrap doesn't require special
   * casing by callers, or when the file is in a stale format (see {@link #quarantineStaleFile}).
   */
  protected <T> List<T> readJsonList(String filePath, TypeReference<List<T>> typeReference) {

    Path path = Path.of(filePath);
    if (!Files.exists(path)) {
      return new ArrayList<>();
    }

    try {
      List<T> result = MAPPER.readValue(path.toFile(), typeReference);
      return (result != null) ? result : new ArrayList<>();
    } catch (MismatchedInputException mie) {
      quarantineStaleFile(path, mie);
      return new ArrayList<>();
    } catch (IOException ioe) {
      throw new UncheckedIOException("Could not read JSON list from file: " + filePath, ioe);
    }
  }

  /**
   * Writes {@code items} to disk as a JSON array (pretty-printed when {@link #USE_PRETTY_PRINT}
   * is enabled), overwriting any existing file.
   */
  protected <T> void writeJsonList(String filePath, List<T> items) {

    try {
      getObjectWriter().writeValue(Path.of(filePath).toFile(), items);
    } catch (IOException ioe) {
      throw new UncheckedIOException("Could not write JSON list to file: " + filePath, ioe);
    }
  }

  /**
   * Reads a single JSON object file into an instance of the given type. Returns {@code null},
   * rather than throwing, when the file does not yet exist so callers can distinguish first-run
   * bootstrap from a genuine read failure. A file in a stale format is handled the same way as a
   * missing one (see {@link #quarantineStaleFile}).
   */
  protected <T> T readJson(String filePath, Class<T> type) {

    Path path = Path.of(filePath);
    if (!Files.exists(path)) {
      return null;
    }

    try {
      return MAPPER.readValue(path.toFile(), type);
    } catch (MismatchedInputException mie) {
      quarantineStaleFile(path, mie);
      return null;
    } catch (IOException ioe) {
      throw new UncheckedIOException("Could not read JSON from file: " + filePath, ioe);
    }
  }

  /**
   * Handles a file that is well-formed JSON but does not match the current DTO shape (an expected
   * property is missing, a value has the wrong type, or the file is empty). Rather than load an
   * incomplete aggregate, the caller starts from empty; the stale file is first renamed to
   * {@code <name>.stale-<timestamp>} so its contents are preserved for manual recovery instead of
   * being overwritten by the next save. Syntactically malformed JSON (e.g. a truncated write) is
   * not treated as stale and still fails the read, since that indicates corruption rather than an
   * outdated format.
   *
   * @throws UncheckedIOException if the stale file cannot be renamed, so its contents are never
   *         silently overwritten
   */
  private static void quarantineStaleFile(Path path, MismatchedInputException cause) {

    Path backup = path.resolveSibling(
        path.getFileName() + ".stale-" + LocalDateTime.now().format(STALE_SUFFIX_FORMAT));
    try {
      Files.move(path, backup);
    } catch (IOException ioe) {
      UncheckedIOException e = new UncheckedIOException(
          "Could not read JSON from file (stale format) or move it aside: " + path, ioe);
      e.addSuppressed(cause);
      throw e;
    }
    LOG.warn("JSON file {} does not match the current format ({}); moved it to {} and starting "
        + "from empty.", path, cause.getOriginalMessage(), backup.getFileName());
  }

  private static final DateTimeFormatter STALE_SUFFIX_FORMAT =
      DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

  /**
   * Writes {@code item} to disk as a single JSON object (pretty-printed when
   * {@link #USE_PRETTY_PRINT} is enabled), overwriting any existing file.
   */
  protected <T> void writeJson(String filePath, T item) {

    try {
      getObjectWriter().writeValue(Path.of(filePath).toFile(), item);
    } catch (IOException ioe) {
      throw new UncheckedIOException("Could not write JSON to file: " + filePath, ioe);
    }
  }

  /**
   * Appends {@code item} as one compact JSON line to {@code filePath}, creating the file (and any
   * missing parent directories) if it doesn't exist yet. Unlike {@link #writeJsonList}, this never
   * reads or rewrites what's already on disk -- each call is a single {@code O(1)} append, which
   * matters for high-frequency, ever-growing logs (e.g. user activity tracking) where a whole-file
   * rewrite per event would not scale. Always compact (one JSON object per line), regardless of
   * {@link #USE_PRETTY_PRINT}, since pretty-printing would break the one-object-per-line format.
   */
  protected <T> void appendJsonLine(String filePath, T item) {

    Path path = Path.of(filePath);
    try {
      if (path.getParent() != null) {
        Files.createDirectories(path.getParent());
      }
      String line = OBJECT_WRITER.writeValueAsString(item) + System.lineSeparator();
      Files.writeString(path, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
          StandardOpenOption.APPEND);
    } catch (IOException ioe) {
      throw new UncheckedIOException("Could not append JSON line to file: " + filePath, ioe);
    }
  }

  protected String basePath;

  public AbstractRepositoryFileSystemImpl() {
    this(null);
  }

  public AbstractRepositoryFileSystemImpl(String basePath) {
    super();
    if (basePath != null) {
      this.basePath = basePath;
    } else {
      this.basePath = System.getProperty("user.home") + "/";
    }
  }

  public String basePath() {
    return basePath;
  }

  public void setBasePath(String basePath) {
    this.basePath = basePath;
  }
}
