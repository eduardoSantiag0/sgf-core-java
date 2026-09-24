package io.github.eduardosantiag0.sgf.parser;

/**
 * Splits SGF text into tokens. It knows the lexical rules only: what a property value is (and how
 * {@code \} escapes work), what an identifier is, and where whitespace may appear. It has no idea
 * what a game tree is; that is {@link SgfParser}'s job.
 *
 * <p>Property values are returned <em>decoded</em>: {@code \]} becomes {@code ]}, {@code \\}
 * becomes {@code \}, a backslash before a line break is a soft line break and disappears, and every
 * kind of line break ({@code \r\n}, {@code \n\r}, {@code \r}, {@code \n}) becomes {@code \n}.
 *
 * <p>One instance per parse; not shared between threads.
 */
final class SgfLexer {

  enum Type {
    OPEN,
    CLOSE,
    SEMICOLON,
    IDENTIFIER,
    VALUE,
    EOF
  }

  /**
   * A token.
   *
   * @param type the kind of token
   * @param text the decoded value for {@link Type#VALUE}, the raw letters for
   *     {@link Type#IDENTIFIER}, the character for punctuation
   * @param offset the offset of the first character of the token
   */
  record Token(Type type, String text, int offset) {}

  private final String input;
  private final int maxValueLength;
  private int pos;
  private Token peeked;

  SgfLexer(String input, int maxValueLength) {
    this.input = input;
    this.maxValueLength = maxValueLength;
  }

  Token peek() {
    if (peeked == null) {
      peeked = read();
    }
    return peeked;
  }

  Token next() {
    Token t = peek();
    peeked = null;
    return t;
  }

  /** Puts back the token just returned by {@link #next()}. */
  void unread(Token token) {
    if (peeked != null) {
      throw new IllegalStateException("Only one token can be pushed back");
    }
    peeked = token;
  }

  private Token read() {
    int n = input.length();
    while (pos < n && isWhitespace(input.charAt(pos))) {
      pos++;
    }
    if (pos >= n) {
      return new Token(Type.EOF, "", n);
    }
    int start = pos;
    char c = input.charAt(pos);
    switch (c) {
      case '(':
        pos++;
        return new Token(Type.OPEN, "(", start);
      case ')':
        pos++;
        return new Token(Type.CLOSE, ")", start);
      case ';':
        pos++;
        return new Token(Type.SEMICOLON, ";", start);
      case '[':
        return readValue();
      default:
        if (isAsciiLetter(c)) {
          while (pos < n && isAsciiLetter(input.charAt(pos))) {
            pos++;
          }
          return new Token(Type.IDENTIFIER, input.substring(start, pos), start);
        }
        throw error(
            input,
            start,
            "Unexpected character " + describe(c),
            "Expected '(', ')', ';', a property identifier (letters) or a '[' value");
    }
  }

  private Token readValue() {
    int start = pos;
    int n = input.length();
    pos++; // the opening '['
    StringBuilder sb = new StringBuilder();
    while (pos < n) {
      char c = input.charAt(pos);
      if (c == ']') {
        pos++;
        return new Token(Type.VALUE, sb.toString(), start);
      }
      if (c == '\\') {
        pos++;
        if (pos >= n) {
          break;
        }
        char escaped = input.charAt(pos);
        pos++;
        if (escaped == '\r' || escaped == '\n') {
          skipPairedLineBreak(escaped); // soft line break: produces nothing
        } else {
          sb.append(escaped);
        }
      } else if (c == '\r' || c == '\n') {
        pos++;
        skipPairedLineBreak(c);
        sb.append('\n');
      } else {
        sb.append(c);
        pos++;
      }
      if (sb.length() > maxValueLength) {
        throw limitError(
            "maxPropertyValueLength",
            maxValueLength,
            start,
            "Property value longer than " + maxValueLength + " characters");
      }
    }
    throw error(
        input,
        n,
        "Unexpected end of SGF",
        "Expected ']' to close the property value that starts at position " + start + " ("
            + describeLocation(input, start) + ")");
  }

  /** Consumes the second character of a {@code \r\n} or {@code \n\r} pair. */
  private void skipPairedLineBreak(char first) {
    if (pos < input.length()) {
      char second = input.charAt(pos);
      if ((first == '\r' && second == '\n') || (first == '\n' && second == '\r')) {
        pos++;
      }
    }
  }

  private static boolean isWhitespace(char c) {
    // ASCII whitespace by code (tab 9, line feed 10, form feed 12, carriage return 13, vertical tab 11),
    // plus a byte order mark (0xFEFF), which can sit at the start of a file or where files were
    // concatenated, and the no-break space (0xA0), common in text copied from web pages.
    return c == 32 || c == 9 || c == 10 || c == 11 || c == 12 || c == 13 || c == 0xFEFF || c == 0xA0;
  }

  private static boolean isAsciiLetter(char c) {
    return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
  }

  private static String describe(char c) {
    if (c > ' ' && c < 0x7F) {
      return "'" + c + "'";
    }
    return String.format("U+%04X", (int) c);
  }

  // ------------------------------------------------------------ error reporting

  private SgfLimitExceededException limitError(String name, long limit, int offset, String problem) {
    return limitError(input, name, limit, offset, problem);
  }

  /**
   * Builds a syntax error with a position. Computing line and column costs a pass over the input,
   * which is fine because it only happens once, when parsing fails.
   */
  static SgfParseException error(String input, int offset, String problem, String expectation) {
    int[] lc = lineAndColumn(input, offset);
    return new SgfParseException(
        problem + " at position " + offset + " (line " + lc[0] + ", column " + lc[1] + "). " + expectation + ".",
        offset,
        lc[0],
        lc[1]);
  }

  static SgfLimitExceededException limitError(
      String input, String name, long limit, int offset, String problem) {
    int[] lc = lineAndColumn(input, offset);
    return new SgfLimitExceededException(
        name,
        limit,
        problem + " at position " + offset + " (line " + lc[0] + ", column " + lc[1] + ")."
            + " Raise SgfParserOptions." + name + " if this input is trusted.",
        offset,
        lc[0],
        lc[1]);
  }

  private static String describeLocation(String input, int offset) {
    int[] lc = lineAndColumn(input, offset);
    return "line " + lc[0] + ", column " + lc[1];
  }

  static int[] lineAndColumn(String input, int offset) {
    int line = 1;
    int column = 1;
    int limit = Math.min(offset, input.length());
    for (int i = 0; i < limit; i++) {
      char c = input.charAt(i);
      if (c == '\n' || (c == '\r' && !(i + 1 < input.length() && input.charAt(i + 1) == '\n'))) {
        line++;
        column = 1;
      } else if (c != '\r') {
        column++;
      }
    }
    return new int[] {line, column};
  }
}
