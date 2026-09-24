package io.github.eduardosantiag0.sgf.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class SgfNodeTest {

  private static SgfNode move(String color, String coord) {
    return new SgfNode().setProperty(color, coord);
  }

  // ------------------------------------------------------------------ properties

  @Test
  void setAndReadProperties() {
    SgfNode node = new SgfNode().setProperty("PB", "Alice").setProperty("AB", "aa", "bb");

    assertThat(node.hasProperty("PB")).isTrue();
    assertThat(node.value("PB")).contains("Alice");
    assertThat(node.values("AB")).containsExactly("aa", "bb");
    assertThat(node.property("AB")).contains(SgfProperty.of("AB", "aa", "bb"));
    assertThat(node.value("XX")).isEmpty();
    assertThat(node.values("XX")).isEmpty();
    assertThat(node.property("XX")).isEmpty();
  }

  @Test
  void replacingAPropertyKeepsItsPosition() {
    SgfNode node = new SgfNode().setProperty("A", "1").setProperty("B", "2").setProperty("A", "3");

    assertThat(node.properties()).extracting(SgfProperty::identifier).containsExactly("A", "B");
    assertThat(node.value("A")).contains("3");
  }

  @Test
  void addValueAppendsOrCreates() {
    SgfNode node = new SgfNode().addValue("AB", "aa").addValue("AB", "bb");

    assertThat(node.values("AB")).containsExactly("aa", "bb");
  }

  @Test
  void removeProperty() {
    SgfNode node = new SgfNode().setProperty("C", "x");

    assertThat(node.removeProperty("C")).isTrue();
    assertThat(node.removeProperty("C")).isFalse();
    assertThat(node.hasProperty("C")).isFalse();
  }

  @Test
  void identifiersMustBeUppercaseLetters() {
    SgfNode node = new SgfNode();

    for (String bad : new String[] {"", "b", "Bb", "B1", "B ", "É"}) {
      assertThatThrownBy(() -> node.setProperty(bad, "x"))
          .as("identifier '%s'", bad)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Invalid property identifier");
    }
  }

  @Test
  void aPropertyNeedsAtLeastOneValue() {
    assertThatThrownBy(() -> new SgfNode().setProperty("B"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("at least one value");
  }

  @Test
  void valuesAreDefensivelyCopied() {
    List<String> mutable = new java.util.ArrayList<>(List.of("aa"));
    SgfNode node = new SgfNode().setProperty("AB", mutable);
    mutable.add("bb");

    assertThat(node.values("AB")).containsExactly("aa");
    assertThatThrownBy(() -> node.values("AB").add("cc")).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void propertiesViewCannotBeModified() {
    SgfNode node = new SgfNode().setProperty("C", "x");

    assertThatThrownBy(() -> node.properties().clear()).isInstanceOf(UnsupportedOperationException.class);
  }

  // ------------------------------------------------------------------ moves and comments

  @Test
  void isMoveNeedsBOrW() {
    assertThat(move("B", "pd").isMove()).isTrue();
    assertThat(move("W", "").isMove()).isTrue();
    assertThat(new SgfNode().setProperty("C", "just a comment").isMove()).isFalse();
    assertThat(new SgfNode().setProperty("AB", "aa").isMove()).isFalse();
    assertThat(new SgfNode().isMove()).isFalse();
  }

  @Test
  void commentAccessors() {
    SgfNode node = new SgfNode();

    assertThat(node.comment()).isEmpty();
    node.setComment("first");
    assertThat(node.comment()).contains("first");
  }

  @Test
  void addCommentOnANodeWithoutOneSetsIt() {
    assertThat(new SgfNode().addComment("First remark").comment()).contains("First remark");
  }

  @Test
  void addCommentAppendsAfterABlankLineInsteadOfOverwriting() {
    SgfNode node = new SgfNode().setComment("original annotation").addComment("second remark");

    assertThat(node.comment()).contains("original annotation\n\nsecond remark");
  }

  @Test
  void addCommentAfterAnEmptyCommentDoesNotAddBlankLines() {
    assertThat(new SgfNode().setComment("").addComment("x").comment()).contains("x");
  }

  // ------------------------------------------------------------------ tree structure

  @Test
  void addChildLinksBothWays() {
    SgfNode parent = new SgfNode();
    SgfNode child = new SgfNode();

    assertThat(parent.addChild(child)).isSameAs(child);

    assertThat(child.parent()).containsSame(parent);
    assertThat(parent.children()).containsExactly(child);
    assertThat(parent.next()).containsSame(child);
    assertThat(child.next()).isEmpty();
  }

  @Test
  void theFirstChildIsTheMainLineAndOthersAreVariations() {
    SgfNode parent = new SgfNode();
    SgfNode a = parent.addChild(move("B", "aa"));
    SgfNode b = parent.addChild(move("B", "bb"));

    assertThat(parent.next()).containsSame(a);
    assertThat(parent.children()).containsExactly(a, b);
  }

  @Test
  void aNodeWithAParentCannotBeAddedElsewhere() {
    SgfNode p1 = new SgfNode();
    SgfNode p2 = new SgfNode();
    SgfNode child = p1.addChild(new SgfNode());

    assertThatThrownBy(() -> p2.addChild(child))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("already has a parent");
  }

  @Test
  void cyclesAreRejected() {
    SgfNode root = new SgfNode();
    SgfNode child = root.addChild(new SgfNode());
    SgfNode grandchild = child.addChild(new SgfNode());

    assertThatThrownBy(() -> grandchild.addChild(root))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("cycle");
    assertThatThrownBy(() -> root.addChild(root)).isInstanceOf(IllegalArgumentException.class);
    assertThat(root.parent()).isEmpty();
  }

  @Test
  void aWholeSubtreeCanBeAttached() {
    SgfNode subtreeRoot = new SgfNode().setProperty("A", "1");
    subtreeRoot.addChild(new SgfNode().setProperty("B", "1"));
    SgfNode root = new SgfNode();

    root.addChild(subtreeRoot);

    assertThat(root.mainLine()).hasSize(3); // root, the attached node and its child
    assertThat(subtreeRoot.next().orElseThrow().root()).isSameAs(root);
  }

  @Test
  void removeFromParentPromotesTheNextSibling() {
    SgfNode parent = new SgfNode();
    SgfNode first = parent.addChild(move("B", "aa"));
    SgfNode second = parent.addChild(move("B", "bb"));

    assertThat(first.removeFromParent()).isTrue();

    assertThat(first.parent()).isEmpty();
    assertThat(parent.next()).containsSame(second);
    assertThat(first.removeFromParent()).isFalse();
  }

  @Test
  void aDetachedNodeCanBeReattached() {
    SgfNode p1 = new SgfNode();
    SgfNode p2 = new SgfNode();
    SgfNode child = p1.addChild(new SgfNode());
    child.removeFromParent();

    p2.addChild(child);

    assertThat(child.parent()).containsSame(p2);
  }

  @Test
  void identityNotContentDecidesEquality() {
    SgfNode a = move("B", "pd");
    SgfNode b = move("B", "pd");

    assertThat(a).isNotEqualTo(b);
    assertThat(a.sameContentAs(b)).isTrue();
  }

  // ------------------------------------------------------------------ variations

  @Test
  void addVariationAddsABranchAfterTheNode() {
    SgfNode root = new SgfNode();
    SgfNode continuation = root.addChild(move("B", "pd"));
    SgfNode w1 = move("W", "dd");
    SgfNode b1 = move("B", "qp");

    SgfNode first = root.addVariation(List.of(w1, b1));

    assertThat(first).isSameAs(w1);
    assertThat(root.children()).containsExactly(continuation, w1);
    assertThat(w1.next()).containsSame(b1);
    assertThat(root.next()).containsSame(continuation); // main line unchanged
  }

  @Test
  void addVariationOnALeafBecomesTheContinuation() {
    SgfNode leaf = new SgfNode();

    leaf.addVariation(move("W", "dd"));

    assertThat(leaf.next()).isPresent();
  }

  @Test
  void addAlternativeAddsASiblingSoItReplacesThisMove() {
    SgfNode root = new SgfNode();
    SgfNode played = root.addChild(move("B", "qp"));
    SgfNode better1 = move("B", "pd");
    SgfNode better2 = move("W", "dd");

    SgfNode first = played.addAlternative(better1, better2);

    assertThat(first).isSameAs(better1);
    assertThat(root.children()).containsExactly(played, better1);
    assertThat(better1.next()).containsSame(better2);
    assertThat(played.hasChildren()).isFalse();
  }

  @Test
  void addAlternativeOnARootIsAnError() {
    assertThatThrownBy(() -> new SgfNode().addAlternative(move("B", "aa")))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void variationNodesMustBeFresh() {
    SgfNode root = new SgfNode();
    SgfNode attached = root.addChild(new SgfNode());
    SgfNode other = new SgfNode();
    SgfNode withChild = new SgfNode();
    withChild.addChild(new SgfNode());

    assertThatThrownBy(() -> other.addVariation(attached)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> other.addVariation(withChild)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> other.addVariation(List.of())).isInstanceOf(IllegalArgumentException.class);
    SgfNode dup = new SgfNode();
    assertThatThrownBy(() -> other.addVariation(dup, dup)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> other.addVariation(other)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aRejectedVariationLeavesTheTreeUntouched() {
    SgfNode root = new SgfNode();
    SgfNode fine = new SgfNode();
    SgfNode attached = new SgfNode();
    new SgfNode().addChild(attached);

    assertThatThrownBy(() -> root.addVariation(fine, attached)).isInstanceOf(IllegalArgumentException.class);

    assertThat(root.hasChildren()).isFalse();
    assertThat(fine.parent()).isEmpty();
  }

  // ------------------------------------------------------------------ paths

  @Test
  void pathsAddressNodesAndAreFindable() {
    SgfNode root = new SgfNode();
    SgfNode a = root.addChild(move("B", "aa"));
    SgfNode b = a.addChild(move("W", "bb"));
    SgfNode c = a.addChild(move("W", "cc"));
    SgfNode d = c.addChild(move("B", "dd"));

    assertThat(root.path()).isEqualTo(SgfNodePath.ROOT);
    assertThat(root.path().toString()).isEqualTo("/");
    assertThat(a.path().toString()).isEqualTo("/0");
    assertThat(b.path().toString()).isEqualTo("/0.0");
    assertThat(c.path().toString()).isEqualTo("/0.1");
    assertThat(d.path().toString()).isEqualTo("/0.1.0");
    assertThat(root.find(d.path())).containsSame(d);
    assertThat(a.find(SgfNodePath.of(1, 0))).containsSame(d);
  }

  @Test
  void aPathThatLeavesTheTreeIsNotFound() {
    SgfNode root = new SgfNode();
    root.addChild(new SgfNode());

    assertThat(root.find(SgfNodePath.of(0, 0))).isEmpty();
    assertThat(root.find(SgfNodePath.of(1))).isEmpty();
  }

  @Test
  void pathTextRoundTrips() {
    for (String text : new String[] {"/", "/0", "/0.0.1", "/12.3.400"}) {
      assertThat(SgfNodePath.parse(text).toString()).isEqualTo(text);
    }
    assertThat(SgfNodePath.parse("/0.1")).isEqualTo(SgfNodePath.of(0, 1)).hasSameHashCodeAs(SgfNodePath.of(0, 1));
    assertThat(SgfNodePath.ROOT.child(2).child(0)).isEqualTo(SgfNodePath.of(2, 0));
  }

  @Test
  void malformedPathsAreRejected() {
    for (String text : new String[] {"", "0", "/a", "/0..1", "/0.", "/-1"}) {
      assertThatThrownBy(() -> SgfNodePath.parse(text)).as(text).isInstanceOf(IllegalArgumentException.class);
    }
    assertThatThrownBy(() -> SgfNodePath.of(-1)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void appendingVariationsDoesNotChangeExistingPaths() {
    SgfNode root = new SgfNode();
    SgfNode a = root.addChild(move("B", "aa"));
    SgfNode b = a.addChild(move("W", "bb"));
    String before = b.path().toString();

    a.addAlternative(move("B", "zz"));
    b.addVariation(move("B", "cc"));

    assertThat(b.path().toString()).isEqualTo(before);
  }

  // ------------------------------------------------------------------ copy and comparison

  @Test
  void copyIsDeepAndIndependent() {
    SgfNode root = new SgfNode().setProperty("GM", "1");
    SgfNode a = root.addChild(move("B", "aa"));
    a.addChild(move("W", "bb"));
    a.addChild(move("W", "cc"));

    SgfNode copy = root.copy();

    assertThat(copy).isNotSameAs(root);
    assertThat(copy.sameContentAs(root)).isTrue();
    assertThat(copy.parent()).isEmpty();
    copy.next().orElseThrow().setComment("only in the copy");
    assertThat(a.comment()).isEmpty();
    assertThat(copy.sameContentAs(root)).isFalse();
  }

  @Test
  void copyOfASubtreeHasNoParent() {
    SgfNode root = new SgfNode();
    SgfNode a = root.addChild(move("B", "aa"));

    assertThat(a.copy().parent()).isEmpty();
  }

  @Test
  void contentComparisonIgnoresPropertyOrder() {
    SgfNode a = new SgfNode().setProperty("A", "1").setProperty("B", "2");
    SgfNode b = new SgfNode().setProperty("B", "2").setProperty("A", "1");

    assertThat(a.sameContentAs(b)).isTrue();
  }

  @Test
  void contentComparisonSeesValuesAndShape() {
    SgfNode a = new SgfNode().setProperty("A", "1");
    SgfNode different = new SgfNode().setProperty("A", "2");
    SgfNode withChild = new SgfNode().setProperty("A", "1");
    withChild.addChild(new SgfNode());

    assertThat(a.sameContentAs(different)).isFalse();
    assertThat(a.sameContentAs(withChild)).isFalse();
    assertThat(a.sameContentAs(null)).isFalse();
  }

  @Test
  void toStringIsShortAndShowsProperties() {
    assertThat(move("B", "pd").setProperty("C", "hi").toString()).isEqualTo("SgfNode{B[pd] C[hi]}");
  }
}
