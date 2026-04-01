/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/html/renderer/HeaderIdGenerator.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package html
package renderer

import ssg.md.ast.AnchorRefTarget
import ssg.md.ast.util.{ AnchorRefTargetBlockPreVisitor, AnchorRefTargetBlockVisitor }
import ssg.md.html.{ Disposable, HtmlRenderer }
import ssg.md.util.ast.{ Document, Node }
import ssg.md.util.data.DataHolder

import scala.collection.mutable
import scala.language.implicitConversions

class HeaderIdGenerator(options: Nullable[DataHolder]) extends HtmlIdGenerator, Disposable {

  private var headerBaseIds: mutable.HashMap[String, Int] = mutable.HashMap.empty
  var resolveDupes:          Boolean                      = HtmlRenderer.HEADER_ID_GENERATOR_RESOLVE_DUPES.get(options)
  var toDashChars:           String                       = HtmlRenderer.HEADER_ID_GENERATOR_TO_DASH_CHARS.get(options)
  var nonDashChars:          String                       = HtmlRenderer.HEADER_ID_GENERATOR_NON_DASH_CHARS.get(options)
  var noDupedDashes:         Boolean                      = HtmlRenderer.HEADER_ID_GENERATOR_NO_DUPED_DASHES.get(options)
  var nonAsciiToLowercase:   Boolean                      = HtmlRenderer.HEADER_ID_GENERATOR_NON_ASCII_TO_LOWERCASE.get(options)

  def this() = {
    this(Nullable.empty)
  }

  override def dispose(): Unit =
    headerBaseIds = mutable.HashMap.empty

  override def generateIds(document: Document): Unit =
    generateIds(document, Nullable.empty)

  override def generateIds(document: Document, preVisitor: Nullable[AnchorRefTargetBlockPreVisitor]): Unit = {
    headerBaseIds.clear()

    resolveDupes = HtmlRenderer.HEADER_ID_GENERATOR_RESOLVE_DUPES.get(document)
    toDashChars = HtmlRenderer.HEADER_ID_GENERATOR_TO_DASH_CHARS.get(document)
    nonDashChars = HtmlRenderer.HEADER_ID_GENERATOR_NON_DASH_CHARS.get(document)
    noDupedDashes = HtmlRenderer.HEADER_ID_GENERATOR_NO_DUPED_DASHES.get(document)
    nonAsciiToLowercase = HtmlRenderer.HEADER_ID_GENERATOR_NON_ASCII_TO_LOWERCASE.get(document)

    val visitor = new AnchorRefTargetBlockVisitor {
      override protected def preVisit(node: Node): Boolean =
        preVisitor.fold(true)(pv => pv.preVisit(node, this))

      override protected def visit(node: AnchorRefTarget): Unit =
        if (node.anchorRefId.isEmpty) {
          val text  = node.anchorRefText
          val refId = generateId(text)
          refId.foreach { id =>
            node.anchorRefId = id
          }
        }
    }
    visitor.visit(document)
  }

  private[renderer] def generateId(text: String): Nullable[String] =
    if (text.nonEmpty) {
      var baseRefId = HeaderIdGenerator.generateId(text, toDashChars, nonDashChars, noDupedDashes, nonAsciiToLowercase)

      if (resolveDupes) {
        if (headerBaseIds.contains(baseRefId)) {
          var index = headerBaseIds(baseRefId)
          index += 1
          headerBaseIds.put(baseRefId, index)
          baseRefId = baseRefId + "-" + index
        } else {
          headerBaseIds.put(baseRefId, 0)
        }
      }

      Nullable(baseRefId)
    } else {
      Nullable.empty
    }

  override def getId(node: Node): Nullable[String] =
    node match {
      case art: AnchorRefTarget => Nullable(art.anchorRefId).flatMap(id => if (id.isEmpty) Nullable.empty else Nullable(id))
      case _ => Nullable.empty
    }

  override def getId(text: CharSequence): Nullable[String] =
    generateId(text.toString)
}

object HeaderIdGenerator {

  def isAlphabetic(c: Char): Boolean =
    ((((1 << Character.UPPERCASE_LETTER) |
      (1 << Character.LOWERCASE_LETTER) |
      (1 << Character.TITLECASE_LETTER) |
      (1 << Character.MODIFIER_LETTER) |
      (1 << Character.OTHER_LETTER) |
      (1 << Character.LETTER_NUMBER)) >> Character.getType(c.toInt)) & 1) != 0

  def generateId(headerText: CharSequence, toDashChars: String, noDupedDashes: Boolean, nonAsciiToLowercase: Boolean): String =
    generateId(headerText, toDashChars, Nullable.empty[String], noDupedDashes, nonAsciiToLowercase)

  def generateId(headerText: CharSequence, toDashCharsIn: String, nonDashCharsIn: Nullable[String], noDupedDashes: Boolean, nonAsciiToLowercase: Boolean): String = {
    val iMax         = headerText.length
    val baseRefId    = new StringBuilder(iMax)
    val toDashChars  = if (toDashCharsIn == null) HtmlRenderer.HEADER_ID_GENERATOR_TO_DASH_CHARS.get(Nullable.empty) else toDashCharsIn // @nowarn - Java interop boundary for null DataHolder
    val nonDashChars = nonDashCharsIn.getOrElse(HtmlRenderer.HEADER_ID_GENERATOR_NON_DASH_CHARS.get(Nullable.empty))

    var i = 0
    while (i < iMax) {
      val c = headerText.charAt(i)
      if (isAlphabetic(c)) {
        if (!nonAsciiToLowercase && !(c >= 'A' && c <= 'Z')) {
          baseRefId.append(c)
        } else {
          baseRefId.append(Character.toLowerCase(c))
        }
      } else if (Character.isDigit(c)) {
        baseRefId.append(c)
      } else if (nonDashChars.indexOf(c) != -1) {
        baseRefId.append(c)
      } else if (
        toDashChars.indexOf(c) != -1 && (!noDupedDashes
          || ((c == '-' && baseRefId.isEmpty)
            || baseRefId.nonEmpty && baseRefId.charAt(baseRefId.length - 1) != '-'))
      ) {
        baseRefId.append('-')
      }
      i += 1
    }
    baseRefId.toString
  }

  class Factory extends HeaderIdGeneratorFactory {
    override def create(context: LinkResolverContext): HtmlIdGenerator =
      new HeaderIdGenerator()

    override def create(): HtmlIdGenerator =
      new HeaderIdGenerator()
  }
}
