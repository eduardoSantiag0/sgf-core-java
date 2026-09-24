/**
 * Go-specific meaning on top of the generic model: {@link
 * io.github.eduardosantiag0.sgf.go.SgfCoordinate coordinates} (SGF and human/GTP forms), {@link
 * io.github.eduardosantiag0.sgf.go.SgfMove moves}, a read-only {@link
 * io.github.eduardosantiag0.sgf.go.GoGame game view} that describes the position at any point as a
 * {@link io.github.eduardosantiag0.sgf.go.GoPosition}, and validation. It applies to games of type
 * {@code GM[1]}.
 *
 * <p>There is no rules implementation here: capture, ko and legality are deliberately left to
 * whoever replays a position.
 */
package io.github.eduardosantiag0.sgf.go;
