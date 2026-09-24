package io.github.eduardosantiag0.sgf.go;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.eduardosantiag0.sgf.model.SgfNode;
import java.util.List;
import org.junit.jupiter.api.Test;

class GoNodesTest {

  @Test
  void readsPointsAndExpandsRectangles() {
    SgfNode node = new SgfNode().setProperty("TR", "aa", "cc:dd");

    assertThat(GoNodes.points(node, "TR"))
        .extracting(SgfCoordinate::toSgf)
        .containsExactly("aa", "cc", "dc", "cd", "dd");
    assertThat(GoNodes.points(node, "SQ")).isEmpty();
  }

  @Test
  void boardCheckedPointsRejectStonesOffTheBoard() {
    SgfNode node = new SgfNode().setProperty("AB", "aa", "ss");

    assertThat(GoNodes.points(node, "AB", 19)).hasSize(2);
    assertThatThrownBy(() -> GoNodes.points(node, "AB", 9))
        .isInstanceOf(SgfSemanticException.class)
        .hasMessageContaining("ss");
  }

  @Test
  void setPointsWritesOneValuePerPointAndEmptyRemoves() {
    SgfNode node = new SgfNode();

    GoNodes.setPoints(node, "CR", List.of(SgfCoordinate.parse("aa"), SgfCoordinate.parse("bb")));
    assertThat(node.values("CR")).containsExactly("aa", "bb");

    GoNodes.setPoints(node, "CR", List.of());
    assertThat(node.hasProperty("CR")).isFalse();
  }

  @Test
  void labels() {
    SgfNode node = new SgfNode();
    GoNodes.addLabel(node, SgfCoordinate.parse("pd"), "A");
    GoNodes.addLabel(node, SgfCoordinate.parse("dd"), "best move: D12");

    assertThat(node.values("LB")).containsExactly("pd:A", "dd:best move: D12");
    assertThat(GoNodes.labels(node))
        .containsExactly(
            org.assertj.core.api.Assertions.entry(SgfCoordinate.parse("pd"), "A"),
            org.assertj.core.api.Assertions.entry(SgfCoordinate.parse("dd"), "best move: D12"));
  }

  @Test
  void aLabelTextMayContainColons() {
    SgfNode node = new SgfNode().setProperty("LB", "aa:x:y");

    assertThat(GoNodes.labels(node)).containsValue("x:y");
  }

  @Test
  void malformedLabelsAreRejected() {
    assertThatThrownBy(() -> GoNodes.labels(new SgfNode().setProperty("LB", "aa")))
        .isInstanceOf(SgfSemanticException.class);
    assertThatThrownBy(() -> GoNodes.labels(new SgfNode().setProperty("LB", "a:x")))
        .isInstanceOf(SgfSemanticException.class);
  }
}
