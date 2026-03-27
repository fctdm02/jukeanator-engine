package com.djt.jukeanator_engine.domain.common.repository;

import com.djt.jukeanator_engine.domain.common.model.utils.ObjectMappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

public class AbstractRepositoryFileSystemImpl {

  protected static Integer MAX_PERSISTENT_IDENTITY_VALUE = Integer.valueOf(0);
  synchronized protected static Integer getNextPersistentIdentityValue() {
    
    Integer nextValue = Integer.valueOf((MAX_PERSISTENT_IDENTITY_VALUE.intValue()-1));
    MAX_PERSISTENT_IDENTITY_VALUE = nextValue;
    return nextValue;
  }
  
  protected static boolean USE_PRETTY_PRINT = false;
  public static boolean getPrettyPrint() {
    return USE_PRETTY_PRINT;
  }
  public static void setPrettyPrint(boolean prettyPrint) {
    USE_PRETTY_PRINT = prettyPrint;
  }

  protected static final ThreadLocal<ObjectMapper> MAPPER = new ThreadLocal<ObjectMapper>() {
    
    @Override
    protected ObjectMapper initialValue() {
      return ObjectMappers.create();
    }
  };
  
  protected static final ThreadLocal<ObjectWriter> OBJECT_WRITER = new ThreadLocal<ObjectWriter>() {
    
    @Override
    protected ObjectWriter initialValue() {
      return ObjectMappers.create().writer();
    }
  };

  protected static final ThreadLocal<ObjectWriter> OBJECT_WRITER_WITH_PRETTY_PRINTER = new ThreadLocal<ObjectWriter>() {
    
    @Override
    protected ObjectWriter initialValue() {
      return ObjectMappers.create().writerWithDefaultPrettyPrinter();
    }
  };
  
  protected static final ObjectWriter getObjectWriter() {
    if  (USE_PRETTY_PRINT) {
      return OBJECT_WRITER_WITH_PRETTY_PRINTER.get(); 
    }
    return OBJECT_WRITER.get();
  }
  
  protected String basePath;

  public AbstractRepositoryFileSystemImpl() {
    this(null);
  }

  public AbstractRepositoryFileSystemImpl(String basePath) {
    super();
    if (basePath != null) {
      this.basePath = basePath;
    } else {
      this.basePath = System.getProperty("user.home") + "/";      
    }
  }
  
  public String basePath() {
    return basePath;
  }
  
  public void setBasePath(String basePath) {
    this.basePath = basePath;
  }
}
