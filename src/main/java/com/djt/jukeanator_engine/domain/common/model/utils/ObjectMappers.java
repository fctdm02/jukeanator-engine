package com.djt.jukeanator_engine.domain.common.model.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public class ObjectMappers {
  private static final ObjectMapper INSTANCE = createMapper();

  public static ObjectMapper create() {
    return INSTANCE;
  }

  public static ObjectMapper createNew() {
    return createMapper();
  }

  public static void configure(ObjectMapper m) {
    m.registerModule(new JavaTimeModule());
    m.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  }

  private static ObjectMapper createMapper() {
    ObjectMapper m = new ObjectMapper();
    configure(m);
    return m;
  }

  private ObjectMappers() {}

}
