package com.djt.jukeanator_engine.domain.common.service;

import com.djt.jukeanator_engine.domain.common.repository.AggregateRootRepository;
import com.djt.jukeanator_engine.domain.common.service.command.CommandProcessor;
import com.djt.jukeanator_engine.domain.common.service.command.model.CommandRequest;
import com.djt.jukeanator_engine.domain.common.service.command.model.CommandResponse;
import com.djt.jukeanator_engine.domain.common.service.query.QueryProcessor;
import com.djt.jukeanator_engine.domain.common.service.query.model.QueryRequest;
import com.djt.jukeanator_engine.domain.common.service.query.model.QueryResponse;
import com.djt.jukeanator_engine.domain.common.service.query.model.QueryResponseItem;

public interface AggregateRootService<T> extends
  AggregateRootRepository<T>,
  CommandProcessor<CommandRequest, CommandResponse>, 
  QueryProcessor<QueryRequest, QueryResponse<QueryRequest, QueryResponseItem>> {

}
