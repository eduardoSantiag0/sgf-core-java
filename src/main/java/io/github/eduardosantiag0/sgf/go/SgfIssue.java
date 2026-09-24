package io.github.eduardosantiag0.sgf.go;

import io.github.eduardosantiag0.sgf.model.SgfNodePath;

/**
 * A finding of {@link GoGame#validate()}: something that is legal SGF syntax but odd or wrong as a
 * Go game.
 *
 * @param severity how serious it is
 * @param path where it is, relative to the game's root
 * @param message a description
 */
public record SgfIssue(Severity severity, SgfNodePath path, String message) {

  /** How serious an issue is. */
  public enum Severity {
    /** Unusual or non-standard, but readable. */
    WARNING,
    /** The game cannot be interpreted correctly. */
    ERROR
  }

  @Override
  public String toString() {
    return severity + " at " + path + ": " + message;
  }
}
