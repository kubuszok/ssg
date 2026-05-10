/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Main entry points for building KaTeX output trees.
 * Combines buildHTML and buildMathML into complete katex elements.
 *
 * Original source: katex src/buildTree.ts
 * Original author: Khan Academy and contributors
 * Original license: MIT
 *
 * upstream-commit: 90de9794
 *
 * Migration notes:
 *   Renames: buildTree -> BuildTree (object)
 *   Convention: default export -> named method
 */
package ssg
package katex
package build

import scala.collection.mutable.ArrayBuffer

import ssg.katex.parse.AnyParseNode
import ssg.katex.tree.DomSpan

object BuildTree {

  private def optionsFromSettings(settings: Settings): Options =
    new Options(
      style = if (settings.displayMode) Style.DISPLAY else Style.TEXT,
      maxSize = settings.maxSize,
      minRuleThickness = settings.minRuleThickness
    )

  private def displayWrap(node: DomSpan, settings: Settings): DomSpan =
    if (settings.displayMode) {
      val classes = ArrayBuffer("katex-display")
      if (settings.leqno) {
        classes += "leqno"
      }
      if (settings.fleqn) {
        classes += "fleqn"
      }
      BuildCommon.makeSpan(classes, ArrayBuffer[ssg.katex.tree.HtmlDomNode](node))
    } else {
      node
    }

  def buildTree(
    tree:       Array[AnyParseNode],
    expression: String,
    settings:   Settings
  ): DomSpan = {
    val options = optionsFromSettings(settings)
    if (settings.output == "mathml") {
      BuildMathML.buildMathML(tree, expression, options, settings.displayMode, true)
    } else {
      val katexNode: DomSpan = if (settings.output == "html") {
        val htmlNode = BuildHTML.buildHTML(tree, options)
        BuildCommon.makeSpan(ArrayBuffer("katex"), ArrayBuffer[ssg.katex.tree.HtmlDomNode](htmlNode))
      } else {
        val mathMLNode = BuildMathML.buildMathML(tree, expression, options, settings.displayMode, false)
        val htmlNode   = BuildHTML.buildHTML(tree, options)
        BuildCommon.makeSpan(ArrayBuffer("katex"), ArrayBuffer[ssg.katex.tree.HtmlDomNode](mathMLNode, htmlNode))
      }

      displayWrap(katexNode, settings)
    }
  }

  def buildHTMLTree(
    tree:       Array[AnyParseNode],
    expression: String,
    settings:   Settings
  ): DomSpan = {
    val options   = optionsFromSettings(settings)
    val htmlNode  = BuildHTML.buildHTML(tree, options)
    val katexNode = BuildCommon.makeSpan(ArrayBuffer("katex"), ArrayBuffer[ssg.katex.tree.HtmlDomNode](htmlNode))
    displayWrap(katexNode, settings)
  }
}
