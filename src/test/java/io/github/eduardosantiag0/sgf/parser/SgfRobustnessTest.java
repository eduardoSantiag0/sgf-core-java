package io.github.eduardosantiag0.sgf.parser;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.eduardosantiag0.sgf.model.SgfCollection;
import io.github.eduardosantiag0.sgf.model.SgfGameTree;
import io.github.eduardosantiag0.sgf.model.SgfNode;
import io.github.eduardosantiag0.sgf.serializer.SgfSerializer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * Recursive SGF implementations tend to overflow the stack at a few thousand nodes; these tests pin
 * down that nothing here is recursive and that one parser is safely shareable.
 */
class SgfRobustnessTest {

  private final SgfParser parser = new SgfParser();

  private static String longGame(int moves) {
    StringBuilder sb = new StringBuilder("(;GM[1]SZ[19]");
    for (int i = 0; i < moves; i++) {
      sb.append(i % 2 == 0 ? ";B[" : ";W[")
          .append((char) ('a' + i % 19))
          .append((char) ('a' + (i / 19) % 19))
          .append(']');
    }
    return sb.append(')').toString();
  }

  @Test
  void aTwoHundredThousandNodeLineIsHandledEndToEnd() {
    SgfCollection parsed = parser.parse(longGame(200_000));
    SgfGameTree game = parsed.firstGame();

    assertThat(game.moveCount()).isEqualTo(200_000);
    assertThat(game.mainLine()).hasSize(200_001);
    assertThat(game.nodes()).hasSize(200_001);

    SgfNode last = game.getMove(200_000);
    assertThat(last.path().depth()).isEqualTo(200_000);
    assertThat(game.find(last.path())).containsSame(last);

    SgfCollection copy = parsed.copy();
    assertThat(copy.sameContentAs(parsed)).isTrue();

    String text = SgfSerializer.compact().serialize(parsed);
    assertThat(parser.parse(text).sameContentAs(parsed)).isTrue();
    String pretty = SgfSerializer.pretty().serialize(parsed);
    assertThat(parser.parse(pretty).sameContentAs(parsed)).isTrue();
  }

  @Test
  void deeplyNestedVariationsWithinTheLimitAreHandled() {
    int depth = 900;
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < depth; i++) {
      sb.append("(;A[").append(i).append(']');
    }
    sb.append(")".repeat(depth));

    SgfCollection parsed = parser.parse(sb.toString());

    assertThat(parsed.firstGame().nodes()).hasSize(depth);
    String text = SgfSerializer.compact().serialize(parsed);
    assertThat(parser.parse(text).sameContentAs(parsed)).isTrue();
    assertThat(parsed.copy().sameContentAs(parsed)).isTrue();
  }

  @Test
  void nestingBeyondTheLimitIsRejectedWithoutStackTrouble() {
    String tooDeep = "(;A[1]".repeat(1_001) + ")".repeat(1_001);

    Throwable t = org.assertj.core.api.Assertions.catchThrowable(() -> parser.parse(tooDeep));

    assertThat(t).isInstanceOf(SgfLimitExceededException.class);
    assertThat(((SgfLimitExceededException) t).limitName()).isEqualTo("maxTreeDepth");
  }

  @Test
  void aHugeNumberOfOpeningParenthesesFailsCleanly() {
    String hostile = "(".repeat(5_000_000);
    SgfParser generous = new SgfParser(SgfParserOptions.defaults().withMaxInputSize(6_000_000));

    Throwable t = org.assertj.core.api.Assertions.catchThrowable(() -> generous.parse(hostile));

    assertThat(t).isInstanceOf(SgfParseException.class);
  }

  @Test
  void wideBranchingIsHandled() {
    StringBuilder sb = new StringBuilder("(;GM[1]");
    for (int i = 0; i < 50_000; i++) {
      sb.append("(;B[aa])");
    }
    sb.append(')');

    SgfCollection parsed = parser.parse(sb.toString());

    assertThat(parsed.firstGame().root().children()).hasSize(50_000);
    assertThat(parser.parse(SgfSerializer.compact().serialize(parsed)).sameContentAs(parsed)).isTrue();
  }

  @Test
  void aSingleHugeValueWithinTheLimitIsHandled() {
    String value = "x".repeat(900_000);

    SgfCollection parsed = parser.parse("(;C[" + value + "])");

    assertThat(parsed.firstGame().root().comment().orElseThrow()).hasSize(900_000);
  }

  @Test
  void onePropertyMayHoldManyValues() {
    StringBuilder sb = new StringBuilder("(;AB");
    for (int i = 0; i < 100_000; i++) {
      sb.append("[aa]");
    }
    sb.append(')');

    assertThat(parser.parse(sb.toString()).firstGame().root().values("AB")).hasSize(100_000);
  }

  @Test
  void oneParserInstanceIsSafeToShareAcrossThreads() throws Exception {
    List<String> inputs = new ArrayList<>();
    List<SgfCollection> expected = new ArrayList<>();
    for (int i = 0; i < 20; i++) {
      String sgf = "(;GM[1]PB[p" + i + "];B[pd](;W[dd];B[qp])(;W[" + (char) ('a' + i) + "a]C[v" + i + "])" + ")";
      inputs.add(sgf);
      expected.add(new SgfParser().parse(sgf));
    }

    ExecutorService pool = Executors.newFixedThreadPool(8);
    try {
      List<Future<Boolean>> results = new ArrayList<>();
      for (int round = 0; round < 400; round++) {
        int index = round % inputs.size();
        results.add(
            pool.submit(
                () -> {
                  SgfCollection parsed = parser.parse(inputs.get(index));
                  String again = SgfSerializer.compact().serialize(parsed);
                  return parsed.sameContentAs(expected.get(index))
                      && parser.parse(again).sameContentAs(expected.get(index));
                }));
      }
      for (Future<Boolean> f : results) {
        assertThat(f.get(30, TimeUnit.SECONDS)).isTrue();
      }
    } finally {
      pool.shutdownNow();
    }
  }
}
