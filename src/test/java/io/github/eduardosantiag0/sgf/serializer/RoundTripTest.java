package io.github.eduardosantiag0.sgf.serializer;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.eduardosantiag0.sgf.model.SgfCollection;
import io.github.eduardosantiag0.sgf.model.SgfGameTree;
import io.github.eduardosantiag0.sgf.model.SgfNode;
import io.github.eduardosantiag0.sgf.parser.SgfParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;

/**
 * The central promise of the library: {@code parse -> serialize -> parse} keeps the meaning of the
 * document.
 */
class RoundTripTest {

  private final SgfParser parser = new SgfParser();

  static List<String> samples() {
    return List.of(
        "(;GM[1]FF[4]SZ[19])",
        "(;B[pd];W[dd];B[qp])",
        "(;GM[1]FF[4]SZ[19]PB[Alice]PW[Bob]KM[6.5])",
        "(;AB[aa][bb][cc])",
        "(;C[Hello world])",
        "(;C[text with \\] bracket])",
        "(;C[trailing backslash \\\\])",
        "(;C[a\\\\\\]b])",
        "(;B[pd];W[dd](;B[qp])(;B[pp]))",
        "(;A[1](;B[1](;C[1])(;D[1](;E[1])(;F[1])))(;G[1]))",
        "(;B[pd];W[dd])(;B[qq];W[dc])",
        "(;B[];W[pd])",
        "(;XX[hello])",
        "(;GM[1]XX[custom-property]DT[2026-09-23]EV[Online Match]KM[6.5]RU[Japanese]RE[B+R])",
        "(;C[semi;colon (paren) [not-a-bracket\\] end])",
        "(;C[multi\nline\ncomment\n\nwith blank line])",
        "(;LB[aa:A][bb:B]TR[cc]SQ[dd:ff]CR[gg]MA[hh])",
        "(;PB[柯洁]PW[박정환]C[コミ6目半 ✓ é])",
        "(;)",
        "(;;;)",
        "(;B[pd];;;W[dd])",
        "(;A[1](;B[1])(;C[1])(;D[1])(;E[1]))",
        "(;A[1](;B[1](;C[1](;D[1](;E[1])))))",
        "(;GM[1];C[note];B[pd];C[note2];W[dd](;B[qp];W[dp];B[pq];W[qc];B[od])(;B[pp]C[alt]))");
  }

  @ParameterizedTest
  @MethodSource("samples")
  void prettyRoundTripKeepsTheContent(String sgf) {
    SgfCollection first = parser.parse(sgf);

    SgfCollection second = parser.parse(SgfSerializer.pretty().serialize(first));

    assertThat(second.sameContentAs(first)).isTrue();
  }

  @ParameterizedTest
  @MethodSource("samples")
  void compactRoundTripKeepsTheContent(String sgf) {
    SgfCollection first = parser.parse(sgf);

    SgfCollection second = parser.parse(SgfSerializer.compact().serialize(first));

    assertThat(second.sameContentAs(first)).isTrue();
  }

  @ParameterizedTest
  @MethodSource("samples")
  void serializationIsStableAfterTheFirstPass(String sgf) {
    String once = SgfSerializer.pretty().serialize(parser.parse(sgf));

    String twice = SgfSerializer.pretty().serialize(parser.parse(once));

    assertThat(twice).isEqualTo(once);
  }

  @Test
  void differentLayoutsOfTheSameTreeParseToTheSameContent() {
    SgfCollection tight = parser.parse("(;B[pd];W[dd](;B[qp])(;B[pp]))");
    SgfCollection loose = parser.parse("(\n  ;B[pd]\n  ;W[dd]\n  (\n    ;B[qp]\n  )\n  (\n    ;B[pp]\n  )\n)");

    assertThat(loose.sameContentAs(tight)).isTrue();
  }

  @Test
  void aChangedValueIsDetected() {
    SgfCollection a = parser.parse("(;B[pd];W[dd])");
    SgfCollection b = parser.parse("(;B[pd];W[dc])");

    assertThat(a.sameContentAs(b)).isFalse();
  }

  @Test
  void variationOrderMatters() {
    SgfCollection a = parser.parse("(;A[1](;B[1])(;C[1]))");
    SgfCollection b = parser.parse("(;A[1](;C[1])(;B[1]))");

    assertThat(a.sameContentAs(b)).isFalse();
  }

  // ------------------------------------------------------------------ randomized

  private static final String[] VALUE_ATOMS = {
    "a", "B", "0", " ", "\n", "]", "[", "\\", ";", "(", ")", ":", "é", "柯", "✓", "\t", "\r\n"
  };

  private static String randomValue(Random random) {
    int length = random.nextInt(8);
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < length; i++) {
      sb.append(VALUE_ATOMS[random.nextInt(VALUE_ATOMS.length)]);
    }
    return sb.toString().replace("\r\n", "\n"); // the lexer normalizes line breaks to \n
  }

  private static SgfNode randomNode(Random random) {
    SgfNode node = new SgfNode();
    String[] ids = {"B", "W", "C", "AB", "XX", "LB", "PL", "KGSDE"};
    int properties = random.nextInt(4);
    for (int i = 0; i < properties; i++) {
      String id = ids[random.nextInt(ids.length)];
      int values = 1 + random.nextInt(3);
      List<String> list = new ArrayList<>();
      for (int v = 0; v < values; v++) {
        list.add(randomValue(random));
      }
      node.setProperty(id, list);
    }
    return node;
  }

  private static SgfGameTree randomTree(Random random) {
    SgfNode root = randomNode(random);
    List<SgfNode> all = new ArrayList<>();
    all.add(root);
    int nodes = random.nextInt(60);
    for (int i = 0; i < nodes; i++) {
      // Biased towards recent nodes so that both long lines and bushy branching occur.
      SgfNode parent =
          random.nextInt(3) == 0
              ? all.get(random.nextInt(all.size()))
              : all.get(Math.max(0, all.size() - 1 - random.nextInt(3)));
      SgfNode child = randomNode(random);
      parent.addChild(child);
      all.add(child);
    }
    return new SgfGameTree(root);
  }

  @Test
  void randomTreesSurviveBothLayouts() {
    Random random = new Random(20260923L);
    for (int i = 0; i < 400; i++) {
      SgfCollection original = new SgfCollection();
      int games = 1 + random.nextInt(3);
      for (int g = 0; g < games; g++) {
        original.addGame(randomTree(random));
      }

      for (SgfSerializer serializer : List.of(SgfSerializer.pretty(), SgfSerializer.compact())) {
        String text = serializer.serialize(original);
        SgfCollection reparsed = parser.parse(text);
        assertThat(reparsed.sameContentAs(original)).as("iteration %d:%n%s", i, text).isTrue();
      }
    }
  }
}
