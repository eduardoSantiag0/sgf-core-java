package io.github.eduardosantiag0.sgf.parser;

import io.github.eduardosantiag0.sgf.SgfException;

/**
 * The input is not syntactically valid SGF. The message says what was found and what was
 * expected, and the location is available programmatically.
 *
 * <p>Syntactic errors (a missing {@code ]} or {@code )}, a bad identifier, ...) are reported
 * here. A document that parses fine but is odd <em>as a Go game</em> (a move outside the board,
 * say) is not a parse error; see {@code GoGame.validate()}.
 */
public class SgfParseException extends SgfException {

  private static final long serialVersionUID = 1L;

  /** Zero-based character offset of the problem. */
  private final int position;

  /** One-based line of the problem. */
  private final int line;

  /** One-based column of the problem. */
  private final int column;

  /**
   * Creates an exception.
   *
   * @param message the full, already formatted message
   * @param position the zero-based character offset of the problem
   * @param line the one-based line of the problem
   * @param column the one-based column of the problem
   */
  public SgfParseException(String message, int position, int line, int column) {
    super(message);
    this.position = position;
    this.line = line;
    this.column = column;
  }

  /**
   * Returns where the problem is.
   *
   * @return the zero-based character offset into the input
   */
  public int position() {
    return position;
  }

  /**
   * Returns the line of the problem.
   *
   * @return the one-based line number
   */
  public int line() {
    return line;
  }

  /**
   * Returns the column of the problem.
   *
   * @return the one-based column number
   */
  public int column() {
    return column;
  }
}
