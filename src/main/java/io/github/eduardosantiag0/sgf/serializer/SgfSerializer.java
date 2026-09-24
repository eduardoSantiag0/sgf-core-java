package io.github.eduardosantiag0.sgf.serializer;

import io.github.eduardosantiag0.sgf.model.SgfCollection;
import io.github.eduardosantiag0.sgf.model.SgfGameTree;
import io.github.eduardosantiag0.sgf.model.SgfNode;
import io.github.eduardosantiag0.sgf.model.SgfProperty;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;

/**
 * Writes the model back as SGF text.
 *
 * <p>A node with a single child continues its sequence; a node with several children ends the
 * sequence and each child starts a parenthesized game tree, first child first. Property values are
 * escaped ({@code \} and {@code ]}); everything else is written as is. Property order inside a
 * node is the order of the model.
 *
 * <p>Two layouts are available. {@link #pretty()} (the default) puts every node and every
 * variation on its own line:
 *
 * <pre>
 * (;GM[1]FF[4]SZ[19]
 * ;B[pd]
 * ;W[dd]
 * (;B[qp])
 * (;B[pp]))
 * </pre>
 *
 * {@link #compact()} writes each game on a single line. Both parse back to the same tree.
 * Whitespace and formatting of a previously parsed document are <em>not</em> preserved, only its
 * content.
 *
 * <p>Iterative and stateless: safe for arbitrarily deep trees and for concurrent use of one
 * instance.
 */
public final class SgfSerializer {

  private final boolean pretty;

  private SgfSerializer(boolean pretty) {
    this.pretty = pretty;
  }

  /**
   * Creates a serializer that writes one node or variation per line.
   *
   * @return a pretty-printing serializer
   */
  public static SgfSerializer pretty() {
    return new SgfSerializer(true);
  }

  /**
   * Creates a serializer that writes each game on one line.
   *
   * @return a compact serializer
   */
  public static SgfSerializer compact() {
    return new SgfSerializer(false);
  }

  /** Creates a pretty-printing serializer; same as {@link #pretty()}. */
  public SgfSerializer() {
    this(true);
  }

  /**
   * Serializes a collection. Games are separated by a line break.
   *
   * @param collection the collection
   * @return the SGF text
   */
  public String serialize(SgfCollection collection) {
    Objects.requireNonNull(collection, "collection");
    StringBuilder out = new StringBuilder();
    boolean first = true;
    for (SgfGameTree game : collection.games()) {
      if (!first) {
        out.append('\n');
      }
      write(game, out);
      first = false;
    }
    return out.toString();
  }

  /**
   * Serializes a single game.
   *
   * @param game the game
   * @return the SGF text
   */
  public String serialize(SgfGameTree game) {
    Objects.requireNonNull(game, "game");
    StringBuilder out = new StringBuilder();
    write(game, out);
    return out.toString();
  }

  private void write(SgfGameTree game, StringBuilder out) {
    // Every item on the stack is the first node of a parenthesized group still to be written,
    // except the dedicated marker below, which stands for "write a closing parenthesis here".
    ArrayDeque<SgfNode> stack = new ArrayDeque<>();
    final SgfNode close = new SgfNode();
    stack.push(game.root());
    boolean rootGroup = true;
    while (!stack.isEmpty()) {
      SgfNode groupStart = stack.pop();
      if (groupStart == close) {
        out.append(')');
        continue;
      }
      if (pretty && !rootGroup) {
        out.append('\n');
      }
      rootGroup = false;
      out.append('(');

      // The sequence: follow single children; a node with zero or several children ends it.
      SgfNode node = groupStart;
      boolean firstNode = true;
      while (true) {
        if (pretty && !firstNode) {
          out.append('\n');
        }
        firstNode = false;
        writeNode(node, out);
        List<SgfNode> children = node.children();
        if (children.size() != 1) {
          stack.push(close);
          for (int i = children.size() - 1; i >= 0; i--) {
            stack.push(children.get(i));
          }
          break;
        }
        node = children.get(0);
      }
    }
  }

  private static void writeNode(SgfNode node, StringBuilder out) {
    out.append(';');
    for (SgfProperty property : node.properties()) {
      out.append(property.identifier());
      for (String value : property.values()) {
        out.append('[');
        escapeInto(value, out);
        out.append(']');
      }
    }
  }

  private static void escapeInto(String value, StringBuilder out) {
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c == '\\' || c == ']') {
        out.append('\\');
      }
      out.append(c);
    }
  }
}
