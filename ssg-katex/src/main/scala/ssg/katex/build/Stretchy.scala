/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * This file provides support to buildMathML.js and buildHTML.js
 * for stretchy wide elements rendered from SVG files
 * and other CSS trickery.
 *
 * Original source: katex src/stretchy.ts
 * Original author: Khan Academy and contributors
 * Original license: MIT
 *
 * upstream-commit: 90de9794
 *
 * Migration notes:
 *   Renames: stretchy -> Stretchy (object)
 *   Convention: Record<string,string> -> Map[String,String]
 *   Idiom: nested function buildSvgSpan_ -> private method
 */
package ssg
package katex
package build

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.LinkedHashMap
import scala.util.boundary
import scala.util.boundary.break

import ssg.commons.Nullable
import ssg.katex.data.Units
import ssg.katex.parse.AnyParseNode
import ssg.katex.tree.{
  HtmlDomNode,
  LineNode,
  MathDomNode,
  MathNode,
  PathNode,
  SvgChildNode,
  SvgNode,
  TextNode
}

object Stretchy {

  private val stretchyCodePoint: Map[String, String] = Map(
    "widehat" -> "^",
    "widecheck" -> "ˇ",
    "widetilde" -> "~",
    "utilde" -> "~",
    "overleftarrow" -> "←",
    "underleftarrow" -> "←",
    "xleftarrow" -> "←",
    "overrightarrow" -> "→",
    "underrightarrow" -> "→",
    "xrightarrow" -> "→",
    "underbrace" -> "⏟",
    "overbrace" -> "⏞",
    "underbracket" -> "⎵",
    "overbracket" -> "⎴",
    "overgroup" -> "⏠",
    "undergroup" -> "⏡",
    "overleftrightarrow" -> "↔",
    "underleftrightarrow" -> "↔",
    "xleftrightarrow" -> "↔",
    "Overrightarrow" -> "⇒",
    "xRightarrow" -> "⇒",
    "overleftharpoon" -> "↼",
    "xleftharpoonup" -> "↼",
    "overrightharpoon" -> "⇀",
    "xrightharpoonup" -> "⇀",
    "xLeftarrow" -> "⇐",
    "xLeftrightarrow" -> "⇔",
    "xhookleftarrow" -> "↩",
    "xhookrightarrow" -> "↪",
    "xmapsto" -> "↦",
    "xrightharpoondown" -> "⇁",
    "xleftharpoondown" -> "↽",
    "xrightleftharpoons" -> "⇌",
    "xleftrightharpoons" -> "⇋",
    "xtwoheadleftarrow" -> "↞",
    "xtwoheadrightarrow" -> "↠",
    "xlongequal" -> "=",
    "xtofrom" -> "⇄",
    "xrightleftarrows" -> "⇄",
    "xrightequilibrium" -> "⇌",  // Not a perfect match.
    "xleftequilibrium" -> "⇋",   // None better available.
    "\\cdrightarrow" -> "→",
    "\\cdleftarrow" -> "←",
    "\\cdlongequal" -> "="
  )

  def stretchyMathML(label: String): MathNode = {
    val node = new MathNode(
      "mo",
      ArrayBuffer[MathDomNode](new TextNode(stretchyCodePoint(label.stripPrefix("\\"))))
    )
    node.setAttribute("stretchy", "true")
    node
  }

  // Many of the KaTeX SVG images have been adapted from glyphs in KaTeX fonts.
  // Copyright (c) 2009-2010, Design Science, Inc. (<www.mathjax.org>)
  // Copyright (c) 2014-2017 Khan Academy (<www.khanacademy.org>)
  // Licensed under the SIL Open Font License, Version 1.1.
  // See \nhttp://scripts.sil.org/OFL

  // Very Long SVGs
  //    Many of the KaTeX stretchy wide elements use a long SVG image and an
  //    overflow: hidden tactic to achieve a stretchy image while avoiding
  //    distortion of arrowheads or brace corners.

  //    The SVG typically contains a very long (400 em) arrow.

  //    The SVG is in a container span that has overflow: hidden, so the span
  //    acts like a window that exposes only part of the  SVG.

  //    The SVG always has a longer, thinner aspect ratio than the container span.
  //    After the SVG fills 100% of the height of the container span,
  //    there is a long arrow shaft left over. That left-over shaft is not shown.
  //    Instead, it is sliced off because the span's CSS has overflow: hidden.

  //    Thus, the reader sees an arrow that matches the subject matter width
  //    without distortion.

  //    Some functions, such as \cancel, need to vary their aspect ratio. These
  //    functions do not get the overflow SVG treatment.

  // In the katexImagesData object just below, the dimensions all
  // correspond to path geometry inside the relevant SVG.
  // For example, \overrightarrow uses the same arrowhead as glyph U+2192
  // from the KaTeX Main font. The scaling factor is 1000.
  // That is, inside the font, that arrowhead is 522 units tall, which
  // corresponds to 0.522 em inside the document.

  // Each entry: (paths, minWidth, height, optionalAlign)
  // For single-path entries, the 4th element is the alignment.
  // For multi-path entries, the 4th element is absent.
  final case class KatexImageData(
      paths: Array[String],
      minWidth: Double,
      height: Int,
      align: Nullable[String] = Nullable.Null
  )

  private val katexImagesData: Map[String, KatexImageData] = Map(
    //   path(s), minWidth, height, align
    "overrightarrow" -> KatexImageData(Array("rightarrow"), 0.888, 522, Nullable("xMaxYMin")),
    "overleftarrow" -> KatexImageData(Array("leftarrow"), 0.888, 522, Nullable("xMinYMin")),
    "underrightarrow" -> KatexImageData(Array("rightarrow"), 0.888, 522, Nullable("xMaxYMin")),
    "underleftarrow" -> KatexImageData(Array("leftarrow"), 0.888, 522, Nullable("xMinYMin")),
    "xrightarrow" -> KatexImageData(Array("rightarrow"), 1.469, 522, Nullable("xMaxYMin")),
    "\\cdrightarrow" -> KatexImageData(Array("rightarrow"), 3.0, 522, Nullable("xMaxYMin")),
    "xleftarrow" -> KatexImageData(Array("leftarrow"), 1.469, 522, Nullable("xMinYMin")),
    "\\cdleftarrow" -> KatexImageData(Array("leftarrow"), 3.0, 522, Nullable("xMinYMin")),
    "Overrightarrow" -> KatexImageData(Array("doublerightarrow"), 0.888, 560, Nullable("xMaxYMin")),
    "xRightarrow" -> KatexImageData(Array("doublerightarrow"), 1.526, 560, Nullable("xMaxYMin")),
    "xLeftarrow" -> KatexImageData(Array("doubleleftarrow"), 1.526, 560, Nullable("xMinYMin")),
    "overleftharpoon" -> KatexImageData(Array("leftharpoon"), 0.888, 522, Nullable("xMinYMin")),
    "xleftharpoonup" -> KatexImageData(Array("leftharpoon"), 0.888, 522, Nullable("xMinYMin")),
    "xleftharpoondown" -> KatexImageData(Array("leftharpoondown"), 0.888, 522, Nullable("xMinYMin")),
    "overrightharpoon" -> KatexImageData(Array("rightharpoon"), 0.888, 522, Nullable("xMaxYMin")),
    "xrightharpoonup" -> KatexImageData(Array("rightharpoon"), 0.888, 522, Nullable("xMaxYMin")),
    "xrightharpoondown" -> KatexImageData(Array("rightharpoondown"), 0.888, 522, Nullable("xMaxYMin")),
    "xlongequal" -> KatexImageData(Array("longequal"), 0.888, 334, Nullable("xMinYMin")),
    "\\cdlongequal" -> KatexImageData(Array("longequal"), 3.0, 334, Nullable("xMinYMin")),
    "xtwoheadleftarrow" -> KatexImageData(Array("twoheadleftarrow"), 0.888, 334, Nullable("xMinYMin")),
    "xtwoheadrightarrow" -> KatexImageData(Array("twoheadrightarrow"), 0.888, 334, Nullable("xMaxYMin")),

    "overleftrightarrow" -> KatexImageData(Array("leftarrow", "rightarrow"), 0.888, 522),
    "overbrace" -> KatexImageData(Array("leftbrace", "midbrace", "rightbrace"), 1.6, 548),
    "underbrace" -> KatexImageData(Array("leftbraceunder", "midbraceunder", "rightbraceunder"), 1.6, 548),
    "underleftrightarrow" -> KatexImageData(Array("leftarrow", "rightarrow"), 0.888, 522),
    "xleftrightarrow" -> KatexImageData(Array("leftarrow", "rightarrow"), 1.75, 522),
    "xLeftrightarrow" -> KatexImageData(Array("doubleleftarrow", "doublerightarrow"), 1.75, 560),
    "xrightleftharpoons" -> KatexImageData(Array("leftharpoondownplus", "rightharpoonplus"), 1.75, 716),
    "xleftrightharpoons" -> KatexImageData(Array("leftharpoonplus", "rightharpoondownplus"), 1.75, 716),
    "xhookleftarrow" -> KatexImageData(Array("leftarrow", "righthook"), 1.08, 522),
    "xhookrightarrow" -> KatexImageData(Array("lefthook", "rightarrow"), 1.08, 522),
    "overlinesegment" -> KatexImageData(Array("leftlinesegment", "rightlinesegment"), 0.888, 522),
    "underlinesegment" -> KatexImageData(Array("leftlinesegment", "rightlinesegment"), 0.888, 522),
    "overbracket" -> KatexImageData(Array("leftbracketover", "rightbracketover"), 1.6, 440),
    "underbracket" -> KatexImageData(Array("leftbracketunder", "rightbracketunder"), 1.6, 410),
    "overgroup" -> KatexImageData(Array("leftgroup", "rightgroup"), 0.888, 342),
    "undergroup" -> KatexImageData(Array("leftgroupunder", "rightgroupunder"), 0.888, 342),
    "xmapsto" -> KatexImageData(Array("leftmapsto", "rightarrow"), 1.5, 522),
    "xtofrom" -> KatexImageData(Array("leftToFrom", "rightToFrom"), 1.75, 528),

    // The next three arrows are from the mhchem package.
    // In mhchem.sty, min-length is 2.0em. But these arrows might appear in the
    // document as \xrightarrow or \xrightleftharpoons. Those have
    // min-length = 1.75em, so we set min-length on these next three to match.
    "xrightleftarrows" -> KatexImageData(Array("baraboveleftarrow", "rightarrowabovebar"), 1.75, 901),
    "xrightequilibrium" -> KatexImageData(Array("baraboveshortleftharpoon", "rightharpoonaboveshortbar"), 1.75, 716),
    "xleftequilibrium" -> KatexImageData(Array("shortbaraboveleftharpoon", "shortrightharpoonabovebar"), 1.75, 716)
  )

  private val wideAccentLabels: Set[String] =
    Set("widehat", "widecheck", "widetilde", "utilde")

  /**
   * Build the SVG span for a stretchy element.
   */
  private def buildSvgSpan(
      label: String,
      base: AnyParseNode,
      options: Options
  ): (HtmlDomNode, Double, Double) = boundary { // (span, minWidth, height)
    val viewBoxWidth = 400000 // default
    if (wideAccentLabels.contains(label)) {
      // There are four SVG images available for each function.
      // Choose a taller image when there are more characters.
      val numChars = if (base.nodeType == "ordgroup") {
        base.asInstanceOf[ssg.katex.parse.ParseNodeOrdgroup].body.length
      } else {
        1
      }

      var viewBoxH = 0
      var vbw = viewBoxWidth
      var pathName = ""
      var h = 0.0

      if (numChars > 5) {
        if (label == "widehat" || label == "widecheck") {
          viewBoxH = 420; vbw = 2364; h = 0.42
          pathName = label + "4"
        } else {
          viewBoxH = 312; vbw = 2340; h = 0.34
          pathName = "tilde4"
        }
      } else {
        val imgIndex = Array(1, 1, 2, 2, 3, 3)(numChars)
        if (label == "widehat" || label == "widecheck") {
          vbw = Array(0, 1062, 2364, 2364, 2364)(imgIndex)
          viewBoxH = Array(0, 239, 300, 360, 420)(imgIndex)
          h = Array(0.0, 0.24, 0.3, 0.3, 0.36, 0.42)(imgIndex)
          pathName = label + imgIndex
        } else {
          vbw = Array(0, 600, 1033, 2339, 2340)(imgIndex)
          viewBoxH = Array(0, 260, 286, 306, 312)(imgIndex)
          h = Array(0.0, 0.26, 0.286, 0.3, 0.306, 0.34)(imgIndex)
          pathName = "tilde" + imgIndex
        }
      }
      val path = new PathNode(pathName)
      val svgNode = new SvgNode(
        ArrayBuffer[SvgChildNode](path),
        LinkedHashMap(
          "width" -> "100%",
          "height" -> Units.makeEm(h),
          "viewBox" -> s"0 0 $vbw $viewBoxH",
          "preserveAspectRatio" -> "none"
        )
      )
      val span = BuildCommon.makeSvgSpan(ArrayBuffer.empty, ArrayBuffer(svgNode), Nullable(options))
      (span, 0.0, h)

    } else {
      val data = katexImagesData(label)
      val paths = data.paths
      val minWidth = data.minWidth
      val viewBoxHeight = data.height
      val h = viewBoxHeight.toDouble / 1000

      val numSvgChildren = paths.length
      val (widthClasses, aligns) = if (numSvgChildren == 1) {
        val align1 = data.align.getOrElse("xMinYMin")
        (Array("hide-tail"), Array(align1))
      } else if (numSvgChildren == 2) {
        (Array("halfarrow-left", "halfarrow-right"),
         Array("xMinYMin", "xMaxYMin"))
      } else if (numSvgChildren == 3) {
        (Array("brace-left", "brace-center", "brace-right"),
         Array("xMinYMin", "xMidYMin", "xMaxYMin"))
      } else {
        throw new Error(
          s"Correct katexImagesData or update code here to support $numSvgChildren children.")
      }

      val spans = ArrayBuffer.empty[HtmlDomNode]
      var i = 0
      while (i < numSvgChildren) {
        val path = new PathNode(paths(i))

        val svgNode = new SvgNode(
          ArrayBuffer[SvgChildNode](path),
          LinkedHashMap(
            "width" -> "400em",
            "height" -> Units.makeEm(h),
            "viewBox" -> s"0 0 $viewBoxWidth $viewBoxHeight",
            "preserveAspectRatio" -> (aligns(i) + " slice")
          )
        )

        val span = BuildCommon.makeSvgSpan(
          ArrayBuffer(widthClasses(i)), ArrayBuffer(svgNode), Nullable(options))
        if (numSvgChildren == 1) {
          break((span, minWidth, h))
        } else {
          span.style = span.style.copy(height = Nullable(Units.makeEm(h)))
          spans += span
        }
        i += 1
      }

      (BuildCommon.makeSpan(ArrayBuffer("stretchy"), spans, Nullable(options)),
       minWidth, h)
    }
  }

  def stretchySvg(
      group: AnyParseNode,
      options: Options
  ): HtmlDomNode = {
    // Create a span with inline SVG for the element.
    val label = group match {
      case a: ssg.katex.parse.ParseNodeAccent => a.label.substring(1)
      case a: ssg.katex.parse.ParseNodeAccentUnder => a.label.substring(1)
      case a: ssg.katex.parse.ParseNodeXArrow => a.label.substring(1)
      case a: ssg.katex.parse.ParseNodeHorizBrace => a.label.substring(1)
      case _ => throw new Error("stretchySvg called on unsupported node type")
    }

    val base = group match {
      case a: ssg.katex.parse.ParseNodeAccent => a.base
      case a: ssg.katex.parse.ParseNodeAccentUnder => a.base
      case _ => group
    }

    val (span, minWidth, height) = buildSvgSpan(label, base, options)

    // Note that we are returning span.depth = 0.
    // Any adjustments relative to the baseline must be done in buildHTML.
    span.height = height
    span.style = span.style.copy(height = Nullable(Units.makeEm(height)))
    if (minWidth > 0) {
      span.style = span.style.copy(minWidth = Nullable(Units.makeEm(minWidth)))
    }

    span
  }

  def stretchyEnclose(
      inner: HtmlDomNode,
      label: String,
      topPad: Double,
      bottomPad: Double,
      options: Options
  ): HtmlDomNode = {
    // Return an image span for \cancel, \bcancel, \xcancel, \fbox, or \angl
    val totalHeight = inner.height + inner.depth + topPad + bottomPad

    val img: HtmlDomNode = if (label.matches(".*(?:fbox|color|angl).*")) {
      val fboxImg = BuildCommon.makeSpan(ArrayBuffer("stretchy", label), ArrayBuffer.empty, Nullable(options))

      if (label == "fbox") {
        val color = options.getColor()
        color.foreach { c =>
          fboxImg.style = fboxImg.style.copy(borderColor = Nullable(c))
        }
      }

      fboxImg
    } else {
      // \cancel, \bcancel, or \xcancel
      // Since \cancel's SVG is inline and it omits the viewBox attribute,
      // its stroke-width will not vary with span area.

      val lines = ArrayBuffer.empty[SvgChildNode]
      if (label.matches("^[bx]cancel$")) {
        lines += new LineNode(LinkedHashMap(
          "x1" -> "0",
          "y1" -> "0",
          "x2" -> "100%",
          "y2" -> "100%",
          "stroke-width" -> "0.046em"
        ))
      }

      if (label.matches("^x?cancel$")) {
        lines += new LineNode(LinkedHashMap(
          "x1" -> "0",
          "y1" -> "100%",
          "x2" -> "100%",
          "y2" -> "0",
          "stroke-width" -> "0.046em"
        ))
      }

      val svgNode = new SvgNode(lines, LinkedHashMap(
        "width" -> "100%",
        "height" -> Units.makeEm(totalHeight)
      ))

      BuildCommon.makeSvgSpan(ArrayBuffer.empty, ArrayBuffer(svgNode), Nullable(options))
    }

    img.height = totalHeight
    img.style = img.style.copy(height = Nullable(Units.makeEm(totalHeight)))

    img
  }
}
