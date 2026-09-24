package io.github.eduardosantiag0.sgf.go;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.eduardosantiag0.sgf.model.SgfGameTree;
import io.github.eduardosantiag0.sgf.model.SgfNode;
import io.github.eduardosantiag0.sgf.parser.SgfParser;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;

class GoGameTest {

  private final SgfParser parser = new SgfParser();

  private GoGame go(String sgf) {
    return GoGame.of(parser.parse(sgf).firstGame());
  }

  // ------------------------------------------------------------------ information

  @Test
  void gameInformation() {
    GoGame game =
        go("(;GM[1]FF[4]SZ[19]KM[6.5]RU[Japanese]PB[Alice]PW[Bob]BR[3d]WR[2k]RE[B+R]DT[2026-09-23]EV[Online Match])");

    assertThat(game.isGo()).isTrue();
    assertThat(game.boardSize()).isEqualTo(19);
    assertThat(game.komi()).contains(6.5);
    assertThat(game.rules()).contains("Japanese");
    assertThat(game.blackName()).contains("Alice");
    assertThat(game.whiteName()).contains("Bob");
    assertThat(game.blackRank()).contains("3d");
    assertThat(game.whiteRank()).contains("2k");
    assertThat(game.result()).contains("B+R");
    assertThat(game.date()).contains("2026-09-23");
    assertThat(game.event()).contains("Online Match");
  }

  @Test
  void absentInformationIsEmptyNotAnError() {
    GoGame game = go("(;B[pd])");

    assertThat(game.boardSize()).isEqualTo(19);
    assertThat(game.komi()).isEmpty();
    assertThat(game.blackName()).isEmpty();
    assertThat(game.isGo()).isTrue();
  }

  @Test
  void boardSizes() {
    assertThat(go("(;SZ[9])").boardSize()).isEqualTo(9);
    assertThat(go("(;SZ[13:13])").boardSize()).isEqualTo(13);
    assertThat(go("(;SZ[ 19 ])").boardSize()).isEqualTo(19);
    assertThat(go("(;SZ[52])").boardSize()).isEqualTo(52);
  }

  @Test
  void unusableBoardSizes() {
    for (String bad : new String[] {"abc", "0", "53", "-3", "19:9", "19:", ":19", ""}) {
      GoGame game = go("(;SZ[" + bad + "])");
      assertThatThrownBy(game::boardSize).as("SZ[%s]", bad).isInstanceOf(SgfSemanticException.class);
    }
  }

  @Test
  void unreadableKomi() {
    assertThatThrownBy(() -> go("(;KM[six and a half])").komi())
        .isInstanceOf(SgfSemanticException.class)
        .hasMessageContaining("komi");
  }

  @Test
  void otherGamesAreNotGo() {
    assertThat(go("(;GM[2])").isGo()).isFalse();
    assertThat(go("(;GM[1])").isGo()).isTrue();
  }

  @Test
  void theViewFollowsChangesToTheTree() {
    SgfGameTree tree = parser.parse("(;GM[1]SZ[9])").firstGame();
    GoGame game = GoGame.of(tree);

    tree.root().setProperty("SZ", "13");

    assertThat(game.boardSize()).isEqualTo(13);
  }

  // ------------------------------------------------------------------ moves

  @Test
  void mainLineMoves() {
    GoGame game = go("(;GM[1];C[x];B[pd];W[];B[tt];W[dd])");

    assertThat(game.mainLineMoves())
        .containsExactly(
            SgfMove.black("pd"),
            SgfMove.pass(StoneColor.WHITE),
            SgfMove.pass(StoneColor.BLACK),
            SgfMove.white("dd"));
  }

  // ------------------------------------------------------------------ positions

  private static final String GAME = "(;GM[1]FF[4]SZ[19]PB[Alice]PW[Bob];B[pd];W[dd];B[qp];W[dp])";

  @Test
  void positionBeforeAMove() {
    GoGame game = go(GAME);

    GoPosition before3 = game.positionBefore(3);

    assertThat(before3.boardSize()).isEqualTo(19);
    assertThat(before3.moves()).containsExactly(SgfMove.black("pd"), SgfMove.white("dd"));
    assertThat(before3.setupStones()).isEmpty();
    assertThat(before3.moveCount()).isEqualTo(2);
    assertThat(before3.hasMidGameSetup()).isFalse();
    assertThat(before3.canonical()).isEqualTo("size=19;setup=;moves=B:pd,W:dd");
  }

  @Test
  void positionBeforeTheFirstMoveIsEmpty() {
    GoPosition before1 = go(GAME).positionBefore(1);

    assertThat(before1.moves()).isEmpty();
    assertThat(before1.canonical()).isEqualTo("size=19;setup=;moves=");
  }

  @Test
  void positionBeforeALaterMoveIncludesEverythingBeforeIt() {
    assertThat(go(GAME).positionBefore(4).moves()).hasSize(3);
  }

  @Test
  void aMoveThatDoesNotExistIsReported() {
    assertThatThrownBy(() -> go(GAME).positionBefore(5)).isInstanceOf(NoSuchElementException.class);
    assertThatThrownBy(() -> go(GAME).positionBefore(0)).isInstanceOf(NoSuchElementException.class);
  }

  @Test
  void nonMoveNodesDoNotShiftMoveNumbers() {
    GoGame game = go("(;GM[1];C[intro];B[pd];C[note];W[dd];B[qp])");

    assertThat(game.positionBefore(3).moves()).containsExactly(SgfMove.black("pd"), SgfMove.white("dd"));
  }

  @Test
  void passesAreMovesInPositions() {
    GoGame game = go("(;GM[1];B[pd];W[];B[dd])");

    assertThat(game.positionBefore(3).moves()).containsExactly(SgfMove.black("pd"), SgfMove.pass(StoneColor.WHITE));
    assertThat(game.positionBefore(3).canonical()).isEqualTo("size=19;setup=;moves=B:pd,W:pass");
  }

  @Test
  void handicapStonesAreInitialStones() {
    GoGame game = go("(;GM[1]SZ[19]HA[2]AB[dd][pp];W[dp];B[pd];W[dd])");

    GoPosition before3 = game.positionBefore(3);

    assertThat(before3.setupStones()).containsExactly(SgfMove.black("dd"), SgfMove.black("pp"));
    assertThat(before3.moves()).containsExactly(SgfMove.white("dp"), SgfMove.black("pd"));
  }

  @Test
  void setupRectanglesAreExpandedAndAeRemovesStones() {
    GoGame game = go("(;GM[1]SZ[9]AB[aa:bb]AW[cc]AE[ab];B[ee])");

    GoPosition spec = game.positionBefore(1);

    assertThat(spec.setupStones())
        .containsExactlyInAnyOrder(
            SgfMove.black("aa"), SgfMove.black("ba"), SgfMove.black("bb"), SgfMove.white("cc"));
  }

  @Test
  void aStoneAddedAsWhiteAfterBlackOnTheSamePointEndsUpWhite() {
    GoPosition spec = go("(;SZ[9]AB[aa]AW[aa];B[ee])").positionBefore(1);

    assertThat(spec.setupStones()).containsExactly(SgfMove.white("aa"));
  }

  @Test
  void setupInTheOrderWrittenDoesNotChangeTheCanonicalForm() {
    GoPosition a = go("(;SZ[9]AB[aa][bb]AW[cc];B[ee])").positionBefore(1);
    GoPosition b = go("(;SZ[9]AW[cc]AB[bb][aa];B[ee])").positionBefore(1);

    assertThat(a.canonical()).isEqualTo(b.canonical());
    assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
  }

  @Test
  void theCanonicalFormIgnoresFormatting() {
    GoPosition a = go("(;GM[1]SZ[19];B[pd];W[dd];B[qp])").positionBefore(3);
    GoPosition b = go("(\n ;GM[1]\n SZ[19]\n ;B[pd]\n ;W[dd]\n ;B[qp]\n)").positionBefore(3);

    assertThat(a.canonical()).isEqualTo(b.canonical());
  }

  @Test
  void differentPositionsHaveDifferentCanonicalForms() {
    assertThat(go(GAME).positionBefore(3).canonical()).isNotEqualTo(go(GAME).positionBefore(4).canonical());
    assertThat(go("(;SZ[9];B[aa];W[bb])").positionBefore(2).canonical())
        .isNotEqualTo(go("(;SZ[13];B[aa];W[bb])").positionBefore(2).canonical());
  }

  @Test
  void setupAfterTheRootIsFlaggedNotSilentlyApplied() {
    GoGame game = go("(;GM[1]SZ[9];B[ee];AB[aa];W[cc])");

    GoPosition spec = game.positionBefore(2);

    assertThat(spec.hasMidGameSetup()).isTrue();
    assertThat(spec.setupStones()).isEmpty();
    assertThat(spec.canonical()).endsWith(";midgame-setup");
  }

  @Test
  void aRootThatIsAMoveHasNoPositionBefore() {
    GoGame game = go("(;B[pd];W[dd])");

    assertThat(game.positionBefore(1).moves()).isEmpty();
    assertThat(game.positionBefore(2).moves()).containsExactly(SgfMove.black("pd"));
  }

  @Test
  void positionsInsideVariations() {
    GoGame game = go("(;GM[1]SZ[19];B[pd];W[dd](;B[qp];W[dp])(;B[pp];W[cc]))");
    SgfNode w = game.tree().getMove(2);
    SgfNode variationLast = w.children().get(1).next().orElseThrow(); // W[cc]

    GoPosition after = game.positionAfter(variationLast);
    GoPosition before = game.positionBefore(variationLast);

    assertThat(after.moves())
        .containsExactly(SgfMove.black("pd"), SgfMove.white("dd"), SgfMove.black("pp"), SgfMove.white("cc"));
    assertThat(before.moves())
        .containsExactly(SgfMove.black("pd"), SgfMove.white("dd"), SgfMove.black("pp"));
  }

  @Test
  void positionBeforeTheRootIsTheEmptyBoard() {
    GoGame game = go("(;GM[1]SZ[9];B[ee])");

    GoPosition spec = game.positionBefore(game.tree().root());

    assertThat(spec.moves()).isEmpty();
    assertThat(spec.setupStones()).isEmpty();
  }

  @Test
  void aNodeFromAnotherGameIsRejected() {
    GoGame game = go(GAME);
    SgfNode stranger = parser.parse("(;B[pd])").firstGame().root();

    assertThatThrownBy(() -> game.positionAfter(stranger)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void positionsReflectEditsMadeToTheTree() {
    SgfGameTree tree = parser.parse(GAME).firstGame();
    GoGame game = GoGame.of(tree);

    tree.getMove(2).addAlternative(SgfMove.black("cd").toNode()); // does not change the main line

    assertThat(game.positionBefore(4).moves()).hasSize(3);
  }

  // ------------------------------------------------------------------ validation

  @Test
  void aHealthyGameHasNoIssues() {
    assertThat(go(GAME).validate()).isEmpty();
  }

  @Test
  void olderButKnownFileFormatVersionsAreNotFlagged() {
    for (String ff : new String[] {"1", "2", "3", "4"}) {
      assertThat(go("(;GM[1]FF[" + ff + "]SZ[19];B[pd])").validate()).as("FF[%s]", ff).isEmpty();
    }
  }

  @Test
  void validationReportsEverythingNotJustTheFirstProblem() {
    GoGame game = go("(;GM[2]FF[9]SZ[9];B[aa];W[zz]C[x];B[ta](;W[ab]B[ac])(;AB[aa]B[bb]))");

    List<SgfIssue> issues = game.validate();

    assertThat(issues).extracting(SgfIssue::severity).contains(SgfIssue.Severity.WARNING, SgfIssue.Severity.ERROR);
    assertThat(issues).extracting(SgfIssue::message).anySatisfy(m -> assertThat(m).contains("GM[2]"));
    assertThat(issues).extracting(SgfIssue::message).anySatisfy(m -> assertThat(m).contains("FF[9]"));
    assertThat(issues).extracting(SgfIssue::message).anySatisfy(m -> assertThat(m).contains("W[zz]"));
    assertThat(issues).extracting(SgfIssue::message).anySatisfy(m -> assertThat(m).contains("B[ta]"));
    assertThat(issues).extracting(SgfIssue::message).anySatisfy(m -> assertThat(m).contains("both B and W"));
    assertThat(issues).extracting(SgfIssue::message).anySatisfy(m -> assertThat(m).contains("mix a move"));
  }

  @Test
  void issuesPointAtTheOffendingNode() {
    GoGame game = go("(;GM[1]SZ[19];B[pd];W[dd](;B[qp])(;B[zz]))");

    List<SgfIssue> issues = game.validate();

    assertThat(issues).hasSize(1);
    assertThat(issues.get(0).path().toString()).isEqualTo("/0.0.1");
    assertThat(issues.get(0).severity()).isEqualTo(SgfIssue.Severity.ERROR);
    assertThat(game.tree().find(issues.get(0).path()).orElseThrow().value("B")).contains("zz");
    assertThat(issues.get(0).toString()).startsWith("ERROR at /0.0.1");
  }

  @Test
  void unusableBoardSizeStopsFurtherChecksButIsReported() {
    List<SgfIssue> issues = go("(;SZ[abc];B[zz])").validate();

    assertThat(issues).hasSize(1);
    assertThat(issues.get(0).message()).contains("SZ[abc]");
  }

  @Test
  void setupOffTheBoardIsAnError() {
    List<SgfIssue> issues = go("(;SZ[9]AB[aa][zz])").validate();

    assertThat(issues).hasSize(1);
    assertThat(issues.get(0).message()).startsWith("AB:");
  }

  @Test
  void validationOfAVeryLongGameIsFast() {
    StringBuilder sb = new StringBuilder("(;GM[1]SZ[19]");
    for (int i = 0; i < 100_000; i++) {
      sb.append(i % 2 == 0 ? ";B[" : ";W[").append((char) ('a' + i % 19)).append((char) ('a' + (i / 19) % 19)).append(']');
    }
    GoGame game = go(sb.append(')').toString());

    assertThat(game.validate()).isEmpty();
    assertThat(game.positionBefore(100_000).moveCount()).isEqualTo(99_999);
  }
}
