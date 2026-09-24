package io.github.eduardosantiag0.sgf.parser;

/**
 * The input is (or may be) valid SGF but exceeds a configured {@link SgfParserOptions} limit.
 * Distinguishing it from a plain {@link SgfParseException} lets callers answer "too big" (for
 * example HTTP 413) differently from "malformed".
 */
public class SgfLimitExceededException extends SgfParseException {

  private static final long serialVersionUID = 1L;

  /** The name of the exceeded option. */
  private final String limitName;

  /** The configured value of the exceeded option. */
  private final long limit;

  /**
   * Creates an exception.
   *
   * @param limitName the name of the exceeded option, such as {@code maxNodes}
   * @param limit the configured limit
   * @param message the full, already formatted message
   * @param position the zero-based character offset where the limit was hit
   * @param line the one-based line
   * @param column the one-based column
   */
  public SgfLimitExceededException(
      String limitName, long limit, String message, int position, int line, int column) {
    super(message, position, line, column);
    this.limitName = limitName;
    this.limit = limit;
  }

  /**
   * Returns which limit was exceeded.
   *
   * @return the option name, for example {@code maxTreeDepth}
   */
  public String limitName() {
    return limitName;
  }

  /**
   * Returns the configured value of the exceeded limit.
   *
   * @return the limit
   */
  public long limit() {
    return limit;
  }
}
