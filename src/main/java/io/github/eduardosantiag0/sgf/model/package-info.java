/**
 * The in-memory representation of SGF: an {@link io.github.eduardosantiag0.sgf.model.SgfCollection}
 * of {@link io.github.eduardosantiag0.sgf.model.SgfGameTree games}, each a tree of
 * {@link io.github.eduardosantiag0.sgf.model.SgfNode nodes} carrying arbitrary
 * {@link io.github.eduardosantiag0.sgf.model.SgfProperty properties}.
 *
 * <p>Game-agnostic on purpose: apart from the move properties {@code B} and {@code W}, which the
 * SGF standard defines for every game, nothing here is specific to Go.
 */
package io.github.eduardosantiag0.sgf.model;
