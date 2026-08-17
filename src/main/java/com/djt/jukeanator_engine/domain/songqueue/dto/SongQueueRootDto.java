package com.djt.jukeanator_engine.domain.songqueue.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Plain, human-readable JSON representation of the singleton {@code SongQueueRootEntity}. This is
 * the top-level shape written to and read from {@code SongQueueRootEntity.SONG_QUEUE_FILENAME}.
 */
public class SongQueueRootDto implements Serializable {

  private static final long serialVersionUID = 1L;

  private String rootPath;
  private List<SongQueueEntryPersistenceDto> entries = new ArrayList<>();

  public SongQueueRootDto() {}

  public SongQueueRootDto(String rootPath, List<SongQueueEntryPersistenceDto> entries) {
    this.rootPath = rootPath;
    this.entries = entries;
  }

  public String getRootPath() {
    return rootPath;
  }

  public void setRootPath(String rootPath) {
    this.rootPath = rootPath;
  }

  public List<SongQueueEntryPersistenceDto> getEntries() {
    return entries;
  }

  public void setEntries(List<SongQueueEntryPersistenceDto> entries) {
    this.entries = entries;
  }
}
