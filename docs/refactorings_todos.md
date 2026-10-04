## Architecture Overview

The project is a **Spring Boot** app with a **Swing UI** running as a desktop jukebox. The main packages visible are:

- `ui.components` — Swing frame and widgets
- `domain.songlibrary.dto` — `GenreDto`, `SongDto`
- `domain.songqueue.dto` — `SongQueueEntryDto`
- `ui.config` — `JukeANatorUserInterfaceProperties`

---

## Refactoring Suggestions

### 1. `JukeANatorFrame` is a God Class (~900 lines)

This is the biggest issue. The frame handles UI construction, state management, pagination logic, image loading, and cell rendering all in one file. It should be split:

- **`GenresPanel`** — extract `buildGenresPanel()`, `buildGenreTile()`, `showGenreDetails()`, `refreshGenresPage()`, `rebuildGenresPagination()` into its own `JPanel` subclass
- **`QueuePanel`** — extract `buildQueuePanel()`, `showQueueSongDetails()`, `QueueListCellRenderer` into its own class
- **`SearchPanel`** — extract `buildSearchPanel()` and all keyboard building methods
- **`NowPlayingPanel`** — extract `buildNowPlayingPanel()`, `setNowPlaying()`, `loadAlbumArt()`, `clearNowPlaying()`
- **`TopPanel`** / **`CreditsPanel`** — extract `buildTopPanel()` and credits logic
- **`JukeboxTabbedPane`** — extract the entire `buildContentPanelTabs()` and `JukeboxTabComponent` inner class

The frame itself should only assemble these components and wire them together.

---

### 2. No Presenter / Controller Layer

All UI update logic (`setGenres()`, `setQueue()`, `setNowPlaying()`) lives directly on the `JFrame`. This tightly couples the data model to the view. A **Presenter** or **Controller** (even a simple POJO) should own the update logic and call methods on a view interface:

```java
public interface JukeANatorView {
  void displayGenres(List<GenreDto> genres);
  void displayQueue(List<SongQueueEntryDto> queue);
  void displayNowPlaying(SongDto song);
}
```

---

### 3. Image Loading Has No Caching for Queue / Now Playing

`genreIconCache` exists for genres but cover art in `QueueListCellRenderer` and `showQueueSongDetails()` reloads from disk on every render cycle. A shared `CoverArtCache` service (backed by a `LinkedHashMap` with a max size for LRU eviction) should be extracted and injected.

---

### 4. `QueueListCellRenderer` Loads Images Inside `getListCellRendererComponent`

This is a Swing anti-pattern — cell renderers are called very frequently. Disk I/O inside `getListCellRendererComponent` will cause jank. Image loading should be async (via `SwingWorker` or an executor) with a placeholder shown until loaded.

---

### 5. Magic Numbers and Inline Colors

Colors and sizes are hardcoded throughout:
- `new Color(70, 70, 70)`, `new Color(180, 180, 180)`, `new Dimension(180, 60)`, font sizes like `34`, `20`, `24`

These should all live in a `JukeANatorTheme` or `UIConstants` class:

```java
public final class JukeANatorTheme {
  public static final Color BG_DARK = new Color(10, 10, 10);
  public static final Color ACCENT_BLUE = new Color(0, 210, 255);
  public static final int TAB_HEIGHT = 96;
  public static final int BUTTON_HEIGHT = 60;
  // etc.
}
```

---

### 6. Genres Pagination State on the Frame

`currentGenresPage`, `genresListModel`, `genreIconCache`, `genresCardLayout`, etc. are all fields on `JukeANatorFrame`. Once `GenresPanel` is extracted, this state moves with it and becomes properly encapsulated.

---

### 7. `buildCreditsDescription()` Should Live on a Model/ViewModel

The credit math (`(5 * creditsPer) + fiveBonusCredits`) is business logic sitting in the view. It belongs in a `CreditsService` or at minimum a `CreditsViewModel`.

---

### 8. `setGenres` / `setQueue` / `setNowPlaying` Are the Only Public API

These are fine as the view's public interface, but they should be extracted to an interface (see point 2) so the frame can be swapped or tested without instantiating a full Swing window.

---

### 9. `javaFX.txt`, `javaFX2.txt`, `javaFX3.txt`, `scratchpad.txt`, `swing.txt`, `player.txt` in Root

These scratch files should be deleted or moved to a `notes/` or `docs/` folder and gitignored. They shouldn't be in the repo root.

---

### Priority Order

- **High**: Extract `GenresPanel`, `QueuePanel`, `SearchPanel` into separate classes
- **High**: Extract `CoverArtCache` and fix async image loading in the cell renderer
- **Medium**: Introduce a View interface + Presenter layer
- **Medium**: Extract `JukeANatorTheme` constants class
- **Low**: Move `buildCreditsDescription` logic to a model
- **Low**: Clean up scratch `.txt` files from repo root