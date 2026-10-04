This is a great moment to simplify your entire system: you already have a UI + domain mapper + service layer, but right now they’re all *duplicating traversal rules*.

What you want is:

> A single **projection DSL + engine** that both the service *and UI* can use to request:

* full library
* popular view
* search results

without rewriting tree logic anywhere.

---

# 1. The real target architecture

You want to replace:

### ❌ Current state

* `SongLibraryMapper` = structural mapper
* Service = filtering + sorting + traversal awareness
* UI = assumes fully-shaped DTOs
* Everything knows too much about structure

---

### ✅ New state

You get:

## One engine

* `ProjectionEngine`

## One DSL

* `Projection<T>`

## One rule system

* predicates (popular, search, etc.)

## Zero nested mapping logic anywhere else

---

# 2. Step 1 — Replace mapper with PURE structural mappers

Your current mapper is doing too much (it mixes traversal + DTO creation + hierarchy).

We split it:

## NEW RULE:

> Mapper should NEVER decide filtering or hierarchy rules

So we refactor it into:

```java id="pure_mapper"
public final class SongLibraryMapper {

  private SongLibraryMapper() {}

  // -------------------------
  // PURE LEAF MAPPERS ONLY
  // -------------------------

  public static SongDto toSongDto(
      ArtistFolderEntity artist,
      AlbumFolderEntity album,
      SongFileEntity song) {

    return new SongDto(
        artist.getPersistentIdentity(),
        artist.getName(),
        album.getPersistentIdentity(),
        album.getName(),
        album.getCoverArtPath(),
        song.getPersistentIdentity(),
        song.getSongName(),
        song.getNumPlays()
    );
  }

  public static AlbumDto toAlbumDto(
      ArtistFolderEntity artist,
      AlbumFolderEntity album,
      List<SongDto> songs) {

    return new AlbumDto(
        artist.getPersistentIdentity(),
        artist.getName(),
        album.getPersistentIdentity(),
        album.getName(),
        album.hasExplicit(),
        album.getRecordLabel(),
        album.getReleaseDate(),
        album.getCoverArtPath(),
        songs
    );
  }

  public static ArtistDto toArtistDto(
      ArtistFolderEntity artist,
      List<AlbumDto> albums) {

    return new ArtistDto(
        artist.getPersistentIdentity(),
        artist.getName(),
        albums
    );
  }

  public static GenreDto toGenreDto(GenreFolderEntity genre) {
    return new GenreDto(
        genre.getPersistentIdentity(),
        genre.getName()
    );
  }
}
```

---

### 🔥 Key change:

* NO recursion
* NO collection traversal
* NO filtering
* ONLY object shaping

---

# 3. Step 2 — Add Projection DSL

Now we introduce your missing abstraction:

## This is the important part:

```java id="dsl"
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

public final class Projection<T> {

  private final Predicate<T> include;
  private final Function<T, List<T>> children;

  public Projection(Predicate<T> include,
                    Function<T, List<T>> children) {
    this.include = include;
    this.children = children;
  }

  public Predicate<T> include() {
    return include;
  }

  public Function<T, List<T>> children() {
    return children;
  }

  // DSL builders
  public static <T> Projection<T> of(
      Predicate<T> include,
      Function<T, List<T>> children) {

    return new Projection<>(include, children);
  }

  public static <T> Projection<T> all(Function<T, List<T>> children) {
    return new Projection<>(t -> true, children);
  }
}
```

---

# 4. Step 3 — Projection Engine (generic tree evaluator)

This is the only recursive code you will ever need:

```java id="engine"
import java.util.List;

public final class ProjectionEngine {

  private ProjectionEngine() {}

  public static <N, D> List<D> project(
      List<N> nodes,
      Projection<N> projection,
      Mapper<N, D> mapper) {

    return nodes.stream()
        .filter(projection.include())
        .map(n -> projectNode(n, projection, mapper))
        .toList();
  }

  private static <N, D> D projectNode(
      N node,
      Projection<N> projection,
      Mapper<N, D> mapper) {

    List<N> children = projection.children().apply(node);

    List<D> projectedChildren =
        children == null ? List.of()
            : children.stream()
                .filter(projection.include())
                .map(child -> projectNode(child, projection, mapper))
                .toList();

    return mapper.map(node, projectedChildren);
  }

  @FunctionalInterface
  public interface Mapper<N, D> {
    D map(N node, List<D> children);
  }
}
```

---

# 5. Step 4 — Define your THREE views (this is the payoff)

## A) FULL LIBRARY VIEW

```java id="full_view"
Projection<ArtistFolderEntity> FULL =
    Projection.all(ArtistFolderEntity::getAlbums);
```

---

## B) POPULAR VIEW

```java id="popular_view"
Predicate<NumPlaysComparable> hasPlays =
    n -> n.getNumPlays() != null && n.getNumPlays() > 0;

Projection<ArtistFolderEntity> POPULAR =
    Projection.of(
        a -> hasPlays.test(a),
        ArtistFolderEntity::getAlbums
    );
```

---

## C) SEARCH VIEW

```java id="search_view"
public static Projection<ArtistFolderEntity> search(String q) {

  String query = q.toLowerCase();

  return Projection.of(
      a -> a.getName() != null &&
           a.getName().toLowerCase().contains(query),
      ArtistFolderEntity::getAlbums
  );
}
```

---

# 6. Step 5 — Service becomes tiny

Now your service has ZERO nested logic:

## POPULAR

```java id="service_popular"
return new SearchResultDto(
    ProjectionEngine.project(
        root.getSongs(),
        Projection.all(SongFileEntity::getAlbum),
        SongLibraryMapper::toSongDto
    ),
    ProjectionEngine.project(
        root.getArtists(),
        POPULAR,
        (artist, albums) ->
            SongLibraryMapper.toArtistDto(
                artist,
                albums.stream().map(a -> (AlbumDto) a).toList()
            )
    ),
    ProjectionEngine.project(
        root.getAlbums(),
        Projection.of(hasPlays, AlbumFolderEntity::getChildSongs),
        (album, songs) ->
            SongLibraryMapper.toAlbumDto(
                album.getParentArtist(),
                album,
                songs.stream().map(s -> (SongDto) s).toList()
            )
    )
);
```

---

# 7. Step 6 — UI becomes completely projection-agnostic

Your Swing UI **does not care anymore**:

* what “popular” means
* what “search” means
* what filtering rules exist

It only consumes:

```java
setGenres(...)
setQueue(...)
setNowPlaying(...)
```

That’s it.

---

# 8. What you now have architecturally

You’ve effectively built:

## A mini CQRS projection engine

- **Projection**: what survives traversal
- **Engine**: how traversal happens
- **Mapper**: how nodes become DTOs
- **Service**: chooses projection
- **UI**: consumes DTOs only

---

# 9. Why this is the “final form” of your design

You now get:

### ✔ No duplicated filters

### ✔ No nested stream chains

### ✔ No mapper recursion logic

### ✔ No service tree awareness

### ✔ No UI coupling to domain structure

### ✔ Easy new views (trending, explicit-only, favorites, etc.)

---

# If you want next step

I can take this even further into:

## “query objects like SQL for your object graph”

Example:

```java
Projection.query(Artist.class)
  .where(hasPlays)
  .expand(Artist::albums)
  .expand(Album::songs)
  .map(toSongDto)
```

That’s basically a lightweight in-memory GraphQL for your music library.

Just say 👍



Good—this is where the projection model actually proves its value, because **search is the first case where naive tree projection breaks down**.

For search, you don’t just want:

> “filter nodes”

You want:

> “keep a node if it matches OR any descendant matches”

That is a **bottom-up pruning rule**, not a simple predicate.

---

# 1. Define correct SEARCH semantics

A node is included if:

* Song matches search OR
* Album contains at least one matching song OR
* Artist contains at least one matching album/song

So we need:

> **match-or-descendant-match rule**

---

# 2. Add a reusable SEARCH-aware projection

We extend your projection model slightly with a **two-phase evaluation**:

* Phase 1: evaluate children
* Phase 2: decide if node survives based on:

  * self match OR
  * any child survived

---

## Projection update (minimal but crucial)

```java id="projection_search_extension"
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

public final class SearchProjection<T> {

  private final Predicate<T> selfMatch;
  private final Function<T, List<T>> children;

  public SearchProjection(Predicate<T> selfMatch,
                          Function<T, List<T>> children) {
    this.selfMatch = selfMatch;
    this.children = children;
  }

  public Predicate<T> selfMatch() {
    return selfMatch;
  }

  public Function<T, List<T>> children() {
    return children;
  }

  public static <T> SearchProjection<T> of(
      Predicate<T> selfMatch,
      Function<T, List<T>> children) {

    return new SearchProjection<>(selfMatch, children);
  }
}
```

---

# 3. SEARCH engine (bottom-up pruning)

This is the key improvement over normal projection:

```java id="search_engine"
import java.util.List;

public final class SearchEngine {

  private SearchEngine() {}

  public static <N, D> List<D> search(
      List<N> nodes,
      SearchProjection<N> projection,
      Mapper<N, D> mapper) {

    return nodes.stream()
        .map(n -> evaluate(n, projection, mapper))
        .flatMap(java.util.Optional::stream)
        .toList();
  }

  private static <N, D> java.util.Optional<D> evaluate(
      N node,
      SearchProjection<N> projection,
      Mapper<N, D> mapper) {

    List<N> children = projection.children().apply(node);

    List<D> projectedChildren =
        (children == null)
            ? List.of()
            : children.stream()
                .map(child -> evaluate(child, projection, mapper))
                .flatMap(java.util.Optional::stream)
                .toList();

    boolean selfMatches = projection.selfMatch().test(node);
    boolean childrenMatch = !projectedChildren.isEmpty();

    if (!selfMatches && !childrenMatch) {
      return java.util.Optional.empty();
    }

    return java.util.Optional.of(
        mapper.map(node, projectedChildren)
    );
  }

  @FunctionalInterface
  public interface Mapper<N, D> {
    D map(N node, List<D> children);
  }
}
```

---

# 4. Define SEARCH predicates (IMPORTANT)

We define a single reusable text matcher:

```java id="search_predicate"
private static boolean matches(String value, String q) {
  return value != null && value.toLowerCase().contains(q);
}
```

---

# 5. SERVICE IMPLEMENTATION (what you asked for)

Now the service becomes clean and declarative.

---

## SEARCH SONGS

```java id="search_songs"
public SearchResultDto getMusicBySearch(String searchFor) {

  if (!isInitialized) {
    throw new SongLibraryException("SongLibraryService has not been initialized yet!");
  }

  String q = searchFor.trim().toLowerCase();

  if (q.isEmpty()) {
    return new SearchResultDto(List.of(), List.of(), List.of());
  }

  // ----------------------------
  // SONG SEARCH PROJECTION
  // ----------------------------
  SearchProjection<SongFileEntity> songProjection =
      SearchProjection.of(
          s -> matches(s.getSongName(), q),
          s -> List.of()
      );

  List<SongDto> songs =
      SearchEngine.search(
          root.getSongs(),
          songProjection,
          (song, ignored) ->
              SongLibraryMapper.toSongDto(song)
      );

  // ----------------------------
  // ALBUM SEARCH PROJECTION
  // ----------------------------
  SearchProjection<AlbumFolderEntity> albumProjection =
      SearchProjection.of(
          a -> matches(a.getName(), q),
          AlbumFolderEntity::getChildSongs
      );

  List<AlbumDto> albums =
      SearchEngine.search(
          root.getAlbums(),
          albumProjection,
          (album, songDtos) ->
              SongLibraryMapper.toAlbumDto(
                  album.getParentArtist(),
                  album,
                  songDtos
              )
      );

  // ----------------------------
  // ARTIST SEARCH PROJECTION
  // ----------------------------
  SearchProjection<ArtistFolderEntity> artistProjection =
      SearchProjection.of(
          a -> matches(a.getName(), q),
          ArtistFolderEntity::getAlbums
      );

  List<ArtistDto> artists =
      SearchEngine.search(
          root.getArtists(),
          artistProjection,
          (artist, albumDtos) ->
              SongLibraryMapper.toArtistDto(
                  artist,
                  albumDtos
              )
      );

  return new SearchResultDto(songs, artists, albums);
}
```

---

# 6. What makes this correct (important insight)

This line is the entire reason this design works:

```java id="rule_core"
selfMatches OR childrenMatch
```

That ensures:

### ✔ Artist appears if:

* artist name matches OR
* any album matches OR
* any song matches

### ✔ Album appears if:

* album name matches OR
* any song matches

### ✔ Song appears if:

* song name matches

---

# 7. Why this is better than your current approach

## Before

* filter at every level
* risk of missing descendants
* duplicated logic per DTO type
* inconsistent “search completeness”

## After

* single reusable engine
* bottom-up correctness guarantee
* no nested stream chains in service
* UI unaffected

---

# 8. What you’ve actually built now

You now have 3 orthogonal projection modes:

- **FULL**: `ProjectionEngine`, no filtering
- **POPULAR**: `ProjectionEngine`, strict predicate
- **SEARCH**: `SearchEngine`, bottom-up OR propagation

---

# If you want next evolution

The next step is combining both behaviors:

> “Search + popularity ranking + limit + pagination”

That turns this into a **real query planner**, similar to a tiny in-memory database engine.

Just say 👍

















---- IGNORE EVERYTHING BELOW




You’ve fixed the **song/album/artist filtering at the top level**, but the remaining leak is structural:

> You are filtering *artists*, but not filtering their *nested albums (and therefore songs)* inside the DTO graph.

So even if an artist has `numPlays > 0`, their `albums → songs` can still include zero-play songs, because `SongLibraryMapper.toArtistDtoList(...)` is likely mapping the full hierarchy blindly.

---

## Root problem

Your current rule:

```java
filter(artist -> artist.getNumPlays() != null && artist.getNumPlays() > 0)
```

only guarantees:

* artist has plays

but NOT:

* albums inside artist have plays
* songs inside albums have plays

So the DTO graph can still contain zero-play descendants.

---

## Correct fix (you need 2 layers of filtering)

You must enforce:

### Rule:

> If an artist is included → only include albums with plays > 0 → only include songs with plays > 0

That means filtering must happen **before mapping**, not after.

---

## Clean refactor (service-level enforcement)

### Step 1: define reusable predicate

```java
java.util.function.Predicate<NumPlaysComparable> hasPlays =
    item -> item.getNumPlays() != null && item.getNumPlays() > 0;
```

---

## Step 2: FIX ARTISTS by filtering nested structure

You need to rebuild artist DTOs *safely*, not rely on mapper blindly.

Replace:

```java
SongLibraryMapper.toArtistDtoList(popularArtists)
```

with something like:

```java
List<ArtistDto> filteredArtists = root.getArtists().stream()
    .filter(hasPlays)
    .map(artist -> new ArtistDto(
        artist.getArtistId(),
        artist.getName(),
        artist.getAlbums().stream()
            .filter(hasPlays)
            .map(album -> new AlbumDto(
                album.getArtistId(),
                album.getArtistName(),
                album.getAlbumId(),
                album.getAlbumName(),
                album.getHasExplicit(),
                album.getRecordLabel(),
                album.getReleaseDate(),
                album.getCoverArtPath(),
                album.getSongs().stream()
                    .filter(hasPlays)
                    .map(SongLibraryMapper::toSongDto) // or inline constructor
                    .toList()
            ))
            .toList()
    ))
    .toList();
```

---

## Step 3: do the same for albums (if needed elsewhere)

If albums are exposed independently, they must also be sanitized:

```java
List<AlbumFolderEntity> popularAlbums = root.getAlbums().stream()
    .filter(hasPlays)
    .filter(album -> album.getSongs().stream().anyMatch(hasPlays))
    .sorted(byNumPlaysDescending)
    .limit(searchResultSize)
    .toList();
```

---

## Step 4 (important design fix)

Right now your system violates a hidden invariant:

> “DTO mapping assumes entity tree is already filtered”

That’s brittle.

### Better long-term fix:

Push this rule into the domain boundary:

* `RootFolderEntity#getArtistsWithPlaysOnly()`
* `ArtistFolderEntity#getAlbumsWithPlaysOnly()`
* `AlbumFolderEntity#getSongsWithPlaysOnly()`

Then your service becomes trivial and impossible to misuse.

---

## Minimal patch vs proper fix

### Minimal patch (quick fix)

* Filter songs inside artist DTO mapping only

### Correct fix (recommended)

* Filter at **all tree levels before mapping**
* Or move filtering into mapper layer explicitly

---

## Key takeaway

Right now your bug is not in `getMusicByPopularity()` anymore.

It’s here:

> ❌ “Mapper is returning unfiltered object graphs”

Fix the *graph construction*, not just the top-level filters.

---

If you want, I can show a **clean mapper refactor** that enforces “no zero-play nodes anywhere” without turning your service into nested stream soup.



# NEXT






Good instinct—this is exactly the kind of rule that belongs in the mapper layer, not scattered across services.

Right now your pain comes from this structural issue:

> Your mappers are doing *dumb copying*, but your domain has *filtering rules*

So the fix is to make the mapper **filter-aware and tree-safe by design**, instead of pushing streams into the service.

---

# Clean solution: “filtered projection mappers”

We introduce a small, reusable filtering pipeline inside `SongLibraryMapper` that guarantees:

> If `numPlays <= 0` → entity never enters DTO graph at any level

---

# 1. Core idea: central predicate

```java id="core_predicate"
private static boolean hasPlays(NumPlaysComparable item) {
  return item.getNumPlays() != null && item.getNumPlays() > 0;
}
```

---

# 2. Public entry points enforce filtering automatically

Instead of:

```java
toArtistDtoList(...)
toAlbumDtoList(...)
toSongDtoList(...)
```

you introduce:

```java
toPopularArtistDtoList(...)
toPopularAlbumDtoList(...)
toPopularSongDtoList(...)
```

These guarantee correctness.

---

# 3. Refactored SongLibraryMapper (clean version)

```java id="mapper_refactor"
public final class SongLibraryMapper {

  private SongLibraryMapper() {}

  // =========================
  // PUBLIC FILTERED ENTRYPOINTS
  // =========================

  public static List<SongDto> toPopularSongDtoList(List<SongFileEntity> songs) {
    return songs.stream()
        .filter(SongLibraryMapper::hasPlays)
        .map(SongLibraryMapper::toSongDto)
        .toList();
  }

  public static List<ArtistDto> toPopularArtistDtoList(List<ArtistFolderEntity> artists) {
    return artists.stream()
        .filter(SongLibraryMapper::hasPlays)
        .map(SongLibraryMapper::toArtistDto)
        .toList();
  }

  public static List<AlbumDto> toPopularAlbumDtoList(List<AlbumFolderEntity> albums) {
    return albums.stream()
        .filter(SongLibraryMapper::hasPlays)
        .map(SongLibraryMapper::toAlbumDto)
        .toList();
  }

  // =========================
  // INTERNAL TREE MAPPING
  // =========================

  private static ArtistDto toArtistDto(ArtistFolderEntity artist) {
    return new ArtistDto(
        artist.getArtistId(),
        artist.getName(),
        artist.getAlbums().stream()
            .filter(SongLibraryMapper::hasPlays)
            .map(SongLibraryMapper::toAlbumDto)
            .toList()
    );
  }

  private static AlbumDto toAlbumDto(AlbumFolderEntity album) {
    return new AlbumDto(
        album.getArtistId(),
        album.getArtistName(),
        album.getAlbumId(),
        album.getAlbumName(),
        album.getHasExplicit(),
        album.getRecordLabel(),
        album.getReleaseDate(),
        album.getCoverArtPath(),
        album.getSongs().stream()
            .filter(SongLibraryMapper::hasPlays)
            .map(SongLibraryMapper::toSongDto)
            .toList()
    );
  }

  private static SongDto toSongDto(SongFileEntity song) {
    return new SongDto(
        song.getArtistId(),
        song.getArtistName(),
        song.getAlbumId(),
        song.getAlbumName(),
        song.getCoverArtPath(),
        song.getSongId(),
        song.getName(),
        song.getNumPlays()
    );
  }

  // =========================
  // SHARED RULE
  // =========================

  private static boolean hasPlays(NumPlaysComparable item) {
    return item != null
        && item.getNumPlays() != null
        && item.getNumPlays() > 0;
  }
}
```

---

# 4. Service becomes clean again (no nested streams)

Now your service collapses back to something readable:

```java id="service_clean"
List<SongFileEntity> popularSongs = root.getSongs().stream()
    .sorted(byNumPlaysDescending)
    .limit(searchResultSize)
    .toList();

List<ArtistFolderEntity> popularArtists = root.getArtists().stream()
    .sorted(byNumPlaysDescending)
    .limit(searchResultSize)
    .toList();

List<AlbumFolderEntity> popularAlbums = root.getAlbums().stream()
    .sorted(byNumPlaysDescending)
    .limit(searchResultSize)
    .toList();

return new SearchResultDto(
    SongLibraryMapper.toPopularSongDtoList(popularSongs),
    SongLibraryMapper.toPopularArtistDtoList(popularArtists),
    SongLibraryMapper.toPopularAlbumDtoList(popularAlbums)
);
```

---

# Why this design works well

### 1. Single source of truth

The “no zero plays anywhere” rule lives in exactly one place.

### 2. No service pollution

Service stays focused on:

* ordering
* limiting
* orchestration

### 3. Recursive safety

Even deeply nested albums/songs are filtered automatically.

### 4. Prevents future bugs

Nobody can accidentally call a “raw mapper” and leak zero-play data unless they explicitly bypass the filtered API.

---

# Optional upgrade (best practice)

If you want to make this even more bulletproof:

### Add “raw vs filtered” naming convention

* `toSongDto()` → raw (internal only)
* `toPopularSongDtoList()` → safe/public

This prevents accidental misuse at compile-time by convention.

---

If you want, I can take this one step further and show a **generic tree-filtering utility** so you don’t repeat `.filter(hasPlays)` in every level of the hierarchy.


Good—this is the point where you stop writing “mappers with conditions” and start treating your system like a **projection engine over one canonical graph**.

The key shift:

> You do NOT have “popular/search/full mappers”
> You have **one traversal engine + different projection policies**

---

# 1. Core idea: separate 3 concerns

We split everything into:

### 1. Selection (what survives traversal)

* “has plays”
* “matches search”
* “everything”

### 2. Projection (how node becomes DTO)

* Song → SongDto
* Artist → ArtistDto

### 3. Traversal (tree walking + pruning)

---

# 2. Generic projection engine

This is the foundation.

```java id="projection_engine"
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

public final class ProjectionEngine {

  private ProjectionEngine() {}

  public static <N, D> List<D> project(
      List<N> nodes,
      Projection<N, D> projection
  ) {
    return nodes.stream()
        .filter(projection.selector())
        .map(node -> projectNode(node, projection))
        .toList();
  }

  private static <N, D> D projectNode(
      N node,
      Projection<N, D> projection
  ) {

    List<N> children = projection.children().apply(node);

    List<D> projectedChildren =
        (children == null)
            ? List.of()
            : children.stream()
                .filter(projection.selector())
                .map(child -> projectNode(child, projection))
                .toList();

    return projection.mapper().apply(node, projectedChildren);
  }

  public record Projection<N, D>(
      Predicate<N> selector,
      Function<N, List<N>> children,
      BiMapper<N, List<D>, D> mapper
  ) {}

  @FunctionalInterface
  public interface BiMapper<N, C, D> {
    D apply(N node, C children);
  }
}
```

---

# 3. Now define ONE canonical rule system

Instead of multiple mappers:

## We define reusable predicates

```java id="rules"
private static boolean hasPlays(NumPlaysComparable n) {
  return n.getNumPlays() != null && n.getNumPlays() > 0;
}

private static boolean matchesSearch(String value, String query) {
  return value != null && value.toLowerCase().contains(query);
}
```

---

# 4. Define projection strategies (THIS is the real power)

## A) FULL LIBRARY (no filtering)

```java id="full_projection"
ProjectionEngine.Projection<ArtistFolderEntity, ArtistDto> FULL_LIBRARY =
    new ProjectionEngine.Projection<>(
        artist -> true,
        ArtistFolderEntity::getAlbums,
        (artist, albums) -> new ArtistDto(
            artist.getArtistId(),
            artist.getName(),
            albums
        )
    );
```

Albums + songs handled similarly:

```java id="album_full"
ProjectionEngine.Projection<AlbumFolderEntity, AlbumDto> FULL_ALBUM =
    new ProjectionEngine.Projection<>(
        album -> true,
        AlbumFolderEntity::getSongs,
        (album, songs) -> new AlbumDto(
            album.getArtistId(),
            album.getArtistName(),
            album.getAlbumId(),
            album.getAlbumName(),
            album.getHasExplicit(),
            album.getRecordLabel(),
            album.getReleaseDate(),
            album.getCoverArtPath(),
            songs
        )
    );
```

Songs:

```java id="song_full"
ProjectionEngine.Projection<SongFileEntity, SongDto> FULL_SONG =
    new ProjectionEngine.Projection<>(
        song -> true,
        song -> List.of(),
        (song, ignored) -> new SongDto(
            song.getArtistId(),
            song.getArtistName(),
            song.getAlbumId(),
            song.getAlbumName(),
            song.getCoverArtPath(),
            song.getSongId(),
            song.getName(),
            song.getNumPlays()
        )
    );
```

---

## B) POPULAR VIEW (your business rule)

Now everything is just swapped selector:

```java id="popular_projection"
Predicate<NumPlaysComparable> hasPlays =
    n -> n.getNumPlays() != null && n.getNumPlays() > 0;
```

```java id="popular_artist"
ProjectionEngine.Projection<ArtistFolderEntity, ArtistDto> POPULAR_ARTIST =
    new ProjectionEngine.Projection<>(
        SongLibraryMapper::hasPlays,
        ArtistFolderEntity::getAlbums,
        (artist, albums) -> new ArtistDto(
            artist.getArtistId(),
            artist.getName(),
            albums
        )
    );
```

```java id="popular_album"
ProjectionEngine.Projection<AlbumFolderEntity, AlbumDto> POPULAR_ALBUM =
    new ProjectionEngine.Projection<>(
        SongLibraryMapper::hasPlays,
        AlbumFolderEntity::getSongs,
        (album, songs) -> new AlbumDto(
            album.getArtistId(),
            album.getArtistName(),
            album.getAlbumId(),
            album.getAlbumName(),
            album.getHasExplicit(),
            album.getRecordLabel(),
            album.getReleaseDate(),
            album.getCoverArtPath(),
            songs
        )
    );
```

```java id="popular_song"
ProjectionEngine.Projection<SongFileEntity, SongDto> POPULAR_SONG =
    new ProjectionEngine.Projection<>(
        SongLibraryMapper::hasPlays,
        song -> List.of(),
        (song, ignored) -> new SongDto(
            song.getArtistId(),
            song.getArtistName(),
            song.getAlbumId(),
            song.getAlbumName(),
            song.getCoverArtPath(),
            song.getSongId(),
            song.getName(),
            song.getNumPlays()
        )
    );
```

---

## C) SEARCH VIEW (composable rule)

Now search becomes just a selector swap:

```java id="search_projection"
public static ProjectionEngine.Projection<ArtistFolderEntity, ArtistDto>
searchArtists(String q) {

  return new ProjectionEngine.Projection<>(
      a -> a.getName() != null && a.getName().toLowerCase().contains(q),
      ArtistFolderEntity::getAlbums,
      (artist, albums) -> new ArtistDto(
          artist.getArtistId(),
          artist.getName(),
          albums
      )
  );
}
```

Same pattern applies to albums and songs.

---

# 5. Service becomes trivial (no streams, no filters, no nesting)

## Popular

```java id="service_popular"
return new SearchResultDto(
    ProjectionEngine.project(root.getSongs(), POPULAR_SONG),
    ProjectionEngine.project(root.getArtists(), POPULAR_ARTIST),
    ProjectionEngine.project(root.getAlbums(), POPULAR_ALBUM)
);
```

## Full library

```java id="service_full"
return new SearchResultDto(
    ProjectionEngine.project(root.getSongs(), FULL_SONG),
    ProjectionEngine.project(root.getArtists(), FULL_LIBRARY),
    ProjectionEngine.project(root.getAlbums(), FULL_ALBUM)
);
```

## Search

```java id="service_search"
String q = normalizedSearch;

return new SearchResultDto(
    ProjectionEngine.project(root.getSongs(), searchSongs(q)),
    ProjectionEngine.project(root.getArtists(), searchArtists(q)),
    ProjectionEngine.project(root.getAlbums(), searchAlbums(q))
);
```

---

# 6. What you just gained

### Before

* 3 different mapping styles
* duplicated `.filter(hasPlays)` everywhere
* nested stream chains
* inconsistent rules across service/mappers

---

### After

You now have:

## ONE engine

* `ProjectionEngine`

## MANY policies

* FULL
* POPULAR
* SEARCH

## ZERO duplicated traversal logic

---

# 7. Why this is the “real architecture”

What you’ve built is effectively:

> A **functional projection system over a music aggregate graph**

This pattern scales to:

* playlists
* recommendations
* genre views
* “most played this week”
* “recently added”
* “explicit-only mode”
* multi-tenant filtering

All without touching traversal code again.

---

# If you want the next step up

I can show you how to evolve this into:

### “queryable projection DSL”

So you can write:

```java
projection()
  .select(hasPlays)
  .expand(Artist::albums)
  .expand(Album::songs)
  .map(toSongDto)
```

That turns your system into something very close to a lightweight CQRS/query engine.

Just say the word.
