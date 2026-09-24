package io.github.eduardosantiag0.sgf.go;

/** The colour of a stone or of the player making a move. */
public enum StoneColor {
  /** Black, written {@code B} in SGF. */
  BLACK("B"),
  /** White, written {@code W} in SGF. */
  WHITE("W");

  private final String sgfIdentifier;

  StoneColor(String sgfIdentifier) {
    this.sgfIdentifier = sgfIdentifier;
  }

  /**
   * Returns the SGF property identifier of a move of this colour.
   *
   * @return {@code "B"} or {@code "W"}
   */
  public String sgfIdentifier() {
    return sgfIdentifier;
  }

  /**
   * Returns the other colour.
   *
   * @return {@link #WHITE} for black and vice versa
   */
  public StoneColor opposite() {
    return this == BLACK ? WHITE : BLACK;
  }
}
