package io.github.eduardosantiag0.sgf.go;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.eduardosantiag0.sgf.model.SgfNode;
import java.util.List;
import org.junit.jupiter.api.Test;

class SgfMoveTest {

  @Test
  void factories() {
    SgfMove b = SgfMove.black("pd");
    SgfMove w = SgfMove.white(SgfCoordinate.parse("dd"));

    assertThat(b.color()).isEqualTo(StoneColor.BLACK);
    assertThat(b.coordinate()).contains(SgfCoordinate.parse("pd"));
    assertThat(b.isPass()).isFalse();
    assertThat(w.color()).isEqualTo(StoneColor.WHITE);
    assertThat(SgfMove.black(SgfCoordinate.parse("pd"))).isEqualTo(b);
    assertThat(SgfMove.of(StoneColor.WHITE, SgfCoordinate.parse("dd"))).isEqualTo(w).hasSameHashCodeAs(w);
  }

  @Test
  void aPass() {
    SgfMove pass = SgfMove.pass(StoneColor.BLACK);

    assertThat(pass.isPass()).isTrue();
    assertThat(pass.coordinate()).isEmpty();
    assertThat(pass.toSgfValue()).isEmpty();
    assertThat(pass.toHuman(19)).isEqualTo("pass");
    assertThat(pass).isNotEqualTo(SgfMove.pass(StoneColor.WHITE));
    assertThat(pass.toString()).isEqualTo("B[]");
  }

  @Test
  void toNodeWritesTheRightProperty() {
    SgfNode black = SgfMove.black("pd").toNode();
    SgfNode white = SgfMove.white("dd").toNode();
    SgfNode pass = SgfMove.pass(StoneColor.WHITE).toNode();

    assertThat(black.value("B")).contains("pd");
    assertThat(white.value("W")).contains("dd");
    assertThat(pass.value("W")).contains("");
    assertThat(black.isMove()).isTrue();
    assertThat(SgfMove.toNodes(List.of(SgfMove.black("pd"), SgfMove.white("dd")))).hasSize(2);
  }

  @Test
  void readsMovesFromNodes() {
    assertThat(SgfMove.from(new SgfNode().setProperty("B", "pd"), 19)).contains(SgfMove.black("pd"));
    assertThat(SgfMove.from(new SgfNode().setProperty("W", "dd"), 19)).contains(SgfMove.white("dd"));
    assertThat(SgfMove.from(new SgfNode().setProperty("C", "no move"), 19)).isEmpty();
  }

  @Test
  void emptyValueIsAPassOnAnyBoard() {
    assertThat(SgfMove.from(new SgfNode().setProperty("B", ""), 19)).contains(SgfMove.pass(StoneColor.BLACK));
    assertThat(SgfMove.from(new SgfNode().setProperty("W", ""), 9)).contains(SgfMove.pass(StoneColor.WHITE));
  }

  @Test
  void legacyTtPassOnlyOnBoardsUpTo19() {
    assertThat(SgfMove.from(new SgfNode().setProperty("B", "tt"), 19)).contains(SgfMove.pass(StoneColor.BLACK));
    assertThat(SgfMove.from(new SgfNode().setProperty("B", "tt"), 9)).contains(SgfMove.pass(StoneColor.BLACK));
    // On a 21x21 board 'tt' is a real point.
    assertThat(SgfMove.from(new SgfNode().setProperty("B", "tt"), 21)).contains(SgfMove.black("tt"));
  }

  @Test
  void movesOutsideTheBoardAreRejected() {
    assertThatThrownBy(() -> SgfMove.from(new SgfNode().setProperty("B", "ta"), 19))
        .isInstanceOf(SgfSemanticException.class)
        .hasMessageContaining("outside a 19x19 board");
    assertThatThrownBy(() -> SgfMove.from(new SgfNode().setProperty("W", "jj"), 9))
        .isInstanceOf(SgfSemanticException.class);
  }

  @Test
  void unreadableMovesAreRejected() {
    assertThatThrownBy(() -> SgfMove.from(new SgfNode().setProperty("B", "p"), 19))
        .isInstanceOf(SgfSemanticException.class);
    assertThatThrownBy(() -> SgfMove.from(new SgfNode().setProperty("B", "p4"), 19))
        .isInstanceOf(SgfSemanticException.class);
  }

  @Test
  void aNodeWithBothColoursIsRejected() {
    SgfNode node = new SgfNode().setProperty("B", "pd").setProperty("W", "dd");

    assertThatThrownBy(() -> SgfMove.from(node, 19))
        .isInstanceOf(SgfSemanticException.class)
        .hasMessageContaining("both B and W");
  }

  @Test
  void humanForm() {
    assertThat(SgfMove.black("pd").toHuman(19)).isEqualTo("Q16");
    assertThat(SgfMove.white("dp").toHuman(19)).isEqualTo("D4");
  }

  @Test
  void colourHelpers() {
    assertThat(StoneColor.BLACK.opposite()).isEqualTo(StoneColor.WHITE);
    assertThat(StoneColor.WHITE.opposite()).isEqualTo(StoneColor.BLACK);
    assertThat(StoneColor.BLACK.sgfIdentifier()).isEqualTo("B");
    assertThat(StoneColor.WHITE.sgfIdentifier()).isEqualTo("W");
  }
}
