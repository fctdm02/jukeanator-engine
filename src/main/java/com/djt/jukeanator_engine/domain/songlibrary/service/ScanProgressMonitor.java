package com.djt.jukeanator_engine.domain.songlibrary.service;

import static java.util.Objects.requireNonNull;
import java.util.function.Consumer;
import com.djt.jukeanator_engine.domain.songlibrary.exception.SongScanCancelledException;

/**
 * Two-way channel between a running file-system scan and whoever started it (e.g. the Swing
 * progress overlay): the scan reports {@link Progress} snapshots through the listener, and the
 * caller can ask the scan to stop or cancel from any thread.
 *
 * <ul>
 * <li><b>Stop</b> -- folder discovery ends and the remaining albums skip the slow per-album work
 * (embedded tag/cover-art extraction and internet lookups). Every album found so far is kept and
 * the library is saved as usual.</li>
 * <li><b>Cancel</b> -- the scan throws {@link SongScanCancelledException} at its next check point
 * and nothing is persisted, leaving the previously loaded library untouched. Cover art and
 * metadata files already written into album folders are left in place, since they only speed up
 * a later scan.</li>
 * </ul>
 *
 * Neither request can interrupt an internet lookup that is already in flight; they take effect
 * once it returns. Once the scan reports {@link Phase#SAVING}, both requests are ignored.
 *
 * @author tmyers
 */
public class ScanProgressMonitor {

  public enum Phase {
    /** Walking the folder tree looking for album folders. */
    DISCOVERING,
    /** Reading tags and looking up cover art/metadata for each discovered album. */
    PROCESSING,
    /** Persisting the scanned library; can no longer be stopped or cancelled. */
    SAVING
  }

  /**
   * @param albumsFound album folders discovered so far
   * @param songsFound song files discovered so far
   * @param albumsProcessed albums whose processing has completed (only meaningful from
   *        {@link Phase#PROCESSING} on)
   * @param currentItem folder path or album name currently being worked on (may be empty)
   */
  public record Progress(Phase phase, int albumsFound, int songsFound, int albumsProcessed,
      String currentItem) {
  }

  private final Consumer<Progress> listener;
  private volatile boolean stopRequested;
  private volatile boolean cancelRequested;

  /** A monitor nobody listens to or stops -- used by the plain scan entry points. */
  public ScanProgressMonitor() {
    this(progress -> {
    });
  }

  /**
   * @param listener called on the scanning thread (not the EDT) for every progress update; keep it
   *        cheap, as discovery can report many updates per second
   */
  public ScanProgressMonitor(Consumer<Progress> listener) {
    requireNonNull(listener, "listener cannot be null");
    this.listener = listener;
  }

  public void requestStop() {
    this.stopRequested = true;
  }

  public void requestCancel() {
    this.cancelRequested = true;
  }

  public boolean isStopRequested() {
    return this.stopRequested;
  }

  public boolean isCancelRequested() {
    return this.cancelRequested;
  }

  /** Called by the scan at each check point. */
  public void throwIfCancelled() {
    if (this.cancelRequested) {
      throw new SongScanCancelledException("Song scan was cancelled");
    }
  }

  public void report(Progress progress) {
    this.listener.accept(progress);
  }

  @Override
  public String toString() {
    return "ScanProgressMonitor[stopRequested=" + stopRequested + ", cancelRequested="
        + cancelRequested + "]";
  }
}
