package com.djt.jukeanator_engine.domain.songlibrary.repository;

import static java.util.Objects.requireNonNull;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.djt.jukeanator_engine.domain.common.exception.EntityAlreadyExistsException;
import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;
import com.djt.jukeanator_engine.domain.songlibrary.exception.SongLibraryServiceException;
import com.djt.jukeanator_engine.domain.songlibrary.model.AlbumFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.AlbumMetaDataFileEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.ArtistFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.FolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.GenreFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.RootFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.SongFileEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

/**
 * JPA/Hibernate-backed implementation of {@link SongLibraryRepository}. One table ({@code
 * song_library}) holds every location's catalog -- folders and songs together, tenant-separated
 * by {@code location_id} and discriminated by {@code class_discriminator} -- see {@link
 * SongLibraryJpaEntity} for the flat row shape and why it's a standalone type rather than a JPA
 * annotation retrofit of the domain model itself.
 *
 * <p>{@code loadAggregateRoot(locationId)} reassembles a full, ordinary {@link RootFolderEntity}
 * tree from the flat rows (calling the domain model's own constructors/{@code addChildFolder}/
 * {@code addChildSong}, exactly as {@code SongScanner} would from real files), so every existing
 * browse/search method on {@code RootFolderEntity} works unchanged regardless of which repository
 * loaded it.
 *
 * <p>{@code storeAggregateRoot(root)} does a <b>diff-based upsert</b> against the location's
 * existing rows rather than a wholesale delete+reinsert: every folder/song in the incoming tree is
 * matched against an existing row by its <b>natural identity</b> (the filesystem path built from
 * the name chain -- see {@code AbstractLibraryEntity#getNaturalIdentity()}), not by {@code id}.
 * That's deliberate: {@code SongScanner} resets its id counter to 1 and reassigns every id in
 * traversal order on <em>every</em> scan, so the same song's scanner-assigned id is not stable
 * across two scans, while its path is. A matched row is updated in place (via managed-entity
 * setters, so Hibernate's dirty checking only emits an {@code UPDATE} when a column actually
 * changed) and <b>keeps its existing persisted id</b>; an unmatched row is inserted; an existing
 * row whose path no longer appears in the incoming tree is deleted. Keeping the id stable across a
 * rescan matters beyond this table: {@code song_queue}, playlists, and play history all persist
 * raw {@code (locationId, albumId, songId)} triples pointing back into {@code song_library}, which
 * a full delete+reinsert would silently invalidate for every unchanged song on every rescan.
 *
 * <p><b>Caller contract -- ids on the tree you pass in may be mutated</b>: since a matched node
 * reuses its old persisted id (which can differ from the id the caller set on it) and a genuinely
 * new node may need renumbering to avoid colliding with a reused id, {@link #storeAggregateRoot}
 * calls {@code setId(...)} on {@code root} and every descendant as it reconciles them, so after
 * the call returns the in-memory tree's ids agree with what was actually persisted. {@code
 * SongLibraryServiceImpl.scanFileSystemForSongs()}'s post-store round-trip check (comparing the
 * scanned tree against a fresh {@link #loadAggregateRoot}) relies on this.
 *
 * <p><b>id is application-assigned, not Hibernate-generated</b>: {@code SongScanner}'s single
 * shared per-scan counter assigns every folder/file a unique id across the whole location (see
 * {@code AbstractLibraryEntity#getId}) -- root, genres, artists, albums, and songs must all
 * already have a non-null id before {@link #storeAggregateRoot} is called, since {@code id} is
 * part of this table's primary key. A brand-new node's incoming id is kept as-is when it doesn't
 * collide with a reused id; only on collision is it renumbered from a counter seeded above the
 * location's current max id.
 *
 * <p><b>Caller contract for a synthetically-built root</b> (e.g. a master populating this from a
 * synced {@code LibrarySnapshotDto} rather than a real filesystem scan): every {@link
 * AlbumMetaDataFileEntity} on the tree must have {@code setLoaded(true)} called after its fields
 * are populated, before calling {@link #storeAggregateRoot}. Otherwise the first read of a
 * metadata field triggers a real filesystem read/write against a path that doesn't exist on that
 * machine -- see {@code ensureLoaded()} on that class. The tree's {@code parentLocation} must also
 * already be set (see {@link RootFolderEntity#setParentLocation}) before calling {@link
 * #storeAggregateRoot}, since this repository sources {@code location_id} from it.
 *
 * <p><b>Known limitation</b>: compilation-album artists that only exist as a song-embedded artist
 * name (no real {@code ArtistFolderEntity} folder on disk -- see {@code ArtistFromSongEntity}) are
 * not currently round-tripped through this schema, since {@code RootFolderEntity.artistsFromSongs}
 * is populated by {@code SongScanner} at scan time, not derived at {@code initialize()} time.
 * Compilation-album artist search may be incomplete on a JPA-hydrated master root until a
 * follow-up adds these to the schema.
 *
 * @author tmyers
 */
public final class SongLibraryRepositoryJpaImpl implements SongLibraryRepository {

  private final EntityManager entityManager;
  private final TransactionTemplate transactionTemplate;
  private final ExecutorService persistenceExecutor = Executors.newSingleThreadExecutor();

  private volatile RootFolderEntity root;

  public SongLibraryRepositoryJpaImpl(EntityManagerFactory entityManagerFactory,
      PlatformTransactionManager transactionManager) {

    requireNonNull(entityManagerFactory, "entityManagerFactory cannot be null");
    requireNonNull(transactionManager, "transactionManager cannot be null");

    this.entityManager = SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory);
    this.transactionTemplate = new TransactionTemplate(transactionManager);
  }

  @Override
  public RootFolderEntity loadAggregateRoot(String naturalIdentity)
      throws EntityDoesNotExistException {

    try {
      return loadAggregateRoot(Integer.parseInt(naturalIdentity.trim()));
    } catch (NumberFormatException nfe) {
      throw new SongLibraryServiceException(
          "SongLibraryRepositoryJpaImpl requires an integer locationId as the natural identity, got: ["
              + naturalIdentity + "]", nfe);
    }
  }

  @Override
  public RootFolderEntity loadAggregateRoot(int locationId) throws EntityDoesNotExistException {

    List<SongLibraryJpaEntity> rows = transactionTemplate.execute(status -> entityManager
        .createQuery("from SongLibraryJpaEntity where locationId = :locationId",
            SongLibraryJpaEntity.class)
        .setParameter("locationId", locationId)
        .getResultList());

    if (rows == null || rows.isEmpty()) {
      throw new EntityDoesNotExistException(
          "No song library found for locationId: [" + locationId + "].");
    }

    RootFolderEntity assembled = assembleRoot(locationId, rows);
    this.root = assembled;
    return assembled;
  }

  @Override
  public void storeAggregateRoot(RootFolderEntity root) {

    requireNonNull(root, "root cannot be null");
    requireNonNull(root.getParentLocation(),
        "root.getParentLocation() cannot be null when storing via SongLibraryRepositoryJpaImpl");
    requireNonNull(root.getId(),
        "root.getId() cannot be null when storing via SongLibraryRepositoryJpaImpl");
    Integer locationId = root.getParentLocation().getPersistentIdentity();

    transactionTemplate.executeWithoutResult(status -> {

      List<SongLibraryJpaEntity> existingRows = entityManager
          .createQuery("from SongLibraryJpaEntity where locationId = :locationId",
              SongLibraryJpaEntity.class)
          .setParameter("locationId", locationId)
          .getResultList();

      Map<Integer, SongLibraryJpaEntity> existingById = new HashMap<>();
      for (SongLibraryJpaEntity row : existingRows) {
        existingById.put(row.getId(), row);
      }

      Map<String, SongLibraryJpaEntity> existingByPath = new HashMap<>();
      Map<Integer, String> pathCache = new HashMap<>();
      for (SongLibraryJpaEntity row : existingRows) {
        existingByPath.put(existingRowPath(row, existingById, pathCache).toLowerCase(), row);
      }

      // Every id currently occupying a row for this location -- whether it ends up matched-and-
      // kept or unmatched-and-deleted below -- is off-limits for a brand-new node's insert.
      // SongScanner restarts its own counter at 1 on every scan, so a genuinely new node can
      // easily be handed an id some other still-present row already owns; claiming the full
      // existing id space up front (not just the matched subset) avoids a same-transaction
      // primary-key collision with a row that hasn't been deleted yet.
      Set<Integer> claimedIds = new HashSet<>(existingById.keySet());

      int maxExistingId = existingRows.stream().mapToInt(SongLibraryJpaEntity::getId).max().orElse(0);
      AtomicInteger fallbackId = new AtomicInteger(maxExistingId + 1);

      Set<Integer> survivingIds = new HashSet<>();

      Integer rootId = reconcileRow(SongLibraryJpaEntity.LibraryItemType.ROOT,
          root.getNaturalIdentity(), locationId, null, root.getRootPath(), root.getId(),
          existingByPath, claimedIds, fallbackId, row -> {}, survivingIds);
      root.setId(rootId);

      reconcileFolderRowsRecursively(root, locationId, rootId, existingByPath, claimedIds, fallbackId,
          survivingIds);

      for (SongLibraryJpaEntity existing : existingRows) {
        if (!survivingIds.contains(existing.getId())) {
          entityManager.remove(existing);
        }
      }
    });

    this.root = root;
  }

  @Override
  public void storeSongLibraryAsync() throws EntityDoesNotExistException {

    RootFolderEntity rootToPersist = this.root;
    if (rootToPersist == null) {
      throw new EntityDoesNotExistException("No song library root has been loaded or stored yet.");
    }

    persistenceExecutor.submit(() -> {
      try {
        storeAggregateRoot(rootToPersist);
      } catch (Exception e) {
        throw new SongLibraryServiceException("Could not asynchronously persist song library", e);
      }
    });
  }

  // ── load-side assembly ──────────────────────────────────────────────────

  private RootFolderEntity assembleRoot(Integer locationId, List<SongLibraryJpaEntity> rows) {

    SongLibraryJpaEntity rootRow = rows.stream()
        .filter(r -> r.getClassDiscriminator() == SongLibraryJpaEntity.LibraryItemType.ROOT)
        .findFirst()
        .orElseThrow(() -> new SongLibraryServiceException(
            "No ROOT row found for locationId: [" + locationId + "]."));

    RootFolderEntity root = new RootFolderEntity(rootRow.getName());
    root.setId(rootRow.getId());

    Map<Integer, FolderEntity> builtFoldersById = new HashMap<>();
    builtFoldersById.put(rootRow.getId(), root);

    Map<Integer, List<SongLibraryJpaEntity>> childRowsByParentId = new HashMap<>();
    List<SongLibraryJpaEntity> songRows = new ArrayList<>();
    for (SongLibraryJpaEntity row : rows) {
      if (row.getClassDiscriminator() == SongLibraryJpaEntity.LibraryItemType.ROOT) {
        continue;
      }
      if (row.getClassDiscriminator() == SongLibraryJpaEntity.LibraryItemType.SONG) {
        songRows.add(row);
      } else if (row.getParentFolderId() != null) {
        childRowsByParentId.computeIfAbsent(row.getParentFolderId(), k -> new ArrayList<>())
            .add(row);
      }
    }

    buildChildFolders(root, rootRow.getId(), childRowsByParentId, builtFoldersById);

    for (SongLibraryJpaEntity songRow : songRows) {
      attachSong(songRow, builtFoldersById);
    }

    root.initialize();
    return root;
  }

  private void buildChildFolders(FolderEntity parent, Integer parentId,
      Map<Integer, List<SongLibraryJpaEntity>> childRowsByParentId,
      Map<Integer, FolderEntity> builtFoldersById) {

    List<SongLibraryJpaEntity> childRows = childRowsByParentId.get(parentId);
    if (childRows == null) {
      return;
    }

    try {
      for (SongLibraryJpaEntity row : childRows) {

        FolderEntity built = switch (row.getClassDiscriminator()) {
          case GENRE -> new GenreFolderEntity(parent, row.getName());
          case ARTIST -> new ArtistFolderEntity(parent, row.getName());
          case ALBUM -> buildAlbumFromRow(parent, row);
          case FOLDER -> new FolderEntity(parent, row.getName());
          case ROOT, SONG_ARTIST, SONG -> throw new SongLibraryServiceException(
              "Unexpected " + row.getClassDiscriminator() + " row with id: [" + row.getId() + "].");
        };
        built.setId(row.getId());

        parent.addChildFolder(built);
        builtFoldersById.put(row.getId(), built);

        if (!(built instanceof AlbumFolderEntity)) {
          buildChildFolders(built, row.getId(), childRowsByParentId, builtFoldersById);
        }
      }
    } catch (EntityAlreadyExistsException e) {
      throw new SongLibraryServiceException("Could not assemble song library tree for locationId: ["
          + (parent instanceof RootFolderEntity ? parent.getName() : parent.getRootFolder().getName())
          + "]", e);
    }
  }

  /**
   * Rehydrates the album's metadata/cover-art from this row's own columns -- see {@link
   * SongLibraryJpaEntity}'s class javadoc for why these no longer live in a separate child row.
   */
  private AlbumFolderEntity buildAlbumFromRow(FolderEntity parent, SongLibraryJpaEntity row) {

    AlbumFolderEntity album = new AlbumFolderEntity(parent, row.getName());
    album.createCoverArtEntity();
    album.createMetadataEntity();

    AlbumMetaDataFileEntity metaData = album.getMetaData();
    metaData.setGenre(row.getAlbumGenre());
    metaData.setCoverArtUrl(row.getAlbumCoverArtUrl());
    metaData.setRecordLabel(row.getAlbumRecordLabel());
    metaData.setReleaseDate(row.getAlbumReleaseDate());
    metaData.setHasExplicit(Boolean.TRUE.equals(row.getAlbumHasExplicit()));
    // Individual setters only -- writeMetadataToFileSystem() performs real disk I/O against a
    // path that doesn't exist for a JPA-hydrated root (see class javadoc's caller contract).
    metaData.setLoaded(true);

    return album;
  }

  private void attachSong(SongLibraryJpaEntity songRow, Map<Integer, FolderEntity> builtFoldersById) {

    FolderEntity parent = builtFoldersById.get(songRow.getParentFolderId());
    if (!(parent instanceof AlbumFolderEntity album)) {
      throw new SongLibraryServiceException(
          "Song row references unknown/non-album parent folder id: [" + songRow.getParentFolderId()
              + "].");
    }

    SongFileEntity song = new SongFileEntity(album, songRow.getName());
    song.setId(songRow.getId());
    song.setArtistName(songRow.getSongArtistName());
    song.setSongName(songRow.getSongName());
    song.setTrackNumber(songRow.getSongTrackNumber());
    song.setNumPlays(songRow.getSongNumPlays() != null ? songRow.getSongNumPlays() : Integer.valueOf(0));
    try {
      album.addChildSong(song);
    } catch (EntityAlreadyExistsException e) {
      throw new SongLibraryServiceException("Could not attach song to album: " + album.getName(), e);
    }
  }

  // ── store-side reconciliation (diff-based upsert) ───────────────────────

  /**
   * Reconstructs an existing row's natural-identity path purely from the flat rows already loaded
   * for this location (no domain-object rehydration needed) -- mirrors {@code
   * AbstractLibraryEntity#getNaturalIdentity()}'s own name-chain-joined-by-{@link
   * File#separatorChar} composition exactly, so it's directly comparable against the incoming
   * tree's real {@code getNaturalIdentity()} values. A row whose parent chain is broken (dangling
   * {@code parentFolderId}, e.g. leftover data from a prior bug) gets a path that can never match
   * anything real, so it naturally falls out as unmatched -- and therefore deleted -- below.
   */
  private String existingRowPath(SongLibraryJpaEntity row, Map<Integer, SongLibraryJpaEntity> existingById,
      Map<Integer, String> pathCache) {

    String cached = pathCache.get(row.getId());
    if (cached != null) {
      return cached;
    }

    String path;
    if (row.getClassDiscriminator() == SongLibraryJpaEntity.LibraryItemType.ROOT
        || row.getParentFolderId() == null) {
      path = row.getName();
    } else {
      SongLibraryJpaEntity parentRow = existingById.get(row.getParentFolderId());
      path = parentRow == null
          ? "##ORPHAN-ROW-NO-PARENT##:" + row.getId()
          : existingRowPath(parentRow, existingById, pathCache) + File.separatorChar + row.getName();
    }

    pathCache.put(row.getId(), path);
    return path;
  }

  private void reconcileFolderRowsRecursively(FolderEntity parent, Integer locationId, Integer parentId,
      Map<String, SongLibraryJpaEntity> existingByPath, Set<Integer> claimedIds, AtomicInteger fallbackId,
      Set<Integer> survivingIds) {

    for (FolderEntity child : parent.getChildFolders()) {

      if (child instanceof AlbumFolderEntity albumFolder) {
        Integer albumId = reconcileRow(SongLibraryJpaEntity.LibraryItemType.ALBUM,
            albumFolder.getNaturalIdentity(), locationId, parentId, albumFolder.getName(),
            albumFolder.getId(), existingByPath, claimedIds, fallbackId,
            row -> applyAlbumFields(row, albumFolder), survivingIds);
        albumFolder.setId(albumId);
        reconcileSongRows(albumFolder, locationId, albumId, existingByPath, claimedIds, fallbackId,
            survivingIds);
        continue;
      }

      SongLibraryJpaEntity.LibraryItemType discriminator;
      if (child instanceof ArtistFolderEntity) {
        discriminator = SongLibraryJpaEntity.LibraryItemType.ARTIST;
      } else if (child instanceof GenreFolderEntity) {
        discriminator = SongLibraryJpaEntity.LibraryItemType.GENRE;
      } else {
        throw new SongLibraryServiceException(
            "Unexpected folder type while storing song library: " + child.getClass().getSimpleName());
      }

      Integer childId = reconcileRow(discriminator, child.getNaturalIdentity(), locationId, parentId,
          child.getName(), child.getId(), existingByPath, claimedIds, fallbackId, row -> {},
          survivingIds);
      child.setId(childId);

      reconcileFolderRowsRecursively(child, locationId, childId, existingByPath, claimedIds, fallbackId,
          survivingIds);
    }
  }

  private void reconcileSongRows(AlbumFolderEntity album, Integer locationId, Integer albumId,
      Map<String, SongLibraryJpaEntity> existingByPath, Set<Integer> claimedIds, AtomicInteger fallbackId,
      Set<Integer> survivingIds) {

    for (SongFileEntity song : album.getChildSongs()) {

      Integer songId = reconcileRow(SongLibraryJpaEntity.LibraryItemType.SONG,
          song.getNaturalIdentity(), locationId, albumId, song.getName(), song.getId(), existingByPath,
          claimedIds, fallbackId, row -> applySongFields(row, song), survivingIds);
      song.setId(songId);
    }
  }

  /**
   * Matches one incoming node (folder or song) against the location's existing rows by natural
   * identity. A match is updated in place via managed-entity setters (Hibernate's dirty checking
   * only issues an {@code UPDATE} for columns that actually changed) and keeps its existing id. No
   * match means an insert: the node's own incoming id is kept unless it collides with an id
   * that's already claimed (by a match elsewhere in the tree, or by an earlier new node in this
   * same store), in which case it's renumbered from {@code fallbackId}.
   */
  private Integer reconcileRow(SongLibraryJpaEntity.LibraryItemType type, String naturalIdentity,
      Integer locationId, Integer parentId, String name, Integer incomingId,
      Map<String, SongLibraryJpaEntity> existingByPath, Set<Integer> claimedIds, AtomicInteger fallbackId,
      Consumer<SongLibraryJpaEntity> applyTypeSpecificFields, Set<Integer> survivingIds) {

    SongLibraryJpaEntity existing = existingByPath.get(naturalIdentity.toLowerCase());

    if (existing != null) {
      existing.setName(name);
      existing.setParentFolderId(parentId);
      existing.setClassDiscriminator(type);
      applyTypeSpecificFields.accept(existing);
      survivingIds.add(existing.getId());
      return existing.getId();
    }

    Integer finalId = incomingId;
    while (finalId == null || claimedIds.contains(finalId)) {
      finalId = fallbackId.getAndIncrement();
    }
    claimedIds.add(finalId);

    SongLibraryJpaEntity row = new SongLibraryJpaEntity(locationId, finalId, name, parentId, type);
    applyTypeSpecificFields.accept(row);
    entityManager.persist(row);
    survivingIds.add(finalId);
    return finalId;
  }

  private void applyAlbumFields(SongLibraryJpaEntity row, AlbumFolderEntity album) {

    AlbumMetaDataFileEntity metaData = album.getMetaData();
    if (metaData != null) {
      row.setAlbumGenre(metaData.getGenre());
      row.setAlbumCoverArtUrl(metaData.getCoverArtUrl());
      row.setAlbumRecordLabel(metaData.getRecordLabel());
      row.setAlbumReleaseDate(metaData.getReleaseDate());
      row.setAlbumHasExplicit(metaData.hasExplicit());
    }
  }

  private void applySongFields(SongLibraryJpaEntity row, SongFileEntity song) {

    row.setSongArtistName(song.getArtistName());
    row.setSongName(song.getSongName());
    row.setSongTrackNumber(song.getTrackNumber());
    row.setSongNumPlays(song.getNumPlays());
  }

  @Override
  public Integer updateNumPlaysForSong(
      RootFolderEntity root,
      Integer locationId,
      Integer albumId,
      Integer songId,
      Integer numPlays) throws EntityDoesNotExistException {

    requireNonNull(locationId, "locationId cannot be null");
    requireNonNull(albumId, "albumId cannot be null");
    requireNonNull(songId, "songId cannot be null");
    requireNonNull(numPlays, "numPlays cannot be null");

    // id alone would already resolve the exact row (it's unique per location across every
    // discriminator), but matching parentFolderId too preserves the caller-facing guarantee that
    // a mismatched albumId/songId pair is rejected rather than silently updating the wrong album's
    // song.
    int rowsUpdated = transactionTemplate.execute(status -> entityManager
        .createQuery("update SongLibraryJpaEntity e set e.songNumPlays = :numPlays "
            + "where e.locationId = :locationId and e.id = :songId "
            + "and e.classDiscriminator = :songType and e.parentFolderId = :albumId")
        .setParameter("numPlays", numPlays)
        .setParameter("locationId", locationId)
        .setParameter("songId", songId)
        .setParameter("songType", SongLibraryJpaEntity.LibraryItemType.SONG)
        .setParameter("albumId", albumId)
        .executeUpdate());

    if (rowsUpdated == 0) {
      throw new EntityDoesNotExistException("No song found for locationId: [" + locationId
          + "], albumId: [" + albumId + "], songId: [" + songId + "].");
    }

    this.root = root;
    return numPlays;
  }
}
