package io.github.eduardosantiag0.sgf.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A node of an SGF game tree: an ordered set of {@linkplain SgfProperty properties} plus the
 * child nodes that may follow it.
 *
 * <h2>Why a node tree instead of "sequences and sub-trees"</h2>
 *
 * <p>The SGF grammar writes a game as {@code GameTree = "(" Sequence GameTree* ")"}. That is a
 * <em>serialization</em> detail: semantically a game is simply a tree of nodes in which a node with
 * several children is a branching point. Modelling the tree directly means that a variation can
 * be attached to <em>any</em> node, including one in the middle of what the file writes as a
 * single sequence, without splitting sequences by hand. The parser and the serializer translate
 * between the grammar and this model.
 *
 * <p>The <strong>first</strong> child of a node continues the main line; every further child is a
 * variation.
 *
 * <h2>Properties</h2>
 *
 * <p>Any valid identifier is accepted, so properties this library knows nothing about survive
 * parsing and serialization. Insertion order is preserved.
 *
 * <h2>Thread safety</h2>
 *
 * <p>Nodes are mutable and <strong>not</strong> thread-safe. Give each thread that edits its own tree (see
 * {@link #copy()}); reading a tree from several threads is fine as long as nobody mutates it.
 * Identity semantics apply: {@code equals}/{@code hashCode} are not overridden, use
 * {@link #sameContentAs(SgfNode)} to compare content.
 */
public final class SgfNode {

  private static final List<SgfNode> NO_CHILDREN = List.of();

  private final Map<String, SgfProperty> properties = new LinkedHashMap<>();
  private List<SgfNode> children = NO_CHILDREN;
  private SgfNode parent;

  /** Creates an empty node with no properties. */
  public SgfNode() {
    // empty on purpose
  }

  // ---------------------------------------------------------------- properties

  /**
   * Returns the properties of this node in insertion order.
   *
   * @return an unmodifiable view of the properties
   */
  public Collection<SgfProperty> properties() {
    return Collections.unmodifiableCollection(properties.values());
  }

  /**
   * Tells whether this node has a property.
   *
   * @param identifier the identifier, for example {@code "B"}
   * @return {@code true} if present
   */
  public boolean hasProperty(String identifier) {
    return properties.containsKey(identifier);
  }

  /**
   * Returns a property.
   *
   * @param identifier the identifier, for example {@code "B"}
   * @return the property, or empty if this node does not have it
   */
  public Optional<SgfProperty> property(String identifier) {
    return Optional.ofNullable(properties.get(identifier));
  }

  /**
   * Returns the first value of a property.
   *
   * @param identifier the identifier
   * @return the first value, or empty if the property is absent
   */
  public Optional<String> value(String identifier) {
    SgfProperty p = properties.get(identifier);
    return p == null ? Optional.empty() : Optional.of(p.value());
  }

  /**
   * Returns all values of a property.
   *
   * @param identifier the identifier
   * @return the values, or an empty list if the property is absent
   */
  public List<String> values(String identifier) {
    SgfProperty p = properties.get(identifier);
    return p == null ? List.of() : p.values();
  }

  /**
   * Sets a property, replacing any previous value. A property that already exists keeps its
   * position in the node.
   *
   * @param identifier the identifier: uppercase letters only
   * @param values one or more values (unescaped text)
   * @return this node, for chaining
   */
  public SgfNode setProperty(String identifier, String... values) {
    properties.put(identifier, SgfProperty.of(identifier, values));
    return this;
  }

  /**
   * Sets a property, replacing any previous value.
   *
   * @param identifier the identifier: uppercase letters only
   * @param values one or more values (unescaped text)
   * @return this node, for chaining
   */
  public SgfNode setProperty(String identifier, List<String> values) {
    properties.put(identifier, new SgfProperty(identifier, values));
    return this;
  }

  /**
   * Appends values to a property, creating it if necessary.
   *
   * @param identifier the identifier: uppercase letters only
   * @param values the values to append; must not be empty
   * @return this node, for chaining
   */
  public SgfNode addValues(String identifier, List<String> values) {
    SgfProperty existing = properties.get(identifier);
    if (existing == null) {
      properties.put(identifier, new SgfProperty(identifier, values));
    } else {
      List<String> merged = new ArrayList<>(existing.values().size() + values.size());
      merged.addAll(existing.values());
      merged.addAll(values);
      properties.put(identifier, new SgfProperty(identifier, merged));
    }
    return this;
  }

  /**
   * Appends one value to a property, creating it if necessary.
   *
   * @param identifier the identifier: uppercase letters only
   * @param value the value to append
   * @return this node, for chaining
   */
  public SgfNode addValue(String identifier, String value) {
    return addValues(identifier, List.of(Objects.requireNonNull(value, "value")));
  }

  /**
   * Removes a property.
   *
   * @param identifier the identifier
   * @return {@code true} if it was present
   */
  public boolean removeProperty(String identifier) {
    return properties.remove(identifier) != null;
  }

  // ---------------------------------------------------------------- moves and comments

  /**
   * Tells whether this node is a <em>move</em>, that is whether it has a {@code B} or a {@code W}
   * property, the move properties the SGF standard defines for every game. A node that only
   * carries, say, a comment or setup properties is not a move.
   *
   * @return {@code true} for move nodes
   */
  public boolean isMove() {
    return properties.containsKey("B") || properties.containsKey("W");
  }

  /**
   * Returns the comment ({@code C} property).
   *
   * @return the comment, or empty if there is none
   */
  public Optional<String> comment() {
    return value("C");
  }

  /**
   * Replaces the comment.
   *
   * @param comment the new comment (unescaped text)
   * @return this node, for chaining
   */
  public SgfNode setComment(String comment) {
    return setProperty("C", Objects.requireNonNull(comment, "comment"));
  }

  /**
   * Appends to the comment. When a non-empty comment already exists the new text is separated
   * from it by a blank line, so original annotations are never overwritten.
   *
   * @param text the text to add (unescaped)
   * @return this node, for chaining
   */
  public SgfNode addComment(String text) {
    Objects.requireNonNull(text, "text");
    String existing = comment().orElse("");
    return setComment(existing.isEmpty() ? text : existing + "\n\n" + text);
  }

  // ---------------------------------------------------------------- tree

  /**
   * Returns the parent node.
   *
   * @return the parent, or empty for a root node
   */
  public Optional<SgfNode> parent() {
    return Optional.ofNullable(parent);
  }

  /**
   * Returns the top-most ancestor of this node (the node itself if it has no parent).
   *
   * @return the root of the tree this node belongs to
   */
  public SgfNode root() {
    SgfNode n = this;
    while (n.parent != null) {
      n = n.parent;
    }
    return n;
  }

  /**
   * Returns the children in order. The first child continues the main line, the others are
   * variations.
   *
   * @return an unmodifiable view of the children
   */
  public List<SgfNode> children() {
    return Collections.unmodifiableList(children);
  }

  /**
   * Tells whether this node has children.
   *
   * @return {@code true} if there is at least one child
   */
  public boolean hasChildren() {
    return !children.isEmpty();
  }

  /**
   * Returns the child that continues the main line.
   *
   * @return the first child, or empty if this node ends its line
   */
  public Optional<SgfNode> next() {
    return children.isEmpty() ? Optional.empty() : Optional.of(children.get(0));
  }

  /**
   * Appends a child. It becomes the main-line continuation if this node had no children,
   * otherwise a variation.
   *
   * @param child a node that has no parent yet
   * @return the child
   * @throws IllegalArgumentException if the child already has a parent, or adding it would create
   *     a cycle
   */
  public SgfNode addChild(SgfNode child) {
    Objects.requireNonNull(child, "child");
    if (child.parent != null) {
      throw new IllegalArgumentException("The node already has a parent; remove it from there first");
    }
    if (child == this) {
      throw new IllegalArgumentException("A node cannot be its own child");
    }
    if (child.hasChildren()) {
      // Only a node that already has descendants can be an ancestor of this one.
      for (SgfNode a = this; a != null; a = a.parent) {
        if (a == child) {
          throw new IllegalArgumentException("Adding this node would create a cycle");
        }
      }
    }
    attach(child);
    return child;
  }

  private void attach(SgfNode child) {
    if (children == NO_CHILDREN) {
      children = new ArrayList<>(2);
    }
    children.add(child);
    child.parent = this;
  }

  /**
   * Detaches this node, with its whole subtree, from its parent. Later siblings move up one
   * position, so if this was the first child the next sibling becomes the main line.
   *
   * @return {@code true} if the node had a parent
   */
  public boolean removeFromParent() {
    if (parent == null) {
      return false;
    }
    parent.children.remove(this);
    parent = null;
    return true;
  }

  /**
   * Adds a new branch <em>after</em> this node: a line of nodes that becomes a further child of
   * this node. The existing continuation is untouched, so the main line does not change when this
   * node already has children.
   *
   * <p>Use {@link #addAlternative(List)} to offer a different move <em>instead of</em> this node's
   * move.
   *
   * @param line the nodes of the new branch in playing order; each must be a fresh node (no
   *     parent, no children)
   * @return the first node of the new branch, handy for adding a comment
   * @throws IllegalArgumentException if the line is empty or contains a node that is not fresh
   */
  public SgfNode addVariation(List<SgfNode> line) {
    Objects.requireNonNull(line, "line");
    if (line.isEmpty()) {
      throw new IllegalArgumentException("A variation needs at least one node");
    }
    Set<SgfNode> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    for (SgfNode n : line) {
      Objects.requireNonNull(n, "line contains null");
      if (n.parent != null || n.hasChildren()) {
        throw new IllegalArgumentException("Variation nodes must be fresh (no parent, no children)");
      }
      if (n == this || !seen.add(n)) {
        throw new IllegalArgumentException("Variation nodes must be distinct and different from this node");
      }
    }
    SgfNode previous = this;
    for (SgfNode n : line) {
      previous.attach(n);
      previous = n;
    }
    return line.get(0);
  }

  /**
   * Adds a new branch after this node.
   *
   * @param line the nodes of the new branch in playing order
   * @return the first node of the new branch
   * @see #addVariation(List)
   */
  public SgfNode addVariation(SgfNode... line) {
    return addVariation(java.util.Arrays.asList(line));
  }

  /**
   * Adds a new branch as a <em>sibling</em> of this node, that is an alternative to this node's
   * move, starting from the position before it: "instead of the move played here, this line".
   * This is how an editor's "add variation here" usually behaves.
   *
   * @param line the nodes of the alternative line in playing order
   * @return the first node of the alternative line, handy for adding a comment
   * @throws IllegalStateException if this node is a root and therefore has no position before it
   * @see #addVariation(List)
   */
  public SgfNode addAlternative(List<SgfNode> line) {
    if (parent == null) {
      throw new IllegalStateException("A root node has no preceding position to branch from");
    }
    return parent.addVariation(line);
  }

  /**
   * Adds an alternative to this node's move.
   *
   * @param line the nodes of the alternative line in playing order
   * @return the first node of the alternative line
   * @see #addAlternative(List)
   */
  public SgfNode addAlternative(SgfNode... line) {
    return addAlternative(java.util.Arrays.asList(line));
  }

  /**
   * Returns this node followed by the chain of first children.
   *
   * @return the main line starting at this node, as a snapshot
   */
  public List<SgfNode> mainLine() {
    List<SgfNode> line = new ArrayList<>();
    for (SgfNode n = this; n != null; n = n.children.isEmpty() ? null : n.children.get(0)) {
      line.add(n);
    }
    return line;
  }

  /**
   * Returns the address of this node relative to the root of its tree.
   *
   * @return the path; {@link SgfNodePath#ROOT} for a root node
   */
  public SgfNodePath path() {
    int depth = 0;
    for (SgfNode n = this; n.parent != null; n = n.parent) {
      depth++;
    }
    int[] indexes = new int[depth];
    int level = depth;
    for (SgfNode n = this; n.parent != null; n = n.parent) {
      indexes[--level] = n.parent.children.indexOf(n);
    }
    return SgfNodePath.of(indexes);
  }

  /**
   * Follows a path starting at this node.
   *
   * @param path the path, relative to this node
   * @return the addressed node, or empty if the path leaves the tree
   */
  public Optional<SgfNode> find(SgfNodePath path) {
    SgfNode n = this;
    for (int level = 0; level < path.depth(); level++) {
      int index = path.indexAt(level);
      if (index >= n.children.size()) {
        return Optional.empty();
      }
      n = n.children.get(index);
    }
    return Optional.of(n);
  }

  // ---------------------------------------------------------------- copy and comparison

  /**
   * Deep-copies this node and everything below it. The copy has no parent. Properties are
   * immutable and therefore shared.
   *
   * @return an independent copy of the subtree
   */
  public SgfNode copy() {
    SgfNode rootCopy = shallowCopy();
    Deque<SgfNode[]> pending = new ArrayDeque<>();
    pending.push(new SgfNode[] {this, rootCopy});
    while (!pending.isEmpty()) {
      SgfNode[] pair = pending.pop();
      for (SgfNode child : pair[0].children) {
        SgfNode childCopy = child.shallowCopy();
        pair[1].attach(childCopy);
        pending.push(new SgfNode[] {child, childCopy});
      }
    }
    return rootCopy;
  }

  private SgfNode shallowCopy() {
    SgfNode copy = new SgfNode();
    copy.properties.putAll(properties);
    return copy;
  }

  /**
   * Compares the <em>content</em> of two subtrees: same properties (their order is irrelevant)
   * and the same children in the same order, recursively. Object identity, parents and formatting
   * play no role. This is the notion of equality a serialize/parse round trip has to preserve.
   *
   * @param other the other subtree root
   * @return {@code true} if both subtrees hold the same content
   */
  public boolean sameContentAs(SgfNode other) {
    if (other == null) {
      return false;
    }
    Deque<SgfNode[]> pending = new ArrayDeque<>();
    pending.push(new SgfNode[] {this, other});
    while (!pending.isEmpty()) {
      SgfNode[] pair = pending.pop();
      SgfNode a = pair[0];
      SgfNode b = pair[1];
      if (a.children.size() != b.children.size() || !a.properties.equals(b.properties)) {
        return false;
      }
      for (int i = 0; i < a.children.size(); i++) {
        pending.push(new SgfNode[] {a.children.get(i), b.children.get(i)});
      }
    }
    return true;
  }

  /** Short debug representation; it does not include children. */
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("SgfNode{");
    boolean first = true;
    for (SgfProperty p : properties.values()) {
      if (!first) {
        sb.append(' ');
      }
      sb.append(p);
      first = false;
    }
    return sb.append('}').toString();
  }
}
