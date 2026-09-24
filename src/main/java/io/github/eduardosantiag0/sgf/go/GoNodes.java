package io.github.eduardosantiag0.sgf.go;

import io.github.eduardosantiag0.sgf.model.SgfNode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Helpers to read and write the point-based properties of a node: stone setup ({@code AB},
 * {@code AW}, {@code AE}), markup ({@code TR}, {@code SQ}, {@code CR}, {@code MA}, {@code SL}) and
 * labels ({@code LB}). They turn the raw values into {@link SgfCoordinate}s, expanding rectangles
 * such as {@code aa:cc}, so callers never parse coordinates by hand.
 */
public final class GoNodes {

  private GoNodes() {}

  /**
   * Reads a point-list property.
   *
   * @param node the node
   * @param identifier the property, for example {@code "AB"} or {@code "TR"}
   * @return every point it denotes, rectangles expanded; empty if the property is absent
   * @throws SgfSemanticException if a value is not a point or rectangle
   */
  public static List<SgfCoordinate> points(SgfNode node, String identifier) {
    List<SgfCoordinate> points = new ArrayList<>();
    for (String value : node.values(identifier)) {
      points.addAll(SgfCoordinate.parsePointList(value));
    }
    return points;
  }

  /**
   * Reads a point-list property and checks that every point is on the board.
   *
   * @param node the node
   * @param identifier the property
   * @param boardSize the size of the board
   * @return every point it denotes; empty if the property is absent
   * @throws SgfSemanticException if a value is malformed or a point is off the board
   */
  public static List<SgfCoordinate> points(SgfNode node, String identifier, int boardSize) {
    List<SgfCoordinate> points = points(node, identifier);
    for (SgfCoordinate p : points) {
      if (!p.isOnBoard(boardSize)) {
        throw new SgfSemanticException(
            "Point '" + p.toSgf() + "' is outside a " + boardSize + "x" + boardSize + " board");
      }
    }
    return points;
  }

  /**
   * Replaces a point-list property. Each point is written as its own value; an empty collection
   * removes the property.
   *
   * @param node the node
   * @param identifier the property
   * @param points the points
   */
  public static void setPoints(SgfNode node, String identifier, Collection<SgfCoordinate> points) {
    if (points.isEmpty()) {
      node.removeProperty(identifier);
      return;
    }
    List<String> values = new ArrayList<>(points.size());
    for (SgfCoordinate p : points) {
      values.add(p.toSgf());
    }
    node.setProperty(identifier, values);
  }

  /**
   * Reads the labels ({@code LB[pd:A][dd:B]}) of a node.
   *
   * @param node the node
   * @return the label text by point, in the order written; empty if there are none
   * @throws SgfSemanticException if a value is not of the form {@code point:text}
   */
  public static Map<SgfCoordinate, String> labels(SgfNode node) {
    Map<SgfCoordinate, String> labels = new LinkedHashMap<>();
    for (String value : node.values("LB")) {
      int colon = value.indexOf(':');
      if (colon < 0) {
        throw new SgfSemanticException("A label must look like 'pd:text' but was '" + value + "'");
      }
      labels.put(SgfCoordinate.parse(value.substring(0, colon)), value.substring(colon + 1));
    }
    return labels;
  }

  /**
   * Adds a label ({@code LB}) to a node.
   *
   * @param node the node
   * @param point where the label goes
   * @param text the label text
   */
  public static void addLabel(SgfNode node, SgfCoordinate point, String text) {
    node.addValue("LB", point.toSgf() + ":" + text);
  }
}
