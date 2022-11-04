package com.nobility.downloader.entities.settings;

import io.objectbox.annotation.Entity;
import io.objectbox.annotation.Id;

@Entity
public class SettingsMeta implements Meta {

    @Id
    public long id;
    private String key;
    private String stringValue;
    private Integer intValue;
    private Long longValue;
    private Double doubleValue;
    private Float floatValue;
    private Boolean booleanValue;

    public SettingsMeta() {}

    @Override
    public String getKey() {
        return key;
    }

    @Override
    public void setKey(String key) {
        this.key = key;
    }

    @Override
    public void setValueObj(Object value) {
        stringValue = null;
        intValue = null;
        longValue = null;
        doubleValue = null;
        floatValue = null;
        booleanValue = null;
        if (value instanceof String) {
            setStringValue((String) value);
        } else if (value instanceof Integer) {
            setIntValue((Integer) value);
        } else if (value instanceof Long) {
            setLongValue((Long) value);
        } else if (value instanceof Double) {
            setDoubleValue((Double) value);
        } else if (value instanceof Float) {
            setFloatValue((Float) value);
        } else if (value instanceof Boolean) {
            setBooleanValue((Boolean) value);
        }
    }

    @Override
    public Object getValueObj() {
        if (stringValue != null) {
            return stringValue;
        } else if (intValue != null) {
            return intValue;
        } else if (longValue != null) {
            return longValue;
        } else if (doubleValue != null) {
            return doubleValue;
        } else if (floatValue != null) {
            return floatValue;
        } else if (booleanValue != null) {
            return booleanValue;
        } else {
            return null;
        }
    }

    @Override
    public String getStringValue() {
        return stringValue;
    }

    @Override
    public Integer getIntValue() {
        return this.intValue;
    }

    @Override
    public Long getLongValue() {
        return this.longValue;
    }

    @Override
    public Double getDoubleValue() {
        return this.doubleValue;
    }

    @Override
    public Float getFloatValue() {
        return this.floatValue;
    }

    @Override
    public Boolean getBooleanValue() {
        return this.booleanValue;
    }

    @Override
    public void setStringValue(String stringValue) {
        this.stringValue = stringValue;
    }

    @Override
    public void setIntValue(Integer intValue) {
        this.intValue = intValue;
    }

    @Override
    public void setLongValue(Long longValue) {
        this.longValue = longValue;
    }

    @Override
    public void setDoubleValue(Double doubleValue) {
        this.doubleValue = doubleValue;
    }

    @Override
    public void setFloatValue(Float floatValue) {
        this.floatValue = floatValue;
    }

    @Override
    public void setBooleanValue(Boolean booleanValue) {
        this.booleanValue = booleanValue;
    }

    public String stringVal() {
        if (stringValue != null) {
            return stringValue;
        }
        return "";
    }

    public Integer intVal() {
        if (intValue != null) {
            return intValue;
        } else if (longValue != null) {
            return longValue.intValue();
        }
        return 0;
    }

    public Boolean booleanVal() {
        if (booleanValue != null) {
            return booleanValue;
        }
        return false;
    }

    public Double doubleVal() {
        if (doubleValue != null) {
            return doubleValue;
        } else if (intValue != null) {
            return intValue.doubleValue();
        } else if (longValue != null) {
            return longValue.doubleValue();
        }
        return 0.1;
    }

    public Float floatVal() {
        if (floatValue != null) {
            return floatValue;
        }
        return 0.1f;
    }

}
