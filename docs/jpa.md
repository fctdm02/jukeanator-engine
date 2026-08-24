# PROMPT
For Object to Relational Mapping with JPA, explain why each column is in this table:

CREATE TABLE `song_library_files` (
  `persistent_identity` int NOT NULL,
  `location_id` int NOT NULL,
  `parent_folder_id` int NOT NULL,
  `file_type` varchar(20) NOT NULL,
  `source_id` int DEFAULT NULL,
  `name` varchar(500) NOT NULL,
  `artist_name` varchar(500) DEFAULT NULL,
  `song_name` varchar(500) DEFAULT NULL,
  `track_number` int DEFAULT NULL,
  `num_plays` int DEFAULT NULL,
  `genre` varchar(255) DEFAULT NULL,
  `cover_art_url` varchar(1000) DEFAULT NULL,
  `record_label` varchar(255) DEFAULT NULL,
  `release_date` varchar(20) DEFAULT NULL,
  `has_explicit` tinyint(1) DEFAULT NULL,
  `location_name` varchar(255) DEFAULT NULL,
  `logo_name` varchar(255) DEFAULT NULL,
  `latitude` double DEFAULT NULL,
  `longitude` double DEFAULT NULL,
  `is_geo_fenced` tinyint(1) DEFAULT NULL,



 # ANSWER
 Here's what each column is doing, given the design explained in the migration's header comment (V3__init_song_library_schema.sql:1-17) and the [SongLibraryFileJpaEntity](src/main/java/com/djt/jukeanator_engine/domain/songlibrary/repository/SongLibraryFileJpaEntity.java) that maps it.

## The core idea: single-table inheritance, done by hand

`song_library_files` is a flat, SINGLE_TABLE-style mapping for four different domain subtypes of `AbstractFileEntity` — `SongFileEntity`, `AlbumCoverArtFileEntity`, `AlbumMetaDataFileEntity`, and `LocationMetaDataFileEntity`. Rather than using JPA's `@Inheritance(strategy = SINGLE_TABLE)` with `@DiscriminatorColumn`, this is a plain `@Entity` with `file_type` acting as a hand-rolled discriminator, and `SongLibraryRepositoryJpaImpl` presumably does the row→subtype assembly manually. That's why most columns are nullable and grouped into "SONG columns" / "ALBUM_METADATA columns" / "LOCATION_METADATA columns" comment blocks — each group is only populated when `file_type` matches that variant.

## Column-by-column

**Identity / structural columns (shared by all 4 file types):**

- **`persistent_identity`** — the JPA `@Id`, generated from the shared `persistent_identity_seq` used by every `AbstractPersistentEntity` subclass across the whole schema. This is the surrogate primary key, not a business key.
- **`location_id`** — the tenant-partitioning column. Per the migration header, every location's catalog is stored in this shared table, so every row must be scoped to a `locations` row (FK `fk_song_library_files_location`). This is what makes it multi-tenant rather than one-table-per-location.
- **`parent_folder_id`** — FK into `song_library_folders`, placing the file inside the folder tree (Root/Genre/Artist/Album) that `SongLibraryRepositoryJpaImpl` reassembles into a `RootFolderEntity`.
- **`file_type`** — the discriminator: `SONG | ALBUM_COVER_ART | ALBUM_METADATA | LOCATION_METADATA`. This single column is what tells you which of the nullable column groups below is meaningful for a given row.
- **`source_id`** — the scan-local id assigned by `SongScanner` on the owning slave (per the migration comment, "never merged or reused across locations"). It's `NULL` for the three singleton file types (cover art / album metadata / location metadata each occur once per album/location and don't need their own per-instance id) and populated only for `SONG` rows.
- **`name`** — the file's display/file-system name, required for all four types.

**SONG columns** (populated only when `file_type = SONG`, mapping `SongFileEntity`):
- **`artist_name`**, **`song_name`** — the track's metadata as read from the file/tag.
- **`track_number`** — position within the album, for ordering/playback.
- **`num_plays`** — a play-count counter tracked per song (likely incremented as it's played, supporting features like popularity sorting).

**ALBUM_METADATA columns** (populated only when `file_type = ALBUM_METADATA`, mapping `AlbumMetaDataFileEntity`, one singleton row per album folder):
- **`genre`**, **`record_label`**, **`release_date`** — album-level catalog metadata.
- **`cover_art_url`** — presumably a remote/cached URL for artwork, distinct from `ALBUM_COVER_ART` file rows which likely represent a locally scanned cover art file.
- **`has_explicit`** — content flag for the album.

**LOCATION_METADATA columns** (populated only when `file_type = LOCATION_METADATA`, mapping `LocationMetaDataFileEntity`, one singleton row per location):
- **`location_name`**, **`logo_name`** — display metadata for the venue/location, denormalized into this row rather than joined from the `locations` table (this row is presumably a snapshot the slave scanned/synced, distinct from the authoritative `locations` table already FK'd via `location_id`).
- **`latitude`**, **`longitude`**, **`is_geo_fenced`** — geofencing data, likely used to restrict playback/access to users physically at the location.

## Why it's shaped this way rather than 4 normalized tables

The migration comment is explicit about the tradeoff: this design deliberately avoids a natural composite key (`location_id, albumId, songId`) because folder rows (root/genre/artist) have no such tuple, so everything — files and folders — uses a generated surrogate key instead, consistent with the rest of the schema's `AbstractPersistentEntity` convention. Splitting into 4 tables would give you real NOT NULL constraints per type, but would also mean the repository can't do one simple query per location and reassemble the tree — it's optimized for "read the whole library for a location back into an object graph" over strict per-type normalization.