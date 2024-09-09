package io.avaje.config.toml;

import io.avaje.config.ConfigParser;
import org.jspecify.annotations.NullMarked;
import org.tomlj.Toml;
import org.tomlj.TomlArray;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.stream.Collectors;

@NullMarked
public final class TomlParser implements ConfigParser {

  private static final String[] extensions = {"toml"};

  @Override
  public String[] supportedExtensions() {
    return extensions;
  }

  @Override
  public Map<String, String> load(Reader reader) {
    try {
      return Toml.parse(reader).dottedEntrySet()
        .stream()
        .collect(Collectors.toMap(Map.Entry::getKey, entry -> readTomlValue(entry.getValue())));
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }

  @Override
  public Map<String, String> load(InputStream is) {
    try {
      return Toml.parse(is).dottedEntrySet()
        .stream()
        .collect(Collectors.toMap(Map.Entry::getKey, entry -> readTomlValue(entry.getValue())));
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }

  private static String readTomlValue(Object object) {
    if (object instanceof TomlArray) {
      TomlArray array = (TomlArray) object;
      return array.toList().stream()
        .map(TomlParser::readTomlValue)
        .collect(Collectors.joining(";"));
    }
    return String.valueOf(object);
  }
}
