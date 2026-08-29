package com.djt.jukeanator_engine.domain.songlibrary.repository;

import static java.util.Objects.requireNonNull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
 * loaded it. {@code storeAggregateRoot(root)} does the reverse: delete every row for that
 * location, then walk the tree and re-insert -- simpler and safe here because the tree handed in
 * is always fully materialized already, unlike {@code UserRepositoryJpaImpl}'s diff/orphan-delete
 * approach for a root that's mutated incrementally over its lifetime.
 *
 * <p><b>id is application-assigned, not Hibernate-generated</b>: {@code SongScanner}'s single
 * shared per-scan counter assigns every folder/file a unique id across the whole location (see
 * {@code AbstractLibraryEntity#getId}) -- root, genres, artists, albums, and songs must all
 * already have a non-null id before {@link #storeAggregateRoot} is called, since {@code id} is
 * part of this table's primary key.
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

      entityManager.createQuery("delete from SongLibraryJpaEntity where locationId = :locationId")
          .setParameter("locationId", locationId)
          .executeUpdate();

      entityManager.persist(new SongLibraryJpaEntity(locationId, root.getId(), root.getRootPath(),
          null, SongLibraryJpaEntity.LibraryItemType.ROOT));

      insertFolderRowsRecursively(root, locationId, root.getId());
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

  // ── store-side decomposition ────────────────────────────────────────────

  private void insertFolderRowsRecursively(FolderEntity parent, Integer locationId, Integer parentId) {

    for (FolderEntity child : parent.getChildFolders()) {

      if (child instanceof AlbumFolderEntity albumFolder) {
        entityManager.persist(buildAlbumRow(albumFolder, locationId, parentId));
        insertSongRows(albumFolder, locationId);
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

      entityManager.persist(
          new SongLibraryJpaEntity(locationId, child.getId(), child.getName(), parentId, discriminator));
      insertFolderRowsRecursively(child, locationId, child.getId());
    }
  }

  private SongLibraryJpaEntity buildAlbumRow(AlbumFolderEntity album, Integer locationId,
      Integer parentId) {

    SongLibraryJpaEntity row = new SongLibraryJpaEntity(locationId, album.getId(), album.getName(),
        parentId, SongLibraryJpaEntity.LibraryItemType.ALBUM);

    AlbumMetaDataFileEntity metaData = album.getMetaData();
    if (metaData != null) {
      row.setAlbumGenre(metaData.getGenre());
      row.setAlbumCoverArtUrl(metaData.getCoverArtUrl());
      row.setAlbumRecordLabel(metaData.getRecordLabel());
      row.setAlbumReleaseDate(metaData.getReleaseDate());
      row.setAlbumHasExplicit(metaData.hasExplicit());
    }
    return row;
  }

  private void insertSongRows(AlbumFolderEntity album, Integer locationId) {

    for (SongFileEntity song : album.getChildSongs()) {

      SongLibraryJpaEntity row = new SongLibraryJpaEntity(locationId, song.getId(), song.getName(),
          album.getId(), SongLibraryJpaEntity.LibraryItemType.SONG);
      row.setSongArtistName(song.getArtistName());
      row.setSongName(song.getSongName());
      row.setSongTrackNumber(song.getTrackNumber());
      row.setSongNumPlays(song.getNumPlays());
      entityManager.persist(row);
    }
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
