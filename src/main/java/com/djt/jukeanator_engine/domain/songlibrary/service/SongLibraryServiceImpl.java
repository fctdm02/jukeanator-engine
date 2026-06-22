package com.djt.jukeanator_engine.domain.songlibrary.service;

import static java.util.Objects.requireNonNull;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;
import com.djt.jukeanator_engine.domain.common.service.AggregateRootService;
import com.djt.jukeanator_engine.domain.common.service.command.model.CommandRequest;
import com.djt.jukeanator_engine.domain.common.service.command.model.CommandResponse;
import com.djt.jukeanator_engine.domain.common.service.query.model.QueryRequest;
import com.djt.jukeanator_engine.domain.common.service.query.model.QueryResponse;
import com.djt.jukeanator_engine.domain.common.service.query.model.QueryResponseItem;
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
import com.djt.jukeanator_engine.domain.songlibrary.exception.SongLibraryException;
import com.djt.jukeanator_engine.domain.songlibrary.exception.SongScanFailedException;
import com.djt.jukeanator_engine.domain.songlibrary.mapper.SongLibraryMapper;
import com.djt.jukeanator_engine.domain.songlibrary.model.AlbumFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.ArtistFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.GenreFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.LibraryItem;
import com.djt.jukeanator_engine.domain.songlibrary.model.RootFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.SongFileEntity;
import com.djt.jukeanator_engine.domain.songlibrary.repository.SongLibraryRepository;
import com.djt.jukeanator_engine.domain.songlibrary.repository.SongLibraryRepositoryFileSystemImpl;
import com.djt.jukeanator_engine.domain.songlibrary.service.utils.SongScanner;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongQueueEntryDto;
import com.djt.jukeanator_engine.domain.songqueue.event.MultipleSongsAddedToQueueEvent;
import com.djt.jukeanator_engine.domain.songqueue.event.SongAddedToQueueEvent;

/**
 * @author tmyers
 */
public final class SongLibraryServiceImpl
    implements SongLibraryService, AggregateRootService<RootFolderEntity> {

  private static final Logger log = LoggerFactory.getLogger(SongLibraryServiceImpl.class);

  private static final String VALID_KEYBOARD_CHARACTERS =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZ" + "0123456789" + "',. !@#$%^&*\"()[]/\\\\?:;";

  private final ApplicationEventPublisher eventPublisher;

  private String rootPath;
  private final SongLibraryRepository songLibraryRepository;
  private final SongScanner songScanner;
  private final Integer searchResultSize;

  private RootFolderEntity root;
  private boolean isInitialized;

  public SongLibraryServiceImpl(String rootPath, SongLibraryRepository songLibraryRepository,
      SongScanner songScanner, Integer searchResultSize, ApplicationEventPublisher eventPublisher) {

    requireNonNull(rootPath, "rootPath cannot be null");
    requireNonNull(songLibraryRepository, "songLibraryRepository cannot be null");
    requireNonNull(songScanner, "songScanner cannot be null");
    requireNonNull(searchResultSize, "searchResultSize cannot be null");
    requireNonNull(eventPublisher, "eventPublisher cannot be null");

    this.rootPath = rootPath;
    this.songLibraryRepository = songLibraryRepository;
    this.songScanner = songScanner;
    this.searchResultSize = searchResultSize;
    this.eventPublisher = eventPublisher;

    // Initialize the song library
    initializeSongLibrary();
  }

  // Service methods
  // USER ROLE METHODS
  @Override
  public SearchResultDto getMusicByPopularity() {

    return getMusic(null, null, SortOrder.POPULARITY);
  }

  /** Controls how results returned from {@link #getMusic} are ordered. */
  private enum SortOrder {
    POPULARITY, TITLE, RELEASE_DATE
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
  public SearchResultDto getMusicBySearch(String searchFor) {

    if (!isInitialized) {
      throw new SongLibraryException("SongLibraryService has not been initialized yet!");
    }

    if (searchFor == null || searchFor.strip().isEmpty()) {
      return new SearchResultDto(List.of(), List.of(), List.of());
    }

    String searchForNormalized = stripNonKeyboardCharacters(searchFor.strip().toLowerCase());

    return getMusic(null, searchForNormalized, SortOrder.POPULARITY);
  }

  @Override
  public SearchResultDto getGenreMusicByPopularity(String genreName) {

    return getMusic(genreName, null, SortOrder.POPULARITY);
  }

  @Override
  public SearchResultDto getGenreMusicByTitle(String genreName) {

    return getMusic(genreName, null, SortOrder.TITLE);
  }

  @Override
  public SearchResultDto getGenreMusicByReleaseDate(String genreName) {

    return getMusic(genreName, null, SortOrder.RELEASE_DATE);
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
   * <li>{@link SortOrder#RELEASE_DATE} — most recent first on {@link LibraryItem#getReleaseDate()},
   * nulls last.</li>
   * </ul>
   */
  private SearchResultDto getMusic(String genreName, String searchFor, SortOrder sortOrder) {

    if (!isInitialized) {
      throw new SongLibraryException("SongLibraryService has not been initialized yet!");
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

      case RELEASE_DATE -> Comparator.comparing(LibraryItem::getReleaseDate,
          Comparator.nullsLast(Comparator.reverseOrder()));

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

    List<SongFileEntity> songs = root.getSongs().stream().filter(hasPlays).filter(inGenre)
        .filter(matchesSearch).sorted(comparator).limit(searchResultSize).toList();

    List<ArtistFolderEntity> artists = root.getArtists().stream().filter(hasPlays).filter(inGenre)
        .filter(matchesSearch).sorted(comparator).limit(searchResultSize).toList();

    List<AlbumFolderEntity> albums = root.getAlbums().stream().filter(hasPlays).filter(inGenre)
        .filter(matchesSearch).sorted(comparator).limit(searchResultSize).toList();

    return new SearchResultDto(SongLibraryMapper.toSongDtoList(songs),
        SongLibraryMapper.toArtistDtoList(artists), SongLibraryMapper.toAlbumDtoList(albums));
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
  public List<GenreDto> getGenres() {

    if (!isInitialized) {
      throw new SongLibraryException("SongLibraryService has not been initialized yet!");
    }
    List<GenreDto> dtos = new ArrayList<>();
    for (GenreFolderEntity genre : root.getGenres()) {

      int numPlays = 0;
      List<Integer> albumIds = new ArrayList<>();
      for (AlbumFolderEntity album : root.getAlbumsForGenre(genre.getPersistentIdentity())) {

        albumIds.add(album.getPersistentIdentity());
        numPlays = numPlays + album.getNumPlays().intValue();
      }
      Collections.sort(albumIds);
      dtos.add(SongLibraryMapper.toGenreDto(genre, albumIds, Integer.valueOf(numPlays)));
    }
    dtos.sort(Comparator.reverseOrder());
    return dtos;
  }

  @Override
  public List<ArtistDto> getArtists() {

    if (!isInitialized) {
      throw new SongLibraryException("SongLibraryService has not been initialized yet!");
    }
    return SongLibraryMapper.toArtistDtoList(root.getArtists());
  }

  @Override
  public List<AlbumDto> getAlbums() {

    if (!isInitialized) {
      throw new SongLibraryException("SongLibraryService has not been initialized yet!");
    }
    return SongLibraryMapper.toAlbumDtoList(root.getAlbums());
  }

  @Override
  public List<AlbumDto> getAlbumsForGenre(Integer genreId) {

    if (!isInitialized) {
      throw new SongLibraryException("SongLibraryService has not been initialized yet!");
    }

    if (genreId == null) {
      return List.of();
    }

    return SongLibraryMapper.toAlbumDtoList(root.getAlbumsForGenre(genreId));
  }

  @Override
  public ArtistDto getArtistByName(String artistName) {

    try {
      return SongLibraryMapper.toArtistDto(root.getArtistByName(artistName));
    } catch (EntityDoesNotExistException e) {
      return null;
    }
  }

  @Override
  public AlbumDto getAlbumById(Integer albumId) {

    try {
      return SongLibraryMapper.toAlbumDto(root.getAlbumById(albumId));
    } catch (EntityDoesNotExistException e) {
      return null;
    }
  }

  @Override
  public SongDto getSongById(Integer albumId, Integer songId) {

    try {
      return SongLibraryMapper.toSongDto(root.getSongById(albumId, songId));
    } catch (EntityDoesNotExistException e) {
      return null;
    }
  }

  @Override
  public SongDto getRandomSongFromBackgroundMusicPlaylist() {

    return SongLibraryMapper
        .toSongDto(this.root.getRandomSongFromBackgroundMusicPlaylist(this.rootPath));
  }


  // ADMIN ROLE METHODS
  @Override
  public Integer scanFileSystemForSongs() throws SongScanFailedException {

    return scanFileSystemForSongs(new ScanRequest(this.rootPath));
  }

  @Override
  public Integer scanFileSystemForSongs(ScanRequest scanRequest) throws SongScanFailedException {

    try {

      // Scan the file system for songs
      this.rootPath = scanRequest.getScanPath();
      this.root.storeSongNumPlays(this.rootPath);
      this.root = songScanner.scanFileSystemForSongs(this.rootPath);

      // Restore song num plays, persist, then re-initialize the root
      this.root.restoreSongNumPlays(this.rootPath);
      if (this.songLibraryRepository instanceof SongLibraryRepositoryFileSystemImpl) {
        ((SongLibraryRepositoryFileSystemImpl) this.songLibraryRepository)
            .setBasePath(this.rootPath);
      }
      this.songLibraryRepository.storeAggregateRoot(this.root);
      this.root.initialize();

      // Initialize the song library
      initializeSongLibrary();

      // Publish the event
      eventPublisher
          .publishEvent(new ScanFileSystemForSongsEvent(rootPath, root.getAlbums().size()));

      return Integer.valueOf(root.getAlbums().size());
    } catch (SongLibraryException sle) {
      throw sle;
    } catch (Exception e) {
      throw new SongScanFailedException(
          "Could not scan file system for songs in: " + rootPath
              + " with acceptedSongFileExtensions: " + songScanner.getAcceptedSongFileExtensions(),
          e);
    }
  }

  @Override
  public Integer resetSongStatistics() {

    try {

      // Reset all the song statistics
      this.root.resetSongStatistics();

      // Store the song library
      this.songLibraryRepository.storeAggregateRoot(this.root);

      // Initialize the song library
      initializeSongLibrary();

      // Publish the event
      eventPublisher.publishEvent(new SongStatisticsChangedEvent());

      return Integer.valueOf(root.getAlbums().size());

    } catch (Exception e) {
      throw new SongLibraryException("Could not reset song statistics", e);
    }
  }

  @Override
  public List<AlbumMetadataDto> searchInternetForAlbumMetadata(String artistName, String albumName,
      int limit) {

    try {

      List<AlbumMetadataDto> albumMetadataResults =
          this.songScanner.searchInternetForAlbumMetadata(artistName, albumName, limit);

      return albumMetadataResults;

    } catch (Exception e) {
      throw new SongLibraryException("Could not search internet for album metadata for artist: "
          + artistName + " and album: " + albumName, e);
    }
  }

  @Override
  public AlbumMetadataDto updateAlbumMetadata(Integer albumId, AlbumMetadataDto albumMetadata) {

    try {

      AlbumFolderEntity album = this.root.getAlbumById(albumId);

      album.getMetaData().writeMetadataToFileSystem(albumMetadata);

      return albumMetadata;

    } catch (Exception e) {
      throw new SongLibraryException("Could not update metadata for album: " + albumId, e);
    }
  }

  @Override
  public String downloadAlbumCoverArt(DownloadAlbumCoverArtRequest downloadAlbumCoverArtRequest) {

    try {

      AlbumFolderEntity album = this.root.getAlbumById(downloadAlbumCoverArtRequest.getAlbumId());

      String coverArtPath = album.getCoverArtPath();

      this.songScanner.downloadCoverArt(coverArtPath,
          downloadAlbumCoverArtRequest.getCoverArtUrl());

      return coverArtPath;

    } catch (Exception e) {
      throw new SongLibraryException(
          "Could not download cover art for: " + downloadAlbumCoverArtRequest, e);
    }
  }

  @Override
  public Boolean authenticateForAdminPanel(
      AuthenticateForAdminPanelRequest authenticateForAdminPanelRequest) {

    String username = authenticateForAdminPanelRequest.getUsername();
    String password = authenticateForAdminPanelRequest.getPassword();

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
      Path hashFile = Path.of(this.rootPath, hashFileName);

      if (!Files.exists(hashFile)) {
        return Boolean.FALSE;
      }

      String storedHash = Files.readString(hashFile, StandardCharsets.UTF_8).trim();

      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashedBytes = digest.digest(password.getBytes(StandardCharsets.UTF_8));

      String computedHash = bytesToHex(hashedBytes);

      return computedHash.equalsIgnoreCase(storedHash);

    } catch (IOException | NoSuchAlgorithmException e) {
      e.printStackTrace();
      return Boolean.FALSE;
    }
  }

  private static String bytesToHex(byte[] bytes) {
    StringBuilder sb = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }

  // Repository methods
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

    throw new SongLibraryException("Not implemented yet!");
  }

  // Query methods
  @Override
  public QueryResponse<QueryRequest, QueryResponseItem> processQuery(QueryRequest queryRequest) {

    throw new SongLibraryException("Not implemented yet!");
  }

  public void initializeSongLibrary() {

    // If we cannot load the song library from disk at startup, then assume a new install and return
    // an
    // empty root folder. The application will automatically ask the user to scan for songs at
    // startup.
    try {

      this.root = this.songLibraryRepository.loadAggregateRoot(this.rootPath);

    } catch (EntityDoesNotExistException ednee) {

      log.error("Could not load song library from: " + rootPath
          + ", using empty song library root for now, error: " + ednee.getMessage());

      this.root = new RootFolderEntity(this.rootPath);
    }

    this.isInitialized = true;
  }

  // Event handlers
  @EventListener
  public void handleSongAddedToQueueEvent(SongAddedToQueueEvent event) {

    try {

      SongFileEntity song = this.root.getSongById(event.queueEntry().getSong().getAlbumId(),
          event.queueEntry().getSong().getSongId());
      song.incrementNumPlays();

      // Publish the event
      eventPublisher.publishEvent(new SongStatisticsChangedEvent());

      this.songLibraryRepository.storeSongLibraryAsync();

    } catch (EntityDoesNotExistException ednee) {
      throw new SongLibraryException("Could not increment num plays for: " + event.queueEntry(),
          ednee);
    }
  }

  @EventListener
  public void handleMultipleSongsAddedToQueueEvent(MultipleSongsAddedToQueueEvent event) {

    try {

      for (SongQueueEntryDto queueEntry : event.queueEntries()) {

        SongFileEntity song = this.root.getSongById(queueEntry.getSong().getAlbumId(),
            queueEntry.getSong().getSongId());
        song.incrementNumPlays();
      }

      // Publish the event
      eventPublisher.publishEvent(new SongStatisticsChangedEvent());

      this.songLibraryRepository.storeSongLibraryAsync();

    } catch (EntityDoesNotExistException ednee) {
      throw new SongLibraryException("Could not increment num plays for: " + event.queueEntries(),
          ednee);
    }
  }
}
