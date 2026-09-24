package io.github.eduardosantiag0.sgf.parser;

import io.github.eduardosantiag0.sgf.model.SgfCollection;
import io.github.eduardosantiag0.sgf.model.SgfGameTree;
import io.github.eduardosantiag0.sgf.model.SgfNode;
import io.github.eduardosantiag0.sgf.parser.SgfLexer.Token;
import io.github.eduardosantiag0.sgf.parser.SgfLexer.Type;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Parses SGF (FF[4]) text into an {@link SgfCollection}.
 *
 * <p>The grammar implemented is
 *
 * <pre>
 * Collection = GameTree+
 * GameTree   = "(" Sequence GameTree* ")"
 * Sequence   = Node+
 * Node       = ";" Property*
 * Property   = PropIdent PropValue+
 * </pre>
 *
 * with whitespace allowed between tokens. A {@code Sequence} becomes a chain of parent/child
 * nodes and each nested {@code GameTree} becomes a further child of the last node of the sequence
 * it follows, so the first child is the main line and the others are variations.
 *
 * <h2>Guarantees</h2>
 *
 * <ul>
 *   <li><strong>Stateless and thread-safe.</strong> An instance only holds its immutable options;
 *       every {@code parse} call works on its own local state, so a single instance can be shared
 *       (for example as a singleton bean) and used concurrently.
 *   <li><strong>No recursion.</strong> Nesting is tracked with an explicit stack, so hostile input
 *       cannot cause a {@link StackOverflowError}; depth is bounded by
 *       {@link SgfParserOptions#maxTreeDepth()} for memory reasons only.
 *   <li><strong>Bounded work.</strong> Input size, node count, nesting and value length are limited
 *       by {@link SgfParserOptions}; exceeding one raises {@link SgfLimitExceededException}.
 *   <li><strong>Lossless properties.</strong> Unknown properties are kept, as are multiple values.
 *       A property repeated within one node (which the standard forbids) is merged rather than
 *       rejected.
 *   <li><strong>Precise errors.</strong> Syntax errors raise {@link SgfParseException} with the
 *       position, line and column, and say what was expected.
 * </ul>
 */
public final class SgfParser {

  private final SgfParserOptions options;

  /** Creates a parser with {@linkplain SgfParserOptions#defaults() default options}. */
  public SgfParser() {
    this(SgfParserOptions.defaults());
  }

  /**
   * Creates a parser.
   *
   * @param options the limits and leniency to apply
   */
  public SgfParser(SgfParserOptions options) {
    this.options = Objects.requireNonNull(options, "options");
  }

  /**
   * Returns the options of this parser.
   *
   * @return the options
   */
  public SgfParserOptions options() {
    return options;
  }

  /**
   * Parses SGF text.
   *
   * @param sgf the SGF text; a leading byte order mark is ignored
   * @return the parsed collection, never empty
   * @throws SgfParseException if the text is not valid SGF
   * @throws SgfLimitExceededException if a limit of the options is exceeded
   */
  public SgfCollection parse(String sgf) {
    Objects.requireNonNull(sgf, "sgf");
    if (sgf.length() > options.maxInputSize()) {
      throw SgfLexer.limitError(
          sgf,
          "maxInputSize",
          options.maxInputSize(),
          0,
          "SGF input has " + sgf.length() + " characters, more than the allowed " + options.maxInputSize());
    }
    return new Run(sgf, options).parse();
  }

  /**
   * Parses SGF bytes, deciding the encoding as described in the class documentation of the
   * decoder: byte order mark, then the {@code CA} property, then UTF-8, then ISO-8859-1.
   *
   * @param bytes the raw content of an SGF file
   * @return the parsed collection
   * @throws SgfParseException if the content is not valid SGF
   * @throws SgfLimitExceededException if a limit of the options is exceeded
   */
  public SgfCollection parse(byte[] bytes) {
    Objects.requireNonNull(bytes, "bytes");
    requireWithinInputLimit(bytes);
    return parse(SgfDecoder.decode(bytes));
  }

  /**
   * Parses SGF bytes in a charset the caller already knows (from an HTTP header, a database column
   * or the source's documentation), skipping the detection done by {@link #parse(byte[])}. A
   * leading byte order mark is ignored.
   *
   * @param bytes the raw content of an SGF file
   * @param charset the charset to decode with
   * @return the parsed collection
   * @throws SgfParseException if the content is not valid SGF
   * @throws SgfLimitExceededException if a limit of the options is exceeded
   */
  public SgfCollection parse(byte[] bytes, Charset charset) {
    Objects.requireNonNull(bytes, "bytes");
    Objects.requireNonNull(charset, "charset");
    requireWithinInputLimit(bytes);
    return parse(new String(bytes, charset));
  }

  /**
   * Parses SGF from a stream. At most {@code maxInputSize + 1} bytes are read, so an endless
   * stream cannot exhaust memory. The stream is not closed.
   *
   * @param in the stream to read
   * @return the parsed collection
   * @throws IOException if reading fails
   * @throws SgfParseException if the content is not valid SGF
   * @throws SgfLimitExceededException if a limit of the options is exceeded
   */
  public SgfCollection parse(InputStream in) throws IOException {
    Objects.requireNonNull(in, "in");
    return parse(in.readNBytes(options.maxInputSize() + 1));
  }

  /**
   * Parses an SGF file.
   *
   * @param file the file to read
   * @return the parsed collection
   * @throws IOException if reading fails
   * @throws SgfParseException if the content is not valid SGF
   * @throws SgfLimitExceededException if a limit of the options is exceeded
   */
  public SgfCollection parse(Path file) throws IOException {
    try (InputStream in = Files.newInputStream(file)) {
      return parse(in);
    }
  }

  private void requireWithinInputLimit(byte[] bytes) {
    if (bytes.length > options.maxInputSize()) {
      throw SgfLexer.limitError(
          "",
          "maxInputSize",
          options.maxInputSize(),
          0,
          "SGF input has " + bytes.length + " bytes, more than the allowed " + options.maxInputSize());
    }
  }

  // ---------------------------------------------------------------------------------------------

  /** A game tree that has been opened with {@code (} and not yet closed. */
  private static final class Frame {
    /** Node the first node of this tree hangs from; {@code null} for a top-level game tree. */
    final SgfNode attachPoint;

    final int openOffset;
    /** Last node of this tree's sequence so far. */
    SgfNode tail;
    /** Set once a nested game tree started: no further nodes may follow. */
    boolean hasSubtrees;

    Frame(SgfNode attachPoint, int openOffset) {
      this.attachPoint = attachPoint;
      this.openOffset = openOffset;
    }
  }

  /** All the mutable state of one parse, kept out of the parser instance on purpose. */
  private static final class Run {
    private final String input;
    private final SgfParserOptions options;
    private final SgfLexer lexer;
    private final SgfCollection collection = new SgfCollection();
    private final ArrayDeque<Frame> stack = new ArrayDeque<>();
    private SgfNode currentRoot;
    private int nodeCount;

    Run(String input, SgfParserOptions options) {
      this.input = input;
      this.options = options;
      this.lexer = new SgfLexer(input, options.maxPropertyValueLength());
    }

    SgfCollection parse() {
      for (Token t = lexer.next(); t.type() != Type.EOF; t = lexer.next()) {
        switch (t.type()) {
          case OPEN -> open(t);
          case SEMICOLON -> node(t);
          case CLOSE -> close(t);
          case IDENTIFIER -> {
            Frame frame = stack.peek();
            if (options.lenient() && frame != null && frame.tail == null) {
              // Lenient: "(" straight followed by properties, the first ';' is missing.
              lexer.unread(t);
              node(t);
            } else {
              throw fail(
                  t.offset(),
                  "Property '" + t.text() + "' is outside a node",
                  stack.isEmpty()
                      ? "Expected '(' to start a game tree"
                      : "Expected ';' to start a node before its properties");
            }
          }
          case VALUE ->
              throw fail(
                  t.offset(),
                  "Unexpected property value",
                  "A value must follow a property identifier, as in B[pd]");
          default -> throw new IllegalStateException("Unhandled token " + t);
        }
      }
      if (!stack.isEmpty()) {
        Frame innermost = stack.peek();
        throw fail(
            input.length(),
            "Unexpected end of SGF",
            "Expected ')' to close the game tree opened at position " + innermost.openOffset
                + "; " + stack.size() + " game tree(s) still open");
      }
      if (collection.size() == 0) {
        throw fail(
            input.length(), "SGF input contains no game tree", "Expected '(' followed by ';' and a node");
      }
      return collection;
    }

    private void open(Token t) {
      SgfNode attachPoint = null;
      if (!stack.isEmpty()) {
        Frame parent = stack.peek();
        if (parent.tail == null) {
          throw fail(t.offset(), "Unexpected '('", "Expected ';' to start a node before a nested game tree");
        }
        parent.hasSubtrees = true;
        attachPoint = parent.tail;
      }
      if (stack.size() >= options.maxTreeDepth()) {
        throw SgfLexer.limitError(
            input,
            "maxTreeDepth",
            options.maxTreeDepth(),
            t.offset(),
            "Game trees nested deeper than " + options.maxTreeDepth() + " levels");
      }
      stack.push(new Frame(attachPoint, t.offset()));
    }

    private void node(Token t) {
      Frame frame = stack.peek();
      if (frame == null) {
        throw fail(t.offset(), "Unexpected ';' outside a game tree", "Expected '(' to start a game tree");
      }
      if (frame.hasSubtrees) {
        throw fail(
            t.offset(),
            "A node cannot follow a nested game tree",
            "Expected ')' or another '(' variation; nodes of a sequence must come before its variations");
      }
      if (++nodeCount > options.maxNodes()) {
        throw SgfLexer.limitError(
            input, "maxNodes", options.maxNodes(), t.offset(), "More than " + options.maxNodes() + " nodes");
      }
      SgfNode node = new SgfNode();
      if (frame.tail != null) {
        frame.tail.addChild(node);
      } else if (frame.attachPoint != null) {
        frame.attachPoint.addChild(node);
      } else {
        currentRoot = node;
      }
      frame.tail = node;
      properties(node);
    }

    private void properties(SgfNode node) {
      while (lexer.peek().type() == Type.IDENTIFIER) {
        Token id = lexer.next();
        String identifier = normalize(id);
        if (lexer.peek().type() != Type.VALUE) {
          Token unexpected = lexer.peek();
          if (unexpected.type() == Type.EOF) {
            throw fail(
                unexpected.offset(),
                "Unexpected end of SGF",
                "Expected a '[' value for property " + id.text());
          }
          throw fail(
              unexpected.offset(),
              "Property '" + id.text() + "' has no value",
              "Expected '[' after the identifier, as in " + id.text() + "[...]");
        }
        List<String> values = new ArrayList<>(1);
        while (lexer.peek().type() == Type.VALUE) {
          values.add(lexer.next().text());
        }
        if (identifier != null) {
          node.addValues(identifier, values);
        }
      }
    }

    /** Returns the identifier to use, or {@code null} if the property is to be ignored. */
    private String normalize(Token id) {
      String text = id.text();
      boolean hasLowercase = false;
      for (int i = 0; i < text.length(); i++) {
        if (text.charAt(i) >= 'a') {
          hasLowercase = true;
          break;
        }
      }
      if (!hasLowercase) {
        return text;
      }
      if (!options.lenient()) {
        throw fail(
            id.offset(),
            "Invalid property identifier '" + text + "'",
            "Property identifiers consist of uppercase letters A-Z only "
                + "(enable SgfParserOptions.lenient to accept FF[3]-style names such as 'White')");
      }
      StringBuilder upper = new StringBuilder();
      for (int i = 0; i < text.length(); i++) {
        char c = text.charAt(i);
        if (c < 'a') {
          upper.append(c);
        }
      }
      return upper.length() == 0 ? null : upper.toString();
    }

    private void close(Token t) {
      Frame frame = stack.poll();
      if (frame == null) {
        if (options.lenient()) {
          return; // lenient: a stray ')' after the end of a game is ignored
        }
        throw fail(t.offset(), "Unexpected ')'", "There is no open '(' to close");
      }
      if (frame.tail == null) {
        throw fail(
            t.offset(),
            "Empty game tree",
            "The tree opened at position " + frame.openOffset + " has no node; expected ';' after '('");
      }
      if (stack.isEmpty()) {
        collection.addGame(new SgfGameTree(currentRoot));
        currentRoot = null;
      }
    }

    private SgfParseException fail(int offset, String problem, String expectation) {
      return SgfLexer.error(input, offset, problem, expectation);
    }
  }
}
