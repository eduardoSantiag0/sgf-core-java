# sgf-core-java

A small, dependency-free Java library to **read, edit and write SGF** (Smart Game Format, FF[4]),
with first-class support for **variations** and helpers for **Go**.

```xml
<dependency>
  <groupId>io.github.eduardosantiag0</groupId>
  <artifactId>sgf-core-java</artifactId>
  <version>0.1.0</version>
</dependency>
```

Java 17+. No runtime dependencies (not even a logging framework).

## Why use it

If your program touches SGF files, this is the layer you would otherwise write yourself:

* an **editor or viewer** that loads a game, lets the user add comments and variations, and saves it;
* a **server or bot** that receives SGF from users, validates it, stores it and hands it back;
* a **study or teaching tool** that annotates games, extracts problems, or merges collections;
* a **converter or importer** between SGF and another format;
* a **front end for a Go-playing program** that needs the position at a given move;
* any script that needs to answer "who played what, and where does the tree branch?".

What you get:

| | |
|---|---|
| **Variations are first-class** | The game is a tree. Add a branch to any node with one call; the main line is the first child. |
| **Nothing is lost** | Properties the library does not know are kept, in order, with all their values. `parse -> edit -> serialize` never drops data you did not touch. |
| **Safe on untrusted input** | Configurable limits, no recursion anywhere (a 200 000-move game and deeply nested variations are tested), and a value limit enforced while reading. |
| **Errors you can act on** | `Unexpected end of SGF at position 129 (line 4, column 1). Expected ')' ...`, with position, line and column also available as getters. |
| **Thread-safe parser** | `SgfParser` and `SgfSerializer` are stateless; share one instance. |
| **Go helpers, kept apart** | Coordinates (`pd` <-> `Q16`), moves, positions, validation live in their own package. The core model knows nothing about Go. |

## Quick start

```java
SgfParser parser = new SgfParser();                        // stateless: share it
SgfCollection collection = parser.parse(sgfText);          // also: byte[], InputStream, Path
SgfGameTree game = collection.firstGame();

SgfNode move = game.getMove(3);                            // the 3rd move actually made
move.addComment("Black has a stronger option here.");

// Offer a different line INSTEAD OF that move, from the position before it:
SgfNode first = move.addAlternative(SgfMove.toNodes(List.of(
    SgfMove.black("pq"), SgfMove.white("qc"), SgfMove.black("od"))));
first.addComment("Try this instead");

String result = SgfSerializer.pretty().serialize(collection);
```

## Recipes

**Read the game information**

```java
GoGame go = GoGame.of(game);
go.blackName();   // Optional<String>: PB
go.boardSize();   // 19 when SZ is absent
go.komi();        // Optional<Double>: KM
go.result();      // Optional<String>: RE
game.root().value("XX");   // any property, known or not
```

**Walk the tree, variations included**

```java
for (SgfNode node : game.mainLine()) { ... }       // the main line
for (SgfNode node : game.nodes()) { ... }          // every node, depth-first, pre-order
node.children();                                   // first = main line, the rest = variations
node.parent();  node.next();  node.isMove();
```

**Edit**

```java
node.setProperty("C", "a comment");        // set (replaces)
node.addValue("AB", "dd");                 // append a value to a multi-value property
node.addComment("more");                   // appends after a blank line, never overwrites
node.addVariation(line);                   // a continuation option AFTER this node
node.addAlternative(line);                 // a sibling: an option INSTEAD OF this node's move
node.removeFromParent();
SgfGameTree backup = game.copy();          // deep copy, e.g. to edit without touching the original
```

**Coordinates**

```java
SgfCoordinate.parse("pd").toHuman(19);          // "Q16"
SgfCoordinate.fromHuman("D4", 19).toSgf();      // "dp"
SgfMove.black("pd").toHuman(19);                // "Q16"
```

**The position at any point**

```java
GoPosition p = go.positionBefore(73);      // or positionBefore(node) / positionAfter(node)
p.boardSize();  p.setupStones();  p.moves();   // handicap/setup stones, then the moves in order
p.canonical();   // "size=19;setup=B:dd,B:pp;moves=B:pd,W:dd,..." - a stable key for the position
```

**Check a game that parsed fine**

```java
for (SgfIssue issue : go.validate()) {
  System.out.println(issue);   // "ERROR at /0.0.1: Move B[zz] is outside a 19x19 board"
}
```

**Handle bad input**

```java
try {
  parser.parse(untrusted);
} catch (SgfLimitExceededException e) {   // too big / too deep: e.limitName(), e.limit()
  ...
} catch (SgfParseException e) {           // malformed: e.line(), e.column(), e.position()
  ...
}
```

## The model

| SGF | Here |
|---|---|
| Collection | `SgfCollection` (one or more games) |
| GameTree + Sequence | `SgfGameTree` (a root `SgfNode`) |
| Node | `SgfNode`: ordered properties + children |
| Property / PropValue | `SgfProperty`: identifier + one or more values |

A node's **first child is the main line, further children are variations.** This is not the shape of
the grammar (`GameTree = "(" Sequence GameTree* ")"`), and that is deliberate: a variation can hang
from *any* node, including one in the middle of what a file writes as a single sequence, so
`addVariation` never has to split and re-join sequences. Parser and serializer translate between
the grammar and the tree; `(;A(;B))` and `(;A;B)` are the same tree.

Values are stored **unescaped**. Escaping (`\]`, `\\`, soft line breaks, mixed line endings) is the
job of the parser and serializer only. The output is normalised, not byte-identical: whitespace and
layout are not preserved, content is.

### Two ways to add a branch

```java
node.addVariation(line);     // a continuation option AFTER this node
node.addAlternative(line);   // an alternative INSTEAD OF this node's move (a sibling)
```

An editor's "add variation here" on a move usually means `addAlternative`. Both take fresh nodes;
`SgfMove.toNodes(...)` builds them from moves.

## Addressing nodes

Three ways to refer to a node, and what each is good for:

| Identifier | Stable across serialize/parse | Unique in the tree | Verdict |
|---|---|---|---|
| Move number (`getMove(n)`) | yes | only on the main line | What people mean by "move 73". Not defined inside variations. |
| Object identity / random id | **no** | yes | Fine inside one in-memory session; useless in storage or across processes. |
| **Path** (`node.path()` -> `/0.0.1`) | **yes** | **yes** | Deterministic child-index address from the root. Use it for bookmarks, comments-by-location, indexes. |

A path stays valid while variations are only *appended*. Removing or reordering siblings changes
later siblings' paths. `game.find(path)` resolves one.

## Robustness (untrusted input)

* **No recursion anywhere** (parse, serialize, copy, compare, paths). A 200 000-move game and 900
  levels of nested variations are covered by tests. Recursive implementations typically overflow
  the stack at a few thousand nodes.
* **Limits** in `SgfParserOptions`: `maxInputSize` (10 MiB), `maxTreeDepth` (1 000), `maxNodes`
  (1 000 000), `maxPropertyValueLength` (1 MiB). The value limit is enforced *while reading*, so an
  unterminated 1 GB value cannot exhaust memory. Exceeding one raises `SgfLimitExceededException`
  (map it to "too large"), distinct from `SgfParseException` ("malformed").
* **Thread safety**: `SgfParser`, `SgfSerializer` and `SgfParserOptions` are stateless/immutable;
  share one instance. Trees are mutable and **not** thread-safe: give each thread that edits its own
  tree (see `copy()`).

### Strict by default, lenient on request

The default rejects invalid SGF. `SgfParserOptions.defaults().withLenient(true)` tolerates three
deviations found in files from real servers: FF[3]-style lowercase identifiers (`White[dd]`), a
first node written without its `;` (seen in Nihon Ki-in exports), and a stray `)`. It still rejects
real structural damage (missing brackets or parentheses, empty trees).

As a one-off check (not part of this repository's test suite) the parser was run over 90 320 real
SGF files from public game collections, about 19 million nodes: all parse (two only in lenient mode,
for exactly the reasons above) and every one round-trips.

### Encoding

`parse(byte[])` (and streams and files) decides in this order: byte order mark, the file's own
`CA[...]` property, valid UTF-8, then ISO-8859-1 (which never fails). There is **no statistical
guessing** of legacy CJK encodings: a Shift-JIS/GBK file without `CA` will decode to the wrong
characters (structure and moves are unaffected). If you know the charset, use
`parse(bytes, charset)`.

## Go support (`...sgf.go`)

Applies to games of type `GM[1]`. Everything here is optional: the core model and parser work for
any SGF game type.

* `SgfCoordinate`: `pd` <-> `Q16`, the only place coordinate conversion lives. SGF counts rows from
  the top; human/GTP counts from the bottom and skips the letter `I`. Also expands `aa:cc` rectangles.
* `SgfMove`: colour + point or pass (`B[]`, and the legacy `B[tt]` on boards up to 19).
* `GoGame`: read-only view of a game tree: board size, players, ranks, komi, rules, result,
  main-line moves, positions.
* `GoPosition`: board size + setup stones + moves. **A description, not a simulation**: there is no
  capture, ko or legality logic; replay it with whatever rules implementation you use. Setup
  properties *after* the root cannot be expressed this way; `hasMidGameSetup()` tells you.
* `GoNodes`: read/write point lists (`AB`, `TR`, ...) and labels (`LB`).
* `GoGame.validate()`: reports SGF that parses fine but is odd as a Go game (move off the board,
  `B` and `W` in one node, ...) instead of failing to parse it.

## What it is not

Not a rules engine, a board renderer, a game database or a player. It reads, models, edits and
writes SGF, and describes Go positions; what you build on that is up to you.

## Building

```
mvn verify            # compile and run the test suite
```

Maintainers: see [RELEASING.md](RELEASING.md).

## Licence

Apache License 2.0.
