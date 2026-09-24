package io.github.eduardosantiag0.sgf.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.eduardosantiag0.sgf.parser.SgfParser;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;

class SgfGameTreeTest {

  private final SgfParser parser = new SgfParser();

  private SgfGameTree game(String sgf) {
    return parser.parse(sgf).firstGame();
  }

  @Test
  void mainLineIncludesTheRootAndFollowsFirstChildren() {
    SgfGameTree game = game("(;GM[1];B[pd];W[dd](;B[qp];W[dp])(;B[pp]))");

    assertThat(game.mainLine()).hasSize(5);
    assertThat(game.mainLine().get(4).value("W")).contains("dp");
  }

  @Test
  void movesSkipNodesThatAreNotMoves() {
    SgfGameTree game = game("(;GM[1];C[intro];B[pd];C[note];W[dd];AB[aa];B[qp])");

    assertThat(game.moveCount()).isEqualTo(3);
    assertThat(game.moves()).extracting(n -> n.isMove()).containsOnly(true);
    assertThat(game.getMove(1).value("B")).contains("pd");
    assertThat(game.getMove(2).value("W")).contains("dd");
    assertThat(game.getMove(3).value("B")).contains("qp");
  }

  @Test
  void aCommentNodeIsNotCountedAsAMove() {
    SgfGameTree game = game("(;GM[1];C[comment])");

    assertThat(game.moveCount()).isZero();
    assertThat(game.move(1)).isEmpty();
  }

  @Test
  void passesCountAsMoves() {
    SgfGameTree game = game("(;B[];W[pd];B[])");

    assertThat(game.moveCount()).isEqualTo(3);
    assertThat(game.getMove(1).value("B")).contains("");
  }

  @Test
  void aRootThatIsItselfAMoveIsMoveNumberOne() {
    SgfGameTree game = game("(;B[pd];W[dd])");

    assertThat(game.getMove(1)).isSameAs(game.root());
  }

  @Test
  void moveNumbersFollowTheMainLineOnly() {
    SgfGameTree game = game("(;B[pd];W[dd](;B[qp];W[dp])(;B[pp];W[cc];B[dc]))");

    assertThat(game.moveCount()).isEqualTo(4);
    assertThat(game.getMove(3).value("B")).contains("qp");
  }

  @Test
  void aMissingMoveIsExplained() {
    SgfGameTree game = game("(;B[pd])");

    assertThat(game.move(0)).isEmpty();
    assertThat(game.move(2)).isEmpty();
    assertThat(game.move(-5)).isEmpty();
    assertThatThrownBy(() -> game.getMove(73))
        .isInstanceOf(NoSuchElementException.class)
        .hasMessageContaining("no move number 73")
        .hasMessageContaining("it has 1 moves");
  }

  @Test
  void nodesAreListedDepthFirstPreOrder() {
    SgfGameTree game = game("(;A[1](;B[1](;C[1])(;D[1]))(;E[1]))");

    assertThat(game.nodes())
        .extracting(n -> n.properties().iterator().next().identifier())
        .containsExactly("A", "B", "C", "D", "E");
  }

  @Test
  void findResolvesPaths() {
    SgfGameTree game = game("(;A[1](;B[1](;C[1])(;D[1]))(;E[1]))");

    assertThat(game.find(SgfNodePath.ROOT)).containsSame(game.root());
    assertThat(game.find(SgfNodePath.parse("/0.1")).orElseThrow().hasProperty("D")).isTrue();
    assertThat(game.find(SgfNodePath.parse("/1")).orElseThrow().hasProperty("E")).isTrue();
    assertThat(game.find(SgfNodePath.parse("/5"))).isEmpty();
  }

  @Test
  void aRootMustNotHaveAParent() {
    SgfNode parent = new SgfNode();
    SgfNode child = parent.addChild(new SgfNode());

    assertThatThrownBy(() -> new SgfGameTree(child)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void copiesAreIndependent() {
    SgfGameTree original = game("(;B[pd];W[dd])");

    SgfGameTree copy = original.copy();
    copy.getMove(1).addComment("changed");

    assertThat(original.getMove(1).comment()).isEmpty();
    assertThat(copy.sameContentAs(original)).isFalse();
  }

  @Test
  void collectionAccessors() {
    SgfCollection collection = parser.parse("(;B[pd])(;B[qq])");

    assertThat(collection).hasSize(2);
    assertThat(collection.firstGame()).isSameAs(collection.game(0));
    assertThat(collection.copy().sameContentAs(collection)).isTrue();
    assertThatThrownBy(() -> new SgfCollection().firstGame()).isInstanceOf(NoSuchElementException.class);
    assertThatThrownBy(() -> collection.games().add(collection.game(0)))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void collectionsWithDifferentGameCountsDiffer() {
    assertThat(parser.parse("(;B[pd])").sameContentAs(parser.parse("(;B[pd])(;B[pd])"))).isFalse();
  }
}
