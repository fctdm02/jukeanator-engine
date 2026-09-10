package com.djt.jukeanator_engine.ui.components;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

/**
 * Accumulates server-paginated results for a single category (artists, albums, or songs) into a
 * growing in-memory buffer, transparently fetching additional server pages as the on-screen
 * "window" advances past what has been buffered so far -- including the case where the buffer has
 * some but not enough remaining items to fill out a full screen page.
 *
 * <p>
 * Used by {@link SearchPanel}, {@link HotHerePanel}, and {@link GenreDetailPanel} so each of the
 * three result columns (artists/albums/songs) can page independently against
 * {@code SongLibraryService}'s per-category page indices, without duplicating the "top up buffer,
 * then slice" logic in each screen.
 */
public final class PagedCategoryBuffer<T> {

  private final List<T> buffer = new ArrayList<>();
  private final int serverPageSize;
  private int nextServerPageIndex = 0;
  private boolean exhausted = false;

  public PagedCategoryBuffer(int serverPageSize) {
    this.serverPageSize = serverPageSize;
  }

  /** Discards all buffered data and resets to page 0 -- call on new search / sort / genre change. */
  public void reset() {
    buffer.clear();
    nextServerPageIndex = 0;
    exhausted = false;
  }

  /**
   * Seeds the buffer with an already-fetched page 0, avoiding a redundant fetch right after
   * construction (e.g. when a screen is opened with results it already has in hand).
   */
  public void seedFirstPage(List<T> firstPage) {
    reset();
    List<T> page = firstPage != null ? firstPage : List.of();
    buffer.addAll(page);
    nextServerPageIndex = 1;
    exhausted = page.size() < serverPageSize;
  }

  /**
   * Ensures items {@code [windowStart, windowStart + windowSize]} are present in the buffer
   * (inclusive of one lookahead item, so callers can tell whether a further page-down is possible),
   * fetching additional server pages via {@code fetchServerPage} as needed. {@code fetchServerPage}
   * is called with the next 0-based server page index to retrieve and must return that page's items
   * for this category alone.
   */
  public void ensureWindowAvailable(int windowStart, int windowSize,
      IntFunction<List<T>> fetchServerPage) {

    int lookaheadEnd = windowStart + windowSize;
    while (!exhausted && buffer.size() <= lookaheadEnd) {
      List<T> page = fetchServerPage.apply(nextServerPageIndex++);
      if (page == null) {
        page = List.of();
      }
      buffer.addAll(page);
      if (page.size() < serverPageSize) {
        exhausted = true;
      }
    }
  }

  public List<T> items() {
    return buffer;
  }

  public boolean isExhausted() {
    return exhausted;
  }
}
