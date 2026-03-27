package com.djt.jukeanator_engine.domain.common.service.query;

import com.djt.jukeanator_engine.domain.common.service.query.model.QueryResponse;
import com.djt.jukeanator_engine.domain.common.service.query.model.QueryResponseItem;
import com.djt.jukeanator_engine.domain.common.service.query.model.QueryRequest;

/**
 * MARKER INTERFACE
 * 
 * @author tmyers
 *
 */
public interface QueryProcessor<S extends QueryRequest, R extends QueryResponse<QueryRequest, QueryResponseItem>> {
  
  /**
   * Queries handle requests for complicated searches that involve:
   * <ol>
   *   <li>search criteria</li>
   *   <li>sorting</li>
   *   <li>pagination</li>
   *   <li>UI use case specific tabular data</li>
   * </ol>
   * The last item, can be likened to the old "Fast Lane Reader" 
   * design pattern, where the data requested is a selection/project
   * of information spread across many entities.
   * 
   * @param queryRequest The query request
   * 
   * @return The query response
   */
  R query(S queryRequest);
}
