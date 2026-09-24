package io.github.eduardosantiag0.sgf.serializer;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.eduardosantiag0.sgf.model.SgfCollection;
import io.github.eduardosantiag0.sgf.model.SgfGameTree;
import io.github.eduardosantiag0.sgf.model.SgfNode;
import io.github.eduardosantiag0.sgf.parser.SgfParser;
import org.junit.jupiter.api.Test;

class SgfSerializerTest {

  private final SgfParser parser = new SgfParser();

  private String compact(String sgf) {
    return SgfSerializer.compact().serialize(parser.parse(sgf));
  }

  private String pretty(String sgf) {
    return SgfSerializer.pretty().serialize(parser.parse(sgf));
  }

  @Test
  void compactLayoutOfALinearGame() {
    assertThat(compact("( ;GM[1] FF[4]\n ;B[pd]\n;W[dd] )")).isEqualTo("(;GM[1]FF[4];B[pd];W[dd])");
  }

  @Test
  void prettyLayoutPutsEachNodeOnItsOwnLine() {
    assertThat(pretty("(;GM[1]FF[4]SZ[19];B[pd];W[dd])"))
        .isEqualTo("(;GM[1]FF[4]SZ[19]\n;B[pd]\n;W[dd])");
  }

  @Test
  void theDefaultConstructorIsPretty() {
    SgfCollection c = parser.parse("(;B[pd];W[dd])");

    assertThat(new SgfSerializer().serialize(c)).isEqualTo(SgfSerializer.pretty().serialize(c));
  }

  @Test
  void variationsCompact() {
    String sgf = "(;B[pd];W[dd](;B[qp])(;B[pp]))";

    assertThat(compact(sgf)).isEqualTo(sgf);
  }

  @Test
  void variationsPretty() {
    assertThat(pretty("(;B[pd];W[dd](;B[qp])(;B[pp]))"))
        .isEqualTo("(;B[pd]\n;W[dd]\n(;B[qp])\n(;B[pp]))");
  }

  @Test
  void nestedVariationsPretty() {
    assertThat(pretty("(;A[1](;B[1];C[1](;D[1])(;E[1]))(;F[1]))"))
        .isEqualTo("(;A[1]\n(;B[1]\n;C[1]\n(;D[1])\n(;E[1]))\n(;F[1]))");
  }

  @Test
  void aSingleNestedTreeIsWrittenAsAContinuation() {
    assertThat(compact("(;A[1](;B[1]))")).isEqualTo("(;A[1];B[1])");
  }

  @Test
  void escapesBackslashAndClosingBracketOnly() {
    SgfNode node = new SgfNode().setProperty("C", "a]b\\c:d;e(f)g[h");

    assertThat(SgfSerializer.compact().serialize(new SgfGameTree(node)))
        .isEqualTo("(;C[a\\]b\\\\c:d;e(f)g[h])");
  }

  @Test
  void newlinesInValuesAreWrittenRaw() {
    SgfNode node = new SgfNode().setProperty("C", "line1\nline2");

    assertThat(SgfSerializer.compact().serialize(new SgfGameTree(node))).isEqualTo("(;C[line1\nline2])");
  }

  @Test
  void severalValuesAreWrittenBackToBack() {
    assertThat(compact("(;AB[aa]  [bb] [cc])")).isEqualTo("(;AB[aa][bb][cc])");
  }

  @Test
  void emptyValueAndEmptyNode() {
    assertThat(compact("(;B[])")).isEqualTo("(;B[])");
    assertThat(compact("(;)")).isEqualTo("(;)");
  }

  @Test
  void unknownPropertiesAreWritten() {
    assertThat(compact("(;XX[hello]KGSDE[aa][bb])")).isEqualTo("(;XX[hello]KGSDE[aa][bb])");
  }

  @Test
  void collectionsAreSeparatedByLineBreaks() {
    assertThat(compact("(;B[pd];W[dd])(;B[qq];W[dc])")).isEqualTo("(;B[pd];W[dd])\n(;B[qq];W[dc])");
  }

  @Test
  void anEmptyCollectionIsAnEmptyString() {
    assertThat(SgfSerializer.pretty().serialize(new SgfCollection())).isEmpty();
  }

  @Test
  void serializingASingleGame() {
    SgfGameTree game = parser.parse("(;B[pd];W[dd])").firstGame();

    assertThat(SgfSerializer.compact().serialize(game)).isEqualTo("(;B[pd];W[dd])");
  }

  @Test
  void aProgrammaticallyBuiltTreeSerializes() {
    SgfNode root = new SgfNode().setProperty("GM", "1").setProperty("SZ", "19");
    SgfNode first = root.addChild(new SgfNode().setProperty("B", "pd"));
    first.addChild(new SgfNode().setProperty("W", "dd"));
    first.addChild(new SgfNode().setProperty("W", "dp"));

    assertThat(SgfSerializer.compact().serialize(new SgfGameTree(root)))
        .isEqualTo("(;GM[1]SZ[19];B[pd](;W[dd])(;W[dp]))");
  }
}
