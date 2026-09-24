package io.github.eduardosantiag0.sgf.model;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * An SGF property: an identifier followed by one or more values, such as
 * {@code AB[aa][bb][cc]}.
 *
 * <p>Values are stored <em>unescaped</em> (the text a human would read); escaping is the
 * business of the parser and the serializer only. An empty string is a legal value ({@code B[]}
 * is a pass). Instances are immutable.
 *
 * @param identifier the property identifier: one or more uppercase ASCII letters
 * @param values the values, never empty, never containing {@code null}
 */
public record SgfProperty(String identifier, List<String> values) {

  // Compact constructor: validates the identifier and defensively copies the values.
  public SgfProperty {
    Objects.requireNonNull(identifier, "identifier");
    Objects.requireNonNull(values, "values");
    if (!isValidIdentifier(identifier)) {
      throw new IllegalArgumentException(
          "Invalid property identifier '" + identifier + "': expected one or more uppercase letters A-Z");
    }
    values = List.copyOf(values);
    if (values.isEmpty()) {
      throw new IllegalArgumentException("Property " + identifier + " needs at least one value");
    }
  }

  /**
   * Creates a property.
   *
   * @param identifier the property identifier
   * @param values one or more values
   * @return the property
   */
  public static SgfProperty of(String identifier, String... values) {
    return new SgfProperty(identifier, Arrays.asList(values));
  }

  /**
   * Returns the first value, which is the only one for the vast majority of properties.
   *
   * @return the first value
   */
  public String value() {
    return values.get(0);
  }

  /**
   * Tells whether a string is a valid FF[4] property identifier.
   *
   * @param identifier the candidate
   * @return {@code true} if it consists of one or more characters {@code A-Z}
   */
  public static boolean isValidIdentifier(String identifier) {
    if (identifier == null || identifier.isEmpty()) {
      return false;
    }
    for (int i = 0; i < identifier.length(); i++) {
      char c = identifier.charAt(i);
      if (c < 'A' || c > 'Z') {
        return false;
      }
    }
    return true;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder(identifier);
    for (String v : values) {
      sb.append('[').append(v).append(']');
    }
    return sb.toString();
  }
}
