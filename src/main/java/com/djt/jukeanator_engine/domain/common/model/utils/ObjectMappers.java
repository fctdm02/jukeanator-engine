package com.djt.jukeanator_engine.domain.common.model.utils;

import com.fasterxml.jackson.databind.ObjectMapper;

public class ObjectMappers {
  private static ObjectMapper mapper;

  public static ObjectMapper create() {
    ObjectMapper m = mapper;
    if (m == null) {
      synchronized (ObjectMappers.class) {
        m = mapper;
        if (m == null) {
          mapper = m = createMapper();
        }
      }
    }
    return mapper;
  }

  public static ObjectMapper createNew() {
    return createMapper();
  }

  public static void configure(ObjectMapper m) {

    // TODO: TDM
    /*
     * JavaTimeModule timeModule = new JavaTimeModule(); timeModule.addSerializer(LocalDate.class,
     * new LocalDateDeserializer(DateTimeFormatter.ISO_LOCAL_DATE));
     * 
     * timeModule.addDeserializer(LocalDateTime.class, new
     * LocalDateTimeDeserializer(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
     * 
     * timeModule.addSerializer(LocalDate.class, new
     * LocalDateSerializer(DateTimeFormatter.ISO_LOCAL_DATE));
     * 
     * timeModule.addSerializer(LocalDateTime.class, new
     * LocalDateTimeSerializer(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
     * 
     * //m.registerModule(new Jdk8Module()); //m.registerModule(new GuavaModule());
     * m.registerModule(timeModule);
     * 
     * m.configOverride(java.util.Date.class) .setFormat(JsonFormat.Value.forPattern("yyyy-MM-dd"));
     * 
     * m.configOverride(Optional.class)
     * .setInclude(JsonInclude.Value.construct(JsonInclude.Include.NON_EMPTY, null));
     * 
     * m.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
     * m.configure(JsonParser.Feature.ALLOW_NON_NUMERIC_NUMBERS, true);
     * m.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);
     */
  }

  private static ObjectMapper createMapper() {
    ObjectMapper m = new ObjectMapper();
    configure(m);
    return m;
  }

  private ObjectMappers() {}

}
