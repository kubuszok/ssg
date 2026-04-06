/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/visitor/serialize.dart (~1300 lines)
 * Original: Copyright (c) 2016 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: serialize.dart -> SerializeVisitor.scala
 *   Convention: Phase 11 skeleton — public API surface only.
 */
package ssg
package sass
package visitor

import ssg.sass.ast.css.CssStylesheet

/** Result of serializing a CSS AST: the CSS text plus an optional source map. */
final case class SerializeResult(css: String, sourceMap: Option[String])

/** A visitor that converts a CSS AST into CSS text.
  *
  * Skeleton — exposes the public entry point only.
  */
final class SerializeVisitor(val style: String = "expanded", val inspect: Boolean = false) {

  def serialize(node: CssStylesheet): SerializeResult =
    throw new UnsupportedOperationException("Phase 11 stub: SerializeVisitor.serialize")
}

object SerializeVisitor {

  /** Convenience entry point: serialize a [[CssStylesheet]] using default options. */
  def serialize(node: CssStylesheet): SerializeResult = new SerializeVisitor().serialize(node)
}
