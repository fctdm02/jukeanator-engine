//@formatter:off
package com.djt.jukeanator_engine.domain.common.service.query.model;

/**
 * 
 * @author tmyers
 *
 */
public class QueryRequest {
  
  public static final String WILDCARD = "*";
  
  public static final String SORT_DIRECTION_ASC = "ASC";
  public static final String SORT_DIRECTION_DESC = "DESC";
  public static final String DEFAULT_SORT_DIRECTION = SORT_DIRECTION_ASC;

  public static final Integer ANY = Integer.valueOf(-2);
  public static final Integer NULL = Integer.valueOf(-1);
  public static final Integer ZERO = Integer.valueOf(0);
  public static final Integer ONE_HUNDRED = Integer.valueOf(100);
  public static final Integer ONE_THOUSAND = Integer.valueOf(1000);
  
  public static final Integer MIN_VALUE = ZERO;
  public static final Integer MAX_LIMIT = ONE_THOUSAND;
  
  public static final Integer DEFAULT_LIMIT = ONE_HUNDRED;
  public static final Integer DEFAULT_OFFSET = ZERO;
  
  private String sort;
  private String sortDirection;
  private Integer offset;
  private Integer limit;
  
  public QueryRequest() {
  }
  
  public QueryRequest(
      String sort,
      String sortDirection, 
      Integer offset, 
      Integer limit) {
    super();
    this.sort = sort;
    this.sortDirection = sortDirection;
    this.offset = offset;
    this.limit = limit;
  }
  
  /**
   * 
   * @return The sort attribute
   */
  public String getSort() {
    return sort;
  }

  /**
   * 
   * @return The sort direction (either ASC or DESC)
   */
  public String getSortDirection() {
    return sortDirection;
  }
  
  /**
   * 
   * @return The offset, which is a non-negative integer (used for pagination)
   */
  public Integer getOffset() {
    return offset;
  }
  
  /**
   * 
   * @return The limit, which is a positive integer (used for pagination)
   */
  public Integer getLimit() {
    return limit;
  }

  public void setSort(String sort) {
    this.sort = sort;
  }

  public void setSortDirection(String sortDirection) {
    this.sortDirection = sortDirection;
  }

  public void setOffset(Integer offset) {
    this.offset = offset;
  }

  public void setLimit(Integer limit) {
    this.limit = limit;
  }
  
  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((limit == null) ? 0 : limit.hashCode());
    result = prime * result + ((offset == null) ? 0 : offset.hashCode());
    result = prime * result + ((sort == null) ? 0 : sort.hashCode());
    result = prime * result + ((sortDirection == null) ? 0 : sortDirection.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    QueryRequest other = (QueryRequest) obj;
    if (limit == null) {
      if (other.limit != null)
        return false;
    } else if (!limit.equals(other.limit))
      return false;
    if (offset == null) {
      if (other.offset != null)
        return false;
    } else if (!offset.equals(other.offset))
      return false;
    if (sort == null) {
      if (other.sort != null)
        return false;
    } else if (!sort.equals(other.sort))
      return false;
    if (sortDirection == null) {
      if (other.sortDirection != null)
        return false;
    } else if (!sortDirection.equals(other.sortDirection))
      return false;
    return true;
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("QueryRequest [sort=").append(sort).append(", sortDirection=")
        .append(sortDirection).append(", offset=").append(offset).append(", limit=").append(limit)
        .append("]");
    return builder.toString();
  }
}
//@formatter:on