package io.github.eduardosantiag0.sgf.parser;

/**
 * Limits and leniency switches of an {@link SgfParser}. SGF often comes from users, so the
 * defaults are finite; raise them deliberately when you trust the source.
 *
 * <p>Immutable: the {@code with...} methods return modified copies.
 *
 * @param maxInputSize the largest accepted input: characters for a {@code String}, bytes for
 *     {@code byte[]}, streams and files
 * @param maxTreeDepth the deepest accepted nesting of parentheses (the top-level game tree has
 *     depth 1)
 * @param maxNodes the largest accepted total number of nodes across the whole input
 * @param maxPropertyValueLength the longest accepted single property value, in characters after
 *     unescaping
 * @param lenient when {@code true}, three common deviations found in files from real servers are
 *     tolerated instead of being syntax errors: (1) FF[3]-style property identifiers with
 *     lowercase letters (such as {@code White} or {@code Comment}) are reduced to their uppercase
 *     letters ({@code W}, {@code C}) as the old format specified, and identifiers left with no
 *     uppercase letter are dropped together with their values; (2) properties written directly
 *     after {@code (} without the {@code ;} that starts the first node get an implicit node (seen
 *     in Nihon Ki-in exports); (3) a {@code )} with no matching {@code (} is ignored. When
 *     {@code false}, all three are reported as {@link SgfParseException}.
 */
public record SgfParserOptions(
    int maxInputSize,
    int maxTreeDepth,
    int maxNodes,
    int maxPropertyValueLength,
    boolean lenient) {

  // Compact constructor: validates that every limit is positive.
  public SgfParserOptions {
    requirePositive("maxInputSize", maxInputSize);
    requirePositive("maxTreeDepth", maxTreeDepth);
    requirePositive("maxNodes", maxNodes);
    requirePositive("maxPropertyValueLength", maxPropertyValueLength);
  }

  /**
   * Returns the default options: 10 MiB of input, nesting depth 1000, one million nodes,
   * 1 MiB per property value, strict syntax.
   *
   * @return the defaults
   */
  public static SgfParserOptions defaults() {
    return new SgfParserOptions(10 * 1024 * 1024, 1_000, 1_000_000, 1024 * 1024, false);
  }

  /**
   * Returns a copy with another input size limit.
   *
   * @param value the new {@code maxInputSize}
   * @return the modified copy
   */
  public SgfParserOptions withMaxInputSize(int value) {
    return new SgfParserOptions(value, maxTreeDepth, maxNodes, maxPropertyValueLength, lenient);
  }

  /**
   * Returns a copy with another nesting depth limit.
   *
   * @param value the new {@code maxTreeDepth}
   * @return the modified copy
   */
  public SgfParserOptions withMaxTreeDepth(int value) {
    return new SgfParserOptions(maxInputSize, value, maxNodes, maxPropertyValueLength, lenient);
  }

  /**
   * Returns a copy with another node count limit.
   *
   * @param value the new {@code maxNodes}
   * @return the modified copy
   */
  public SgfParserOptions withMaxNodes(int value) {
    return new SgfParserOptions(maxInputSize, maxTreeDepth, value, maxPropertyValueLength, lenient);
  }

  /**
   * Returns a copy with another property value length limit.
   *
   * @param value the new {@code maxPropertyValueLength}
   * @return the modified copy
   */
  public SgfParserOptions withMaxPropertyValueLength(int value) {
    return new SgfParserOptions(maxInputSize, maxTreeDepth, maxNodes, value, lenient);
  }

  /**
   * Returns a copy with another leniency.
   *
   * @param value the new {@code lenient}
   * @return the modified copy
   */
  public SgfParserOptions withLenient(boolean value) {
    return new SgfParserOptions(maxInputSize, maxTreeDepth, maxNodes, maxPropertyValueLength, value);
  }

  private static void requirePositive(String name, int value) {
    if (value <= 0) {
      throw new IllegalArgumentException(name + " must be positive but was " + value);
    }
  }
}
