package com.djt.jukeanator_engine.domain.songqueue.model;

import static java.util.Objects.requireNonNull;
import com.djt.jukeanator_engine.domain.common.model.AbstractEntity;
import com.djt.jukeanator_engine.domain.common.model.AbstractPersistentEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.SongFileEntity;

public class SongQueueEntryEntity extends AbstractPersistentEntity {
  private static final long serialVersionUID = 1L;

  private SongFileEntity song;
  private Integer priority;

  public SongQueueEntryEntity(SongFileEntity song) {
    this(song, Integer.valueOf(0));
  }
  
  public SongQueueEntryEntity(SongFileEntity song, Integer priority) {
    super();
    requireNonNull(song, "song cannot be null");
    requireNonNull(priority, "priority cannot be null");
    this.song = song;    
    this.priority = priority;
  }

  public SongFileEntity getSong() {
    return song;
  }
  
  public Integer getPriority() {
    return priority;
  }

  @Override
  public int compareTo(AbstractEntity obj) {

    if (obj instanceof SongQueueEntryEntity) {

      SongQueueEntryEntity that = (SongQueueEntryEntity)obj;
      
      Integer thisPriority = this.getPriority();
      Integer thatPriority = that.getPriority();
      
      return thisPriority.compareTo(thatPriority);
    }
    throw new IllegalStateException("Cannot compare this: " + this + " to an instance of: " + obj.getClassAndNaturalIdentity());
  }
  
  @Override
  public String getNaturalIdentity() {
    
    StringBuilder sb = new StringBuilder();
    sb.append("priority: ");
    sb.append(priority);    
    sb.append(", song: ");
    sb.append(song.getNaturalIdentity());    
    return sb.toString();
  }
}
