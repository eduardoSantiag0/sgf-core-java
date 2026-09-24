package io.github.eduardosantiag0.sgf.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Syntax errors and limits: the parser must fail loudly, precisely and safely. */
class SgfParserErrorsTest {

  private final SgfParser parser = new SgfParser();

  private SgfParseException failure(String sgf) {
    return failure(parser, sgf);
  }

  private static SgfParseException failure(SgfParser p, String sgf) {
    Throwable t = org.assertj.core.api.Assertions.catchThrowable(() -> p.parse(sgf));
    assertThat(t).isInstanceOf(SgfParseException.class);
    return (SgfParseException) t;
  }

  @Test
  void missingClosingParenthesis() {
    SgfParseException e = failure("(;B[pd]");

    assertThat(e).hasMessageContaining("Unexpected end of SGF at position 7");
    assertThat(e).hasMessageContaining("Expected ')'");
    assertThat(e.position()).isEqualTo(7);
  }

  @Test
  void missingClosingParenthesisNamesWhereTheTreeWasOpened() {
    SgfParseException e = failure("(;B[pd](;W[dd]");

    assertThat(e).hasMessageContaining("opened at position 7");
    assertThat(e).hasMessageContaining("2 game tree(s) still open");
  }

  @Test
  void missingClosingBracket() {
    SgfParseException e = failure("(;C[abc)");

    assertThat(e).hasMessageContaining("Unexpected end of SGF");
    assertThat(e).hasMessageContaining("Expected ']'");
    assertThat(e).hasMessageContaining("starts at position 3");
  }

  @Test
  void bracketEscapedRightBeforeTheEndIsStillUnterminated() {
    assertThat(failure("(;C[abc\\]")).hasMessageContaining("Expected ']'");
  }

  @Test
  void backslashAsLastCharacter() {
    assertThat(failure("(;C[abc\\")).hasMessageContaining("Unexpected end of SGF");
  }

  @Test
  void lowercaseIdentifierIsInvalidInStrictMode() {
    SgfParseException e = failure("(;Bb[pd])");

    assertThat(e).hasMessageContaining("Invalid property identifier 'Bb'");
    assertThat(e.position()).isEqualTo(2);
  }

  @Test
  void digitsAreNotPartOfAnIdentifier() {
    SgfParseException e = failure("(;B1[pd])");

    assertThat(e).hasMessageContaining("Unexpected character '1'");
    assertThat(e.position()).isEqualTo(3);
  }

  @Test
  void unexpectedCharacter() {
    assertThat(failure("(;B[pd]#)")).hasMessageContaining("Unexpected character '#'");
  }

  @Test
  void controlCharactersAreDescribedByCodePoint() {
    assertThat(failure("(;B[pd]\u0001)")).hasMessageContaining("U+0001");
  }

  @Test
  void emptyTree() {
    SgfParseException e = failure("()");

    assertThat(e).hasMessageContaining("Empty game tree");
    assertThat(e.position()).isEqualTo(1);
  }

  @Test
  void emptyNestedTree() {
    assertThat(failure("(;B[pd]())")).hasMessageContaining("Empty game tree");
  }

  @Test
  void emptyInputAndWhitespaceOnly() {
    assertThat(failure("")).hasMessageContaining("no game tree");
    assertThat(failure("  \n ")).hasMessageContaining("no game tree");
  }

  @Test
  void extraClosingParenthesis() {
    assertThat(failure("(;B[pd]))")).hasMessageContaining("Unexpected ')'");
  }

  @Test
  void nodeOutsideAnyTree() {
    assertThat(failure(";B[pd]")).hasMessageContaining("outside a game tree");
  }

  @Test
  void textBeforeTheFirstTree() {
    assertThat(failure("hello (;B[pd])")).hasMessageContaining("Property 'hello' is outside a node");
  }

  @Test
  void propertyWithoutNode() {
    SgfParseException e = failure("(B[pd])");

    assertThat(e).hasMessageContaining("Property 'B' is outside a node");
    assertThat(e).hasMessageContaining("Expected ';'");
  }

  @Test
  void valueWithoutIdentifier() {
    assertThat(failure("(;[pd])")).hasMessageContaining("Unexpected property value");
  }

  @Test
  void identifierWithoutValue() {
    SgfParseException e = failure("(;B)");

    assertThat(e).hasMessageContaining("Property 'B' has no value");
    assertThat(e.position()).isEqualTo(3);
  }

  @Test
  void identifierWithoutValueAtEndOfInput() {
    assertThat(failure("(;B")).hasMessageContaining("Unexpected end of SGF");
  }

  @Test
  void nodeAfterAVariationIsRejected() {
    assertThat(failure("(;A[1](;B[1]);C[1])")).hasMessageContaining("cannot follow a nested game tree");
  }

  @Test
  void treeNestedDirectlyInsideAnOpeningParenthesis() {
    assertThat(failure("((;B[pd]))")).hasMessageContaining("Unexpected '('");
  }

  @Test
  void errorLocationHasLineAndColumn() {
    String sgf = "(;GM[1]\n;B[pd]\n;W[dd]\n";

    SgfParseException e = failure(sgf);

    assertThat(e.position()).isEqualTo(sgf.length());
    assertThat(e.line()).isEqualTo(4);
    assertThat(e.column()).isEqualTo(1);
    assertThat(e).hasMessageContaining("line 4, column 1");
  }

  @Test
  void lineNumbersCountCrLfAsOneBreak() {
    SgfParseException e = failure("(;GM[1]\r\n;B[pd]\r\n#");

    assertThat(e.line()).isEqualTo(3);
    assertThat(e.column()).isEqualTo(1);
  }

  @Test
  void parseErrorsAreSgfExceptions() {
    assertThatThrownBy(() -> parser.parse("(")).isInstanceOf(io.github.eduardosantiag0.sgf.SgfException.class);
  }

  // ------------------------------------------------------------------ limits

  private static SgfLimitExceededException limit(SgfParser p, String sgf) {
    Throwable t = org.assertj.core.api.Assertions.catchThrowable(() -> p.parse(sgf));
    assertThat(t).isInstanceOf(SgfLimitExceededException.class);
    return (SgfLimitExceededException) t;
  }

  @Test
  void treeDepthAtTheLimitIsAccepted() {
    SgfParser p = new SgfParser(SgfParserOptions.defaults().withMaxTreeDepth(3));

    assertThatCode(() -> p.parse("(;A[1](;B[1](;C[1])))")).doesNotThrowAnyException();
  }

  @Test
  void treeDepthBeyondTheLimit() {
    SgfParser p = new SgfParser(SgfParserOptions.defaults().withMaxTreeDepth(3));

    SgfLimitExceededException e = limit(p, "(;A[1](;B[1](;C[1](;D[1]))))");

    assertThat(e.limitName()).isEqualTo("maxTreeDepth");
    assertThat(e.limit()).isEqualTo(3);
    assertThat(e).hasMessageContaining("nested deeper than 3");
  }

  @Test
  void tooManyNodes() {
    SgfParser p = new SgfParser(SgfParserOptions.defaults().withMaxNodes(2));

    SgfLimitExceededException e = limit(p, "(;A[1];B[1];C[1])");

    assertThat(e.limitName()).isEqualTo("maxNodes");
  }

  @Test
  void nodeLimitCountsAcrossGames() {
    SgfParser p = new SgfParser(SgfParserOptions.defaults().withMaxNodes(3));

    assertThat(limit(p, "(;A[1];B[1])(;C[1];D[1])").limitName()).isEqualTo("maxNodes");
  }

  @Test
  void propertyValueTooLong() {
    SgfParser p = new SgfParser(SgfParserOptions.defaults().withMaxPropertyValueLength(5));

    assertThatCode(() -> p.parse("(;C[12345])")).doesNotThrowAnyException();
    assertThat(limit(p, "(;C[123456])").limitName()).isEqualTo("maxPropertyValueLength");
  }

  @Test
  void propertyValueLimitIsEnforcedWhileReadingNotAfter() {
    SgfParser p = new SgfParser(SgfParserOptions.defaults().withMaxPropertyValueLength(10));
    String hugeUnterminated = "(;C[" + "x".repeat(1_000_000);

    // It must stop with a *limit* error long before it would notice the missing ']'.
    assertThat(limit(p, hugeUnterminated).limitName()).isEqualTo("maxPropertyValueLength");
  }

  @Test
  void inputTooLarge() {
    SgfParser p = new SgfParser(SgfParserOptions.defaults().withMaxInputSize(10));

    SgfLimitExceededException e = limit(p, "(;GM[1]FF[4]SZ[19])");

    assertThat(e.limitName()).isEqualTo("maxInputSize");
    assertThat(e).hasMessageContaining("Raise SgfParserOptions.maxInputSize");
  }

  @Test
  void limitExceptionsAreParseExceptions() {
    SgfParser p = new SgfParser(SgfParserOptions.defaults().withMaxNodes(1));

    assertThatThrownBy(() -> p.parse("(;A[1];B[1])")).isInstanceOf(SgfParseException.class);
  }

  @Test
  void optionsValidateTheirLimits() {
    assertThatThrownBy(() -> SgfParserOptions.defaults().withMaxNodes(0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("maxNodes");
  }
}
