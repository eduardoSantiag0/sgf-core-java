package io.github.eduardosantiag0.sgf.go;

import io.github.eduardosantiag0.sgf.model.SgfGameTree;
import io.github.eduardosantiag0.sgf.model.SgfNode;
import io.github.eduardosantiag0.sgf.model.SgfNodePath;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

/**
 * A Go-aware, read-only <em>view</em> of an {@link SgfGameTree}. The tree stays the single source
 * of truth (edits made to it are visible here at once); this class only adds Go meaning to it:
 * board size, players, the moves as {@link SgfMove}s, and the position at any point.
 *
 * <p>Nothing is cached and no state is kept besides the tree, so a view is cheap to create and
 * follows the same thread-safety rules as the tree itself.
 *
 * <p>Accessors throw {@link SgfSemanticException} when a value is present but unusable (say
 * {@code SZ[abc]}); use {@link #validate()} to collect every such problem without stopping at the
 * first.
 */
public final class GoGame {

  private static final int DEFAULT_BOARD_SIZE = 19;

  private final SgfGameTree tree;

  private GoGame(SgfGameTree tree) {
    this.tree = tree;
  }

  /**
   * Creates the view.
   *
   * @param tree the game to look at
   * @return the view
   */
  public static GoGame of(SgfGameTree tree) {
    return new GoGame(Objects.requireNonNull(tree, "tree"));
  }

  /**
   * Returns the underlying tree.
   *
   * @return the tree
   */
  public SgfGameTree tree() {
    return tree;
  }

  // ---------------------------------------------------------------- game information

  /**
   * Returns the board size ({@code SZ}); 19 when the property is absent.
   *
   * @return the size of the square board
   * @throws SgfSemanticException if {@code SZ} is unreadable, out of range or not square
   */
  public int boardSize() {
    Optional<String> sz = tree.root().value("SZ");
    if (sz.isEmpty()) {
      return DEFAULT_BOARD_SIZE;
    }
    String text = sz.get().trim();
    int colon = text.indexOf(':');
    if (colon < 0) {
      return checkedSize(parseSize(text, text), text);
    }
    int columns = parseSize(text.substring(0, colon), text);
    int rows = parseSize(text.substring(colon + 1), text);
    if (columns != rows) {
      throw new SgfSemanticException("Rectangular boards are not supported: SZ[" + text + "]");
    }
    return checkedSize(columns, text);
  }

  private static int parseSize(String part, String whole) {
    try {
      return Integer.parseInt(part.trim());
    } catch (NumberFormatException e) {
      throw new SgfSemanticException("Unreadable board size SZ[" + whole + "]");
    }
  }

  private static int checkedSize(int size, String text) {
    if (size < 1 || size > SgfCoordinate.MAX_BOARD_SIZE) {
      throw new SgfSemanticException(
          "Board size SZ[" + text + "] must be between 1 and " + SgfCoordinate.MAX_BOARD_SIZE);
    }
    return size;
  }

  /**
   * Tells whether this is a Go game: {@code GM} is 1 or absent.
   *
   * @return {@code true} for Go
   */
  public boolean isGo() {
    return tree.root().value("GM").map(v -> v.trim().equals("1")).orElse(true);
  }

  /**
   * Returns the komi ({@code KM}).
   *
   * @return the komi, or empty if the property is absent
   * @throws SgfSemanticException if it is not a number
   */
  public Optional<Double> komi() {
    return tree.root()
        .value("KM")
        .map(
            v -> {
              try {
                return Double.parseDouble(v.trim());
              } catch (NumberFormatException e) {
                throw new SgfSemanticException("Unreadable komi KM[" + v + "]");
              }
            });
  }

  /**
   * Returns the name of the black player ({@code PB}).
   *
   * @return the name, if present
   */
  public Optional<String> blackName() {
    return tree.root().value("PB");
  }

  /**
   * Returns the name of the white player ({@code PW}).
   *
   * @return the name, if present
   */
  public Optional<String> whiteName() {
    return tree.root().value("PW");
  }

  /**
   * Returns the rank of the black player ({@code BR}).
   *
   * @return the rank, if present
   */
  public Optional<String> blackRank() {
    return tree.root().value("BR");
  }

  /**
   * Returns the rank of the white player ({@code WR}).
   *
   * @return the rank, if present
   */
  public Optional<String> whiteRank() {
    return tree.root().value("WR");
  }

  /**
   * Returns the ruleset ({@code RU}), for example {@code Japanese}.
   *
   * @return the rules, if present
   */
  public Optional<String> rules() {
    return tree.root().value("RU");
  }

  /**
   * Returns the result ({@code RE}), for example {@code B+R}.
   *
   * @return the result, if present
   */
  public Optional<String> result() {
    return tree.root().value("RE");
  }

  /**
   * Returns the date text ({@code DT}), unparsed.
   *
   * @return the date, if present
   */
  public Optional<String> date() {
    return tree.root().value("DT");
  }

  /**
   * Returns the event ({@code EV}).
   *
   * @return the event, if present
   */
  public Optional<String> event() {
    return tree.root().value("EV");
  }

  // ---------------------------------------------------------------- moves

  /**
   * Returns the moves of the main line as {@link SgfMove}s.
   *
   * @return the moves, passes included
   * @throws SgfSemanticException if a move is unusable
   */
  public List<SgfMove> mainLineMoves() {
    int size = boardSize();
    List<SgfMove> moves = new ArrayList<>();
    for (SgfNode node : tree.moves()) {
      moves.add(SgfMove.from(node, size).orElseThrow());
    }
    return moves;
  }

  // ---------------------------------------------------------------- positions

  /**
   * Describes the position <em>before</em> the n-th move of the main line: the board as it was when
   * that move was about to be played, with the setup stones and the n-1 moves that led to it.
   *
   * @param moveNumber the move number, starting at 1
   * @return the position
   * @throws NoSuchElementException if the main line has no such move
   * @throws SgfSemanticException if a value on the way is unusable
   */
  public GoPosition positionBefore(int moveNumber) {
    return positionBefore(tree.getMove(moveNumber));
  }

  /**
   * Describes the position before a node, which may be anywhere in the tree, variations included.
   * The position is what the path from the root to the node's parent leads to.
   *
   * @param node a node of this game
   * @return the position; the empty board for the root
   * @throws IllegalArgumentException if the node does not belong to this game
   * @throws SgfSemanticException if a value on the way is unusable
   */
  public GoPosition positionBefore(SgfNode node) {
    List<SgfNode> path = pathTo(node);
    path.remove(path.size() - 1);
    return build(path);
  }

  /**
   * Describes the position after a node, which may be anywhere in the tree, variations included.
   *
   * @param node a node of this game
   * @return the position
   * @throws IllegalArgumentException if the node does not belong to this game
   * @throws SgfSemanticException if a value on the way is unusable
   */
  public GoPosition positionAfter(SgfNode node) {
    return build(pathTo(node));
  }

  private List<SgfNode> pathTo(SgfNode node) {
    Objects.requireNonNull(node, "node");
    if (node.root() != tree.root()) {
      throw new IllegalArgumentException("The node does not belong to this game");
    }
    List<SgfNode> path = new ArrayList<>();
    for (SgfNode n = node; n != null; n = n.parent().orElse(null)) {
      path.add(n);
    }
    Collections.reverse(path);
    return path;
  }

  private GoPosition build(List<SgfNode> path) {
    int size = boardSize();
    Map<SgfCoordinate, StoneColor> stones = new LinkedHashMap<>();
    List<SgfMove> moves = new ArrayList<>();
    boolean midGameSetup = false;
    for (int i = 0; i < path.size(); i++) {
      SgfNode node = path.get(i);
      if (hasSetup(node)) {
        if (i == 0) {
          applySetup(node, size, stones);
        } else {
          midGameSetup = true;
        }
      }
      SgfMove.from(node, size).ifPresent(moves::add);
    }
    List<SgfMove> initial = new ArrayList<>(stones.size());
    stones.forEach((point, color) -> initial.add(SgfMove.of(color, point)));
    return new GoPosition(size, initial, moves, midGameSetup);
  }

  private static boolean hasSetup(SgfNode node) {
    return node.hasProperty("AB") || node.hasProperty("AW") || node.hasProperty("AE");
  }

  private static void applySetup(SgfNode node, int size, Map<SgfCoordinate, StoneColor> stones) {
    for (SgfCoordinate p : GoNodes.points(node, "AB", size)) {
      stones.put(p, StoneColor.BLACK);
    }
    for (SgfCoordinate p : GoNodes.points(node, "AW", size)) {
      stones.put(p, StoneColor.WHITE);
    }
    for (SgfCoordinate p : GoNodes.points(node, "AE", size)) {
      stones.remove(p);
    }
  }

  // ---------------------------------------------------------------- validation

  /**
   * Looks for content that is legal SGF but not a sensible Go game, and reports all of it instead
   * of stopping at the first problem. Checks: game type, file format version (1-4 are all fine,
   * FF[3] files are common), board size, moves that are
   * unreadable or off the board, nodes with both {@code B} and {@code W}, setup points that are
   * unreadable or off the board, and nodes mixing a move with setup properties.
   *
   * @return the issues found; empty if the game looks fine
   */
  public List<SgfIssue> validate() {
    List<SgfIssue> issues = new ArrayList<>();
    SgfNode root = tree.root();

    root.value("GM")
        .filter(gm -> !gm.trim().equals("1"))
        .ifPresent(
            gm -> issues.add(issue(SgfIssue.Severity.WARNING, SgfNodePath.ROOT, "GM[" + gm + "] is not a Go game (GM[1])")));
    root.value("FF")
        .filter(ff -> !ff.trim().matches("[1-4]"))
        .ifPresent(
            ff ->
                issues.add(
                    issue(SgfIssue.Severity.WARNING, SgfNodePath.ROOT, "FF[" + ff + "] is not a known SGF version (1-4)")));

    int size;
    try {
      size = boardSize();
    } catch (SgfSemanticException e) {
      issues.add(issue(SgfIssue.Severity.ERROR, SgfNodePath.ROOT, e.getMessage()));
      return issues; // every other check depends on the board size
    }

    for (SgfNode node : tree.nodes()) {
      try {
        SgfMove.from(node, size);
      } catch (SgfSemanticException e) {
        issues.add(issue(SgfIssue.Severity.ERROR, node.path(), e.getMessage()));
      }
      for (String id : new String[] {"AB", "AW", "AE"}) {
        try {
          GoNodes.points(node, id, size);
        } catch (SgfSemanticException e) {
          issues.add(issue(SgfIssue.Severity.ERROR, node.path(), id + ": " + e.getMessage()));
        }
      }
      if (node.isMove() && hasSetup(node)) {
        issues.add(
            issue(
                SgfIssue.Severity.WARNING,
                node.path(),
                "A node should not mix a move (B/W) with setup properties (AB/AW/AE)"));
      }
    }
    return issues;
  }

  private static SgfIssue issue(SgfIssue.Severity severity, SgfNodePath path, String message) {
    return new SgfIssue(severity, path, message);
  }
}
