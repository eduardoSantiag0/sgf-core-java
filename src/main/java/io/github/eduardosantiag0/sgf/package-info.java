/**
 * A dependency-free library to read, edit and write SGF (Smart Game Format, FF[4]) with a focus on
 * Go.
 *
 * <p>The pieces, from text to text:
 *
 * <ul>
 *   <li>{@link io.github.eduardosantiag0.sgf.parser.SgfParser} turns SGF text into a tree;
 *   <li>{@link io.github.eduardosantiag0.sgf.model} is that tree: collections, game trees, nodes
 *       and properties, with variations as first-class citizens;
 *   <li>{@link io.github.eduardosantiag0.sgf.serializer.SgfSerializer} writes the tree back;
 *   <li>{@link io.github.eduardosantiag0.sgf.go} adds Go meaning on top: coordinates, moves,
 *       positions and validation.
 * </ul>
 *
 * <p>It is a building block for editors, servers, bots, study tools, converters and anything else
 * that handles SGF. It knows SGF and, in one dedicated package, Go; it knows nothing about where
 * games come from or what is done with them afterwards.
 */
package io.github.eduardosantiag0.sgf;
