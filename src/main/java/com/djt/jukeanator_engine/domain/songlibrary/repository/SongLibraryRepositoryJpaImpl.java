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
import com.djt.jukeanator_engine.domain.songlibrary.model.LocationMetaDataFileEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.RootFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.SongFileEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

/**
 * JPA/Hibernate-backed implementation of {@link SongLibraryRepository}. One database (and one
 * pair of tables, {@code song_library_folders}/{@code song_library_files}) holds every location's
 * catalog, tenant-separated by {@code location_id} -- see {@link SongLibraryFolderJpaEntity}/
 * {@link SongLibraryFileJpaEntity} for the flat row shape and why they're separate types from the
 * domain object graph.
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
 * <p><b>Caller contract for a synthetically-built root</b> (e.g. a master populating this from a
 * synced {@code LibrarySnapshotDto} rather than a real filesystem scan): every
 * {@link AlbumMetaDataFileEntity}/{@link LocationMetaDataFileEntity} on the tree must have {@code
 * setLoaded(true)} called after its fields are populated, before calling
 * {@link #storeAggregateRoot}. Otherwise the first read of a metadata field triggers a real
 * filesystem read/write against a path that doesn't exist on that machine -- see the {@code
 * ensureLoaded()} methods on those two classes.
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

    List<SongLibraryFolderJpaEntity> folderRows = transactionTemplate.execute(status -> entityManager
        .createQuery("from SongLibraryFolderJpaEntity where locationId = :locationId",
            SongLibraryFolderJpaEntity.class)
        .setParameter("locationId", locationId)
        .getResultList());

    if (folderRows == null || folderRows.isEmpty()) {
      throw new EntityDoesNotExistException(
          "No song library found for locationId: [" + locationId + "].");
    }

    List<SongLibraryFileJpaEntity> fileRows = transactionTemplate.execute(status -> entityManager
        .createQuery("from SongLibraryFileJpaEntity where locationId = :locationId",
            SongLibraryFileJpaEntity.class)
        .setParameter("locationId", locationId)
        .getResultList());

    RootFolderEntity assembled = assembleRoot(locationId, folderRows, fileRows);
    this.root = assembled;
    return assembled;
  }

  @Override
  public void storeAggregateRoot(RootFolderEntity root) {

    requireNonNull(root, "root cannot be null");
    Integer locationId = root.getMetadata().getLocationId();
    requireNonNull(locationId,
        "root.getMetadata().getLocationId() cannot be null when storing via SongLibraryRepositoryJpaImpl");

    transactionTemplate.executeWithoutResult(status -> {

      entityManager.createQuery("delete from SongLibraryFileJpaEntity where locationId = :locationId")
          .setParameter("locationId", locationId)
          .executeUpdate();
      entityManager.createQuery("delete from SongLibraryFolderJpaEntity where locationId = :locationId")
          .setParameter("locationId", locationId)
          .executeUpdate();

      SongLibraryFolderJpaEntity rootRow = new SongLibraryFolderJpaEntity(locationId,
          SongLibraryFolderJpaEntity.FolderType.ROOT, null, null, root.getRootPath());
      entityManager.persist(rootRow);

      insertFolderRowsRecursively(root, locationId, rootRow.getPersistentIdentity());

      LocationMetaDataFileEntity metadata = root.getMetadata();
      SongLibraryFileJpaEntity metadataRow = new SongLibraryFileJpaEntity(locationId,
          rootRow.getPersistentIdentity(), SongLibraryFileJpaEntity.FileType.LOCATION_METADATA, null,
          LocationMetaDataFileEntity.LOCATION_METADATA_FILENAME);
      metadataRow.setLocationName(metadata.getLocationName());
      metadataRow.setLogoName(metadata.getLogoName());
      metadataRow.setLatitude(metadata.getLatitude());
      metadataRow.setLongitude(metadata.getLongitude());
      metadataRow.setIsGeoFenced(metadata.isGeoFenced());
      entityManager.persist(metadataRow);
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

  private RootFolderEntity assembleRoot(Integer locationId,
      List<SongLibraryFolderJpaEntity> folderRows, List<SongLibraryFileJpaEntity> fileRows) {

    SongLibraryFolderJpaEntity rootRow = folderRows.stream()
        .filter(r -> r.getFolderType() == SongLibraryFolderJpaEntity.FolderType.ROOT)
        .findFirst()
        .orElseThrow(() -> new SongLibraryServiceException(
            "No ROOT folder row found for locationId: [" + locationId + "]."));

    RootFolderEntity root = new RootFolderEntity(rootRow.getName());

    Map<Integer, FolderEntity> builtFoldersByRowId = new HashMap<>();
    builtFoldersByRowId.put(rootRow.getPersistentIdentity(), root);

    Map<Integer, List<SongLibraryFolderJpaEntity>> childRowsByParentRowId = new HashMap<>();
    for (SongLibraryFolderJpaEntity row : folderRows) {
      if (row.getParentFolderId() != null) {
        childRowsByParentRowId.computeIfAbsent(row.getParentFolderId(), k -> new ArrayList<>())
            .add(row);
      }
    }

    buildChildFolders(root, rootRow.getPersistentIdentity(), childRowsByParentRowId,
        builtFoldersByRowId);

    for (SongLibraryFileJpaEntity fileRow : fileRows) {
      attachFile(fileRow, builtFoldersByRowId);
    }

    root.initialize();
    return root;
  }

  private void buildChildFolders(FolderEntity parent, Integer parentRowId,
      Map<Integer, List<SongLibraryFolderJpaEntity>> childRowsByParentRowId,
      Map<Integer, FolderEntity> builtFoldersByRowId) {

    List<SongLibraryFolderJpaEntity> childRows = childRowsByParentRowId.get(parentRowId);
    if (childRows == null) {
      return;
    }

    try {
      for (SongLibraryFolderJpaEntity row : childRows) {

        FolderEntity built = switch (row.getFolderType()) {
          case GENRE -> new GenreFolderEntity(parent, row.getName());
          case ARTIST -> new ArtistFolderEntity(parent, row.getName());
          case ALBUM -> new AlbumFolderEntity(parent, row.getName());
          case ROOT -> throw new SongLibraryServiceException(
              "Unexpected nested ROOT folder row with id: [" + row.getPersistentIdentity() + "].");
        };
        // The row's own persistentIdentity is a surrogate database key used only for
        // parent_folder_id linkage during assembly -- the domain object's public id is the
        // scan-local sourceId column, which is what getAlbumById/getGenreById/etc. are keyed by.
        built.setPersistentIdentity(row.getSourceId());

        if (built instanceof AlbumFolderEntity albumFolder) {
          albumFolder.createCoverArtEntity();
          albumFolder.createMetadataEntity();
        }

        parent.addChildFolder(built);
        builtFoldersByRowId.put(row.getPersistentIdentity(), built);

        buildChildFolders(built, row.getPersistentIdentity(), childRowsByParentRowId,
            builtFoldersByRowId);
      }
    } catch (EntityAlreadyExistsException e) {
      throw new SongLibraryServiceException("Could not assemble song library tree for locationId: ["
          + (parent instanceof RootFolderEntity ? parent.getName() : parent.getRootFolder().getName())
          + "]", e);
    }
  }

  private void attachFile(SongLibraryFileJpaEntity fileRow,
      Map<Integer, FolderEntity> builtFoldersByRowId) {

    FolderEntity parent = builtFoldersByRowId.get(fileRow.getParentFolderId());
    if (parent == null) {
      throw new SongLibraryServiceException(
          "File row references unknown parent folder row id: [" + fileRow.getParentFolderId() + "].");
    }

    switch (fileRow.getFileType()) {

      case SONG -> {
        AlbumFolderEntity album = (AlbumFolderEntity) parent;
        SongFileEntity song = new SongFileEntity(album, fileRow.getName());
        song.setPersistentIdentity(fileRow.getSourceId());
        song.setArtistName(fileRow.getArtistName());
        song.setSongName(fileRow.getSongName());
        song.setTrackNumber(fileRow.getTrackNumber());
        song.setNumPlays(fileRow.getNumPlays() != null ? fileRow.getNumPlays() : Integer.valueOf(0));
        try {
          album.addChildSong(song);
        } catch (EntityAlreadyExistsException e) {
          throw new SongLibraryServiceException("Could not attach song to album: " + album.getName(),
              e);
        }
      }

      case ALBUM_METADATA -> {
        AlbumFolderEntity album = (AlbumFolderEntity) parent;
        AlbumMetaDataFileEntity metaData = album.getMetaData();
        // Individual setters only -- writeMetadataToFileSystem() performs real disk I/O against a
        // path that doesn't exist for a JPA-hydrated root (see class javadoc's caller contract).
        metaData.setGenre(fileRow.getGenre());
        metaData.setCoverArtUrl(fileRow.getCoverArtUrl());
        metaData.setRecordLabel(fileRow.getRecordLabel());
        metaData.setReleaseDate(fileRow.getReleaseDate());
        metaData.setHasExplicit(Boolean.TRUE.equals(fileRow.getHasExplicit()));
        metaData.setLoaded(true);
      }

      case LOCATION_METADATA -> {
        RootFolderEntity rootFolder = (RootFolderEntity) parent;
        LocationMetaDataFileEntity metadata = rootFolder.getMetadata();
        metadata.setLocationId(fileRow.getLocationId());
        metadata.setLocationName(fileRow.getLocationName());
        metadata.setLogoName(fileRow.getLogoName());
        metadata.setLatitude(fileRow.getLatitude());
        metadata.setLongitude(fileRow.getLongitude());
        metadata.setGeoFenced(Boolean.TRUE.equals(fileRow.getIsGeoFenced()));
        metadata.setLoaded(true);
      }

      case ALBUM_COVER_ART -> {
        // AlbumFolderEntity.createCoverArtEntity() already synthesized a fresh
        // AlbumCoverArtFileEntity when the album folder was built above -- there is no real file
        // on master's disk to reference (cover art bytes are served separately by
        // LocationService), so there is nothing further to attach.
      }
    }
  }

  // ── store-side decomposition ────────────────────────────────────────────

  private void insertFolderRowsRecursively(FolderEntity parent, Integer locationId,
      Integer parentRowId) {

    for (FolderEntity child : parent.getChildFolders()) {

      SongLibraryFolderJpaEntity.FolderType folderType;
      if (child instanceof AlbumFolderEntity) {
        folderType = SongLibraryFolderJpaEntity.FolderType.ALBUM;
      } else if (child instanceof ArtistFolderEntity) {
        folderType = SongLibraryFolderJpaEntity.FolderType.ARTIST;
      } else if (child instanceof GenreFolderEntity) {
        folderType = SongLibraryFolderJpaEntity.FolderType.GENRE;
      } else {
        throw new SongLibraryServiceException(
            "Unexpected folder type while storing song library: " + child.getClass().getSimpleName());
      }

      SongLibraryFolderJpaEntity row = new SongLibraryFolderJpaEntity(locationId, folderType,
          parentRowId, child.getPersistentIdentity(), child.getName());
      entityManager.persist(row);

      if (child instanceof AlbumFolderEntity albumFolder) {
        insertAlbumFileRows(albumFolder, locationId, row.getPersistentIdentity());
      } else {
        insertFolderRowsRecursively(child, locationId, row.getPersistentIdentity());
      }
    }
  }

  private void insertAlbumFileRows(AlbumFolderEntity album, Integer locationId, Integer albumRowId) {

    for (SongFileEntity song : album.getChildSongs()) {

      SongLibraryFileJpaEntity row = new SongLibraryFileJpaEntity(locationId, albumRowId,
          SongLibraryFileJpaEntity.FileType.SONG, song.getPersistentIdentity(), song.getName());
      row.setArtistName(song.getArtistName());
      row.setSongName(song.getSongName());
      row.setTrackNumber(song.getTrackNumber());
      row.setNumPlays(song.getNumPlays());
      entityManager.persist(row);
    }

    AlbumMetaDataFileEntity metaData = album.getMetaData();
    if (metaData != null) {
      SongLibraryFileJpaEntity metadataRow = new SongLibraryFileJpaEntity(locationId, albumRowId,
          SongLibraryFileJpaEntity.FileType.ALBUM_METADATA, null, AlbumFolderEntity.METADATA_FILENAME);
      metadataRow.setGenre(metaData.getGenre());
      metadataRow.setCoverArtUrl(metaData.getCoverArtUrl());
      metadataRow.setRecordLabel(metaData.getRecordLabel());
      metadataRow.setReleaseDate(metaData.getReleaseDate());
      metadataRow.setHasExplicit(metaData.hasExplicit());
      entityManager.persist(metadataRow);
    }
  }
}
