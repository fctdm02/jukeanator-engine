package com.djt.jukeanator_engine.domain.songqueue.mapper;

import java.util.ArrayList;
import java.util.List;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongQueueEntryDto;
import com.djt.jukeanator_engine.domain.songqueue.model.SongQueueEntryEntity;

/**
 * @author tmyers
 */
public final class SongQueueMapper {
  
  public static List<SongQueueEntryDto> toDto(List<SongQueueEntryEntity> entities) {
    
    List<SongQueueEntryDto> dtos = new ArrayList<>();
    
    for (SongQueueEntryEntity entity: entities) {
            
      SongQueueEntryDto dto = new SongQueueEntryDto(
          entity.getSong().getName(),
          entity.getSong().getNumPlays(),
          entity.getPriority());

      dtos.add(dto);
    }
    
    return dtos;
  }
}