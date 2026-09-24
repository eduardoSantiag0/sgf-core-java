package io.github.eduardosantiag0.sgf.go;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A point on a Go board in SGF terms: {@code x} counts columns from the left and {@code y} counts
 * rows from the <strong>top</strong>, both from zero. SGF writes it as two letters, {@code a} to
 * {@code z} for 0-25 and {@code A} to {@code Z} for 26-51, so {@code aa} is the top-left corner
 * and on a 19x19 board {@code pd} is the star point at the upper right.
 *
 * <p>The conversion to and from what people (and the Go Text Protocol, GTP) call
 * coordinates lives here, and only here: columns are letters {@code A-Z} <em>skipping I</em>, rows
 * are numbers counted from the <strong>bottom</strong>. On a 19x19 board {@code pd} is
 * {@code Q16}. A pass is not a coordinate; see {@link SgfMove}.
 *
 * @param x the column, from 0 at the left
 * @param y the row, from 0 at the top
 */
public record SgfCoordinate(int x, int y) {

  /** The largest board SGF can address with two letters. */
  public static final int MAX_BOARD_SIZE = 52;

  private static final String HUMAN_COLUMNS = "ABCDEFGHJKLMNOPQRSTUVWXYZ";

  // Compact constructor: validates that both components fit the two-letter SGF encoding.
  public SgfCoordinate {
    if (x < 0 || x >= MAX_BOARD_SIZE || y < 0 || y >= MAX_BOARD_SIZE) {
      throw new IllegalArgumentException(
          "Coordinate components must be in 0.." + (MAX_BOARD_SIZE - 1) + " but were (" + x + ", " + y + ")");
    }
  }

  /**
   * Parses the two-letter SGF form.
   *
   * @param sgf for example {@code "pd"}
   * @return the coordinate
   * @throws SgfSemanticException if the text is not exactly two letters
   */
  public static SgfCoordinate parse(String sgf) {
    if (sgf == null || sgf.length() != 2) {
      throw new SgfSemanticException("A point must be two letters such as 'pd' but was '" + sgf + "'");
    }
    int x = letterIndex(sgf.charAt(0));
    int y = letterIndex(sgf.charAt(1));
    if (x < 0 || y < 0) {
      throw new SgfSemanticException("A point must be two letters such as 'pd' but was '" + sgf + "'");
    }
    return new SgfCoordinate(x, y);
  }

  /**
   * Parses a point list element, which is either one point ({@code aa}) or a rectangle
   * ({@code aa:cc}, any two opposite corners) that stands for every point inside it. Rectangles are
   * expanded row by row, left to right.
   *
   * @param value the value of a point-list property such as {@code AB}
   * @return the points it denotes, at least one
   * @throws SgfSemanticException if it is malformed
   */
  public static List<SgfCoordinate> parsePointList(String value) {
    int colon = value.indexOf(':');
    if (colon < 0) {
      return List.of(parse(value));
    }
    SgfCoordinate a = parse(value.substring(0, colon));
    SgfCoordinate b = parse(value.substring(colon + 1));
    int x1 = Math.min(a.x, b.x);
    int x2 = Math.max(a.x, b.x);
    int y1 = Math.min(a.y, b.y);
    int y2 = Math.max(a.y, b.y);
    List<SgfCoordinate> points = new ArrayList<>((x2 - x1 + 1) * (y2 - y1 + 1));
    for (int y = y1; y <= y2; y++) {
      for (int x = x1; x <= x2; x++) {
        points.add(new SgfCoordinate(x, y));
      }
    }
    return points;
  }

  /**
   * Parses the human / GTP form such as {@code D4} or {@code q16}.
   *
   * @param human column letter (never {@code I}) followed by a row number
   * @param boardSize the size of the (square) board
   * @return the coordinate
   * @throws SgfSemanticException if it is malformed or off the board
   */
  public static SgfCoordinate fromHuman(String human, int boardSize) {
    requireGtpBoardSize(boardSize);
    String text = human == null ? "" : human.trim().toUpperCase(Locale.ROOT);
    if (text.length() < 2) {
      throw new SgfSemanticException("Not a board point: '" + human + "'");
    }
    int column = HUMAN_COLUMNS.indexOf(text.charAt(0));
    int row;
    try {
      row = Integer.parseInt(text.substring(1));
    } catch (NumberFormatException e) {
      throw new SgfSemanticException("Not a board point: '" + human + "'");
    }
    if (column < 0 || column >= boardSize || row < 1 || row > boardSize) {
      throw new SgfSemanticException("Point '" + human + "' is not on a " + boardSize + "x" + boardSize + " board");
    }
    return new SgfCoordinate(column, boardSize - row);
  }

  /**
   * Returns the two-letter SGF form.
   *
   * @return for example {@code "pd"}
   */
  public String toSgf() {
    return "" + letter(x) + letter(y);
  }

  /**
   * Tells whether this point lies on a board.
   *
   * @param boardSize the size of the (square) board
   * @return {@code true} if both components are smaller than the size
   */
  public boolean isOnBoard(int boardSize) {
    return x < boardSize && y < boardSize;
  }

  /**
   * Returns the human / GTP form.
   *
   * @param boardSize the size of the (square) board, at most 25
   * @return for example {@code "Q16"}
   * @throws SgfSemanticException if the point is off the board
   */
  public String toHuman(int boardSize) {
    requireGtpBoardSize(boardSize);
    if (!isOnBoard(boardSize)) {
      throw new SgfSemanticException("Point '" + toSgf() + "' is not on a " + boardSize + "x" + boardSize + " board");
    }
    return HUMAN_COLUMNS.charAt(x) + Integer.toString(boardSize - y);
  }

  @Override
  public String toString() {
    return toSgf();
  }

  private static void requireGtpBoardSize(int boardSize) {
    if (boardSize < 1 || boardSize > HUMAN_COLUMNS.length()) {
      throw new SgfSemanticException(
          "Human coordinates only exist for boards of size 1.." + HUMAN_COLUMNS.length() + " but the size is " + boardSize);
    }
  }

  private static int letterIndex(char c) {
    if (c >= 'a' && c <= 'z') {
      return c - 'a';
    }
    if (c >= 'A' && c <= 'Z') {
      return 26 + (c - 'A');
    }
    return -1;
  }

  private static char letter(int index) {
    return index < 26 ? (char) ('a' + index) : (char) ('A' + index - 26);
  }
}
