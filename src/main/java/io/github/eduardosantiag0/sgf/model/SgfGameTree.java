package io.github.eduardosantiag0.sgf.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

/**
 * One game of an SGF collection: a root node and everything below it.
 *
 * <p>The tree is navigated through its <em>main line</em> (the root followed by the chain of first
 * children) and through move numbers. A <em>move</em> is a node with a {@code B} or {@code W}
 * property; nodes without one (comments, setup, markup only) are not counted, so
 * {@code getMove(73)} is the 73rd move actually made (a pass, which is an empty value, counts).
 *
 * <p>Not thread-safe when mutated; see {@link SgfNode}.
 */
public final class SgfGameTree {

  private final SgfNode root;

  /**
   * Creates a game around a root node.
   *
   * @param root the root node; it must not have a parent
   */
  public SgfGameTree(SgfNode root) {
    Objects.requireNonNull(root, "root");
    if (root.parent().isPresent()) {
      throw new IllegalArgumentException("The root of a game tree must not have a parent");
    }
    this.root = root;
  }

  /**
   * Returns the root node, which normally carries the game information ({@code GM}, {@code SZ},
   * {@code PB}, ...).
   *
   * @return the root node
   */
  public SgfNode root() {
    return root;
  }

  /**
   * Returns the main line: the root followed by the chain of first children.
   *
   * @return a snapshot of the main line, including non-move nodes
   */
  public List<SgfNode> mainLine() {
    return root.mainLine();
  }

  /**
   * Returns the moves of the main line, in order.
   *
   * @return a snapshot of the main-line nodes that have a {@code B} or {@code W} property
   */
  public List<SgfNode> moves() {
    List<SgfNode> moves = new ArrayList<>();
    for (SgfNode n = root; n != null; n = n.next().orElse(null)) {
      if (n.isMove()) {
        moves.add(n);
      }
    }
    return moves;
  }

  /**
   * Counts the moves of the main line.
   *
   * @return the number of main-line nodes that are moves
   */
  public int moveCount() {
    int count = 0;
    for (SgfNode n = root; n != null; n = n.next().orElse(null)) {
      if (n.isMove()) {
        count++;
      }
    }
    return count;
  }

  /**
   * Returns the n-th move of the main line.
   *
   * @param moveNumber the move number, starting at 1
   * @return the move node, or empty if the main line has fewer moves
   */
  public Optional<SgfNode> move(int moveNumber) {
    if (moveNumber < 1) {
      return Optional.empty();
    }
    int count = 0;
    for (SgfNode n = root; n != null; n = n.next().orElse(null)) {
      if (n.isMove() && ++count == moveNumber) {
        return Optional.of(n);
      }
    }
    return Optional.empty();
  }

  /**
   * Returns the n-th move of the main line.
   *
   * @param moveNumber the move number, starting at 1
   * @return the move node
   * @throws NoSuchElementException if the main line has no such move
   */
  public SgfNode getMove(int moveNumber) {
    return move(moveNumber)
        .orElseThrow(
            () ->
                new NoSuchElementException(
                    "The main line has no move number " + moveNumber + " (it has " + moveCount() + " moves)"));
  }

  /**
   * Returns every node of the tree, main line and variations, in depth-first pre-order (a parent
   * before its children, the first child's subtree before the second's).
   *
   * @return a snapshot of all nodes
   */
  public List<SgfNode> nodes() {
    List<SgfNode> all = new ArrayList<>();
    Deque<SgfNode> pending = new ArrayDeque<>();
    pending.push(root);
    while (!pending.isEmpty()) {
      SgfNode n = pending.pop();
      all.add(n);
      List<SgfNode> children = n.children();
      for (int i = children.size() - 1; i >= 0; i--) {
        pending.push(children.get(i));
      }
    }
    return all;
  }

  /**
   * Finds a node by path.
   *
   * @param path the path, relative to the root
   * @return the node, or empty if the path does not exist in this tree
   */
  public Optional<SgfNode> find(SgfNodePath path) {
    return root.find(path);
  }

  /**
   * Deep-copies the game.
   *
   * @return an independent copy
   */
  public SgfGameTree copy() {
    return new SgfGameTree(root.copy());
  }

  /**
   * Compares the content of two games.
   *
   * @param other the other game
   * @return {@code true} if both hold the same tree content
   * @see SgfNode#sameContentAs(SgfNode)
   */
  public boolean sameContentAs(SgfGameTree other) {
    return other != null && root.sameContentAs(other.root);
  }

  @Override
  public String toString() {
    return "SgfGameTree{root=" + root + "}";
  }
}
