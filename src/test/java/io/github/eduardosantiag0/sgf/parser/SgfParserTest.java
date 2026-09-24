package io.github.eduardosantiag0.sgf.parser;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.eduardosantiag0.sgf.model.SgfCollection;
import io.github.eduardosantiag0.sgf.model.SgfNode;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Successful parsing: the structure the parser must produce. */
class SgfParserTest {

  private final SgfParser parser = new SgfParser();

  private SgfNode root(String sgf) {
    return parser.parse(sgf).firstGame().root();
  }

  @Test
  void minimalGame() {
    SgfCollection collection = parser.parse("(;GM[1]FF[4]SZ[19])");

    assertThat(collection.size()).isEqualTo(1);
    SgfNode root = collection.firstGame().root();
    assertThat(root.value("GM")).contains("1");
    assertThat(root.value("FF")).contains("4");
    assertThat(root.value("SZ")).contains("19");
    assertThat(root.hasChildren()).isFalse();
  }

  @Test
  void emptyNodeIsAllowed() {
    SgfNode root = root("(;)");

    assertThat(root.properties()).isEmpty();
  }

  @Test
  void simpleSequence() {
    SgfNode root = root("(;B[pd];W[dd];B[qp])");

    List<SgfNode> line = root.mainLine();
    assertThat(line).hasSize(3);
    assertThat(line.get(0).value("B")).contains("pd");
    assertThat(line.get(1).value("W")).contains("dd");
    assertThat(line.get(2).value("B")).contains("qp");
    assertThat(line.get(2).parent()).contains(line.get(1));
  }

  @Test
  void metadata() {
    SgfNode root = root("(;GM[1]FF[4]SZ[19]PB[Alice]PW[Bob]KM[6.5])");

    assertThat(root.value("PB")).contains("Alice");
    assertThat(root.value("PW")).contains("Bob");
    assertThat(root.value("KM")).contains("6.5");
    assertThat(root.properties()).hasSize(6);
  }

  @Test
  void propertyOrderIsPreserved() {
    SgfNode root = root("(;PW[b]GM[1]PB[a])");

    assertThat(root.properties()).extracting("identifier").containsExactly("PW", "GM", "PB");
  }

  @Test
  void propertyWithSeveralValues() {
    SgfNode root = root("(;AB[aa][bb][cc])");

    assertThat(root.values("AB")).containsExactly("aa", "bb", "cc");
  }

  @Test
  void whitespaceBetweenValuesOfOneProperty() {
    SgfNode root = root("(;AB[aa] [bb]\n\t[cc])");

    assertThat(root.values("AB")).containsExactly("aa", "bb", "cc");
  }

  @Test
  void whitespaceBetweenTokensIsIrrelevant() {
    SgfNode spaced = root(" \n( ;GM [1]\r\n  PB\t[A] ;B [pd]\n; W[dd] ) \n");
    SgfNode tight = root("(;GM[1]PB[A];B[pd];W[dd])");

    assertThat(spaced.sameContentAs(tight)).isTrue();
  }

  @Test
  void comment() {
    SgfNode root = root("(;C[Hello world])");

    assertThat(root.comment()).contains("Hello world");
  }

  @Test
  void byteOrderMarkCharacterIsIgnored() {
    SgfNode root = root("﻿(;GM[1])");

    assertThat(root.value("GM")).contains("1");
  }

  // ------------------------------------------------------------------ escaping

  @Test
  void escapedClosingBracketDoesNotEndTheValue() {
    SgfNode root = root("(;C[text with \\] bracket])");

    assertThat(root.comment()).contains("text with ] bracket");
  }

  @Test
  void escapedBackslash() {
    assertThat(root("(;C[a\\\\b])").comment()).contains("a\\b");
  }

  @Test
  void escapedBackslashRightBeforeTheClosingBracket() {
    // C[a\\] is the two characters  a \  and the value ends at the final ']'.
    SgfNode root = root("(;C[a\\\\];B[pd])");

    assertThat(root.comment()).contains("a\\");
    assertThat(root.next().orElseThrow().value("B")).contains("pd");
  }

  @Test
  void escapedBackslashFollowedByEscapedBracket() {
    assertThat(root("(;C[a\\\\\\]b])").comment()).contains("a\\]b");
  }

  @Test
  void escapingAnOrdinaryCharacterYieldsThatCharacter() {
    assertThat(root("(;C[a\\:b\\nc])").comment()).contains("a:bnc");
  }

  @Test
  void structuralCharactersInsideValuesAreJustText() {
    SgfNode root = root("(;C[a;b)c(d[e];B[pd])");

    assertThat(root.comment()).contains("a;b)c(d[e");
    assertThat(root.next().orElseThrow().value("B")).contains("pd");
  }

  @Test
  void softLineBreakDisappears() {
    assertThat(root("(;C[foo\\\nbar])").comment()).contains("foobar");
    assertThat(root("(;C[foo\\\r\nbar])").comment()).contains("foobar");
  }

  @Test
  void everyKindOfLineBreakBecomesNewline() {
    assertThat(root("(;C[a\r\nb\rc\nd\n\re])").comment()).contains("a\nb\nc\nd\ne");
  }

  @Test
  void unicodeIsPreserved() {
    assertThat(root("(;PB[柯洁]PW[박정환]C[コミ6目半 ✓])").value("PB")).contains("柯洁");
    assertThat(root("(;PB[柯洁]PW[박정환]C[コミ6目半 ✓])").comment()).contains("コミ6目半 ✓");
  }

  // ------------------------------------------------------------------ unknown / odd properties

  @Test
  void unknownPropertyIsKept() {
    SgfNode root = root("(;XX[hello]DT[2026-09-23]EV[Online Match]KM[6.5]RU[Japanese]RE[B+R])");

    assertThat(root.value("XX")).contains("hello");
    assertThat(root.value("DT")).contains("2026-09-23");
    assertThat(root.value("RE")).contains("B+R");
  }

  @Test
  void anyUppercaseIdentifierLengthIsAccepted() {
    SgfNode root = root("(;KGSDE[aa]MULTIGOGM[1])");

    assertThat(root.value("KGSDE")).contains("aa");
    assertThat(root.value("MULTIGOGM")).contains("1");
  }

  @Test
  void propertyRepeatedInOneNodeIsMerged() {
    assertThat(root("(;AB[aa]AB[bb])").values("AB")).containsExactly("aa", "bb");
  }

  @Test
  void lenientModeNormalizesFf3StyleIdentifiers() {
    SgfParser lenient = new SgfParser(SgfParserOptions.defaults().withLenient(true));

    SgfNode root = lenient.parse("(;GaMe[1];White[dd]Comment[hi]xyz[ignored])").firstGame().root();

    assertThat(root.value("GM")).contains("1");
    SgfNode second = root.next().orElseThrow();
    assertThat(second.value("W")).contains("dd");
    assertThat(second.comment()).contains("hi");
    assertThat(second.properties()).hasSize(2);
  }

  @Test
  void passIsAnEmptyValue() {
    SgfNode root = root("(;B[];W[pd])");

    assertThat(root.value("B")).contains("");
    assertThat(root.isMove()).isTrue();
  }

  // ------------------------------------------------------------------ trees

  @Test
  void oneVariation() {
    SgfNode root = root("(;B[pd];W[dd](;B[qp])(;B[pp]))");

    SgfNode w = root.next().orElseThrow();
    assertThat(w.value("W")).contains("dd");
    assertThat(w.children()).hasSize(2);
    assertThat(w.children().get(0).value("B")).contains("qp"); // main line
    assertThat(w.children().get(1).value("B")).contains("pp"); // variation
  }

  @Test
  void variationsBeforeTheEndOfTheGameAreAllowedOnTheRoot() {
    SgfNode root = root("(;GM[1](;B[pd])(;B[dd]))");

    assertThat(root.children()).hasSize(2);
  }

  @Test
  void nestedVariations() {
    SgfNode root = root("(;A[1](;B[1](;C[1])(;D[1](;E[1])(;F[1])))(;G[1]))");

    assertThat(root.children()).hasSize(2);
    SgfNode b = root.children().get(0);
    SgfNode g = root.children().get(1);
    assertThat(b.value("B")).contains("1");
    assertThat(g.value("G")).contains("1");
    assertThat(b.children()).hasSize(2);
    SgfNode d = b.children().get(1);
    assertThat(d.children()).extracting(n -> n.properties().iterator().next().identifier()).containsExactly("E", "F");
  }

  @Test
  void longSequenceInsideAVariation() {
    SgfNode root = root("(;B[pd];W[dd](;B[qp];W[dp];B[pq])(;B[pp]))");

    SgfNode w = root.next().orElseThrow();
    List<SgfNode> firstBranch = w.children().get(0).mainLine();
    assertThat(firstBranch).hasSize(3);
    assertThat(w.children().get(1).hasChildren()).isFalse();
  }

  @Test
  void variationsHangFromTheLastNodeOfTheSequenceTheyFollow() {
    SgfNode root = root("(;A[1];B[1](;C[1])(;D[1]))");

    SgfNode b = root.next().orElseThrow();
    assertThat(root.children()).hasSize(1);
    assertThat(b.children()).hasSize(2);
  }

  @Test
  void aSingleNestedTreeIsTheSameAsAContinuation() {
    SgfNode nested = root("(;A[1](;B[1];C[1]))");
    SgfNode flat = root("(;A[1];B[1];C[1])");

    assertThat(nested.sameContentAs(flat)).isTrue();
  }

  @Test
  void multipleGames() {
    SgfCollection collection = parser.parse("(;B[pd];W[dd])\n(;B[qq];W[dc])");

    assertThat(collection.size()).isEqualTo(2);
    assertThat(collection.game(0).root().value("B")).contains("pd");
    assertThat(collection.game(1).root().value("B")).contains("qq");
    assertThat(collection.game(1).mainLine()).hasSize(2);
  }

  @Test
  void gamesAreIndependentTrees() {
    SgfCollection collection = parser.parse("(;A[1];B[1])(;C[1])");

    assertThat(collection.game(0).nodes()).hasSize(2);
    assertThat(collection.game(1).nodes()).hasSize(1);
  }

  // ------------------------------------------------------------------ lenient mode

  private final SgfParser lenient = new SgfParser(SgfParserOptions.defaults().withLenient(true));

  @Test
  void propertiesRightAfterTheOpeningParenthesisGetAnImplicitNodeInLenientMode() {
    // Shape of real Nihon Ki-in exports: "(" then properties, the first ";" is missing.
    SgfNode root = lenient.parse("(\nTE[x]\nPB[Alice]\n;B[pd];W[dd])").firstGame().root();

    assertThat(root.value("TE")).contains("x");
    assertThat(root.value("PB")).contains("Alice");
    assertThat(root.mainLine()).hasSize(3);
  }

  @Test
  void theImplicitNodeIsTheSameAsWritingTheSemicolon() {
    assertThat(lenient.parse("(TE[x]PB[a];B[pd])").sameContentAs(parser.parse("(;TE[x]PB[a];B[pd])"))).isTrue();
  }

  @Test
  void theImplicitNodeAlsoWorksInsideAVariation() {
    SgfNode root = lenient.parse("(;A[1](B[1]C[2];D[1]))").firstGame().root();

    assertThat(root.next().orElseThrow().values("C")).containsExactly("2");
  }

  @Test
  void aStrayClosingParenthesisIsIgnoredInLenientModeOnly() {
    String sgf = "(;B[pd];W[dd])\n)";

    assertThat(lenient.parse(sgf).firstGame().moveCount()).isEqualTo(2);
    assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> parser.parse(sgf)))
        .isInstanceOf(SgfParseException.class)
        .hasMessageContaining("Unexpected ')'");
  }

  @Test
  void lenientModeStillRejectsRealStructureErrors() {
    for (String bad : new String[] {"(;B[pd]", "()", "(;C[abc)", "", "(;B)", "(;A[1](;B[1]);C[1])"}) {
      assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> lenient.parse(bad)))
          .as(bad)
          .isInstanceOf(SgfParseException.class);
    }
  }

  @Test
  void aPropertyBeforeAnyGameTreeIsStillAnErrorInLenientMode() {
    assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> lenient.parse("B[pd] (;B[pd])")))
        .isInstanceOf(SgfParseException.class)
        .hasMessageContaining("outside a node");
  }
}
