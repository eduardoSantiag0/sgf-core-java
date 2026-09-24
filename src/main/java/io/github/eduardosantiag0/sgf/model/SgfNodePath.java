package io.github.eduardosantiag0.sgf.model;

import java.util.Arrays;
import java.util.Objects;

/**
 * A deterministic address of a node inside a game tree: the child index to follow at each level,
 * starting from the root. The root itself has the empty path, printed as {@code /}; the main line
 * is {@code /0}, {@code /0.0}, {@code /0.0.0}, ...; the first variation of the node at
 * {@code /0.0} is {@code /0.0.1}.
 *
 * <p>Unlike an object identity or a random id, a path <strong>survives</strong> a
 * serialize/parse round trip and is identical across processes, which makes it suitable as (part
 * of) a key in a cache, an index or a bookmark. It stays valid while variations are only
 * <em>appended</em>; removing or
 * reordering siblings changes the addresses of later siblings.
 */
public final class SgfNodePath {

  /** The path of a game tree's root node. */
  public static final SgfNodePath ROOT = new SgfNodePath(new int[0]);

  private final int[] indexes;

  private SgfNodePath(int[] indexes) {
    this.indexes = indexes;
  }

  /**
   * Creates a path from child indexes.
   *
   * @param indexes the child index at each level, each {@code >= 0}
   * @return the path
   */
  public static SgfNodePath of(int... indexes) {
    for (int index : indexes) {
      if (index < 0) {
        throw new IllegalArgumentException("Child index must not be negative: " + index);
      }
    }
    return indexes.length == 0 ? ROOT : new SgfNodePath(indexes.clone());
  }

  /**
   * Parses the textual form produced by {@link #toString()}.
   *
   * @param text for example {@code "/"} or {@code "/0.0.1"}
   * @return the path
   * @throws IllegalArgumentException if the text is malformed
   */
  public static SgfNodePath parse(String text) {
    Objects.requireNonNull(text, "text");
    if (!text.startsWith("/")) {
      throw new IllegalArgumentException("A node path must start with '/': " + text);
    }
    if (text.length() == 1) {
      return ROOT;
    }
    String[] parts = text.substring(1).split("\\.", -1);
    int[] result = new int[parts.length];
    for (int i = 0; i < parts.length; i++) {
      try {
        result[i] = Integer.parseInt(parts[i]);
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException("Invalid node path segment '" + parts[i] + "' in " + text, e);
      }
      if (result[i] < 0) {
        throw new IllegalArgumentException("Child index must not be negative in " + text);
      }
    }
    return new SgfNodePath(result);
  }

  /**
   * Returns the number of steps from the root.
   *
   * @return the depth; {@code 0} for the root
   */
  public int depth() {
    return indexes.length;
  }

  /**
   * Returns the child indexes.
   *
   * @return a copy of the indexes
   */
  public int[] indexes() {
    return indexes.clone();
  }

  /**
   * Returns the path of a child of the node this path addresses.
   *
   * @param childIndex the child index, {@code >= 0}
   * @return the extended path
   */
  public SgfNodePath child(int childIndex) {
    if (childIndex < 0) {
      throw new IllegalArgumentException("Child index must not be negative: " + childIndex);
    }
    int[] extended = Arrays.copyOf(indexes, indexes.length + 1);
    extended[indexes.length] = childIndex;
    return new SgfNodePath(extended);
  }

  int indexAt(int level) {
    return indexes[level];
  }

  @Override
  public boolean equals(Object o) {
    return this == o || (o instanceof SgfNodePath other && Arrays.equals(indexes, other.indexes));
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(indexes);
  }

  @Override
  public String toString() {
    if (indexes.length == 0) {
      return "/";
    }
    StringBuilder sb = new StringBuilder("/");
    for (int i = 0; i < indexes.length; i++) {
      if (i > 0) {
        sb.append('.');
      }
      sb.append(indexes[i]);
    }
    return sb.toString();
  }
}
