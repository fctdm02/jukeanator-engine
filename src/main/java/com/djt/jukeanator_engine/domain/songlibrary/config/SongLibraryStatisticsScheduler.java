package com.djt.jukeanator_engine.domain.songlibrary.config;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.djt.jukeanator_engine.config.AppProperties;
import com.djt.jukeanator_engine.domain.songlibrary.service.SongLibraryService;

/**
 * On slave/standalone instances, keeps the on-disk CD stats file up-to-date by periodically
 * re-storing the song library and statistics — daily and on shutdown — so it's available for
 * restore, transfer to another machine, or other recovery needs. Skipped on the master, which is
 * headless/location-agnostic and doesn't own a local song library to persist.
 */
@Component
public class SongLibraryStatisticsScheduler {

  private static final Logger log = LoggerFactory.getLogger(SongLibraryStatisticsScheduler.class);

  private final AppProperties appProperties;
  private final SongLibraryService songLibraryService;

  public SongLibraryStatisticsScheduler(
      AppProperties appProperties, SongLibraryService songLibraryService) {
    this.appProperties = appProperties;
    this.songLibraryService = songLibraryService;
  }

  @Scheduled(cron = "0 0 5 * * *")
  public void storeSongLibraryAndStatisticsDaily() {
    storeIfNotMaster("scheduled 5:00 AM run");
  }

  @PreDestroy
  public void storeSongLibraryAndStatisticsOnShutdown() {
    storeIfNotMaster("application shutdown");
  }

  private void storeIfNotMaster(String trigger) {

    if (appProperties.isMaster()) {
      return;
    }

    try {
      songLibraryService.storeSongLibraryAndStatistics();
      log.info("Stored song library and statistics ({})", trigger);
    } catch (Exception e) {
      log.error("Failed to store song library and statistics ({})", trigger, e);
    }
  }
}
