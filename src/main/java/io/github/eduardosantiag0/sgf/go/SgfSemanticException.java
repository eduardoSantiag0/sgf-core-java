package io.github.eduardosantiag0.sgf.go;

import io.github.eduardosantiag0.sgf.SgfException;

/**
 * The SGF is syntactically fine but a value does not make sense as Go: a coordinate that is not
 * two letters, a move outside the board, an unreadable {@code SZ} or {@code KM}, ...
 *
 * <p>Kept apart from {@link io.github.eduardosantiag0.sgf.parser.SgfParseException} on purpose:
 * a game with one odd move is still a perfectly good SGF document and can be parsed, edited and
 * written back.
 */
public class SgfSemanticException extends SgfException {

  private static final long serialVersionUID = 1L;

  /**
   * Creates an exception.
   *
   * @param message what is wrong
   */
  public SgfSemanticException(String message) {
    super(message);
  }
}
