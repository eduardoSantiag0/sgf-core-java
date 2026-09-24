package io.github.eduardosantiag0.sgf;

/**
 * Base class of every exception thrown by this library for problems with SGF
 * <em>content</em> (as opposed to programming errors, which raise the usual
 * {@link IllegalArgumentException} / {@link IllegalStateException}).
 *
 * <p>Unchecked on purpose: SGF handling usually sits several layers below the code that can react
 * to a bad file, and a checked exception would only be wrapped again on the way up.
 */
public class SgfException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /**
   * Creates an exception.
   *
   * @param message a description of the problem
   */
  public SgfException(String message) {
    super(message);
  }

  /**
   * Creates an exception with a cause.
   *
   * @param message a description of the problem
   * @param cause the underlying cause
   */
  public SgfException(String message, Throwable cause) {
    super(message, cause);
  }
}
