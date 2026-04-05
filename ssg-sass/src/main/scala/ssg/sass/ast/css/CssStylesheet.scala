/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/ast/css/stylesheet.dart, lib/src/ast/css/modifiable/stylesheet.dart
 * Original: Copyright (c) 2016, 2019 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: stylesheet.dart + modifiable/stylesheet.dart -> CssStylesheet.scala
 *   Convention: Dart class + implements -> Scala trait + concrete impl
 *   Idiom: CssStylesheet.empty uses SourceFile with empty text
 */
package ssg
package sass
package ast
package css

import ssg.sass.Nullable
import ssg.sass.util.{FileSpan, SourceFile}
import ssg.sass.visitor.CssVisitor

/** A plain CSS stylesheet.
  *
  * This is the root plain CSS node. It contains top-level statements.
  */
trait CssStylesheet extends CssParentNode

object CssStylesheet {

  /** Creates an unmodifiable stylesheet containing [children]. */
  def apply(childNodes: Iterable[CssNode], span: FileSpan): CssStylesheet =
    new UnmodifiableCssStylesheet(childNodes, span)

  /** Creates an empty stylesheet with the given source URL. */
  def empty(url: Nullable[String] = Nullable.empty): CssStylesheet = {
    val file = SourceFile(url, "")
    apply(Nil, file.span(0, 0))
  }

  /** Concrete unmodifiable implementation of CssStylesheet. */
  private final class UnmodifiableCssStylesheet(
    childNodes: Iterable[CssNode],
    val span: FileSpan
  ) extends CssNode with CssStylesheet {

    def parent: Nullable[CssParentNode] = Nullable.empty

    val children: List[CssNode] = childNodes.toList

    def isGroupEnd: Boolean = false

    def isChildless: Boolean = false

    def accept[T](visitor: CssVisitor[T]): T =
      visitor.visitCssStylesheet(this)
  }
}

/** A modifiable version of CssStylesheet for use in the evaluation step. */
final class ModifiableCssStylesheet(val span: FileSpan)
    extends ModifiableCssParentNode with CssStylesheet {

  def accept[T](visitor: CssVisitor[T]): T =
    visitor.visitCssStylesheet(this)

  def equalsIgnoringChildren(other: ModifiableCssNode): Boolean =
    other.isInstanceOf[ModifiableCssStylesheet]

  def copyWithoutChildren(): ModifiableCssStylesheet =
    ModifiableCssStylesheet(span)
}
