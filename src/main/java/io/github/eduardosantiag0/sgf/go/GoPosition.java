package io.github.eduardosantiag0.sgf.go;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A Go position described by how it comes about: the board size, the stones that were on the board
 * before the first move (handicap stones, or the set-up of a problem) and the moves played since,
 * in order.
 *
 * <p>This is a <em>description</em>, not a simulation: it says which stones were placed and which
 * moves were played, and does not apply the rules. There is no capture, ko or legality logic in this
 * library, on purpose. Whatever consumes the position (a board renderer, a rules implementation, a
 * game-playing program, a database) is expected to replay it with the rules it cares about. Most
 * programs that speak Go can be fed exactly this pair of "setup stones" and "moves".
 *
 * <p>Limitation: setup properties ({@code AB}/{@code AW}/{@code AE}) in a node <em>after</em> the
 * root cannot be expressed as a list of setup stones. They are not applied; {@link
 * #hasMidGameSetup()} tells you when that happened so you can refuse or handle the game.
 *
 * <p>Immutable.
 */
public final class GoPosition {

  private final int boardSize;
  private final List<SgfMove> setupStones;
  private final List<SgfMove> moves;
  private final boolean midGameSetup;

  /**
   * Creates a position description.
   *
   * @param boardSize the board size
   * @param setupStones the stones on the board before the first move
   * @param moves the moves played, in order
   * @param midGameSetup whether setup properties beyond the root were ignored
   */
  public GoPosition(
      int boardSize, List<SgfMove> setupStones, List<SgfMove> moves, boolean midGameSetup) {
    this.boardSize = boardSize;
    this.setupStones = List.copyOf(setupStones);
    this.moves = List.copyOf(moves);
    this.midGameSetup = midGameSetup;
  }

  /**
   * Returns the board size.
   *
   * @return the size of the (square) board
   */
  public int boardSize() {
    return boardSize;
  }

  /**
   * Returns the stones that were on the board before the first move, as non-pass "moves" of the
   * colour of each stone.
   *
   * @return the setup stones, in the order the root node listed them
   */
  public List<SgfMove> setupStones() {
    return setupStones;
  }

  /**
   * Returns the moves played, passes included.
   *
   * @return the moves in playing order
   */
  public List<SgfMove> moves() {
    return moves;
  }

  /**
   * Returns the number of moves played.
   *
   * @return {@code moves().size()}
   */
  public int moveCount() {
    return moves.size();
  }

  /**
   * Tells whether some node after the root carried setup stones, which this description cannot
   * represent.
   *
   * @return {@code true} if such setup was ignored
   */
  public boolean hasMidGameSetup() {
    return midGameSetup;
  }

  /**
   * Returns a deterministic text form of the position, identical for identical positions no matter
   * how the SGF was formatted or in which order its setup stones were listed. Handy as a key
   * wherever positions must be compared, de-duplicated, indexed or cached (an opening book, a
   * database of positions, a memoisation map); hashing and storing are left to the caller.
   *
   * <p>Example: {@code size=19;setup=B:dd,W:pp;moves=B:pd,W:dd,B:pass}
   *
   * @return the canonical text
   */
  public String canonical() {
    List<SgfMove> sorted = new ArrayList<>(setupStones);
    sorted.sort(
        Comparator.comparing((SgfMove m) -> m.color())
            .thenComparingInt(m -> m.coordinate().orElseThrow().x())
            .thenComparingInt(m -> m.coordinate().orElseThrow().y()));
    StringBuilder sb = new StringBuilder("size=").append(boardSize).append(";setup=");
    appendMoves(sb, sorted);
    sb.append(";moves=");
    appendMoves(sb, moves);
    if (midGameSetup) {
      sb.append(";midgame-setup");
    }
    return sb.toString();
  }

  private static void appendMoves(StringBuilder sb, List<SgfMove> list) {
    for (int i = 0; i < list.size(); i++) {
      SgfMove m = list.get(i);
      if (i > 0) {
        sb.append(',');
      }
      sb.append(m.color().sgfIdentifier()).append(':').append(m.isPass() ? "pass" : m.toSgfValue());
    }
  }

  /** Two positions are equal when their {@linkplain #canonical() canonical forms} are. */
  @Override
  public boolean equals(Object o) {
    return this == o || (o instanceof GoPosition other && canonical().equals(other.canonical()));
  }

  @Override
  public int hashCode() {
    return canonical().hashCode();
  }

  @Override
  public String toString() {
    return "GoPosition{" + canonical() + "}";
  }
}
