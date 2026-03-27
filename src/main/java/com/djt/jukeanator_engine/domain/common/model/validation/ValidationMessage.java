package com.djt.jukeanator_engine.domain.common.model.validation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ValidationMessage implements Comparable<ValidationMessage> {
  
  private static final List<ValidationMessage> EMPTY_LIST = new ArrayList<>();
  
  private final MessageType messageType;
  private final String entity;
  private final String attribute;
  private final String message;
  
  public ValidationMessage(
      MessageType messageType,
      String entity,
      String attribute,
      String message) {
    
    this.messageType = messageType;
    this.entity = entity;
    this.attribute = attribute;
    this.message = message;
  }
  
  public MessageType getMessageType() {
    return messageType;
  }

  public String getEntity() {
    return entity;
  }

  public String getAttribute() {
    return attribute;
  }

  public String getMessage() {
    return message;
  }
  
  @Override
  public int compareTo(ValidationMessage that) {
    
    int compareTo = this.messageType.compareTo(that.messageType);
    if (compareTo == 0) {
      compareTo = this.attribute.compareTo(that.attribute);
      if (compareTo == 0) {
        compareTo = this.message.compareTo(that.message);
        if (compareTo == 0) {
          compareTo = this.entity.compareTo(that.entity);  
        }
      }
    }
    return compareTo;
  }
  
  @Override
  public String toString() {
    return new StringBuilder()
        .append(messageType)
        .append(": ")
        .append(entity)
        .append(": [")
        .append(attribute)
        .append("] [")
        .append(message)
        .append("]\n")
        .toString();
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((attribute == null) ? 0 : attribute.hashCode());
    result = prime * result + ((entity == null) ? 0 : entity.hashCode());
    result = prime * result + ((message == null) ? 0 : message.hashCode());
    result = prime * result + ((messageType == null) ? 0 : messageType.hashCode());
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
    ValidationMessage other = (ValidationMessage) obj;
    if (attribute == null) {
      if (other.attribute != null)
        return false;
    } else if (!attribute.equals(other.attribute))
      return false;
    if (entity == null) {
      if (other.entity != null)
        return false;
    } else if (!entity.equals(other.entity))
      return false;
    if (message == null) {
      if (other.message != null)
        return false;
    } else if (!message.equals(other.message))
      return false;
    if (messageType != other.messageType)
      return false;
    return true;
  }

  public static enum MessageType {
    ERROR(1, "Error"),
    INFO(2, "Info");
    
    private static final Map<Integer, MessageType> TYPES;
    
    private final int id;
    private final String name;
    
    static {
      TYPES = new HashMap<>();
      for (MessageType type : MessageType.values()) {
        TYPES.put(type.id, type);
      }
    }
    
    public static MessageType get(int id) {
      return TYPES.get(id);
    }
    
    private MessageType(int id, String name) {
      this.id = id;
      this.name = name;
    }
    
    public int getId() {
      return id;
    }
    
    public String getName() {
      return name;
    }
  }

  public static List<ValidationMessage> getInfoLevelMessages(List<ValidationMessage> simpleValidationMessages) {
    
    return getFilteredMessages(simpleValidationMessages, MessageType.INFO);
  }
  
  public static List<ValidationMessage> getErrorLevelMessages(List<ValidationMessage> simpleValidationMessages) {

    return getFilteredMessages(simpleValidationMessages, MessageType.ERROR);
  }
  
  public static List<ValidationMessage> getFilteredMessages(
      List<ValidationMessage> simpleValidationMessages,
      MessageType messageType) {
    
    if (simpleValidationMessages == null || simpleValidationMessages.isEmpty()) {
      return EMPTY_LIST;
    }
    List<ValidationMessage> messages = new ArrayList<>();
    for (ValidationMessage simpleValidationMessage: simpleValidationMessages) {
      
      if (simpleValidationMessage.messageType.equals(messageType)) {
        messages.add(simpleValidationMessage);
      }
    }
    return messages;
  }  
}