package io.github.eduardosantiag0.sgf.go;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class SgfCoordinateTest {

  @Test
  void parsesSgfPoints() {
    assertThat(SgfCoordinate.parse("aa")).isEqualTo(new SgfCoordinate(0, 0));
    assertThat(SgfCoordinate.parse("pd")).isEqualTo(new SgfCoordinate(15, 3));
    assertThat(SgfCoordinate.parse("ss")).isEqualTo(new SgfCoordinate(18, 18));
    assertThat(SgfCoordinate.parse("AA")).isEqualTo(new SgfCoordinate(26, 26));
    assertThat(SgfCoordinate.parse("zZ")).isEqualTo(new SgfCoordinate(25, 51));
  }

  @Test
  void everyPointOfTheLargestBoardRoundTripsThroughText() {
    for (int x = 0; x < 52; x++) {
      for (int y = 0; y < 52; y++) {
        SgfCoordinate c = new SgfCoordinate(x, y);
        assertThat(SgfCoordinate.parse(c.toSgf())).isEqualTo(c);
      }
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "a", "aaa", "a1", "1a", "a-", "é", "  ", ":a"})
  void rejectsMalformedPoints(String bad) {
    assertThatThrownBy(() -> SgfCoordinate.parse(bad)).isInstanceOf(SgfSemanticException.class);
  }

  @Test
  void rejectsNull() {
    assertThatThrownBy(() -> SgfCoordinate.parse(null)).isInstanceOf(SgfSemanticException.class);
  }

  @Test
  void constructorRejectsOutOfRangeComponents() {
    assertThatThrownBy(() -> new SgfCoordinate(-1, 0)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new SgfCoordinate(0, 52)).isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @CsvSource({
    "pd, 19, Q16", // the classic upper-right star point
    "dp, 19, D4",
    "dd, 19, D16",
    "pp, 19, Q4",
    "aa, 19, A19",
    "ss, 19, T1",
    "jj, 19, K10", // tengen of a 19x19 board
    "ee, 9, E5", // tengen of a 9x9 board
    "cc, 9, C7",
    "gg, 13, G7", // tengen of a 13x13 board
    "ii, 19, J11" // SGF 'i' is column 8, and the human letter after H is J
  })
  void convertsToHumanCoordinates(String sgf, int size, String human) {
    assertThat(SgfCoordinate.parse(sgf).toHuman(size)).isEqualTo(human);
  }

  @Test
  void humanColumnsSkipTheLetterI() {
    StringBuilder columns = new StringBuilder();
    for (int x = 0; x < 19; x++) {
      columns.append(new SgfCoordinate(x, 0).toHuman(19).charAt(0));
    }

    assertThat(columns.toString()).isEqualTo("ABCDEFGHJKLMNOPQRST");
  }

  @Test
  void parsesHumanCoordinatesCaseInsensitively() {
    assertThat(SgfCoordinate.fromHuman("Q16", 19)).isEqualTo(SgfCoordinate.parse("pd"));
    assertThat(SgfCoordinate.fromHuman("q16", 19)).isEqualTo(SgfCoordinate.parse("pd"));
    assertThat(SgfCoordinate.fromHuman(" d4 ", 19)).isEqualTo(SgfCoordinate.parse("dp"));
    assertThat(SgfCoordinate.fromHuman("A1", 19)).isEqualTo(SgfCoordinate.parse("as"));
    assertThat(SgfCoordinate.fromHuman("T19", 19)).isEqualTo(SgfCoordinate.parse("sa"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "D", "4", "I5", "D0", "D20", "U5", "D-1", "Dx", "DD"})
  void rejectsBadHumanCoordinates(String bad) {
    assertThatThrownBy(() -> SgfCoordinate.fromHuman(bad, 19)).isInstanceOf(SgfSemanticException.class);
  }

  @Test
  void humanCoordinatesRespectTheBoardSize() {
    assertThatThrownBy(() -> SgfCoordinate.fromHuman("K10", 9)).isInstanceOf(SgfSemanticException.class);
    assertThatThrownBy(() -> SgfCoordinate.parse("jj").toHuman(9)).isInstanceOf(SgfSemanticException.class);
  }

  @Test
  void humanCoordinatesExistOnlyUpToTwentyFive() {
    assertThatThrownBy(() -> SgfCoordinate.parse("aa").toHuman(26)).isInstanceOf(SgfSemanticException.class);
    assertThatThrownBy(() -> SgfCoordinate.fromHuman("A1", 30)).isInstanceOf(SgfSemanticException.class);
  }

  @Test
  void humanAndSgfFormsAgreeOnEveryPointOfCommonBoards() {
    for (int size : new int[] {9, 13, 19, 25}) {
      for (int x = 0; x < size; x++) {
        for (int y = 0; y < size; y++) {
          SgfCoordinate c = new SgfCoordinate(x, y);
          assertThat(SgfCoordinate.fromHuman(c.toHuman(size), size)).isEqualTo(c);
        }
      }
    }
  }

  @Test
  void onBoardCheck() {
    assertThat(SgfCoordinate.parse("ss").isOnBoard(19)).isTrue();
    assertThat(SgfCoordinate.parse("ts").isOnBoard(19)).isFalse();
    assertThat(SgfCoordinate.parse("st").isOnBoard(19)).isFalse();
  }

  @Test
  void pointListExpandsRectangles() {
    assertThat(SgfCoordinate.parsePointList("aa")).containsExactly(SgfCoordinate.parse("aa"));
    assertThat(SgfCoordinate.parsePointList("aa:bb"))
        .extracting(SgfCoordinate::toSgf)
        .containsExactly("aa", "ba", "ab", "bb");
    assertThat(SgfCoordinate.parsePointList("aa:cc")).hasSize(9);
  }

  @Test
  void pointListRectangleCornersMayComeInAnyOrder() {
    assertThat(SgfCoordinate.parsePointList("cc:aa")).containsExactlyElementsOf(SgfCoordinate.parsePointList("aa:cc"));
    assertThat(SgfCoordinate.parsePointList("ca:ac")).containsExactlyElementsOf(SgfCoordinate.parsePointList("aa:cc"));
  }

  @Test
  void pointListDegenerateRectangleIsOnePointOrALine() {
    assertThat(SgfCoordinate.parsePointList("dd:dd")).containsExactly(SgfCoordinate.parse("dd"));
    assertThat(SgfCoordinate.parsePointList("aa:ad")).hasSize(4);
  }

  @Test
  void pointListRejectsMalformedValues() {
    for (String bad : new String[] {"", "a", "aa:", ":aa", "aa:b", "aa:bb:cc"}) {
      assertThatThrownBy(() -> SgfCoordinate.parsePointList(bad)).as(bad).isInstanceOf(SgfSemanticException.class);
    }
  }
}
