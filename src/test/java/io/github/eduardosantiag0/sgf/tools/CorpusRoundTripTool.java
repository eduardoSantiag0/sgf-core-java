package io.github.eduardosantiag0.sgf.tools;

import io.github.eduardosantiag0.sgf.model.SgfCollection;
import io.github.eduardosantiag0.sgf.model.SgfGameTree;
import io.github.eduardosantiag0.sgf.parser.SgfParseException;
import io.github.eduardosantiag0.sgf.parser.SgfParser;
import io.github.eduardosantiag0.sgf.parser.SgfParserOptions;
import io.github.eduardosantiag0.sgf.serializer.SgfSerializer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Manual, large-scale check against a local directory of real {@code .sgf} files: parses every
 * file (retrying in lenient mode on a strict failure), serializes the result in both layouts,
 * reparses and checks the content survived. Reports totals and every failure found.
 *
 * <p>Deliberately named without a {@code Test} suffix so {@code mvn test} compiles but never runs
 * it; it is meant to be pointed at a large corpus by hand, which can take minutes and needs files
 * this repository does not ship. See the "Strict by default, lenient on request" section of the
 * README for how this was last used.
 *
 * <p>Usage (from the project root, after {@code mvn -q test-compile}):
 *
 * <pre>
 * java -cp "target/classes;target/test-classes" io.github.eduardosantiag0.sgf.tools.CorpusRoundTripTool [directory] [failures-file]
 * </pre>
 *
 * (use {@code :} instead of {@code ;} between classpath entries on Linux/macOS). {@code directory}
 * defaults to {@code games} (next to the project root) when omitted. Every {@code .sgf} file under
 * it, recursively, is checked; the optional {@code failures-file} receives one line per failure, in
 * addition to the first ones printed to stdout.
 */
public final class CorpusRoundTripTool {

  private static final SgfParser STRICT = new SgfParser();
  private static final SgfParser LENIENT = new SgfParser(SgfParserOptions.defaults().withLenient(true));
  private static final int PROGRESS_EVERY = 2_000;
  private static final int MAX_FAILURES_PRINTED = 50;

  private long files;
  private long strictOk;
  private long lenientOnlyOk;
  private long parseFailed;
  private long roundTripFailed;
  private long totalNodes;
  private long totalBytes;
  private final List<String> failures = new ArrayList<>();

  public static void main(String[] args) throws IOException {

      Path root = args.length >= 1
              ? Path.of(args[0])
              : Path.of(".");

    if (!Files.isDirectory(root)) {
      System.err.println(root + " is not a directory");
      System.exit(2);
      return;
    }

    Path failuresFile = args.length >= 2 ? Path.of(args[1]) : null;
    new CorpusRoundTripTool().run(root, failuresFile);
  }

  private void run(Path root, Path failuresFile) throws IOException {
    long start = System.nanoTime();
    List<Path> sgfFiles;
    try (Stream<Path> walk = Files.walk(root)) {
      sgfFiles =
          walk.filter(p -> Files.isRegularFile(p) && hasSgfExtension(p))
              .sorted(Comparator.naturalOrder())
              .toList();
    }
    System.out.println("Found " + sgfFiles.size() + " .sgf file(s) under " + root);

    for (Path file : sgfFiles) {
      check(file);
      if (files % PROGRESS_EVERY == 0) {
        printProgress(start);
      }
    }
    printProgress(start);
    printSummary(start);

    if (failuresFile != null) {
      Files.write(failuresFile, failures, StandardCharsets.UTF_8);
      System.out.println("Wrote " + failures.size() + " failure line(s) to " + failuresFile);
    }
  }

  private static boolean hasSgfExtension(Path p) {
    return p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".sgf");
  }

  private void check(Path file) {
    files++;
    byte[] bytes;
    try {
      bytes = Files.readAllBytes(file);
    } catch (IOException e) {
      recordFailure(file, "read failed: " + e);
      return;
    }
    totalBytes += bytes.length;

    SgfCollection collection;
    boolean lenient = false;
    try {
      collection = STRICT.parse(bytes);
    } catch (SgfParseException strictError) {
      try {
        collection = LENIENT.parse(bytes);
        lenient = true;
      } catch (RuntimeException lenientError) {
        parseFailed++;
        recordFailure(
            file,
            "parse failed (strict: " + strictError.getMessage() + "; lenient: " + lenientError.getMessage() + ")");
        return;
      }
    } catch (RuntimeException other) {
      parseFailed++;
      recordFailure(file, "parse failed: " + other);
      return;
    }

    if (lenient) {
      lenientOnlyOk++;
    } else {
      strictOk++;
    }
    for (SgfGameTree game : collection.games()) {
      totalNodes += game.nodes().size();
    }

    checkRoundTrip(file, collection, SgfSerializer.pretty(), "pretty");
    checkRoundTrip(file, collection, SgfSerializer.compact(), "compact");
  }

  private void checkRoundTrip(Path file, SgfCollection original, SgfSerializer serializer, String layout) {
    try {
      SgfCollection reparsed = STRICT.parse(serializer.serialize(original));
      if (!reparsed.sameContentAs(original)) {
        roundTripFailed++;
        recordFailure(file, layout + " round-trip changed content");
      }
    } catch (RuntimeException e) {
      roundTripFailed++;
      recordFailure(file, layout + " round-trip threw: " + e);
    }
  }

  private void recordFailure(Path file, String reason) {
    String line = file + ": " + reason;
    failures.add(line);
    if (failures.size() <= MAX_FAILURES_PRINTED) {
      System.out.println("FAIL " + line);
    }
  }

  private void printProgress(long startNanos) {
    double seconds = (System.nanoTime() - startNanos) / 1_000_000_000.0;
    System.out.printf(
        "... %d files (%d strict, %d lenient-only, %d failed), %d nodes, %.1fs%n",
        files, strictOk, lenientOnlyOk, parseFailed, totalNodes, seconds);
  }

  private void printSummary(long startNanos) {
    double seconds = (System.nanoTime() - startNanos) / 1_000_000_000.0;
    System.out.println();
    System.out.println("==== corpus round-trip summary ====");
    System.out.println("files scanned       : " + files);
    System.out.println("parsed strict        : " + strictOk);
    System.out.println("parsed lenient-only  : " + lenientOnlyOk);
    System.out.println("failed to parse      : " + parseFailed);
    System.out.println("round-trip failures  : " + roundTripFailed);
    System.out.println("total nodes          : " + totalNodes);
    System.out.println("total bytes          : " + totalBytes);
    System.out.printf("elapsed              : %.1fs%n", seconds);
    if (seconds > 0) {
      System.out.printf("throughput           : %.0f files/s, %.0f nodes/s%n", files / seconds, totalNodes / seconds);
    }
  }
}
