package com.djt.jukeanator_engine.domain.common.repository;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import com.djt.jukeanator_engine.domain.common.model.utils.ObjectMappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

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

  protected static final ObjectMapper MAPPER = ObjectMappers.create();

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
   * casing by callers.
   */
  protected <T> List<T> readJsonList(String filePath, TypeReference<List<T>> typeReference) {

    Path path = Path.of(filePath);
    if (!Files.exists(path)) {
      return new ArrayList<>();
    }

    try {
      List<T> result = MAPPER.readValue(path.toFile(), typeReference);
      return (result != null) ? result : new ArrayList<>();
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
