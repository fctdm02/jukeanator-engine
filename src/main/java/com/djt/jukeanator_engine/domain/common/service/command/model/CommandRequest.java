package com.djt.jukeanator_engine.domain.common.service.command.model;

import java.io.Serializable;

/**
 * MARKER INTERFACE
 * 
 * NOTE: This interface extends <code>Serializable</code>, as they
 * is intended to be placed into command queues for processing
 * (for load/scalability purposes)
 * 
 * @author tmyers
 *
 */
public interface CommandRequest extends Serializable {
  
}
