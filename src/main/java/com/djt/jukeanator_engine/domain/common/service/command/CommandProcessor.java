package com.djt.jukeanator_engine.domain.common.service.command;

import com.djt.jukeanator_engine.domain.common.service.command.model.CommandRequest;
import com.djt.jukeanator_engine.domain.common.service.command.model.CommandResponse;

/**
 * MARKER INTERFACE
 * 
 * @author tmyers
 *
 */
public interface CommandProcessor<T extends CommandRequest, R extends CommandResponse> {
  
  /**
   * Commands handle requests Create, Update and Delete operations
   * that are use case specific, and obviously, involve the mutation
   * of the aggregate root.  Any changes by these commands will 
   * be published by the aggregate root service, so any interested
   * parties can respond appropriately (e.g. invalidate/reload 
   * a cache, re-process data, etc.)
   * 
   * @param commandRequest The command request
   * 
   * @return The command response
   */
  R processCommand(T commandRequest);
}
