package com.djt.jukeanator_engine.domain.songlibrary.service;

import static java.util.Objects.requireNonNull;
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
import com.djt.jukeanator_engine.domain.songlibrary.dto.ArtistDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.GenreDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.ScanRequest;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SearchResultDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SongDto;
import com.djt.jukeanator_engine.domain.songlibrary.event.ResetSongStatisticsEvent;
import com.djt.jukeanator_engine.domain.songlibrary.event.ScanFileSystemForSongsEvent;
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

  private final ApplicationEventPublisher eventPublisher;

  private String scanPath;
  private SongLibraryRepository songLibraryRepository;
  private SongScanner songScanner;
  private Integer searchResultSize = Integer.valueOf(50);

  private RootFolderEntity root;
  private boolean isInitialized;

  public SongLibraryServiceImpl(String scanPath, SongLibraryRepository songLibraryRepository,
      SongScanner songScanner, Integer searchResultSize, ApplicationEventPublisher eventPublisher) {

    requireNonNull(scanPath, "scanPath cannot be null");
    requireNonNull(songLibraryRepository, "songLibraryRepository cannot be null");
    requireNonNull(songScanner, "songScanner cannot be null");
    requireNonNull(searchResultSize, "searchResultSize cannot be null");
    requireNonNull(eventPublisher, "eventPublisher cannot be null");

    this.scanPath = scanPath;
    this.songLibraryRepository = songLibraryRepository;
    this.songScanner = songScanner;
    this.searchResultSize = searchResultSize;
    this.eventPublisher = eventPublisher;

    // Initialize the song library
    initializeSongLibrary();
  }

  // Service methods
  @Override
  public SearchResultDto getMusicByPopularity() {

    return getMusic(null, null, SortOrder.POPULARITY);
  }

  /** Controls how results returned from {@link #getMusic} are ordered. */
  private enum SortOrder {
    POPULARITY, TITLE, RELEASE_DATE
  }

  @Override
  public SearchResultDto getMusicBySearch(String searchFor) {

    if (!isInitialized) {
      throw new SongLibraryException("SongLibraryService has not been initialized yet!");
    }

    requireNonNull(searchFor, "searchFor cannot be null");

    final String normalizedSearch = searchFor.trim().toLowerCase();

    if (normalizedSearch.isEmpty()) {
      return new SearchResultDto(List.of(), List.of(), List.of());
    }

    return getMusic(null, normalizedSearch, SortOrder.POPULARITY);
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
   * Filtering rules:
   * <ul>
   * <li>When {@code genreName} is non-null, all items in that genre are included regardless of play
   * count (zero-play items are valid genre members).</li>
   * <li>When {@code searchFor} is non-null, only items whose title matches the search term are
   * included; play count is irrelevant.</li>
   * <li>When both are null (global popularity browse), only items with at least one play are
   * included.</li>
   * </ul>
   *
   * <p>
   * Sort order:
   * <ul>
   * <li>{@link SortOrder#POPULARITY} — highest play count first; search results additionally weight
   * exact/prefix/suffix/contains matches above raw popularity.</li>
   * <li>{@link SortOrder#TITLE} — ascending alphabetical on {@link LibraryItem#getTitle()}.</li>
   * <li>{@link SortOrder#RELEASE_DATE} — most recent first on {@link LibraryItem#getReleaseDate()},
   * nulls last.</li>
   * </ul>
   *
   * @param genreName genre filter; {@code null} means all genres.
   * @param searchFor pre-normalized (trimmed, lower-cased) search term; {@code null} means no text
   *        filter.
   * @param sortOrder how to order the results.
   */
  private SearchResultDto getMusic(String genreName, String searchFor, SortOrder sortOrder) {

    if (!isInitialized) {
      throw new SongLibraryException("SongLibraryService has not been initialized yet!");
    }

    // ── Filters ───────────────────────────────────────────────────────────

    // Play-count guard: skip unplayed items only on the global popularity browse.
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
          // Search: primary sort is match quality, secondary is play count.
          yield Comparator
              .comparingInt(
                  (LibraryItem item) -> calculateSearchResultWeight(item.getTitle(), searchFor))
              .thenComparing(LibraryItem::getNumPlays, Comparator.nullsFirst(Integer::compareTo))
              .reversed();
        }
        // Plain popularity browse: highest play count first.
        yield Comparator
            .comparing(LibraryItem::getNumPlays, Comparator.nullsFirst(Integer::compareTo))
            .reversed();
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

    if (value == null) {
      return Integer.valueOf(0);
    }

    String normalizedValue = value.toLowerCase().trim();

    // Full word match (highest priority)
    for (String word : normalizedValue.split("\\s+")) {
      if (word.equals(normalizedSearch)) {
        return Integer.valueOf(4);
      }
    }

    // Starts with
    if (normalizedValue.startsWith(normalizedSearch)) {
      return Integer.valueOf(3);
    }

    // Ends with
    if (normalizedValue.endsWith(normalizedSearch)) {
      return Integer.valueOf(2);
    }

    // Contains
    if (normalizedValue.contains(normalizedSearch)) {
      return Integer.valueOf(1);
    }

    return Integer.valueOf(0);
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
  public ArtistDto getArtistById(Integer artistId) {

    try {
      return SongLibraryMapper.toArtistDto(root.getArtistById(artistId));
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
  public Integer scanFileSystemForSongs(ScanRequest scanRequest) throws SongScanFailedException {

    try {

      // Scan the file system for songs
      this.scanPath = scanRequest.getScanPath();
      this.root = songScanner.scanFileSystemForSongs(this.scanPath);
      this.root.restoreSongNumPlays();

      // Store the song library
      if (this.songLibraryRepository instanceof SongLibraryRepositoryFileSystemImpl) {
        ((SongLibraryRepositoryFileSystemImpl) this.songLibraryRepository)
            .setBasePath(this.scanPath);
      }
      this.songLibraryRepository.storeAggregateRoot(this.root);

      // Initialize the song library
      initializeSongLibrary();

      // Publish the event
      eventPublisher.publishEvent(new ScanFileSystemForSongsEvent(scanPath, root.getAlbums().size()));

      return Integer.valueOf(root.getAlbums().size());
    } catch (SongLibraryException sle) {
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
    try {

      // Reset all the song statistics
      this.root.resetSongStatistics();

      // Store the song library
      this.songLibraryRepository.storeAggregateRoot(this.root);

      // Initialize the song library
      initializeSongLibrary();

      // Publish the event
      eventPublisher.publishEvent(new ResetSongStatisticsEvent());

      return Integer.valueOf(root.getAlbums().size());

    } catch (Exception e) {
      throw new SongLibraryException("Could not reset song statistics", e);
    }
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

      this.root = this.songLibraryRepository.loadAggregateRoot(this.scanPath);

    } catch (EntityDoesNotExistException ednee) {

      log.error("Could not load song library from: " + scanPath
          + ", using empty song library root for now, error: " + ednee.getMessage());

      this.root = new RootFolderEntity();
    }

    this.isInitialized = true;
  }

  // Event handlers
  @EventListener
  public void handleSongAddedToQueueEvent(SongAddedToQueueEvent event) {

    try {

      SongFileEntity song = this.root.getSongById(event.queueEntry().getSong().getAlbumId(), event.queueEntry().getSong().getSongId());
      song.incrementNumPlays();
      
      this.songLibraryRepository.storeSongLibraryAsync();

    } catch (EntityDoesNotExistException ednee) {
      throw new SongLibraryException(
          "Could not increment num plays for: " + event.queueEntry(), 
          ednee);
    }
  }
  
  @EventListener
  public void handleMultipleSongsAddedToQueueEvent(MultipleSongsAddedToQueueEvent event) {

    try {

      for (SongQueueEntryDto queueEntry: event.queueEntries()) {
        
        SongFileEntity song = this.root.getSongById(queueEntry.getSong().getAlbumId(), queueEntry.getSong().getSongId());
        song.incrementNumPlays();
      }
      
      this.songLibraryRepository.storeSongLibraryAsync();

    } catch (EntityDoesNotExistException ednee) {
      throw new SongLibraryException(
          "Could not increment num plays for: " + event.queueEntries(), 
          ednee);
    }
  }  
}
