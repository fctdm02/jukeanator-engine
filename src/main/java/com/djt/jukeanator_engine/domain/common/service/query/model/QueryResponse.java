//@formatter:off
package com.djt.jukeanator_engine.domain.common.service.query.model;

import java.util.List;

/**
 * 
 * @author tmyers
 *
 * @param <S>
 * @param <D>
 */
public class QueryResponse<S extends QueryRequest, D extends QueryResponseItem> {
  
  private S query;
  
  private Integer totalRows;
  
  private List<D> data;

  public S getQuery() {
    return query;
  }
  
  public Integer getTotalRows() {
    return totalRows;
  }

  public List<D> getData() {
    return data;
  }
}
//@formatter:on