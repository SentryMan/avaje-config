package io.avaje.config;

import io.avaje.config.load.Loader;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Core implementation of Configuration.
 */
class CoreConfiguration implements Configuration {

  private final ModifyAwareProperties properties;

  private final Map<String, OnChangeListener> callbacks = new ConcurrentHashMap<>();

  static Configuration load() {
    return new CoreConfiguration(new Loader().load());
  }

  CoreConfiguration(Properties source) {
    this.properties = new ModifyAwareProperties(this, source);
  }

  @Override
  public Properties eval(Properties properties) {

    final ExpressionEval exprEval = Loader.evalFor(properties);

    Properties evalCopy = new Properties();
    Enumeration<?> names = properties.propertyNames();
    while (names.hasMoreElements()) {
      String name = (String) names.nextElement();
      evalCopy.setProperty(name, exprEval.eval(properties.getProperty(name)));
    }
    return evalCopy;
  }

  @Override
  public void loadIntoSystemProperties() {
    properties.loadIntoSystemProperties();
  }

  @Override
  public Properties asProperties() {
    return properties.asProperties();
  }

  private String getProperty(String key) {
    return properties.getProperty(key);
  }

  private String getRequired(String key) {
    String value = getProperty(key);
    if (value == null) {
      throw new IllegalStateException("Missing required configuration parameter [" + key + "]");
    }
    return value;
  }

  @Override
  public String get(String key) {
    return getRequired(key);
  }

  @Override
  public String get(String key, String defaultValue) {
    return properties.getProperty(key, defaultValue);
  }

  @Override
  public Optional<String> getOptional(String key) {
    return Optional.ofNullable(getProperty(key));
  }

  @Override
  public boolean getBool(String key) {
    return Boolean.parseBoolean(getRequired(key));
  }

  @Override
  public boolean getBool(String key, boolean defaultValue) {
    return properties.getBool(key, defaultValue);
  }

  @Override
  public int getInt(String key) {
    return Integer.parseInt(getRequired(key));
  }

  @Override
  public int getInt(String key, int defaultValue) {
    String val = getProperty(key);
    return (val == null) ? defaultValue : Integer.parseInt(val);
  }

  @Override
  public long getLong(String key) {
    return Long.parseLong(getRequired(key));
  }

  @Override
  public long getLong(String key, long defaultValue) {
    String val = getProperty(key);
    return (val == null) ? defaultValue : Long.parseLong(val);
  }

  @Override
  public void onChange(String key, Consumer<String> callback) {
    onChangeRegister(DataType.STRING, key, callback);
  }

  @Override
  public void onChangeInt(String key, Consumer<Integer> callback) {
    onChangeRegister(DataType.INT, key, callback);
  }

  @Override
  public void onChangeLong(String key, Consumer<Long> callback) {
    onChangeRegister(DataType.LONG, key, callback);
  }

  @Override
  public void onChangeBool(String key, Consumer<Boolean> callback) {
    onChangeRegister(DataType.BOOL, key, callback);
  }

  private void fireOnChange(String key, String value) {
    OnChangeListener listener = callbacks.get(key);
    if (listener != null) {
      listener.fireOnChange(value);
    }
  }

  private void onChangeRegister(DataType type, String key, Consumer<?> callback) {
    callbacks.computeIfAbsent(key, s -> new OnChangeListener()).register(new Callback(type, callback));
  }

  @Override
  public void setProperty(String key, String newValue) {
    properties.setProperty(key, newValue);
  }

  private static class OnChangeListener {

    private final List<Callback> callbacks = new ArrayList<>();

    void register(Callback callback) {
      callbacks.add(callback);
    }

    void fireOnChange(String value) {
      for (Callback callback : callbacks) {
        callback.fireOnChange(value);
      }
    }
  }

  private enum DataType {
    INT,
    LONG,
    BOOL,
    STRING
  }

  @SuppressWarnings("rawtypes")
  private static class Callback {

    private final DataType type;

    private final Consumer consumer;

    Callback(DataType type, Consumer consumer) {
      this.type = type;
      this.consumer = consumer;
    }

    @SuppressWarnings("unchecked")
    void fireOnChange(String value) {
      consumer.accept(convert(value));
    }

    private Object convert(String value) {
      switch (type) {
        case INT:
          return Integer.valueOf(value);
        case LONG:
          return Long.valueOf(value);
        case BOOL:
          return Boolean.valueOf(value);
        default:
          return value;
      }
    }
  }

  private static class ModifyAwareProperties {

    /**
     * Null value placeholder in properties ConcurrentHashMap.
     */
    private static final String NULL_PLACEHOLDER = "NULL";

    private final Map<String, String> properties = new ConcurrentHashMap<>();

    private final Map<String, Boolean> propertiesBoolCache = new ConcurrentHashMap<>();

    private final CoreConfiguration config;

    ModifyAwareProperties(CoreConfiguration config, Properties source) {
      this.config = config;
      loadAll(source);
    }

    private void loadAll(Properties source) {
      for (Map.Entry<Object, Object> entry : source.entrySet()) {
        if (entry.getValue() != null) {
          properties.put(entry.getKey().toString(), entry.getValue().toString());
        }
      }
    }

    void setProperty(String key, String newValue) {
      Object oldValue;
      if (newValue == null) {
        oldValue = properties.remove(key);
      } else {
        oldValue = properties.put(key, newValue);
      }
      if (!Objects.equals(newValue, oldValue)) {
        propertiesBoolCache.remove(key);
        config.fireOnChange(key, newValue);
      }
    }

    /**
     * Get boolean property with caching to take into account misses/default values
     * and parseBoolean(). As getBool is expected to be used in a dynamic feature toggle
     * with very high concurrent use.
     */
    boolean getBool(String key, boolean defaultValue) {
      final Boolean cachedValue = propertiesBoolCache.get(key);
      if (cachedValue != null) {
        return cachedValue;
      }
      // populate our specialised boolean cache to minimise costs on heavy use
      final String rawValue = getProperty(key);
      boolean value = (rawValue == null) ? defaultValue : Boolean.parseBoolean(rawValue);
      propertiesBoolCache.put(key, value);
      return value;
    }

    String getProperty(String key) {
      return getProperty(key, NULL_PLACEHOLDER);
    }

    /**
     * Get property with caching taking into account defaultValue and "null".
     */
    String getProperty(String key, String defaultValue) {
      String val = properties.get(key);
      if (val == null) {
        // defining property at runtime with System property backing
        val = System.getProperty(key);
        if (val == null) {
          val = (defaultValue == null) ? NULL_PLACEHOLDER : defaultValue;
        }
        // cache in concurrent map to provide higher concurrent use
        properties.put(key, val);
      }
      return (val != NULL_PLACEHOLDER) ? val: null;
    }

    void loadIntoSystemProperties() {
      for (Map.Entry<String, String> entry : properties.entrySet()) {
        final String value = entry.getValue();
        if (value != NULL_PLACEHOLDER) {
          System.setProperty(entry.getKey(), value);
        }
      }
    }

    Properties asProperties() {
      Properties props = new Properties();
      for (Map.Entry<String, String> entry : properties.entrySet()) {
        final String value = entry.getValue();
        if (value != NULL_PLACEHOLDER) {
          props.setProperty(entry.getKey(), value);
        }
      }
      return props;
    }
  }

}
