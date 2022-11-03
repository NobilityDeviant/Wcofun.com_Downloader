package com.nobility.downloader.entities.settings;

public interface Meta {

    String getKey();

    void setKey(String key);

    void setValueObj(Object value);

    Object getValueObj();

    void setStringValue(String stringValue);

    String getStringValue();

    void setIntValue(Integer intValue);

    Integer getIntValue();

    void setLongValue(Long longValue);

    Long getLongValue();

    void setDoubleValue(Double doubleValue);

    Double getDoubleValue();

    void setFloatValue(Float floatValue);

    Float getFloatValue();

    void setBooleanValue(Boolean booleanValue);

    Boolean getBooleanValue();

}
