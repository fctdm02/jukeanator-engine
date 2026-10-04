package com.djt.jukeanator_engine.domain.songlibrary.exception;

/**
 * Thrown out of a file-system scan when the operator cancels it via
 * {@link com.djt.jukeanator_engine.domain.songlibrary.service.ScanProgressMonitor#requestCancel()}.
 * Nothing is persisted, so the previously loaded song library (if any) is left untouched.
 */
public class SongScanCancelledException extends SongLibraryServiceException {
  private static final long serialVersionUID = 1L;

  public SongScanCancelledException(String message) {
    super(message);
  }
}
