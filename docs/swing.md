Looking at your code, the key insight is that `AlbumDetailDialog` wraps `AlbumViewPanel` in a modal dialog with a countdown timer and close button. To convert this to an in-panel navigation pattern, you need:

1. A **shared `AlbumDetailCard`** panel (replaces the dialog, contains the same content + footer)
2. A **`TabNavigator` interface** so any tab can push/pop cards generically
3. Update `HomePanel` (and by extension the other tabs) to use `CardLayout` push/pop instead of opening a dialog

Here's the refactored design:

---

### 1. `AlbumDetailCard.java` — replaces `AlbumDetailDialog`

This is the core change. The countdown + close button move into a panel footer instead of a dialog footer.Here's how to do the full refactor. The key moves are:

**`TabNavigator` interface** — each tab panel implements this so `AlbumDetailCard` can call `popToRoot()` without knowing what tab it's in.

```java
// TabNavigator.java
public interface TabNavigator {
    void pushAlbumDetail(AlbumDto album);
    void popToRoot();
}
```

**`AlbumDetailCard.java`** — a plain `JPanel` with the same content as the old dialog footer. The countdown calls `navigator.popToRoot()` instead of `dispose()`.

```java
public class AlbumDetailCard extends JPanel {

    private static final int TIMEOUT_SECONDS = 120;

    private int secondsRemaining = TIMEOUT_SECONDS;
    private final Timer countdownTimer;
    private final JLabel timeoutLabel = new JLabel();
    private final JProgressBar timeoutBar = new JProgressBar(0, TIMEOUT_SECONDS);

    public AlbumDetailCard(
            Frame owner,
            AlbumDto album,
            ImageLoader imageLoader,
            SongQueueService songQueueService,
            int normalPlayCost,
            int priorityCost,
            int threshold1, int threshold2, int threshold3,
            boolean enableBigScrollBars,
            TabNavigator navigator) {

        setLayout(new BorderLayout());
        setBackground(BG_DARK);

        AlbumViewPanel.SongClickListener songClick = song -> {
            secondsRemaining = TIMEOUT_SECONDS;
            updateTimeout();
            AddSongToQueueDialog.show(owner, song, imageLoader,
                    normalPlayCost, priorityCost,
                    () -> songQueueService.addSongToQueue(
                            new AddSongToQueueRequest(song.getAlbumId(), song.getSongId(), 0)),
                    () -> songQueueService.addSongToQueue(
                            new AddSongToQueueRequest(song.getAlbumId(), song.getSongId(), 1)));
        };

        AlbumViewPanel.AlbumClickListener albumClick = clicked -> {
            secondsRemaining = TIMEOUT_SECONDS;
            updateTimeout();
            AddAlbumToQueueDialog.show(owner, clicked, imageLoader,
                    normalPlayCost, priorityCost,
                    () -> songQueueService.addAlbumToQueue(
                            new AddAlbumToQueueRequest(clicked.getAlbumId(), 0)),
                    () -> songQueueService.addAlbumToQueue(
                            new AddAlbumToQueueRequest(clicked.getAlbumId(), 1)));
        };

        AlbumViewPanel albumView = new AlbumViewPanel(
                album, imageLoader,
                threshold1, threshold2, threshold3,
                enableBigScrollBars, songClick, albumClick);

        add(albumView, BorderLayout.CENTER);
        add(buildFooter(navigator), BorderLayout.SOUTH);

        countdownTimer = new Timer(1000, e -> {
            if (--secondsRemaining <= 0) navigator.popToRoot();
            else updateTimeout();
        });
        countdownTimer.start();
    }

    /** Must be called when the card is removed from view so the timer doesn't leak. */
    public void dismiss() {
        countdownTimer.stop();
    }

    private JPanel buildFooter(TabNavigator navigator) {
        // same layout as AlbumDetailDialog.buildFooter(),
        // but CLOSE calls navigator.popToRoot() instead of dispose()
        JPanel footer = new JPanel(new BorderLayout(12, 0));
        // ... (identical styling) ...
        JButton close = new JButton("CLOSE");
        close.addActionListener(e -> navigator.popToRoot());
        // ... add timeoutBar, timeoutLabel, close ...
        return footer;
    }

    private void updateTimeout() {
        timeoutBar.setValue(secondsRemaining);
        timeoutLabel.setText("Closes in " + secondsRemaining + "s");
    }
}
```

**`HomePanel`** now implements `TabNavigator` — `pushAlbumDetail` swaps in an `AlbumDetailCard` card and shows it; `popToRoot` stops the card and returns to `CARD_GRID`.

```java
public class HomePanel extends JPanel implements TabNavigator {

    private static final String CARD_GRID   = "GRID";
    private static final String CARD_DETAIL = "DETAIL";

    private AlbumDetailCard currentDetailCard; // track so we can dismiss() it

    // ... existing fields ...

    @Override
    public void pushAlbumDetail(AlbumDto album) {
        Frame owner = (Frame) SwingUtilities.getWindowAncestor(this);

        AlbumDto full = fetchFull(album);
        int albumNormal   = normalPlayCost  * full.getSongs().size();
        int albumPriority = priorityCost    * full.getSongs().size();

        if (currentDetailCard != null) {
            currentDetailCard.dismiss();  // stop old timer
        }

        currentDetailCard = new AlbumDetailCard(
                owner, full, imageLoader, songQueueService,
                albumNormal, albumPriority,
                popularityT1, popularityT2, popularityT3,
                enableBigScrollBars, this);   // 'this' is the TabNavigator

        // Replace whatever is in the DETAIL slot
        replaceCard(CARD_DETAIL, currentDetailCard);
        cardLayout.show(rootPanel, CARD_DETAIL);
    }

    @Override
    public void popToRoot() {
        if (currentDetailCard != null) {
            currentDetailCard.dismiss();
            currentDetailCard = null;
        }
        cardLayout.show(rootPanel, CARD_GRID);
    }

    // ── in buildGridCard(), album click becomes: ──────────────────────────
    //   album -> pushAlbumDetail(album)
    // (same one-liner across every tab)
}
```

The `replaceCard` helper is a clean way to avoid the index-based hack that already existed in `showArtist`:

```java
private void replaceCard(String name, JPanel newPanel) {
    // Remove the old component with that card name if present
    for (int i = rootPanel.getComponentCount() - 1; i >= 0; i--) {
        if (rootPanel.getComponent(i).getName() != null
                && rootPanel.getComponent(i).getName().equals(name)) {
            rootPanel.remove(i);
            break;
        }
    }
    newPanel.setName(name);
    rootPanel.add(newPanel, name);
    rootPanel.revalidate();
}
```

The other tabs (`SearchPanel`, `GenrePanel`, `HotHerePanel`) follow the exact same pattern — implement `TabNavigator`, call `pushAlbumDetail` from their album-click listener, `popToRoot` from the back button and card's own `dismiss()`. The two queue dialogs (`AddSongToQueueDialog`, `AddAlbumToQueueDialog`) are **entirely unchanged** since they're short-lived modals that block on top of whatever panel is showing.

The main behavioral difference worth noting: since the countdown is now running inside a panel rather than a blocking modal, any background work (e.g. queue polling refreshing a "Now Playing" widget elsewhere in the frame) will continue normally while the detail card is visible — which is actually an improvement.













































Let me read the full `JukeANatorFrame` to extract the current Search, Hot Here, and Genre tab implementations before writing anything.Good, I have everything I need. Now let me also read the `AlbumViewPanel` file since `AlbumDetailCard` will need it, and verify what's in `AddSongToQueueDialog` and `AddAlbumToQueueDialog`.Now I have everything. Let me also check `AddAlbumToQueueDialog` quickly for its signature.Now I have a complete picture of all three tabs. I can write all three panels and the shared `ResultsColumnPanel` helper cleanly. Let me produce the files.Now let me verify the thumbnail loading gap in `ResultsColumnPanel` — the factory can't call `imageLoader` because it's stateless, so I need to address that properly. Let me check what the original code did and make a decision: either pass `imageLoader` into the factory, or load thumbnails in the row. Passing it in is the right call.Now update the two callers to pass `imageLoader` into `ResultsColumnPanel.build`:Good. Now do a final sanity check — verify no lingering references to `AlbumDetailDialog` or the old frame-level helpers in any of the four output files:`repaintChildren` in `ResultsColumnPanel` is correct — it's the local private helper, not the old frame method. All clean.Here's a full account of every decision made across the four files.

---

### `ResultsColumnPanel` — new shared factory

The old `buildSearchResultColumn` and `buildHotHereColumn` in `JukeANatorFrame` were almost identical (only `previewCount` differed). They're now a single static factory `ResultsColumnPanel.build(...)`. Key points:

- `ImageLoader` is passed in so thumbnail loading works correctly — the old TODO comment placeholder is replaced with an actual `loadFilesystemImage` call.
- Item rendering uses pattern-matching `instanceof` (Java 16+) in `extractFields`, eliminating the category string equality checks scattered across the old row builder.
- `onUp` / `onDown` are `Runnable` — each caller passes a lambda that adjusts its own offset field and calls its own rebuild method, so the panel carries no offset state of its own.
- `onItemClick` is a `Consumer<T>` — the factory passes the raw item back to the tab panel, which does the actual dispatch (open dialog, push card, etc.). The factory has no knowledge of services or navigation.

---

### `SearchPanel`

- Implements `TabNavigator`. `popToRoot()` returns to `CARD_RESULTS` if a search was active (`lastResult != null`), otherwise `CARD_ENTRY` — matching the original behaviour where CLEAR sent you back to the hero screen.
- The keyboard, search bar, and results column rebuilds are all self-contained. The `syncSearchLabel` method keeps both the entry-card and results-card search labels in sync, exactly as before.
- `pushArtist` / `pushAlbumDetail` use `replaceCard` by name, removing the old tree-walk helpers (`findTabRootPanel`, `findCardLayout`, `getPreviousCard`, `removeCardIfPresent`).
- Song clicks go directly to `AddSongToQueueDialog` (unchanged modal). Album clicks go through `pushAlbumDetail` (in-panel card). Artist clicks go through `pushArtist` -> `ArtistDetailPanel`.

---

### `HotHerePanel`

- The simplest of the three: loads popularity data once in the constructor (same as the old `buildHotHerePanel`), then `rebuildColumnsPanel` mirrors the old `rebuildHotHereColumnsPanel`.
- `popToRoot()` always returns to `CARD_CONTENT` since there's no entry/results distinction here.
- Row click dispatch is identical in shape to `SearchPanel` — same three-case switch.

---

### `GenrePanel`

The genres tab had a two-level structure in the original: an outer `genresContentPanel` CardLayout (`GRID` ↔ `DETAILS`), and the `AlbumDetailDialog` opened modally on top. The refactor preserves this two-level structure cleanly:

- **Outer layout** (`outerRoot`): `CARD_GENRES` (the whole inner genre nav) ↔ `CARD_DETAIL` (the full-screen `AlbumDetailCard`).
- **Inner layout** (`innerRoot`): `INNER_GRID` (genre icon grid with pagination) ↔ `INNER_ALBUMS` (the `GenreDetailPanel` for the selected genre).
- `activeGenre` tracks which genre's album list is showing. When `popToRoot()` is called from an expiring `AlbumDetailCard`, it returns to `CARD_GENRES` and then shows `INNER_ALBUMS` if a genre was active, or `INNER_GRID` if not — so the back behaviour is contextually correct.
- `setGenres(List<GenreDto>)` is kept as a public method matching the original `JukeANatorFrame.setGenres` call site — `JukeANatorFrame` just needs to hold a reference to `GenrePanel` and call it instead.

---

### What to delete from `JukeANatorFrame`

Once these panels are wired in, the following can be removed: `buildSearchPanel`, `buildSearchEntryCard`, `buildSearchBarPanel`, `buildFixedKeyboardPanel`, `buildKeyboardPanel`, `buildKeyboardRow1/2/3`, `createKeyboardButton`, `styleKeyboardButton`, `updateSearchEntryLabel`, `resetSearchState`, `executeSearch`, `rebuildSearchResultsPanel`, `buildSearchResultColumn`, `buildEmptySearchResultRow`, `adjustOffset`, `styleNavButton`, `buildSearchResultRow`, `repaintChildren`, `findTabRootPanel`, `findCardLayout`, `getPreviousCard`, `removeCardIfPresent`, `openAlbumDetailFromRow`, `buildHotHerePanel`, `rebuildHotHereColumnsPanel`, `buildHotHereColumn`, `adjustHotHereOffset`, `buildGenresPanel`, `refreshGenresPage`, `rebuildGenresPagination`, `stylePageNavButton`, `buildGenreTile`, `showGenreDetails`, and all associated field declarations.