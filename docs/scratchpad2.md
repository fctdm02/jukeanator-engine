You’re basically trying to fix two problems at once:

1. **Zero-play leakage across nested projections (albums -> artists -> songs)**
2. **Two orthogonal projections:**

   * *POPULAR* = filter by `numPlays > 0` + sort by plays
   * *SEARCH* = include only entities matching search term anywhere in their subtree
   * *FULL* = no filtering, full tree

Right now your service is doing **all three concerns inline**, which is why it’s drifting into stream soup and inconsistent rules.

The clean fix is:

> **Make the domain return a single canonical tree, then apply reusable “view projections” on top of it.**

---

# 1. Core Idea: Stop mapping “lists”, start filtering a tree

Introduce a **projection pipeline**:

```text
RootFolderEntity
   ↓
TreePredicate (what survives)
   ↓
TreeProjection (how it is shaped/sorted)
   ↓
DTO mapping (pure, dumb)
```

So instead of:

* `.filter(hasPlays)` everywhere
* `.filter(searchMatch)` everywhere

You do:

* `TreeFilter`
* `TreeProjector`

---

# 2. Step 1 — Generic Tree Filter Utility

This is the key abstraction you’re missing.

```java
public final class TreeFilter {

  public static <A, B, C> List<A> filter(
      List<A> roots,
      java.util.function.Predicate<A> nodePredicate,
      java.util.function.Function<A, List<B>> childrenGetter,
      java.util.function.BiFunction<A, List<B>, A> reattachChildren) {

    return roots.stream()
        .map(node -> filterNode(node, nodePredicate, childrenGetter, reattachChildren))
        .filter(java.util.Objects::nonNull)
        .toList();
  }

  private static <A, B> A filterNode(
      A node,
      java.util.function.Predicate<A> nodePredicate,
      java.util.function.Function<A, List<B>> childrenGetter,
      java.util.function.BiFunction<A, List<B>, A> reattachChildren) {

    List<B> children = childrenGetter.apply(node);

    List<B> filteredChildren = children == null ? List.of() :
        children.stream()
            .map(child -> (B) child)
            .filter(nodePredicate)
            .toList();

    boolean keepNode = nodePredicate.test(node) || !filteredChildren.isEmpty();

    if (!keepNode) {
      return null;
    }

    return reattachChildren.apply(node, filteredChildren);
  }
}
```

But this is still generic in a weak way.

👉 In practice, we simplify per domain (music tree).

---

# 3. Step 2 — Domain-aware Tree Projection (clean version)

We define a **MusicTreeViewFilter**:

```java
public final class MusicTreeFilters {

  public static final java.util.function.Predicate<NumPlaysComparable> HAS_PLAYS =
      item -> item.getNumPlays() != null && item.getNumPlays() > 0;

  public static final java.util.function.Predicate<NumPlaysComparable> MATCHES_SEARCH(String term) {
    String t = term.toLowerCase();
    return item -> {
      if (item instanceof SongFileEntity s) return s.getSongName().toLowerCase().contains(t);
      if (item instanceof ArtistFolderEntity a) return a.getName().toLowerCase().contains(t);
      if (item instanceof AlbumFolderEntity a) return a.getName().toLowerCase().contains(t);
      return false;
    };
  }
}
```

---

# 4. Step 3 — Projection Type Enum (this is the key unification)

```java
public enum LibraryProjectionType {
  FULL,
  POPULAR,
  SEARCH
}
```

---

# 5. Step 4 — Single Service Entry Point

Replace your 3 mental models with ONE:

```java
public SearchResultDto getMusic(LibraryProjectionType type, String searchFor) {

  if (!isInitialized) {
    throw new SongLibraryException("SongLibraryService has not been initialized yet!");
  }

  return switch (type) {

    case FULL -> map(root.getSongs(), root.getArtists(), root.getAlbums());

    case POPULAR -> map(
        root.getSongs().stream().filter(MusicTreeFilters.HAS_PLAYS).toList(),
        root.getArtists().stream().filter(MusicTreeFilters.HAS_PLAYS).toList(),
        root.getAlbums().stream().filter(MusicTreeFilters.HAS_PLAYS).toList()
    );

    case SEARCH -> {
      String term = requireNonNull(searchFor).trim().toLowerCase();

      yield map(
          root.getSongs().stream()
              .filter(s -> MusicTreeFilters.MATCHES_SEARCH(term).test(s))
              .toList(),

          root.getArtists().stream()
              .filter(a -> MusicTreeFilters.MATCHES_SEARCH(term).test(a))
              .toList(),

          root.getAlbums().stream()
              .filter(a -> MusicTreeFilters.MATCHES_SEARCH(term).test(a))
              .toList()
      );
    }
  };
}
```

At first glance this looks similar—but the key improvement is:

> The rule is centralized, not embedded in sorting logic.

---

# 6. Step 5 — Fix the REAL bug in your current system

Right now your problem is deeper:

### ❌ Current issue

You filter at each level independently:

* artist has plays -> included
* album has plays -> included
* song has plays -> included

BUT:

> artist might include albums that include zero-play songs

So your DTO becomes inconsistent.

---

# 7. Step 6 — TRUE hierarchical filtering (correct model)

You actually want:

> “An entity survives ONLY if it has at least one valid child OR itself is valid.”

So we enforce **bottom-up filtering**:

---

### SONG LEVEL

```java
boolean keepSong = predicate.test(song);
```

---

### ALBUM LEVEL

```java
List<SongFileEntity> filteredSongs =
    album.getChildSongs().stream()
        .filter(predicate)
        .toList();

boolean keepAlbum = predicate.test(album) || !filteredSongs.isEmpty();
```

---

### ARTIST LEVEL

```java
List<AlbumFolderEntity> filteredAlbums =
    artist.getAlbums().stream()
        .map(album -> filterAlbum(album, predicate))
        .filter(Objects::nonNull)
        .toList();

boolean keepArtist = predicate.test(artist) || !filteredAlbums.isEmpty();
```

---

# 8. Step 7 — Clean projection functions (THIS replaces mapper spam)

```java
private SearchResultDto map(
    List<SongFileEntity> songs,
    List<ArtistFolderEntity> artists,
    List<AlbumFolderEntity> albums) {

  return new SearchResultDto(
      SongLibraryMapper.toSongDtoList(songs),
      SongLibraryMapper.toArtistDtoList(artists),
      SongLibraryMapper.toAlbumDtoList(albums)
  );
}
```

---

# 9. Step 8 — REMOVE this from your system entirely

You can delete duplication in:

### ❌ Remove everywhere:

* `.filter(hasPlays)`
* `.filter(searchWeight > 0)`
* repeated comparator logic per method
* per-method sorting pipelines

---

# 10. Final architecture (what your service becomes)

Your service becomes:

```text
SongLibraryService
   ├── load root
   ├── apply projection (FULL / POPULAR / SEARCH)
   ├── delegate to mapper
```

NOT:

```text
SongLibraryService
   ├── 400 lines of stream logic
   ├── repeated filtering rules
   ├── inconsistent hierarchy behavior
```

---

# 11. If you want the *best version* (next step)

The real “enterprise clean” version of this is:

### Introduce:

```java
TreeProjectionEngine
   - apply(Tree root, ProjectionRule rule)
```

Then your service becomes:

```java
return projectionEngine.project(root, POPULAR);
return projectionEngine.project(root, SEARCH(term));
```

---

# Bottom line

You fix your system by enforcing:

* **ONE traversal rule (tree filtering)**
* **ONE projection entry point**
* **NO filtering inside mapper**
* **NO per-method duplication**

---

If you want, I can next refactor your **SongLibraryMapper + RootFolderEntity structure** so it supports true immutable projections (which removes 80% of this complexity permanently).
