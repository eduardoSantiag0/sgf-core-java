package io.github.eduardosantiag0.sgf.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Decoding of bytes, streams and files: BOM, declared CA charset, UTF-8, ISO-8859-1 fallback. */
class SgfBytesTest {

  private final SgfParser parser = new SgfParser();

  private String comment(byte[] bytes) {
    return parser.parse(bytes).firstGame().root().comment().orElseThrow();
  }

  private static byte[] concat(byte[] a, byte[] b) {
    byte[] out = new byte[a.length + b.length];
    System.arraycopy(a, 0, out, 0, a.length);
    System.arraycopy(b, 0, out, a.length, b.length);
    return out;
  }

  @Test
  void utf8WithoutDeclaration() {
    assertThat(comment("(;C[é 柯洁 ✓])".getBytes(StandardCharsets.UTF_8))).isEqualTo("é 柯洁 ✓");
  }

  @Test
  void utf8ByteOrderMarkIsSkipped() {
    byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    assertThat(comment(concat(bom, "(;C[é])".getBytes(StandardCharsets.UTF_8)))).isEqualTo("é");
  }

  @Test
  void utf16LittleEndianWithByteOrderMark() {
    byte[] bom = {(byte) 0xFF, (byte) 0xFE};

    assertThat(comment(concat(bom, "(;C[é柯])".getBytes(StandardCharsets.UTF_16LE)))).isEqualTo("é柯");
  }

  @Test
  void utf16BigEndianWithByteOrderMark() {
    byte[] bom = {(byte) 0xFE, (byte) 0xFF};

    assertThat(comment(concat(bom, "(;C[é柯])".getBytes(StandardCharsets.UTF_16BE)))).isEqualTo("é柯");
  }

  @Test
  void declaredLatin1IsHonoured() {
    byte[] bytes = "(;CA[ISO-8859-1]C[café])".getBytes(StandardCharsets.ISO_8859_1);

    assertThat(comment(bytes)).isEqualTo("café");
  }

  @Test
  void declaredLegacyChineseEncodingIsHonoured() {
    byte[] bytes = "(;GM[1]CA[GB18030]PB[柯洁]C[黑中盘胜])".getBytes(Charset.forName("GB18030"));

    SgfParserOptions options = SgfParserOptions.defaults();
    var root = new SgfParser(options).parse(bytes).firstGame().root();

    assertThat(root.value("PB")).contains("柯洁");
    assertThat(root.comment()).contains("黑中盘胜");
  }

  @Test
  void declaredShiftJisIsHonoured() {
    byte[] bytes = "(;CA[Shift_JIS]PB[井山裕太]C[コミ6目半])".getBytes(Charset.forName("Shift_JIS"));

    assertThat(comment(bytes)).isEqualTo("コミ6目半");
  }

  @Test
  void declarationIsNotConfusedWithALongerIdentifier() {
    // "PCA" is not "CA"; the bytes are UTF-8 so the right answer is UTF-8.
    byte[] bytes = "(;PCA[ISO-8859-1]C[é])".getBytes(StandardCharsets.UTF_8);

    assertThat(comment(bytes)).isEqualTo("é");
  }

  @Test
  void unknownDeclaredCharsetFallsBackToDetection() {
    byte[] bytes = "(;CA[no-such-charset]C[é])".getBytes(StandardCharsets.UTF_8);

    assertThat(comment(bytes)).isEqualTo("é");
  }

  @Test
  void invalidUtf8FallsBackToLatin1InsteadOfFailing() {
    byte[] bytes = "(;C[café])".getBytes(StandardCharsets.ISO_8859_1); // 0xE9 alone is invalid UTF-8

    assertThat(comment(bytes)).isEqualTo("café");
  }

  @Test
  void byteInputLargerThanTheLimitIsRejectedBeforeDecoding() {
    SgfParser small = new SgfParser(SgfParserOptions.defaults().withMaxInputSize(8));

    assertThatThrownBy(() -> small.parse("(;GM[1]FF[4])".getBytes(StandardCharsets.UTF_8)))
        .isInstanceOf(SgfLimitExceededException.class)
        .hasMessageContaining("bytes");
  }

  @Test
  void streamsAreReadUpToTheLimitOnly() throws IOException {
    SgfParser small = new SgfParser(SgfParserOptions.defaults().withMaxInputSize(100));
    // Far larger than the limit; the parser must give up without reading it all.
    var endless =
        new java.io.InputStream() {
          long served;

          @Override
          public int read() {
            served++;
            return 'x';
          }

          @Override
          public int read(byte[] b, int off, int len) {
            served += len;
            java.util.Arrays.fill(b, off, off + len, (byte) 'x');
            return len;
          }
        };

    assertThatThrownBy(() -> small.parse(endless)).isInstanceOf(SgfLimitExceededException.class);
    assertThat(endless.served).isLessThan(10_000);
  }

  @Test
  void parsesFromAStream() throws IOException {
    var in = new ByteArrayInputStream("(;B[pd];W[dd])".getBytes(StandardCharsets.UTF_8));

    assertThat(parser.parse(in).firstGame().moveCount()).isEqualTo(2);
  }

  @Test
  void parsesFromAFile(@TempDir Path dir) throws IOException {
    Path file = dir.resolve("game.sgf");
    Files.writeString(file, "(;GM[1];B[pd];W[dd])", StandardCharsets.UTF_8);

    assertThat(parser.parse(file).firstGame().moveCount()).isEqualTo(2);
  }

  @Test
  void anExplicitCharsetSkipsDetection() {
    byte[] shiftJis = "(;PB[井山裕太]C[コミ6目半])".getBytes(Charset.forName("Shift_JIS")); // no CA at all

    var root = parser.parse(shiftJis, Charset.forName("Shift_JIS")).firstGame().root();

    assertThat(root.value("PB")).contains("井山裕太");
    assertThat(root.comment()).contains("コミ6目半");
  }

  @Test
  void anExplicitCharsetHonoursTheLimitToo() {
    SgfParser small = new SgfParser(SgfParserOptions.defaults().withMaxInputSize(8));

    assertThatThrownBy(() -> small.parse("(;GM[1]FF[4])".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8))
        .isInstanceOf(SgfLimitExceededException.class);
  }
}
