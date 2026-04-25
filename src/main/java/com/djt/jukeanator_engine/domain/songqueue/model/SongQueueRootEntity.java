package com.djt.jukeanator_engine.domain.songqueue.model;

import static java.util.Objects.requireNonNull;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import com.djt.jukeanator_engine.domain.common.model.AbstractPersistentEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.SongFileEntity;

public class SongQueueRootEntity extends AbstractPersistentEntity {
  private static final long serialVersionUID = 1L;
  
  public static final String SONG_QUEUE_FILENAME = "JukeANator.PL";  

  private String location;
  
  private TreeSet<SongQueueEntryEntity> songs = new TreeSet<>();

  public SongQueueRootEntity() {}

  public SongQueueRootEntity(String location) {
    super();
    requireNonNull(location, "location cannot be null");
    this.location = location;
  }

  public String getLocation() {
    return location;
  }

  @Override
  public String getNaturalIdentity() {
    return location;
  }
  
  public List<SongQueueEntryEntity> getSongs() {
    
    List<SongQueueEntryEntity> list = new ArrayList<>();
    list.addAll(this.songs);
    return list;
  }
  
  public int addSongToQueue(SongFileEntity song, Integer priority) {
    
    SongQueueEntryEntity entry = new SongQueueEntryEntity(song, priority);
    songs.add(entry);
    List<SongQueueEntryEntity> list = getSongs();
    return list.indexOf(entry);
  }
}
