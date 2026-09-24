package io.github.eduardosantiag0.sgf.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * The content of an SGF file or string: one or more {@linkplain SgfGameTree game trees}.
 *
 * <p>Not thread-safe when mutated; see {@link SgfNode}.
 */
public final class SgfCollection implements Iterable<SgfGameTree> {

  private final List<SgfGameTree> games = new ArrayList<>();

  /** Creates an empty collection. */
  public SgfCollection() {
    // empty on purpose
  }

  /**
   * Creates a collection holding the given games.
   *
   * @param games the games, in order
   * @return the collection
   */
  public static SgfCollection of(SgfGameTree... games) {
    SgfCollection collection = new SgfCollection();
    for (SgfGameTree game : games) {
      collection.addGame(game);
    }
    return collection;
  }

  /**
   * Appends a game.
   *
   * @param game the game to add
   * @return this collection, for chaining
   */
  public SgfCollection addGame(SgfGameTree game) {
    games.add(Objects.requireNonNull(game, "game"));
    return this;
  }

  /**
   * Returns the games.
   *
   * @return an unmodifiable view of the games, in order
   */
  public List<SgfGameTree> games() {
    return Collections.unmodifiableList(games);
  }

  /**
   * Returns the number of games.
   *
   * @return the game count
   */
  public int size() {
    return games.size();
  }

  /**
   * Returns the first game.
   *
   * @return the first game
   * @throws NoSuchElementException if the collection is empty (a parsed collection never is)
   */
  public SgfGameTree firstGame() {
    if (games.isEmpty()) {
      throw new NoSuchElementException("The collection contains no games");
    }
    return games.get(0);
  }

  /**
   * Returns a game by index.
   *
   * @param index the zero-based index
   * @return the game
   */
  public SgfGameTree game(int index) {
    return games.get(index);
  }

  /**
   * Deep-copies the collection.
   *
   * @return an independent copy
   */
  public SgfCollection copy() {
    SgfCollection copy = new SgfCollection();
    for (SgfGameTree game : games) {
      copy.addGame(game.copy());
    }
    return copy;
  }

  /**
   * Compares the content of two collections: same number of games, each with the same content.
   *
   * @param other the other collection
   * @return {@code true} if both hold the same content
   */
  public boolean sameContentAs(SgfCollection other) {
    if (other == null || other.games.size() != games.size()) {
      return false;
    }
    for (int i = 0; i < games.size(); i++) {
      if (!games.get(i).sameContentAs(other.games.get(i))) {
        return false;
      }
    }
    return true;
  }

  @Override
  public Iterator<SgfGameTree> iterator() {
    return games().iterator();
  }

  @Override
  public String toString() {
    return "SgfCollection{games=" + games.size() + "}";
  }
}
