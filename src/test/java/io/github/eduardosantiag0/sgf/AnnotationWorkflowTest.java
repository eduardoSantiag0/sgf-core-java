package io.github.eduardosantiag0.sgf;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.eduardosantiag0.sgf.go.GoGame;
import io.github.eduardosantiag0.sgf.go.GoPosition;
import io.github.eduardosantiag0.sgf.go.SgfMove;
import io.github.eduardosantiag0.sgf.model.SgfCollection;
import io.github.eduardosantiag0.sgf.model.SgfGameTree;
import io.github.eduardosantiag0.sgf.model.SgfNode;
import io.github.eduardosantiag0.sgf.model.SgfNodePath;
import io.github.eduardosantiag0.sgf.parser.SgfParser;
import io.github.eduardosantiag0.sgf.serializer.SgfSerializer;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The central use case of the library, end to end: load a game, find a move, annotate it, add an
 * alternative line, write the game back, and be sure that nothing of the original was lost.
 *
 * <p>This is what an SGF editor, a study tool, a teaching server or a bot does all day.
 */
class AnnotationWorkflowTest {

  private static final String ORIGINAL =
      """
      (;GM[1]FF[4]SZ[19]
      PB[Alice]
      PW[Bob]
      DT[2024-05-01]
      XX[custom-property]
      ;B[pd]
      ;W[dd]
      ;B[qp]
      ;W[dp])
      """;

  private static final String COMMENT = "A sharper option exists here.\nSee the variation.";

  /** A line that could have been played instead of move 3 (a Black move). */
  private static final List<SgfMove> ALTERNATIVE =
      List.of(
          SgfMove.black("pq"),
          SgfMove.white("qc"),
          SgfMove.black("od"),
          SgfMove.white("fc"),
          SgfMove.black("cn"));

  private final SgfParser parser = new SgfParser();
  private final SgfSerializer serializer = SgfSerializer.pretty();

  private String annotated() {
    SgfCollection collection = parser.parse(ORIGINAL);
    SgfNode move = collection.firstGame().getMove(3);
    assertThat(move.value("B")).contains("qp");

    move.addComment(COMMENT);
    move.addAlternative(SgfMove.toNodes(ALTERNATIVE)).addComment("Alternative line");

    return serializer.serialize(collection);
  }

  @Test
  void theResultIsValidSgfThatParsesAgain() {
    assertThat(parser.parse(annotated()).size()).isEqualTo(1);
  }

  @Test
  void theOriginalGameIsStillThere() {
    SgfGameTree game = parser.parse(annotated()).firstGame();

    assertThat(game.moveCount()).isEqualTo(4); // the main line is untouched
    assertThat(GoGame.of(game).mainLineMoves())
        .containsExactly(
            SgfMove.black("pd"), SgfMove.white("dd"), SgfMove.black("qp"), SgfMove.white("dp"));
  }

  @Test
  void unknownAndGameInformationPropertiesSurvive() {
    SgfNode root = parser.parse(annotated()).firstGame().root();

    assertThat(root.value("XX")).contains("custom-property");
    assertThat(root.value("PB")).contains("Alice");
    assertThat(root.value("PW")).contains("Bob");
    assertThat(root.value("DT")).contains("2024-05-01");
  }

  @Test
  void theCommentIsOnTheAnnotatedMove() {
    SgfGameTree game = parser.parse(annotated()).firstGame();

    assertThat(game.getMove(3).comment()).contains(COMMENT);
    assertThat(game.getMove(1).comment()).isEmpty();
  }

  @Test
  void theAddedVariationExistsAndItsFiveMovesAreInOrder() {
    SgfGameTree game = parser.parse(annotated()).firstGame();
    SgfNode beforeTheAnnotatedMove = game.getMove(2);

    assertThat(beforeTheAnnotatedMove.children()).hasSize(2);
    SgfNode alternative = beforeTheAnnotatedMove.children().get(1);
    assertThat(alternative.comment()).contains("Alternative line");

    List<SgfNode> line = alternative.mainLine();
    assertThat(line).hasSize(5);
    assertThat(line).extracting(n -> SgfMove.from(n, 19).orElseThrow()).containsExactlyElementsOf(ALTERNATIVE);
  }

  @Test
  void theAlternativeBranchesFromTheSamePositionAsTheMoveItReplaces() {
    SgfGameTree game = parser.parse(annotated()).firstGame();
    GoGame go = GoGame.of(game);
    SgfNode alternativeStart = game.getMove(2).children().get(1);

    assertThat(go.positionBefore(alternativeStart)).isEqualTo(go.positionBefore(3));
    assertThat(go.positionAfter(alternativeStart.mainLine().get(4)).moves())
        .containsExactly(
            SgfMove.black("pd"),
            SgfMove.white("dd"),
            SgfMove.black("pq"),
            SgfMove.white("qc"),
            SgfMove.black("od"),
            SgfMove.white("fc"),
            SgfMove.black("cn"));
  }

  @Test
  void theResultHasTheExpectedShape() {
    assertThat(annotated())
        .isEqualTo(
            """
            (;GM[1]FF[4]SZ[19]PB[Alice]PW[Bob]DT[2024-05-01]XX[custom-property]
            ;B[pd]
            ;W[dd]
            (;B[qp]C[A sharper option exists here.
            See the variation.]
            ;W[dp])
            (;B[pq]C[Alternative line]
            ;W[qc]
            ;B[od]
            ;W[fc]
            ;B[cn]))""");
  }

  @Test
  void parseModifySerializeParseKeepsEverythingSemantically() {
    SgfCollection edited = parser.parse(ORIGINAL);
    SgfNode move = edited.firstGame().getMove(3);
    move.addComment("first note");
    move.addAlternative(SgfMove.toNodes(ALTERNATIVE)).addComment("second note");

    SgfCollection reparsed = parser.parse(serializer.serialize(edited));

    assertThat(reparsed.sameContentAs(edited)).isTrue();
  }

  @Test
  void theOriginalCanBeKeptUntouchedNextToAnEditedCopy() {
    SgfCollection original = parser.parse(ORIGINAL);
    SgfCollection edited = original.copy();

    edited.firstGame().getMove(3).addAlternative(SgfMove.toNodes(ALTERNATIVE));

    assertThat(original.firstGame().nodes()).hasSize(5);
    assertThat(edited.firstGame().nodes()).hasSize(10);
    assertThat(serializer.serialize(original)).isEqualTo(serializer.serialize(parser.parse(ORIGINAL)));
  }

  // ------------------------------------------------------------------ reading a game

  @Test
  void thePositionBeforeAMoveCanBeDescribedAndShownInHumanCoordinates() {
    GoGame go = GoGame.of(parser.parse(ORIGINAL).firstGame());

    GoPosition position = go.positionBefore(3);

    assertThat(position.boardSize()).isEqualTo(19);
    assertThat(position.moves()).extracting(m -> m.toHuman(19)).containsExactly("Q16", "D16");
    assertThat(SgfMove.from(go.tree().getMove(3), 19).orElseThrow().toHuman(19)).isEqualTo("R4");
  }

  @Test
  void aNodeKeepsTheSameAddressAcrossEditingAndARoundTrip() {
    SgfGameTree game = parser.parse(ORIGINAL).firstGame();
    SgfNode move = game.getMove(3);
    SgfNodePath address = move.path();

    move.addAlternative(SgfMove.toNodes(ALTERNATIVE));
    SgfGameTree afterReparse = parser.parse(serializer.serialize(new SgfCollection().addGame(game))).firstGame();

    assertThat(address.toString()).isEqualTo("/0.0.0");
    assertThat(afterReparse.find(address).orElseThrow().value("B")).contains("qp");
    assertThat(afterReparse.find(SgfNodePath.parse(address.toString())).orElseThrow().value("B")).contains("qp");
  }

  @Test
  void theGamesOfACollectionAreEditedIndependently() {
    SgfCollection collection = parser.parse(ORIGINAL + ORIGINAL);

    collection.game(1).getMove(3).addComment("only in the second game");

    assertThat(collection.game(0).getMove(3).comment()).isEmpty();
    assertThat(collection.game(1).getMove(3).comment()).contains("only in the second game");
  }
}
