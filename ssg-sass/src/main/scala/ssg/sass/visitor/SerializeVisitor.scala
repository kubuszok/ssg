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
 *   Idiom: Minimum viable implementation. Supports expanded (default) and
 *     compressed output styles. Emits stylesheets, style rules, declarations,
 *     comments, at-rules, media rules, supports rules, imports, keyframe blocks.
 *   Source map generation is NOT YET IMPLEMENTED.
 */
package ssg
package sass
package visitor

import ssg.sass.ast.css.{
  CssAtRule,
  CssComment,
  CssDeclaration,
  CssImport,
  CssKeyframeBlock,
  CssMediaRule,
  CssNode,
  CssStyleRule,
  CssStylesheet,
  CssSupportsRule
}

/** Output style for serialization: "expanded" (default, multi-line) or
  * "compressed" (single-line, no whitespace).
  */
object OutputStyle {
  val Expanded: String = "expanded"
  val Compressed: String = "compressed"
}

/** Result of serializing a CSS AST: the CSS text plus an optional source map. */
final case class SerializeResult(css: String, sourceMap: Option[String] = None)

/** A visitor that converts a CSS AST into CSS text. */
final class SerializeVisitor(
  val style: String = OutputStyle.Expanded,
  val inspect: Boolean = false
) extends CssVisitor[Unit] {

  private val buffer = new StringBuilder()
  private var indentLevel: Int = 0

  private def isCompressed: Boolean = style == OutputStyle.Compressed

  /** Serialize the given stylesheet to CSS text. */
  def serialize(node: CssStylesheet): SerializeResult = {
    buffer.clear()
    indentLevel = 0
    visitCssStylesheet(node)
    SerializeResult(buffer.toString(), sourceMap = None)
  }

  // ---------------------------------------------------------------------------
  // Formatting helpers
  // ---------------------------------------------------------------------------

  private def writeIndent(): Unit = {
    if (!isCompressed) {
      var i = 0
      while (i < indentLevel) {
        buffer.append("  ")
        i += 1
      }
    }
  }

  private def writeLine(): Unit = {
    if (!isCompressed) buffer.append('\n')
  }

  private def writeSpace(): Unit = {
    if (!isCompressed) buffer.append(' ')
  }

  private def writeChildren(children: List[CssNode]): Unit = {
    buffer.append('{')
    writeLine()
    indentLevel += 1
    var first = true
    for (child <- children) {
      if (!first && !isCompressed) writeLine()
      first = false
      writeIndent()
      child.accept(this)
    }
    indentLevel -= 1
    writeLine()
    writeIndent()
    buffer.append('}')
  }

  // ---------------------------------------------------------------------------
  // Visitor methods
  // ---------------------------------------------------------------------------

  override def visitCssStylesheet(node: CssStylesheet): Unit = {
    var first = true
    for (child <- node.children) {
      if (!first) writeLine()
      first = false
      child.accept(this)
    }
    if (!isCompressed && node.children.nonEmpty) buffer.append('\n')
  }

  override def visitCssStyleRule(node: CssStyleRule): Unit = {
    // Selector is stored as Any (placeholder until selector AST is wired)
    buffer.append(node.selector.toString)
    writeSpace()
    writeChildren(node.children)
  }

  override def visitCssDeclaration(node: CssDeclaration): Unit = {
    buffer.append(node.name.value)
    buffer.append(':')
    writeSpace()
    buffer.append(node.value.value.toCssString())
    buffer.append(';')
  }

  override def visitCssComment(node: CssComment): Unit = {
    // In compressed mode, only preserve /*! comments
    if (isCompressed && !node.isPreserved) return
    buffer.append(node.text)
  }

  override def visitCssAtRule(node: CssAtRule): Unit = {
    buffer.append('@')
    buffer.append(node.name.value)
    node.value.foreach { v =>
      buffer.append(' ')
      buffer.append(v.value)
    }
    if (node.isChildless) {
      buffer.append(';')
    } else {
      writeSpace()
      writeChildren(node.children)
    }
  }

  override def visitCssMediaRule(node: CssMediaRule): Unit = {
    buffer.append("@media ")
    buffer.append(node.queries.mkString(", "))
    writeSpace()
    writeChildren(node.children)
  }

  override def visitCssSupportsRule(node: CssSupportsRule): Unit = {
    buffer.append("@supports ")
    buffer.append(node.condition.value)
    writeSpace()
    writeChildren(node.children)
  }

  override def visitCssImport(node: CssImport): Unit = {
    buffer.append("@import ")
    buffer.append(node.url.value)
    node.modifiers.foreach { m =>
      buffer.append(' ')
      buffer.append(m.value)
    }
    buffer.append(';')
  }

  override def visitCssKeyframeBlock(node: CssKeyframeBlock): Unit = {
    buffer.append(node.selector.value.mkString(", "))
    writeSpace()
    writeChildren(node.children)
  }
}

object SerializeVisitor {

  /** Convenience entry point: serialize a [[CssStylesheet]] using default options. */
  def serialize(node: CssStylesheet): SerializeResult =
    new SerializeVisitor().serialize(node)

  /** Serialize compressed (minified). */
  def serializeCompressed(node: CssStylesheet): SerializeResult =
    new SerializeVisitor(style = OutputStyle.Compressed).serialize(node)
}
