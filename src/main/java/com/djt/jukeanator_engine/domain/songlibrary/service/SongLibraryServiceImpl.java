package com.djt.jukeanator_engine.domain.songlibrary.service;

import static java.util.Objects.requireNonNull;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import com.djt.jukeanator_engine.config.AppProperties;
import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;
import com.djt.jukeanator_engine.domain.common.service.AggregateRootService;
import com.djt.jukeanator_engine.domain.common.service.command.model.CommandRequest;
import com.djt.jukeanator_engine.domain.common.service.command.model.CommandResponse;
import com.djt.jukeanator_engine.domain.common.service.query.model.QueryRequest;
import com.djt.jukeanator_engine.domain.common.service.query.model.QueryResponse;
import com.djt.jukeanator_engine.domain.common.service.query.model.QueryResponseItem;
import com.djt.jukeanator_engine.domain.location.model.LocationEntity;
import com.djt.jukeanator_engine.domain.location.service.LocationService;
import com.djt.jukeanator_engine.domain.songlibrary.dto.AlbumDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.AlbumMetadataDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.ArtistDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.AuthenticateForAdminPanelRequest;
import com.djt.jukeanator_engine.domain.songlibrary.dto.DownloadAlbumCoverArtRequest;
import com.djt.jukeanator_engine.domain.songlibrary.dto.GenreDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.ScanRequest;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SearchResultDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SongDto;
import com.djt.jukeanator_engine.domain.songlibrary.event.ScanFileSystemForSongsEvent;
import com.djt.jukeanator_engine.domain.songlibrary.event.SongStatisticsChangedEvent;
import com.djt.jukeanator_engine.domain.songlibrary.exception.SongLibraryServiceException;
import com.djt.jukeanator_engine.domain.songlibrary.exception.SongScanFailedException;
import com.djt.jukeanator_engine.domain.songlibrary.mapper.SongLibraryMapper;
import com.djt.jukeanator_engine.domain.songlibrary.model.AlbumFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.ArtistFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.GenreFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.LibraryItem;
import com.djt.jukeanator_engine.domain.songlibrary.model.RootFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.SongFileEntity;
import com.djt.jukeanator_engine.domain.songlibrary.repository.SongLibraryObjectPersistor;
import com.djt.jukeanator_engine.domain.songlibrary.repository.SongLibraryRepository;
import com.djt.jukeanator_engine.domain.songlibrary.repository.SongLibraryRepositoryFileSystemImpl;
import com.djt.jukeanator_engine.domain.songlibrary.service.utils.SongLibraryStructuralComparator;
import com.djt.jukeanator_engine.domain.songlibrary.service.utils.SongScanner;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongQueueEntryDto;
import com.djt.jukeanator_engine.domain.songqueue.event.MultipleSongsAddedToQueueEvent;
import com.djt.jukeanator_engine.domain.songqueue.event.SongAddedToQueueEvent;

/**
 * @author tmyers
 */
public class SongLibraryServiceImpl
    implements SongLibraryService, AggregateRootService<RootFolderEntity> {

  private static final Logger log = LoggerFactory.getLogger(SongLibraryServiceImpl.class);

  private static final String VALID_KEYBOARD_CHARACTERS =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZ" + "0123456789" + "',. !@#$%^&*\"()[]/\\\\?:;";

  private final ApplicationEventPublisher eventPublisher;
  private final String dataDir;
  private final SongLibraryRepository songLibraryRepository;
  private final LocationService locationService;
  private final SongScanner songScanner;
  private final Integer searchResultSize;
  private final boolean isMaster;
  private final boolean jpaRepositoryType;

  // This instance's own initial guess at its locationId (app.location-id) -- passed to
  // LocationService.getOrCreateOwnLocation() the first time this instance ever boots, so a slave's
  // local LocationEntity is created with the same id master already assigned it. Standalone mode
  // leaves this null, letting LocationService auto-generate one instead.
  private final Integer configuredLocationId;

  // Every location's loaded RootFolderEntity, keyed by locationId. On standalone/slave this always
  // has exactly one entry (this instance's own location, == ownRoot below). On master, entries are
  // populated lazily on demand as locations are browsed (see getOrLoadRoot) -- master never holds
  // every registered location in memory up front.
  private final Map<Integer, RootFolderEntity> songLibraryRoots = new HashMap<>();

  // This instance's own location and its root -- used by the admin/scan methods below, which are
  // inherently local to whichever instance owns the physical library and never take a locationId
  // parameter. Both stay null / an empty placeholder root on master, which owns no location of its
  // own.
  private LocationEntity ownLocation;
  private RootFolderEntity ownRoot;

  private boolean isInitialized;
  private boolean libraryLoadFailedAtStartup;

  public SongLibraryServiceImpl(AppProperties appProperties,
      SongLibraryRepository songLibraryRepository, LocationService locationService,
      SongScanner songScanner, Integer searchResultSize, ApplicationEventPublisher eventPublisher) {

    requireNonNull(appProperties, "appProperties cannot be null");
    requireNonNull(songLibraryRepository, "songLibraryRepository cannot be null");
    requireNonNull(locationService, "locationService cannot be null");
    requireNonNull(songScanner, "songScanner cannot be null");
    requireNonNull(searchResultSize, "searchResultSize cannot be null");
    requireNonNull(eventPublisher, "eventPublisher cannot be null");

    this.dataDir = appProperties.getDataDir();
    this.songLibraryRepository = songLibraryRepository;
    this.locationService = locationService;
    this.songScanner = songScanner;
    this.searchResultSize = searchResultSize;
    this.eventPublisher = eventPublisher;
    this.isMaster = appProperties.isMaster();
    this.jpaRepositoryType = "jpa".equals(appProperties.getRepositoryType());
    this.configuredLocationId = appProperties.getLocationId();

    // Initialize the song library
    initialize();

    // If the CD Stats file was hand-edited (e.g. to tweak song play counts for manual testing)
    // after the song library was last persisted, restore statistics from it now so the edits
    // take effect at startup. Not applicable under JPA, where num-plays live in song_library
    // and are always current -- CDStats.TXT there is only a migrate-back-to-filesystem backup
    // (see storeSongLibraryAndStatistics()), never a source of truth to restore from.
    if (!this.jpaRepositoryType) {
      restoreSongStatisticsIfCdStatsFileIsNewer();
    }
  }

  public void initialize() {

    if (this.isMaster) {

      // Master is location-agnostic and owns no library of its own -- every location's library is
      // loaded on demand via getOrLoadRoot(locationId) once it's been synced (see
      // LocationServiceImpl). ownRoot stays an empty, never-persisted placeholder purely so
      // admin/scan methods (which are never valid on master) fail with a clear error instead of an
      // NPE if ever mistakenly invoked here.
      this.ownRoot = new RootFolderEntity("");
      this.ownRoot.initialize();
      this.ownLocation = null;
      this.isInitialized = true;
      this.libraryLoadFailedAtStartup = false;
      log.info("Master instance -- no own song library; locations are loaded on demand.");
      return;
    }

    this.ownLocation = this.locationService.getOrCreateOwnLocation(this.configuredLocationId);

    // If we cannot load the song library from disk at startup, then assume a new install and
    // return an empty ownRoot folder. The application will automatically ask the user to scan for
    // songs at startup.
    try {

      this.ownRoot = loadOwnRoot();
      this.libraryLoadFailedAtStartup = false;

    } catch (EntityDoesNotExistException ednee) {

      log.error("Could not load song library from dataDir: " + this.dataDir
          + ", using empty song library ownRoot for now, error: " + ednee.getMessage());

      // No rootPath is known yet -- this placeholder is never persisted, and its parentLocation
      // (which would require disk I/O against a real rootPath if it were the old metadata object)
      // is still wired below so query methods don't NPE before the user completes the initial scan.
      this.ownRoot = new RootFolderEntity("");
      this.ownRoot.initialize();
      this.libraryLoadFailedAtStartup = true;
    }

    this.ownRoot.setParentLocation(this.ownLocation);
    this.ownLocation.setLocationSongLibraryRoot(this.ownRoot);
    this.songLibraryRoots.put(this.ownLocation.getPersistentIdentity(), this.ownRoot);

    this.isInitialized = true;

    if (this.libraryLoadFailedAtStartup) {
      log.info("locationName: <none, awaiting initial scan>");
      log.info("rootPath: <none, awaiting initial scan>");
    } else {
      log.info("locationName: " + this.ownRoot.getLocationName());
      log.info("rootPath: " + this.ownRoot.getRootPath());
    }
    log.info("searchResultSize: " + this.searchResultSize);
  }

  /**
   * Resolves the {@link RootFolderEntity} for {@code locationId}, loading it from the repository
   * and caching it in {@link #songLibraryRoots} on first use if not already resident.
   */
  private RootFolderEntity getOrLoadRoot(Integer locationId) {

    // Objects.equals (not a plain non-null check) so this also matches when both are null -- only
    // master has ownLocation == null now (see initialize()'s LocationService-backed bootstrap,
    // which guarantees standalone/slave always resolves a real ownLocation, even before its first
    // scan completes). Master's null case must still short-circuit to the in-memory ownRoot
    // placeholder rather than fall through to a repository load keyed by a locationId that isn't
    // real.
    if (Objects.equals(locationId, getOwnLocationId()) && this.ownRoot != null) {
      return this.ownRoot;
    }

    RootFolderEntity cached = this.songLibraryRoots.get(locationId);
    if (cached != null) {
      return cached;
    }

    try {
      RootFolderEntity loaded =
          this.songLibraryRepository.loadAggregateRoot(locationId == null ? 0 : locationId.intValue());
      loaded.initialize();
      this.songLibraryRoots.put(locationId, loaded);
      return loaded;
    } catch (EntityDoesNotExistException ednee) {
      throw new SongLibraryServiceException(
          "No song library found for locationId: [" + locationId + "]", ednee);
    }
  }

  private void requireNotMaster() {
    if (this.isMaster) {
      throw new SongLibraryServiceException(
          "Admin/scan methods are not supported on the master instance -- they are inherently local "
              + "to the slave that owns the library.");
    }
  }

  /**
   * Resolves this instance's own song library root from {@link #songLibraryRepository}. Filesystem
   * mode has no id-keyed storage -- {@code loadAggregateRoot(int)} always throws there -- so it must
   * be looked up by the name derived from a leftover {@code .oos} file when one exists. JPA mode is
   * the opposite: it's keyed by locationId, so a leftover {@code .oos} file (e.g. from before this
   * instance was migrated from filesystem to JPA) is only relevant as a fallback if the database has
   * no row yet -- see {@link #adoptLocalOosFileIntoJpaStore}.
   */
  private RootFolderEntity loadOwnRoot() throws EntityDoesNotExistException {

    if (!this.jpaRepositoryType) {

      Path oosFile = findMostRecentOosFile().orElse(null);

      return (oosFile != null)
          ? this.songLibraryRepository.loadAggregateRoot(deriveLocationNameFromOosFilename(oosFile))
          : this.songLibraryRepository.loadAggregateRoot(this.ownLocation.getPersistentIdentity());
    }

    try {
      return this.songLibraryRepository.loadAggregateRoot(this.ownLocation.getPersistentIdentity());
    } catch (EntityDoesNotExistException ednee) {
      return adoptLocalOosFileIntoJpaStore(ednee);
    }
  }

  /**
   * Called when JPA mode finds no database row for this location -- most commonly because this
   * {@code dataDir} was previously run in filesystem mode, which left a {@code .oos} file behind. If
   * such a file exists, deserialize it, store it into the JPA repository, then perform the same
   * store/reload structural round-trip check {@link #scanFileSystemForSongs(ScanRequest)} performs
   * after a fresh scan -- so a corrupt or incompatible local file fails startup loudly instead of
   * silently adopting bad data. If no local {@code .oos} file exists either, rethrow {@code
   * notFound} so the caller falls back to the empty-placeholder/prompt-for-scan path, same as a
   * genuinely new install.
   */
  private RootFolderEntity adoptLocalOosFileIntoJpaStore(EntityDoesNotExistException notFound)
      throws EntityDoesNotExistException {

    Path oosFile = findMostRecentOosFile().orElse(null);
    if (oosFile == null) {
      throw notFound;
    }

    RootFolderEntity localRoot;
    try {
      localRoot = new SongLibraryObjectPersistor().loadSongLibraryFromDisk(oosFile.toString());
      localRoot.initialize();
    } catch (ClassNotFoundException | IOException | RuntimeException e) {
      log.warn("Found local song library file {} while looking for locationId [{}] in the JPA "
          + "repository, but could not read it -- ignoring it.", oosFile,
          this.ownLocation.getPersistentIdentity(), e);
      throw notFound;
    }

    log.info("No song library found in the database for locationId [{}], but found a local song "
        + "library file at {} -- adopting it into the database.",
        this.ownLocation.getPersistentIdentity(), oosFile);

    localRoot.setParentLocation(this.ownLocation);
    this.songLibraryRepository.storeAggregateRoot(localRoot);

    RootFolderEntity rehydratedRoot;
    try {
      rehydratedRoot =
          this.songLibraryRepository.loadAggregateRoot(this.ownLocation.getPersistentIdentity());
    } catch (EntityDoesNotExistException ednee) {
      throw new SongLibraryServiceException(
          "Adopted local song library file " + oosFile + " into the database for locationId ["
              + this.ownLocation.getPersistentIdentity()
              + "] but could not reload it immediately after storing it.", ednee);
    }

    List<String> differences =
        SongLibraryStructuralComparator.findDifferences(localRoot, rehydratedRoot);
    if (!differences.isEmpty()) {
      throw new SongLibraryServiceException(
          "Adopted local song library file " + oosFile + " into the database for locationId ["
              + this.ownLocation.getPersistentIdentity()
              + "], but the tree reloaded from the database does not match the local copy: "
              + differences);
    }

    return rehydratedRoot;
  }

  /**
   * Lists the {@code .oos} files directly under {@link #dataDir} and returns the most recently
   * modified one, if any. Under normal slave/standalone operation there should be at most one.
   */
  private Optional<Path> findMostRecentOosFile() {

    List<Path> oosFiles = new ArrayList<>();

    try (DirectoryStream<Path> stream = Files.newDirectoryStream(Path.of(this.dataDir),
        "*" + SongLibraryRepository.OOS_FILE_EXTENSION)) {

      for (Path candidate : stream) {
        if (Files.isRegularFile(candidate)) {
          oosFiles.add(candidate);
        }
      }

    } catch (IOException e) {
      return Optional.empty();
    }

    return oosFiles.stream().max(Comparator.comparing(this::lastModifiedTimeOrEpoch));
  }

  private FileTime lastModifiedTimeOrEpoch(Path path) {
    try {
      return Files.getLastModifiedTime(path);
    } catch (IOException e) {
      return FileTime.fromMillis(0L);
    }
  }

  /**
   * Derives a location name from a persisted {@code .oos} filename by stripping the extension and
   * replacing underscores with spaces -- the inverse of
   * {@code SongLibraryRepositoryFileSystemImpl.sanitizeLocationNameForFilename}, which replaces
   * spaces (and other filesystem-unsafe characters) with underscores when writing. E.g.
   * {@code Location_Name.oos} -> {@code "Location Name"}.
   */
  private String deriveLocationNameFromOosFilename(Path oosFile) {

    String filename = oosFile.getFileName().toString();
    String stem = filename.substring(0,
        filename.length() - SongLibraryRepository.OOS_FILE_EXTENSION.length());

    return stem.replace('_', ' ');
  }

  private void restoreSongStatisticsIfCdStatsFileIsNewer() {

    String resolvedSongLibraryPath = this.songLibraryRepository.getResolvedFilePath();
    if (resolvedSongLibraryPath == null) {
      return;
    }

    Path cdStatsPath = Path.of(this.dataDir, RootFolderEntity.CD_STATS);
    Path songLibraryPath = Path.of(resolvedSongLibraryPath);

    if (!Files.exists(cdStatsPath) || !Files.exists(songLibraryPath)) {
      return;
    }

    try {

      FileTime cdStatsLastModified = Files.getLastModifiedTime(cdStatsPath);
      FileTime songLibraryLastModified = Files.getLastModifiedTime(songLibraryPath);

      if (cdStatsLastModified.compareTo(songLibraryLastModified) > 0) {

        log.info(
            "CD stats file {} was modified after {} was last persisted, restoring song statistics from it.",
            cdStatsPath, songLibraryPath);

        restoreSongStatistics(cdStatsPath.toString());
      }

    } catch (IOException e) {
      log.warn("Could not compare last modified timestamps of {} and {}", cdStatsPath,
          songLibraryPath, e);
    }
  }

  // Service methods
  // USER ROLE METHODS
  @Override
  public SearchResultDto getMusicByPopularity(Integer locationId) {

    return getMusic(getOrLoadRoot(locationId), null, null, SortOrder.POPULARITY);
  }

  /** Controls how results returned from {@link #getMusic} are ordered. */
  private enum SortOrder {
    POPULARITY, TITLE
  }

  private String stripNonKeyboardCharacters(String value) {

    if (value == null || value.isBlank()) {
      return value;
    }

    StringBuilder result = new StringBuilder(value.length());

    for (char c : value.toCharArray()) {
      if (VALID_KEYBOARD_CHARACTERS.indexOf(Character.toUpperCase(c)) >= 0) {
        result.append(c);
      }
    }

    return result.toString();
  }

  @Override
  public SearchResultDto getMusicBySearch(Integer locationId, String searchFor) {
    return getMusicBySearch(locationId, searchFor, searchResultSize);
  }

  @Override
  public SearchResultDto getMusicBySearch(Integer locationId, String searchFor, int limit) {

    if (!isInitialized) {
      throw new SongLibraryServiceException("SongLibraryService has not been initialized yet!");
    }

    if (searchFor == null || searchFor.strip().isEmpty()) {
      return new SearchResultDto(List.of(), List.of(), List.of(), 0, 0, 0);
    }

    String searchForNormalized = stripNonKeyboardCharacters(searchFor.strip().toLowerCase());

    return getMusic(getOrLoadRoot(locationId), null, searchForNormalized, SortOrder.POPULARITY, limit);
  }

  @Override
  public SearchResultDto getGenreMusicByPopularity(Integer locationId, String genreName) {

    return getMusic(getOrLoadRoot(locationId), genreName, null, SortOrder.POPULARITY);
  }

  @Override
  public SearchResultDto getGenreMusicByTitle(Integer locationId, String genreName) {

    return getMusic(getOrLoadRoot(locationId), genreName, null, SortOrder.TITLE);
  }

  /**
   * Central query worker for all music-retrieval service methods.
   *
   * <p>
   * Sort order:
   * <ul>
   * <li>{@link SortOrder#POPULARITY} — highest play count first; search results additionally weight
   * exact/prefix/suffix/contains matches above raw popularity. If weights match, higher play counts
   * break the tie.</li>
   * <li>{@link SortOrder#TITLE} — ascending alphabetical on {@link LibraryItem#getTitle()}.</li>
   * </ul>
   */
  private SearchResultDto getMusic(RootFolderEntity root, String genreName, String searchFor,
      SortOrder sortOrder) {
    return getMusic(root, genreName, searchFor, sortOrder, searchResultSize);
  }

  private SearchResultDto getMusic(RootFolderEntity root, String genreName, String searchFor,
      SortOrder sortOrder, int limit) {

    if (!isInitialized) {
      throw new SongLibraryServiceException("SongLibraryService has not been initialized yet!");
    }

    // ── Filters ───────────────────────────────────────────────────────────

    java.util.function.Predicate<LibraryItem> hasPlays =
        (genreName != null || searchFor != null) ? item -> true
            : item -> item.getNumPlays() != null && item.getNumPlays() > 0;

    java.util.function.Predicate<LibraryItem> inGenre =
        item -> genreName == null || genreName.equalsIgnoreCase(item.getParentGenre().getName());

    java.util.function.Predicate<LibraryItem> matchesSearch = item -> {
      if (searchFor == null)
        return true;
      return calculateSearchResultWeight(item.getTitle(), searchFor) > 0;
    };

    // ── Comparators ───────────────────────────────────────────────────────

    Comparator<LibraryItem> comparator = switch (sortOrder) {

      case TITLE -> Comparator.comparing(LibraryItem::getTitle,
          Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));

      case POPULARITY -> {
        if (searchFor != null) {
          // Rule: Primary sort is match quality (descending).
          // Secondary tiebreaker sort is play count (descending, pushing nulls/unplayed to the
          // bottom).
          yield Comparator
              .comparingInt(
                  (LibraryItem item) -> calculateSearchResultWeight(item.getTitle(), searchFor))
              .reversed().thenComparing(LibraryItem::getNumPlays,
                  Comparator.nullsLast(Comparator.reverseOrder()));
        }
        // Plain popularity browse: highest play count first.
        yield Comparator.comparing(LibraryItem::getNumPlays,
            Comparator.nullsLast(Comparator.reverseOrder()));
      }
    };

    // ── Queries ───────────────────────────────────────────────────────────

    List<SongFileEntity> songs = root.getSongs().stream().filter(hasPlays)
        .filter(inGenre).filter(matchesSearch).sorted(comparator).limit(limit).toList();

    List<ArtistFolderEntity> artists = root.getArtists().stream().filter(hasPlays)
        .filter(inGenre).filter(matchesSearch).sorted(comparator).limit(limit).toList();

    List<AlbumFolderEntity> albums = root.getAlbums().stream().filter(hasPlays)
        .filter(inGenre).filter(matchesSearch).sorted(comparator).limit(limit).toList();

    SearchResultDto dto = new SearchResultDto(
        SongLibraryMapper.toSongDtoList(songs),
        SongLibraryMapper.toArtistDtoList(artists), 
        SongLibraryMapper.toAlbumDtoList(albums),
        artists.size(),
        albums.size(),
        songs.size());
    
    return dto;
  }

  private int calculateSearchResultWeight(String value, String normalizedSearch) {

    if (value == null || normalizedSearch == null || normalizedSearch.isBlank()) {
      return 0;
    }

    String normalizedValue = value.toLowerCase().strip();

    //
    // Exact Match (+1000)
    //
    if (normalizedValue.equals(normalizedSearch)) {
      return 1000;
    }

    //
    // Full Word Match (+900)
    //
    for (String word : normalizedValue.split("\\s+")) {
      if (word.equals(normalizedSearch)) {
        return 900;
      }
    }

    //
    // Starts With (+500)
    //
    if (normalizedValue.startsWith(normalizedSearch)) {
      return 500;
    }

    //
    // Word Starts With (+400)
    //
    for (String word : normalizedValue.split("\\s+")) {
      if (word.startsWith(normalizedSearch)) {
        return 400;
      }
    }

    //
    // Contains (+300)
    //
    if (normalizedValue.contains(normalizedSearch)) {
      return 300;
    }

    //
    // Fuzzy Subsequence Matching
    //
    int score = 0;

    int valueIdx = 0;
    int searchIdx = 0;

    int valueLen = normalizedValue.length();
    int searchLen = normalizedSearch.length();

    int firstMatchIdx = -1;
    int lastMatchIdx = -1;

    while (valueIdx < valueLen && searchIdx < searchLen) {

      char valueChar = normalizedValue.charAt(valueIdx);
      char searchChar = normalizedSearch.charAt(searchIdx);

      if (valueChar == searchChar) {

        if (firstMatchIdx < 0) {
          firstMatchIdx = valueIdx;
        }

        //
        // Leading match bonus
        //
        if (searchIdx == 0) {

          if (valueIdx == 0) {
            score += 20;
          } else if (normalizedValue.charAt(valueIdx - 1) == ' ') {
            score += 15;
          }
        }

        //
        // Consecutive character bonus
        //
        if (lastMatchIdx >= 0) {

          int gap = valueIdx - lastMatchIdx - 1;

          if (gap == 0) {
            score += 10;
          } else {
            //
            // Gap penalty
            //
            score -= gap * 5;
          }
        }

        score += 5;

        lastMatchIdx = valueIdx;
        searchIdx++;
      }

      valueIdx++;
    }

    //
    // Search term not fully matched
    //
    if (searchIdx < searchLen) {
      return 0;
    }

    //
    // Density Check
    //
    int spanLength = lastMatchIdx - firstMatchIdx + 1;

    double density = (double) searchLen / spanLength;

    if (density < 0.70d) {
      return 0;
    }

    //
    // Penalize trailing unmatched characters
    //
    score -= (valueLen - lastMatchIdx - 1);

    //
    // Minimum fuzzy threshold
    //
    if (score < 25) {
      return 0;
    }

    return score;
  }

  @Override
  public List<GenreDto> getGenres(Integer locationId) {

    if (!isInitialized) {
      throw new SongLibraryServiceException("SongLibraryService has not been initialized yet!");
    }
    RootFolderEntity root = getOrLoadRoot(locationId);
    List<GenreDto> dtos = new ArrayList<>();
    for (GenreFolderEntity genre : root.getGenres()) {

      int numPlays = 0;
      List<Integer> albumIds = new ArrayList<>();
      for (AlbumFolderEntity album : root.getAlbumsForGenre(genre.getId())) {

        albumIds.add(album.getId());
        numPlays = numPlays + album.getNumPlays().intValue();
      }
      Collections.sort(albumIds);
      dtos.add(SongLibraryMapper.toGenreDto(genre, albumIds, Integer.valueOf(numPlays)));
    }
    dtos.sort(Comparator.comparing(GenreDto::numPlays).reversed());
    return dtos;
  }

  @Override
  public List<ArtistDto> getArtists(Integer locationId) {

    if (!isInitialized) {
      throw new SongLibraryServiceException("SongLibraryService has not been initialized yet!");
    }
    return SongLibraryMapper.toArtistDtoList(getOrLoadRoot(locationId).getArtists());
  }

  @Override
  public List<AlbumDto> getAlbums(Integer locationId) {

    if (!isInitialized) {
      throw new SongLibraryServiceException("SongLibraryService has not been initialized yet!");
    }
    return SongLibraryMapper.toAlbumDtoList(getOrLoadRoot(locationId).getAlbums());
  }

  @Override
  public List<AlbumDto> getAlbumsForGenre(Integer locationId, Integer genreId) {

    if (!isInitialized) {
      throw new SongLibraryServiceException("SongLibraryService has not been initialized yet!");
    }

    if (genreId == null) {
      return List.of();
    }

    return SongLibraryMapper.toAlbumDtoList(getOrLoadRoot(locationId).getAlbumsForGenre(genreId));
  }

  @Override
  public ArtistDto getArtistByName(Integer locationId, String artistName) {

    try {
      return SongLibraryMapper.toArtistDto(getOrLoadRoot(locationId).getArtistByName(artistName));
    } catch (EntityDoesNotExistException ednee) {
      throw new SongLibraryServiceException("Could not find artist by name: " + artistName, ednee);
    }
  }

  @Override
  public ArtistDto getArtistById(Integer locationId, Integer artistId) {

    try {
      return SongLibraryMapper.toArtistDto(getOrLoadRoot(locationId).getArtistById(artistId));
    } catch (EntityDoesNotExistException ednee) {
      throw new SongLibraryServiceException("Could not find artist by id: " + artistId, ednee);
    }
  }

  @Override
  public ArtistDto getArtistByAlbumId(Integer locationId, Integer albumId) {

    try {
      return SongLibraryMapper.toArtistDto(getOrLoadRoot(locationId).getArtistByAlbumId(albumId));
    } catch (EntityDoesNotExistException ednee) {
      throw new SongLibraryServiceException(
          "Could not find artist by album id: " + albumId, ednee);
    }
  }

  @Override
  public AlbumDto getAlbumById(Integer locationId, Integer albumId) {

    try {
      return SongLibraryMapper.toAlbumDto(getOrLoadRoot(locationId).getAlbumById(albumId));
    } catch (EntityDoesNotExistException ednee) {
      throw new SongLibraryServiceException("Could not find album by id: " + albumId, ednee);
    }
  }

  @Override
  public SongDto getSongById(Integer locationId, Integer albumId, Integer songId) {

    try {
      return SongLibraryMapper.toSongDto(getOrLoadRoot(locationId).getSongById(albumId, songId));
    } catch (EntityDoesNotExistException ednee) {
      throw new SongLibraryServiceException("Could not find song by id: " + albumId, ednee);
    }
  }


  // ADMIN ROLE METHODS -- always local to this instance's own location; never take a locationId.
  @Override
  public Integer scanFileSystemForSongs() throws SongScanFailedException {

    return scanFileSystemForSongs(new ScanRequest(this.ownRoot.getRootPath()));
  }

  @Override
  public Integer scanFileSystemForSongs(ScanRequest scanRequest) throws SongScanFailedException {

    requireNotMaster();

    String scanPath = scanRequest.scanPath();

    try {

      // Scan the file system for songs
      this.ownRoot.storeSongStatistics(this.dataDir);

      RootFolderEntity scannedRoot = songScanner.scanFileSystemForSongs(scanPath);
      // SongScanner has no notion of LocationEntity -- wire it here so SongLibraryRepositoryJpaImpl
      // (which sources location_id from this) can persist the freshly-scanned tree.
      scannedRoot.setParentLocation(this.ownLocation);

      // Restore song num plays, persist, then re-initialize the scanned root
      scannedRoot.restoreSongStatisticsForRootPath(this.dataDir, scannedRoot.getRootPath());

      this.songLibraryRepository.storeAggregateRoot(scannedRoot);

      if (this.jpaRepositoryType) {

        RootFolderEntity rehydratedRoot = null;
        try {
          rehydratedRoot = this.songLibraryRepository.loadAggregateRoot(
              this.ownLocation.getPersistentIdentity());
        } catch (EntityDoesNotExistException ednee) {
          throw new SongLibraryServiceException(
              "Song library round-trip check failed: could not reload the song library for "
                  + "locationId [" + this.ownLocation.getPersistentIdentity()
                  + "] immediately after storing it.", ednee);
        }

        List<String> differences =
            SongLibraryStructuralComparator.findDifferences(scannedRoot, rehydratedRoot);
        if (!differences.isEmpty()) {
          throw new SongLibraryServiceException(
              "Song library round-trip check failed for locationId ["
                  + this.ownLocation.getPersistentIdentity()
                  + "] -- the tree reloaded from song_library does not match what was just "
                  + "scanned: " + differences);
        }
      }

      scannedRoot.storeSongStatistics(this.dataDir);
      scannedRoot.initialize();

      // Re-derive ownRoot/ownLocation from persisted state, same discovery path as startup.
      initialize();

      // Publish the event
      eventPublisher.publishEvent(new ScanFileSystemForSongsEvent(
          scanPath, 
          this.ownRoot.getAlbums().size(),
          this.ownRoot.getSongs().size()));

      return Integer.valueOf(this.ownRoot.getAlbums().size());
    } catch (SongLibraryServiceException sle) {
      throw sle;
    } catch (Exception e) {
      throw new SongScanFailedException(
          "Could not scan file system for songs in: " + scanPath
              + " with acceptedSongFileExtensions: " + songScanner.getAcceptedSongFileExtensions(),
          e);
    }
  }

  @Override
  public Integer resetSongStatistics() {

    requireNotMaster();

    try {

      // Reset all the song statistics
      this.ownRoot.resetSongStatistics();

      // Store the song library
      this.songLibraryRepository.storeAggregateRoot(this.ownRoot);

      // Initialize the song library
      initialize();

      // Publish the event
      eventPublisher.publishEvent(new SongStatisticsChangedEvent());

      return Integer.valueOf(this.ownRoot.getAlbums().size());

    } catch (Exception e) {
      throw new SongLibraryServiceException("Could not reset song statistics", e);
    }
  }

  @Override
  public Integer restoreSongStatistics(String filename) {

    requireNotMaster();

    try {

      // Restore the song statistics
      this.ownRoot.restoreSongStatisticsForFile(this.ownRoot.getRootPath(), filename);

      // Store the song library
      this.songLibraryRepository.storeAggregateRoot(this.ownRoot);

      // Initialize the song library
      initialize();

      // Publish the event
      eventPublisher.publishEvent(new SongStatisticsChangedEvent());

      return Integer.valueOf(this.ownRoot.getAlbums().size());

    } catch (Exception e) {
      throw new SongLibraryServiceException("Could not restore song statistics from: " + filename, e);
    }
  }

  @Override
  public Integer storeSongLibraryAndStatistics() {

    requireNotMaster();

    try {

      // Store the song library
      this.songLibraryRepository.storeAggregateRoot(this.ownRoot);

      if (this.jpaRepositoryType) {

        // Keep a filesystem (.oos) backup of the in-memory library up to date too, so this
        // instance can be switched back to repositoryType: filesystem later without losing data
        // -- the reverse of adoptLocalOosFileIntoJpaStore's filesystem-to-JPA migration.
        new SongLibraryRepositoryFileSystemImpl(this.dataDir).storeAggregateRoot(this.ownRoot);
      }

      // Store the song statistics. Num-plays are already kept current per-event under JPA (via
      // SongLibraryRepository.updateNumPlaysForSong()), but CDStats.TXT is also the persistent
      // record that a filesystem-mode scan reads back via restoreSongStatisticsForRootPath() to
      // carry num-plays forward across a rescan -- so keep it current even under JPA in case this
      // instance is later switched back to repositoryType: filesystem and rescanned.
      this.ownRoot.storeSongStatistics(this.dataDir);

      return Integer.valueOf(this.ownRoot.getAlbums().size());

    } catch (Exception e) {
      throw new SongLibraryServiceException(
          "Could not store song library and statistics to: " + this.dataDir, e);
    }
  }

  @Override
  public List<AlbumMetadataDto> searchInternetForAlbumMetadata(String artistName, String albumName,
      int limit) {

    requireNotMaster();

    try {

      List<AlbumMetadataDto> albumMetadataResults =
          this.songScanner.searchInternetForAlbumMetadata(artistName, albumName, limit);

      return albumMetadataResults;

    } catch (Exception e) {
      throw new SongLibraryServiceException("Could not search internet for album metadata for artist: "
          + artistName + " and album: " + albumName, e);
    }
  }

  @Override
  public AlbumMetadataDto updateAlbumMetadata(Integer albumId, AlbumMetadataDto albumMetadata) {

    requireNotMaster();

    try {

      AlbumFolderEntity album = this.ownRoot.getAlbumById(albumId);

      album.getMetaData().writeMetadataToFileSystem(albumMetadata);

      return albumMetadata;

    } catch (Exception e) {
      throw new SongLibraryServiceException("Could not update metadata for album: " + albumId, e);
    }
  }

  @Override
  public String downloadAlbumCoverArt(DownloadAlbumCoverArtRequest downloadAlbumCoverArtRequest) {

    requireNotMaster();

    try {

      AlbumFolderEntity album =
          this.ownRoot.getAlbumById(downloadAlbumCoverArtRequest.albumId());

      String coverArtPath = album.getCoverArtPath();

      this.songScanner.downloadCoverArt(coverArtPath,
          downloadAlbumCoverArtRequest.coverArtUrl());

      return coverArtPath;

    } catch (Exception e) {
      throw new SongLibraryServiceException(
          "Could not download cover art for: " + downloadAlbumCoverArtRequest, e);
    }
  }

  @Override
  public Boolean authenticateForAdminPanel(
      AuthenticateForAdminPanelRequest authenticateForAdminPanelRequest) {

    requireNotMaster();

    String username = authenticateForAdminPanelRequest.username();
    String password = authenticateForAdminPanelRequest.password();

    if (username == null || username.isBlank() || password == null || password.isBlank()) {
      return Boolean.FALSE;
    }

    String lowerUsername = username.toLowerCase();

    if ("admin".equals(lowerUsername)) {
      return authenticatePassword(password, "admin.sha");
    }

    if ("owner".equals(lowerUsername)) {
      return authenticatePassword(password, "owner.sha");
    }

    return Boolean.FALSE;
  }

  private Boolean authenticatePassword(String password, String hashFileName) {

    try {
      Path hashFile = Path.of(this.dataDir, hashFileName);

      if (!Files.exists(hashFile)) {
        return Boolean.FALSE;
      }

      String storedHash = Files.readString(hashFile, StandardCharsets.UTF_8).trim();

      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashedBytes = digest.digest(password.getBytes(StandardCharsets.UTF_8));

      String computedHash = bytesToHex(hashedBytes);

      return computedHash.equalsIgnoreCase(storedHash);

    } catch (IOException | NoSuchAlgorithmException e) {
      throw new SongLibraryServiceException("Could not authenticate password using hash file: " + hashFileName, e);
    }
  }

  private static String bytesToHex(byte[] bytes) {
    StringBuilder sb = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }

  @Override
  public Boolean isLibraryLoadFailedAtStartup() {
    return this.libraryLoadFailedAtStartup;
  }

  // Repository methods
  @Override
  public RootFolderEntity getSongLibraryRoot(Integer locationId) {
    return getOrLoadRoot(locationId);
  }

  @Override
  public Integer getOwnLocationId() {
    return this.ownLocation != null ? this.ownLocation.getPersistentIdentity() : null;
  }

  @Override
  public LocationEntity getOwnLocation() {
    return this.ownLocation;
  }

  @Override
  public void reinitializeOwnLocation() {
    initialize();
  }

  @Override
  public void renameOwnLocationLibraryFileIfNameChanged(String previousLocationName) {

    requireNotMaster();

    // getLocationName() delegates to parentLocation.getName() -- the very same LocationEntity
    // LocationService#updateOwnLocationInfo just mutated in place, so this already reflects the
    // new name by the time this is called.
    String currentLocationName = this.ownRoot.getLocationName();
    if (Objects.equals(previousLocationName, currentLocationName)) {
      return;
    }

    this.songLibraryRepository.renameLocationLibraryFile(previousLocationName,
        currentLocationName);

    if (this.jpaRepositoryType) {

      // Under JPA, the primary repository has no per-location file of its own to rename (see
      // SongLibraryRepository#renameLocationLibraryFile's no-op default) -- the call above was a
      // no-op. But storeSongLibraryAndStatistics() keeps a .oos backup on disk under JPA too (so
      // this instance can be switched back to repositoryType: filesystem later without losing
      // data), and that backup's filename is just as location-name-derived as the filesystem
      // repository's own file. Rename it here too, so it doesn't linger under the old name as an
      // orphan until the next storeSongLibraryAndStatistics() call writes a second file under the
      // new name.
      new SongLibraryRepositoryFileSystemImpl(this.dataDir)
          .renameLocationLibraryFile(previousLocationName, currentLocationName);
    }
  }

  @Override
  public RootFolderEntity loadAggregateRoot(String naturalIdentity)
      throws EntityDoesNotExistException {

    return this.songLibraryRepository.loadAggregateRoot(naturalIdentity);
  }

  @Override
  public RootFolderEntity loadAggregateRoot(int persistentIdentity)
      throws EntityDoesNotExistException {

    return this.songLibraryRepository.loadAggregateRoot(persistentIdentity);
  }

  @Override
  public void storeAggregateRoot(RootFolderEntity root) {

    this.songLibraryRepository.storeAggregateRoot(root);
  }

  // Command methods
  @Override
  public CommandResponse processCommand(CommandRequest commandRequest) {

    throw new SongLibraryServiceException("Not implemented yet!");
  }

  // Query methods
  @Override
  public QueryResponse<QueryRequest, QueryResponseItem> processQuery(QueryRequest queryRequest) {

    throw new SongLibraryServiceException("Not implemented yet!");
  }

  // Event handlers -- only ever fired for this instance's own local queue activity (a remote
  // location's mutation happens on the owning slave's own process; that event never crosses to
  // master), so these always operate on ownRoot.
  @EventListener
  public void handleSongAddedToQueueEvent(SongAddedToQueueEvent event) {

    try {

      // Update the number of song plays and store to the repository
      Integer locationId = getOwnLocationId();
      Integer albumId = event.queueEntry().song().albumId();
      Integer songId = event.queueEntry().song().songId();
      
      SongFileEntity song = this.ownRoot.getSongById(
          event.queueEntry().song().albumId(), 
          event.queueEntry().song().songId());

      Integer numPlays = song.incrementNumPlays();

      this.songLibraryRepository.updateNumPlaysForSong(
          ownRoot, 
          locationId, 
          albumId,
          songId, 
          numPlays);

      // Publish the event
      eventPublisher.publishEvent(new SongStatisticsChangedEvent());
      
    } catch (EntityDoesNotExistException ednee) {
      throw new SongLibraryServiceException("Could not increment num plays for: " + event.queueEntry(),
          ednee);
    }
  }

  @EventListener
  public void handleMultipleSongsAddedToQueueEvent(MultipleSongsAddedToQueueEvent event) {

    try {

      for (SongQueueEntryDto queueEntry : event.queueEntries()) {

        // Update the number of song plays and store to the repository
        Integer locationId = getOwnLocationId();
        Integer albumId = queueEntry.song().albumId();
        Integer songId = queueEntry.song().songId();
        
        SongFileEntity song = this.ownRoot.getSongById(
            queueEntry.song().albumId(), 
            queueEntry.song().songId());

        Integer numPlays = song.incrementNumPlays();

        this.songLibraryRepository.updateNumPlaysForSong(
            ownRoot, 
            locationId, 
            albumId,
            songId, 
            numPlays);
      }

      // Publish the event
      eventPublisher.publishEvent(new SongStatisticsChangedEvent());

    } catch (EntityDoesNotExistException ednee) {
      throw new SongLibraryServiceException("Could not increment num plays for: " + event.queueEntries(),
          ednee);
    }
  }
}
