package io.github.eduardosantiag0.sgf.go;

import io.github.eduardosantiag0.sgf.model.SgfNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A move of one player: a colour and either a point or a pass.
 *
 * <p>In SGF a pass is {@code B[]} (or, for boards up to 19x19 only, the older {@code B[tt]}).
 * Immutable.
 */
public final class SgfMove {

  private final StoneColor color;
  private final SgfCoordinate coordinate; // null means pass

  private SgfMove(StoneColor color, SgfCoordinate coordinate) {
    this.color = Objects.requireNonNull(color, "color");
    this.coordinate = coordinate;
  }

  /**
   * Creates a move on a point.
   *
   * @param color who plays
   * @param coordinate where
   * @return the move
   */
  public static SgfMove of(StoneColor color, SgfCoordinate coordinate) {
    return new SgfMove(color, Objects.requireNonNull(coordinate, "coordinate"));
  }

  /**
   * Creates a black move.
   *
   * @param sgfPoint the point in SGF form, for example {@code "pd"}
   * @return the move
   */
  public static SgfMove black(String sgfPoint) {
    return of(StoneColor.BLACK, SgfCoordinate.parse(sgfPoint));
  }

  /**
   * Creates a white move.
   *
   * @param sgfPoint the point in SGF form, for example {@code "dd"}
   * @return the move
   */
  public static SgfMove white(String sgfPoint) {
    return of(StoneColor.WHITE, SgfCoordinate.parse(sgfPoint));
  }

  /**
   * Creates a black move.
   *
   * @param coordinate where
   * @return the move
   */
  public static SgfMove black(SgfCoordinate coordinate) {
    return of(StoneColor.BLACK, coordinate);
  }

  /**
   * Creates a white move.
   *
   * @param coordinate where
   * @return the move
   */
  public static SgfMove white(SgfCoordinate coordinate) {
    return of(StoneColor.WHITE, coordinate);
  }

  /**
   * Creates a pass.
   *
   * @param color who passes
   * @return the move
   */
  public static SgfMove pass(StoneColor color) {
    return new SgfMove(color, null);
  }

  /**
   * Reads the move of a node.
   *
   * @param node any node
   * @param boardSize the size of the board, needed to recognise the legacy pass {@code tt} and to
   *     check that the point exists
   * @return the move, or empty if the node has neither {@code B} nor {@code W}
   * @throws SgfSemanticException if the node has both, or the value is not a point on the board
   */
  public static Optional<SgfMove> from(SgfNode node, int boardSize) {
    boolean black = node.hasProperty("B");
    boolean white = node.hasProperty("W");
    if (black && white) {
      throw new SgfSemanticException("A node must not have both B and W");
    }
    if (!black && !white) {
      return Optional.empty();
    }
    StoneColor color = black ? StoneColor.BLACK : StoneColor.WHITE;
    String value = node.value(color.sgfIdentifier()).orElseThrow();
    if (value.isEmpty() || (boardSize <= 19 && value.equals("tt"))) {
      return Optional.of(pass(color));
    }
    SgfCoordinate point = SgfCoordinate.parse(value);
    if (!point.isOnBoard(boardSize)) {
      throw new SgfSemanticException(
          "Move " + color.sgfIdentifier() + "[" + value + "] is outside a " + boardSize + "x" + boardSize + " board");
    }
    return Optional.of(of(color, point));
  }

  /**
   * Creates a fresh node holding this move, ready to be added to a tree.
   *
   * @return a new node with a {@code B} or {@code W} property
   */
  public SgfNode toNode() {
    return new SgfNode().setProperty(color.sgfIdentifier(), toSgfValue());
  }

  /**
   * Creates fresh nodes for a line of moves, ready for
   * {@link SgfNode#addVariation(List)} or {@link SgfNode#addAlternative(List)}.
   *
   * @param moves the moves in playing order
   * @return one new node per move
   */
  public static List<SgfNode> toNodes(List<SgfMove> moves) {
    List<SgfNode> nodes = new ArrayList<>(moves.size());
    for (SgfMove move : moves) {
      nodes.add(move.toNode());
    }
    return nodes;
  }

  /**
   * Returns who plays.
   *
   * @return the colour
   */
  public StoneColor color() {
    return color;
  }

  /**
   * Tells whether this is a pass.
   *
   * @return {@code true} for a pass
   */
  public boolean isPass() {
    return coordinate == null;
  }

  /**
   * Returns where the stone goes.
   *
   * @return the point, or empty for a pass
   */
  public Optional<SgfCoordinate> coordinate() {
    return Optional.ofNullable(coordinate);
  }

  /**
   * Returns the property value: the two-letter point, or the empty string for a pass.
   *
   * @return the SGF value
   */
  public String toSgfValue() {
    return coordinate == null ? "" : coordinate.toSgf();
  }

  /**
   * Returns the point in human / GTP form.
   *
   * @param boardSize the size of the board, at most 25
   * @return for example {@code "Q16"}, or {@code "pass"}
   */
  public String toHuman(int boardSize) {
    return coordinate == null ? "pass" : coordinate.toHuman(boardSize);
  }

  @Override
  public boolean equals(Object o) {
    return this == o
        || (o instanceof SgfMove other && color == other.color && Objects.equals(coordinate, other.coordinate));
  }

  @Override
  public int hashCode() {
    return Objects.hash(color, coordinate);
  }

  @Override
  public String toString() {
    return color.sgfIdentifier() + "[" + toSgfValue() + "]";
  }
}
