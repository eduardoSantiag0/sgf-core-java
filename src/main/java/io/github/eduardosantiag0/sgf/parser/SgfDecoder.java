package io.github.eduardosantiag0.sgf.parser;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns the bytes of an SGF file into text. SGF declares its own encoding in the {@code CA}
 * property, which sits in the first node and is plain ASCII, so it can be read before decoding.
 *
 * <p>Order of decision: byte order mark; then the declared {@code CA[...]} charset if the JVM
 * knows it; then UTF-8 if the bytes are valid UTF-8; and finally ISO-8859-1, which maps every byte
 * and therefore never fails (the FF[4] default). There is deliberately no statistical guessing of
 * legacy CJK encodings; files in those should declare {@code CA}.
 */
final class SgfDecoder {

  private static final int SNIFF_LENGTH = 4096;
  private static final Pattern CA =
      Pattern.compile("(?<![A-Za-z])CA\\s*\\[\\s*([^\\]\\\\\\s]{1,64})\\s*\\]");

  private SgfDecoder() {}

  static String decode(byte[] bytes) {
    int offset = 0;
    Charset charset = null;
    if (startsWith(bytes, 0xEF, 0xBB, 0xBF)) {
      charset = StandardCharsets.UTF_8;
      offset = 3;
    } else if (startsWith(bytes, 0xFE, 0xFF)) {
      charset = StandardCharsets.UTF_16BE;
      offset = 2;
    } else if (startsWith(bytes, 0xFF, 0xFE)) {
      charset = StandardCharsets.UTF_16LE;
      offset = 2;
    }
    if (charset == null) {
      charset = declaredCharset(bytes);
    }
    if (charset == null) {
      charset = isValidUtf8(bytes) ? StandardCharsets.UTF_8 : StandardCharsets.ISO_8859_1;
    }
    return new String(bytes, offset, bytes.length - offset, charset);
  }

  private static Charset declaredCharset(byte[] bytes) {
    String head = new String(bytes, 0, Math.min(bytes.length, SNIFF_LENGTH), StandardCharsets.ISO_8859_1);
    Matcher m = CA.matcher(head);
    if (!m.find()) {
      return null;
    }
    try {
      return Charset.forName(m.group(1));
    } catch (IllegalArgumentException e) {
      // IllegalCharsetNameException and UnsupportedCharsetException both extend it.
      return null;
    }
  }

  private static boolean isValidUtf8(byte[] bytes) {
    try {
      StandardCharsets.UTF_8
          .newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(bytes));
      return true;
    } catch (CharacterCodingException e) {
      return false;
    }
  }

  private static boolean startsWith(byte[] bytes, int... prefix) {
    if (bytes.length < prefix.length) {
      return false;
    }
    for (int i = 0; i < prefix.length; i++) {
      if ((bytes[i] & 0xFF) != prefix[i]) {
        return false;
      }
    }
    return true;
  }
}
