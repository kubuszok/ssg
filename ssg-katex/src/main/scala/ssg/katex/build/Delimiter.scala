/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * This file deals with creating delimiters of various sizes. The TeXbook
 * discusses these routines on page 441-442, in the "Another subroutine sets box
 * x to a specified variable delimiter" paragraph.
 *
 * There are three main routines here. `makeSmallDelim` makes a delimiter in the
 * normal font, but in either text, script, or scriptscript style.
 * `makeLargeDelim` makes a delimiter in textstyle, but in one of the Size1,
 * Size2, Size3, or Size4 fonts. `makeStackedDelim` makes a delimiter out of
 * smaller pieces that are stacked on top of one another.
 *
 * The functions take a parameter `center`, which determines if the delimiter
 * should be centered around the axis.
 *
 * Then, there are three exposed functions. `sizedDelim` makes a delimiter in
 * one of the given sizes. This is used for things like `\bigl`.
 * `customSizedDelim` makes a delimiter with a given total height+depth. It is
 * called in places like `\sqrt`. `leftRightDelim` makes an appropriate
 * delimiter which surrounds an expression of a given height an depth. It is
 * used in `\left` and `\right`.
 *
 * Original source: katex src/delimiter.ts
 * Original author: Khan Academy and contributors
 * Original license: MIT
 *
 * upstream-commit: 90de9794
 *
 * Migration notes:
 *   Renames: delimiter -> Delimiter (object)
 *   Convention: StackedDelimiterFont union -> type alias
 *   Idiom: Delimiter ADT (small|large|stack) -> sealed trait + case classes
 */
package ssg
package katex
package build

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.LinkedHashMap
import scala.util.boundary
import scala.util.boundary.break

import ssg.commons.Nullable
import ssg.katex.ParseError
import ssg.katex.data.{CharacterMetrics, FontMetrics, FontMetricsData, Symbols, SvgGeometry, Units}
import ssg.katex.tree.{
  DomSpan,
  HtmlDomNode,
  PathNode,
  SvgChildNode,
  SvgNode,
  SvgSpan,
  SymbolNode
}

object Delimiter {

  type StackedDelimiterFont = String // "Size1-Regular" | "Size4-Regular"

  /**
   * Get the metrics for a given symbol and font, after transformation (i.e.
   * after following replacement from symbols.js)
   */
  private def getMetrics(
      symbol: String,
      font: String,
      mode: Mode
  ): CharacterMetrics = {
    val replace = Symbols.math.get(symbol).flatMap(_.replace.toOption)
    val metrics =
      FontMetrics.getCharacterMetrics(replace.getOrElse(symbol), font, mode)
    if (metrics.isEmpty) {
      throw new Error(s"Unsupported symbol $symbol and font size $font.")
    }
    metrics.get
  }

  /**
   * Puts a delimiter span in a given style, and adds appropriate height, depth,
   * and maxFontSizes.
   */
  private def styleWrap(
      delim: HtmlDomNode,
      toStyle: Style,
      options: Options,
      classes: Array[String]
  ): DomSpan = {
    val newOptions = options.havingBaseStyle(toStyle)

    val span = BuildCommon.makeSpan(
      ArrayBuffer.from(classes) ++ newOptions.sizingClasses(options),
      ArrayBuffer(delim), Nullable(options))

    val delimSizeMultiplier =
      newOptions.sizeMultiplier / options.sizeMultiplier
    span.height *= delimSizeMultiplier
    span.depth *= delimSizeMultiplier
    span.maxFontSize = newOptions.sizeMultiplier

    span
  }

  private def centerSpan(
      span: DomSpan,
      options: Options,
      style: Style
  ): Unit = {
    val newOptions = options.havingBaseStyle(style)
    val shift =
      (1 - options.sizeMultiplier / newOptions.sizeMultiplier) *
      options.fontMetrics().axisHeight

    span.classes += "delimcenter"
    span.style = span.style.copy(top = Nullable(Units.makeEm(shift)))
    span.height -= shift
    span.depth += shift
  }

  /**
   * Makes a small delimiter. This is a delimiter that comes in the Main-Regular
   * font, but is restyled to either be in textstyle, scriptstyle, or
   * scriptscriptstyle.
   */
  private def makeSmallDelim(
      delim: String,
      style: Style,
      center: Boolean,
      options: Options,
      mode: Mode,
      classes: Array[String]
  ): DomSpan = {
    val text = BuildCommon.makeSymbol(delim, "Main-Regular", mode, Nullable(options))
    val span = styleWrap(text, style, options, classes)
    if (center) {
      centerSpan(span, options, style)
    }
    span
  }

  /**
   * Builds a symbol in the given font size (note size is an integer)
   */
  private def mathrmSize(
      value: String,
      size: Int,
      mode: Mode,
      options: Options
  ): SymbolNode = {
    BuildCommon.makeSymbol(value, "Size" + size + "-Regular",
      mode, Nullable(options))
  }

  /**
   * Makes a large delimiter. This is a delimiter that comes in the Size1, Size2,
   * Size3, or Size4 fonts. It is always rendered in textstyle.
   */
  private def makeLargeDelim(
      delim: String,
      size: Int,
      center: Boolean,
      options: Options,
      mode: Mode,
      classes: Array[String]
  ): DomSpan = {
    val inner = mathrmSize(delim, size, mode, options)
    val span = styleWrap(
      BuildCommon.makeSpan(
        ArrayBuffer("delimsizing", "size" + size), ArrayBuffer[HtmlDomNode](inner),
        Nullable(options)),
      Style.TEXT, options, classes)
    if (center) {
      centerSpan(span, options, Style.TEXT)
    }
    span
  }

  /**
   * Make a span from a font glyph with the given offset and in the given font.
   * This is used in makeStackedDelim to make the stacking pieces for the delimiter.
   */
  private def makeGlyphSpan(
      symbol: String,
      font: StackedDelimiterFont,
      mode: Mode
  ): VListElem = {
    val sizeClass =
      // Apply the correct CSS class to choose the right font.
      if (font == "Size1-Regular") "delim-size1"
      else /* if (font == "Size4-Regular") */ "delim-size4"

    val corner = BuildCommon.makeSpan(
      ArrayBuffer("delimsizinginner", sizeClass),
      ArrayBuffer[HtmlDomNode](BuildCommon.makeSpan(
        ArrayBuffer.empty,
        ArrayBuffer[HtmlDomNode](BuildCommon.makeSymbol(symbol, font, mode)))))

    // Since this will be passed into `makeVList` in the end, wrap the element
    // in the appropriate tag that VList uses.
    VListElem(elem = corner)
  }

  private def makeInner(
      ch: String,
      height: Double,
      options: Options
  ): VListElem = {
    // Create a span with inline SVG for the inner part of a tall stacked delimiter.
    val charCode = ch.charAt(0).toInt
    val width: Double = {
      val s4 = FontMetricsData.metricMap.get("Size4-Regular")
      val s1 = FontMetricsData.metricMap.get("Size1-Regular")
      s4.flatMap(_.get(charCode)).map(_(4))
        .orElse(s1.flatMap(_.get(charCode)).map(_(4)))
        .getOrElse(0.0)
    }
    val path = new PathNode("inner",
      Nullable(SvgGeometry.innerPath(ch, Math.round(1000 * height).toInt)))
    val svgNode = new SvgNode(
      ArrayBuffer[SvgChildNode](path),
      LinkedHashMap(
        "width" -> Units.makeEm(width),
        "height" -> Units.makeEm(height),
        // Override CSS rule `.katex svg { width: 100% }`
        "style" -> ("width:" + Units.makeEm(width)),
        "viewBox" -> ("0 0 " + (1000 * width).toInt + " " + Math.round(1000 * height)),
        "preserveAspectRatio" -> "xMinYMin"
      )
    )
    val span = BuildCommon.makeSvgSpan(ArrayBuffer.empty, ArrayBuffer(svgNode), Nullable(options))
    span.height = height
    span.style = span.style.copy(
      height = Nullable(Units.makeEm(height)),
      width = Nullable(Units.makeEm(width))
    )
    VListElem(elem = span)
  }

  // Helpers for makeStackedDelim
  private val lapInEms: Double = 0.008
  private val lap: VListKern = VListKern(-1 * lapInEms)
  private val verts: Set[String] = Set("|", "\\lvert", "\\rvert", "\\vert")
  private val doubleVerts: Set[String] = Set("\\|", "\\lVert", "\\rVert", "\\Vert")

  /**
   * Make a stacked delimiter out of a given delimiter, with the total height at
   * least `heightTotal`. This routine is mentioned on page 442 of the TeXbook.
   */
  private def makeStackedDelim(
      delim: String,
      heightTotal: Double,
      center: Boolean,
      options: Options,
      mode: Mode,
      classes: Array[String]
  ): DomSpan = {
    // There are four parts, the top, an optional middle, a repeated part, and a
    // bottom.
    var top: String = delim
    var middle: Nullable[String] = Nullable.Null
    var repeat: String = delim
    var bottom: String = delim
    var svgLabel = ""
    var viewBoxWidth = 0
    // Also keep track of what font the delimiters are in
    var font: StackedDelimiterFont = "Size1-Regular"

    // We set the parts and font based on the symbol. Note that we use
    // '⏐' instead of '|' and '‖' instead of '\\|' for the
    // repeats of the arrows
    if (delim == "\\uparrow") {
      repeat = "⏐"; bottom = "⏐"
    } else if (delim == "\\Uparrow") {
      repeat = "‖"; bottom = "‖"
    } else if (delim == "\\downarrow") {
      top = "⏐"; repeat = "⏐"
    } else if (delim == "\\Downarrow") {
      top = "‖"; repeat = "‖"
    } else if (delim == "\\updownarrow") {
      top = "\\uparrow"
      repeat = "⏐"
      bottom = "\\downarrow"
    } else if (delim == "\\Updownarrow") {
      top = "\\Uparrow"
      repeat = "‖"
      bottom = "\\Downarrow"
    } else if (verts.contains(delim)) {
      repeat = "∣"
      svgLabel = "vert"
      viewBoxWidth = 333
    } else if (doubleVerts.contains(delim)) {
      repeat = "∥"
      svgLabel = "doublevert"
      viewBoxWidth = 556
    } else if (delim == "[" || delim == "\\lbrack") {
      top = "⎡"; repeat = "⎢"; bottom = "⎣"
      font = "Size4-Regular"
      svgLabel = "lbrack"; viewBoxWidth = 667
    } else if (delim == "]" || delim == "\\rbrack") {
      top = "⎤"; repeat = "⎥"; bottom = "⎦"
      font = "Size4-Regular"
      svgLabel = "rbrack"; viewBoxWidth = 667
    } else if (delim == "\\lfloor" || delim == "⌊") {
      repeat = "⎢"; top = "⎢"; bottom = "⎣"
      font = "Size4-Regular"
      svgLabel = "lfloor"; viewBoxWidth = 667
    } else if (delim == "\\lceil" || delim == "⌈") {
      top = "⎡"; repeat = "⎢"; bottom = "⎢"
      font = "Size4-Regular"
      svgLabel = "lceil"; viewBoxWidth = 667
    } else if (delim == "\\rfloor" || delim == "⌋") {
      repeat = "⎥"; top = "⎥"; bottom = "⎦"
      font = "Size4-Regular"
      svgLabel = "rfloor"; viewBoxWidth = 667
    } else if (delim == "\\rceil" || delim == "⌉") {
      top = "⎤"; repeat = "⎥"; bottom = "⎥"
      font = "Size4-Regular"
      svgLabel = "rceil"; viewBoxWidth = 667
    } else if (delim == "(" || delim == "\\lparen") {
      top = "⎛"; repeat = "⎜"; bottom = "⎝"
      font = "Size4-Regular"
      svgLabel = "lparen"; viewBoxWidth = 875
    } else if (delim == ")" || delim == "\\rparen") {
      top = "⎞"; repeat = "⎟"; bottom = "⎠"
      font = "Size4-Regular"
      svgLabel = "rparen"; viewBoxWidth = 875
    } else if (delim == "\\{" || delim == "\\lbrace") {
      top = "⎧"; middle = Nullable("⎨")
      bottom = "⎩"; repeat = "⎪"
      font = "Size4-Regular"
    } else if (delim == "\\}" || delim == "\\rbrace") {
      top = "⎫"; middle = Nullable("⎬")
      bottom = "⎭"; repeat = "⎪"
      font = "Size4-Regular"
    } else if (delim == "\\lgroup" || delim == "⟮") {
      top = "⎧"; bottom = "⎩"
      repeat = "⎪"; font = "Size4-Regular"
    } else if (delim == "\\rgroup" || delim == "⟯") {
      top = "⎫"; bottom = "⎭"
      repeat = "⎪"; font = "Size4-Regular"
    } else if (delim == "\\lmoustache" || delim == "⎰") {
      top = "⎧"; bottom = "⎭"
      repeat = "⎪"; font = "Size4-Regular"
    } else if (delim == "\\rmoustache" || delim == "⎱") {
      top = "⎫"; bottom = "⎩"
      repeat = "⎪"; font = "Size4-Regular"
    }

    // Get the metrics of the four sections
    val topMetrics = getMetrics(top, font, mode)
    val topHeightTotal = topMetrics.height + topMetrics.depth
    val repeatMetrics = getMetrics(repeat, font, mode)
    val repeatHeightTotal = repeatMetrics.height + repeatMetrics.depth
    val bottomMetrics = getMetrics(bottom, font, mode)
    val bottomHeightTotal = bottomMetrics.height + bottomMetrics.depth
    var middleHeightTotal = 0.0
    var middleFactor = 1
    if (middle.isDefined) {
      val middleMetrics = getMetrics(middle.get, font, mode)
      middleHeightTotal = middleMetrics.height + middleMetrics.depth
      middleFactor = 2 // repeat symmetrically above and below middle
    }

    // Calculate the minimal height that the delimiter can have.
    // It is at least the size of the top, bottom, and optional middle combined.
    val minHeight = topHeightTotal + bottomHeightTotal + middleHeightTotal

    // Compute the number of copies of the repeat symbol we will need
    val repeatCount = Math.max(0, Math.ceil(
      (heightTotal - minHeight) / (middleFactor * repeatHeightTotal)).toInt)

    // Compute the total height of the delimiter including all the symbols
    val realHeightTotal =
      minHeight + repeatCount * middleFactor * repeatHeightTotal

    // The center of the delimiter is placed at the center of the axis. Note
    // that in this context, "center" means that the delimiter should be
    // centered around the axis in the current style, while normally it is
    // centered around the axis in textstyle.
    var axisHeight = options.fontMetrics().axisHeight
    if (center) {
      axisHeight *= options.sizeMultiplier
    }
    // Calculate the depth
    val depth = realHeightTotal / 2 - axisHeight

    // Now, we start building the pieces that will go into the vlist
    // Keep a list of the pieces of the stacked delimiter
    val stack = ArrayBuffer.empty[VListChild]

    if (svgLabel.nonEmpty) {
      // Instead of stacking glyphs, create a single SVG.
      // This evades browser problems with imprecise positioning of spans.
      val midHeight = realHeightTotal - topHeightTotal - bottomHeightTotal
      val viewBoxHeight = Math.round(realHeightTotal * 1000).toInt
      val pathStr = SvgGeometry.tallDelim(svgLabel, Math.round(midHeight * 1000).toInt)
      val path = new PathNode(svgLabel, Nullable(pathStr))
      val widthStr = Units.makeEm(viewBoxWidth.toDouble / 1000)
      val heightStr = Units.makeEm(viewBoxHeight.toDouble / 1000)
      val svg = new SvgNode(
        ArrayBuffer[SvgChildNode](path),
        LinkedHashMap(
          "width" -> widthStr,
          "height" -> heightStr,
          "viewBox" -> s"0 0 $viewBoxWidth $viewBoxHeight"
        )
      )
      val wrapper = BuildCommon.makeSvgSpan(ArrayBuffer.empty, ArrayBuffer(svg), Nullable(options))
      wrapper.height = viewBoxHeight.toDouble / 1000
      wrapper.style = wrapper.style.copy(
        width = Nullable(widthStr),
        height = Nullable(heightStr)
      )
      stack += VListElem(elem = wrapper)
    } else {
      // Stack glyphs
      // Start by adding the bottom symbol
      stack += makeGlyphSpan(bottom, font, mode)
      stack += lap // overlap

      if (middle.isEmpty) {
        // The middle section will be an SVG. Make it an extra 0.016em tall.
        // We'll overlap by 0.008em at top and bottom.
        val innerHeight = realHeightTotal - topHeightTotal - bottomHeightTotal +
            2 * lapInEms
        stack += makeInner(repeat, innerHeight, options)
      } else {
        // When there is a middle bit, we need the middle part and two repeated
        // sections
        val innerHeight = (realHeightTotal - topHeightTotal -
            bottomHeightTotal - middleHeightTotal) / 2 + 2 * lapInEms
        stack += makeInner(repeat, innerHeight, options)
        // Now insert the middle of the brace.
        stack += lap
        stack += makeGlyphSpan(middle.get, font, mode)
        stack += lap
        stack += makeInner(repeat, innerHeight, options)
      }

      // Add the top symbol
      stack += lap
      stack += makeGlyphSpan(top, font, mode)
    }

    // Finally, build the vlist
    val newOptions = options.havingBaseStyle(Style.TEXT)
    val inner = BuildCommon.makeVList(VListParam.Positioned(
      positionType = "bottom",
      positionData = depth,
      children = stack.toArray
    ), newOptions)

    styleWrap(
      BuildCommon.makeSpan(
        ArrayBuffer("delimsizing", "mult"), ArrayBuffer[HtmlDomNode](inner), Nullable(newOptions)),
      Style.TEXT, options, classes)
  }

  // All surds have 0.08em padding above the vinculum inside the SVG.
  // That keeps browser span height rounding error from pinching the line.
  private val vbPad: Int = 80   // padding above the surd, measured inside the viewBox.
  private val emPad: Double = 0.08 // padding, in ems, measured in the document.

  private def sqrtSvg(
      sqrtName: String,
      height: Double,
      viewBoxHeight: Int,
      extraVinculum: Double,
      options: Options
  ): SvgSpan = {
    val path = SvgGeometry.sqrtPath(sqrtName, extraVinculum, viewBoxHeight)
    val pathNode = new PathNode(sqrtName, Nullable(path))

    val svg = new SvgNode(
      ArrayBuffer[SvgChildNode](pathNode),
      LinkedHashMap(
        // Note: 1000:1 ratio of viewBox to document em width.
        "width" -> "400em",
        "height" -> Units.makeEm(height),
        "viewBox" -> ("0 0 400000 " + viewBoxHeight),
        "preserveAspectRatio" -> "xMinYMin slice"
      )
    )

    BuildCommon.makeSvgSpan(ArrayBuffer("hide-tail"), ArrayBuffer(svg), Nullable(options))
  }

  /**
   * Make a sqrt image of the given height,
   */
  def makeSqrtImage(
      height: Double,
      options: Options
  ): (SvgSpan, Double, Double) = { // (span, ruleWidth, advanceWidth)
    // Define a newOptions that removes the effect of size changes such as \Huge.
    // We don't pick different a height surd for \Huge. For it, we scale up.
    val newOptions = options.havingBaseSizing()

    // Pick the desired surd glyph from a sequence of surds.
    val delim = traverseSequence("\\surd", height * newOptions.sizeMultiplier,
      stackLargeDelimiterSequence, newOptions)

    var sizeMultiplier = newOptions.sizeMultiplier // default

    // The standard sqrt SVGs each have a 0.04em thick vinculum.
    // If Settings.minRuleThickness is larger than that, we add extraVinculum.
    val extraVinculum = Math.max(0.0,
      options.minRuleThickness - options.fontMetrics().sqrtRuleThickness)

    // Create a span containing an SVG image of a sqrt symbol.
    // We create viewBoxes with 80 units of "padding" above each surd.
    // Then browser rounding error on the parent span height will not
    // encroach on the ink of the vinculum. But that padding is not
    // included in the TeX-like `height` used for calculation of
    // vertical alignment. So texHeight = span.height < span.style.height.

    val (span, spanHeight, texHeight, advanceWidth): (SvgSpan, Double, Double, Double) = delim match {
      case _: DelimSmall =>
        // Get an SVG that is derived from glyph U+221A in font KaTeX-Main.
        // 1000 unit normal glyph height.
        val viewBoxHeight = (1000 + 1000 * extraVinculum + vbPad).toInt
        if (height < 1.0) {
          sizeMultiplier = 1.0   // mimic a \textfont radical
        } else if (height < 1.4) {
          sizeMultiplier = 0.7   // mimic a \scriptfont radical
        }
        val sh = (1.0 + extraVinculum + emPad) / sizeMultiplier
        val th = (1.00 + extraVinculum) / sizeMultiplier
        val s = sqrtSvg("sqrtMain", sh, viewBoxHeight, extraVinculum,
          options)
        s.style = s.style.copy(minWidth = Nullable("0.853em"))
        val aw = 0.833 / sizeMultiplier // from the font.
        (s, sh, th, aw)

      case d: DelimLarge =>
        // These SVGs come from fonts: KaTeX_Size1, _Size2, etc.
        val viewBoxHeight = ((1000 + vbPad) * sizeToMaxHeight(d.size)).toInt
        val th = (sizeToMaxHeight(d.size) + extraVinculum) / sizeMultiplier
        val sh = (sizeToMaxHeight(d.size) + extraVinculum + emPad) / sizeMultiplier
        val s = sqrtSvg("sqrtSize" + d.size, sh, viewBoxHeight,
          extraVinculum, options)
        s.style = s.style.copy(minWidth = Nullable("1.02em"))
        val aw = 1.0 / sizeMultiplier // 1.0 from the font.
        (s, sh, th, aw)

      case _: DelimStack =>
        // Tall sqrt. In TeX, this would be stacked using multiple glyphs.
        // We'll use a single SVG to accomplish the same thing.
        val sh = height + extraVinculum + emPad
        val th = height + extraVinculum
        val viewBoxHeight = (Math.floor(1000 * height + extraVinculum) + vbPad).toInt
        val s = sqrtSvg("sqrtTall", sh, viewBoxHeight, extraVinculum,
          options)
        s.style = s.style.copy(minWidth = Nullable("0.742em"))
        val aw = 1.056
        (s, sh, th, aw)
    }

    span.height = texHeight
    span.style = span.style.copy(height = Nullable(Units.makeEm(spanHeight)))

    (
      span,
      // Calculate the actual line width.
      // This actually should depend on the chosen font -- e.g. \boldmath
      // should use the thicker surd symbols from e.g. KaTeX_Main-Bold, and
      // have thicker rules.
      (options.fontMetrics().sqrtRuleThickness + extraVinculum) * sizeMultiplier,
      advanceWidth
    )
  }

  // There are three kinds of delimiters, delimiters that stack when they become
  // too large
  private val stackLargeDelimiters: Set[String] = Set(
    "(", "\\lparen", ")", "\\rparen",
    "[", "\\lbrack", "]", "\\rbrack",
    "\\{", "\\lbrace", "\\}", "\\rbrace",
    "\\lfloor", "\\rfloor", "⌊", "⌋",
    "\\lceil", "\\rceil", "⌈", "⌉",
    "\\surd"
  )

  // delimiters that always stack
  private val stackAlwaysDelimiters: Set[String] = Set(
    "\\uparrow", "\\downarrow", "\\updownarrow",
    "\\Uparrow", "\\Downarrow", "\\Updownarrow",
    "|", "\\|", "\\vert", "\\Vert",
    "\\lvert", "\\rvert", "\\lVert", "\\rVert",
    "\\lgroup", "\\rgroup", "⟮", "⟯",
    "\\lmoustache", "\\rmoustache", "⎰", "⎱"
  )

  // and delimiters that never stack
  private val stackNeverDelimiters: Set[String] = Set(
    "<", ">", "\\langle", "\\rangle", "/", "\\backslash", "\\lt", "\\gt"
  )

  // Metrics of the different sizes. Found by looking at TeX's output of
  // $\bigl| // \Bigl| \biggl| \Biggl| \showlists$
  // Used to create stacked delimiters of appropriate sizes in makeSizedDelim.
  val sizeToMaxHeight: Array[Double] = Array(0, 1.2, 1.8, 2.4, 3.0)

  /**
   * Used to create a delimiter of a specific size, where `size` is 1, 2, 3, or 4.
   */
  def makeSizedDelim(
      delimIn: String,
      size: Int,
      options: Options,
      mode: Mode,
      classes: Array[String]
  ): DomSpan = {
    var delim = delimIn
    // < and > turn into \langle and \rangle in delimiters
    if (delim == "<" || delim == "\\lt" || delim == "⟨") {
      delim = "\\langle"
    } else if (delim == ">" || delim == "\\gt" || delim == "⟩") {
      delim = "\\rangle"
    }

    // Sized delimiters are never centered.
    if (stackLargeDelimiters.contains(delim) ||
        stackNeverDelimiters.contains(delim)) {
      makeLargeDelim(delim, size, false, options, mode, classes)
    } else if (stackAlwaysDelimiters.contains(delim)) {
      makeStackedDelim(
        delim, sizeToMaxHeight(size), false, options, mode, classes)
    } else {
      throw new ParseError("Illegal delimiter: '" + delim + "'")
    }
  }

  /**
   * There are three different sequences of delimiter sizes that the delimiters
   * follow depending on the kind of delimiter. This is used when creating custom
   * sized delimiters to decide whether to create a small, large, or stacked
   * delimiter.
   *
   * In real TeX, these sequences aren't explicitly defined, but are instead
   * defined inside the font metrics. Since there are only three sequences that
   * are possible for the delimiters that TeX defines, it is easier to just encode
   * them explicitly here.
   */

  sealed trait DelimType
  final case class DelimSmall(style: Style) extends DelimType
  final case class DelimLarge(size: Int) extends DelimType
  final case class DelimStack() extends DelimType

  // Delimiters that never stack try small delimiters and large delimiters only
  private val stackNeverDelimiterSequence: Array[DelimType] = Array(
    DelimSmall(Style.SCRIPTSCRIPT),
    DelimSmall(Style.SCRIPT),
    DelimSmall(Style.TEXT),
    DelimLarge(1),
    DelimLarge(2),
    DelimLarge(3),
    DelimLarge(4)
  )

  // Delimiters that always stack try the small delimiters first, then stack
  private val stackAlwaysDelimiterSequence: Array[DelimType] = Array(
    DelimSmall(Style.SCRIPTSCRIPT),
    DelimSmall(Style.SCRIPT),
    DelimSmall(Style.TEXT),
    DelimStack()
  )

  // Delimiters that stack when large try the small and then large delimiters, and
  // stack afterwards
  private val stackLargeDelimiterSequence: Array[DelimType] = Array(
    DelimSmall(Style.SCRIPTSCRIPT),
    DelimSmall(Style.SCRIPT),
    DelimSmall(Style.TEXT),
    DelimLarge(1),
    DelimLarge(2),
    DelimLarge(3),
    DelimLarge(4),
    DelimStack()
  )

  /**
   * Get the font used in a delimiter based on what kind of delimiter it is.
   * TODO(#963) Use more specific font family return type once that is introduced.
   */
  private def delimTypeToFont(delimType: DelimType): String = {
    delimType match {
      case _: DelimSmall => "Main-Regular"
      case d: DelimLarge => "Size" + d.size + "-Regular"
      case _: DelimStack => "Size4-Regular"
    }
  }

  /**
   * Traverse a sequence of types of delimiters to decide what kind of delimiter
   * should be used to create a delimiter of the given height+depth.
   */
  private def traverseSequence(
      delim: String,
      height: Double,
      sequence: Array[DelimType],
      options: Options
  ): DelimType = boundary {
    // Here, we choose the index we should start at in the sequences. In smaller
    // sizes (which correspond to larger numbers in style.size) we start earlier
    // in the sequence. Thus, scriptscript starts at index 3-3=0, script starts
    // at index 3-2=1, text starts at 3-1=2, and display starts at min(2,3-0)=2
    val start = Math.min(2, 3 - options.style.size)
    var i = start
    while (i < sequence.length) {
      val delimType = sequence(i)
      delimType match {
        case _: DelimStack =>
          // This is always the last delimiter, so we just break the loop now.
          i = sequence.length // break
        case _ =>
          val metrics = getMetrics(delim, delimTypeToFont(delimType), Mode.Math)
          var heightDepth = metrics.height + metrics.depth

          // Small delimiters are scaled down versions of the same font, so we
          // account for the style change size.
          delimType match {
            case d: DelimSmall =>
              val newOptions = options.havingBaseStyle(d.style)
              heightDepth *= newOptions.sizeMultiplier
            case _ => // no scaling for large
          }

          // Check if the delimiter at this size works for the given height.
          if (heightDepth > height) {
            break(delimType)
          }
          i += 1
      }
    }

    // If we reached the end of the sequence, return the last sequence element.
    sequence(sequence.length - 1)
  }

  /**
   * Make a delimiter of a given height+depth, with optional centering. Here, we
   * traverse the sequences, and create a delimiter that the sequence tells us to.
   */
  def makeCustomSizedDelim(
      delimIn: String,
      height: Double,
      center: Boolean,
      options: Options,
      mode: Mode,
      classes: Array[String]
  ): DomSpan = {
    var delim = delimIn
    if (delim == "<" || delim == "\\lt" || delim == "⟨") {
      delim = "\\langle"
    } else if (delim == ">" || delim == "\\gt" || delim == "⟩") {
      delim = "\\rangle"
    }

    // Decide what sequence to use
    val sequence: Array[DelimType] =
      if (stackNeverDelimiters.contains(delim)) stackNeverDelimiterSequence
      else if (stackLargeDelimiters.contains(delim)) stackLargeDelimiterSequence
      else stackAlwaysDelimiterSequence

    // Look through the sequence
    val delimType = traverseSequence(delim, height, sequence, options)

    // Get the delimiter from font glyphs.
    // Depending on the sequence element we decided on, call the
    // appropriate function.
    delimType match {
      case d: DelimSmall =>
        makeSmallDelim(delim, d.style, center, options, mode, classes)
      case d: DelimLarge =>
        makeLargeDelim(delim, d.size, center, options, mode, classes)
      case _: DelimStack =>
        makeStackedDelim(delim, height, center, options, mode, classes)
    }
  }

  /**
   * Make a delimiter for use with `\left` and `\right`, given a height and depth
   * of an expression that the delimiters surround.
   */
  def makeLeftRightDelim(
      delim: String,
      height: Double,
      depth: Double,
      options: Options,
      mode: Mode,
      classes: Array[String]
  ): DomSpan = {
    // We always center \left/\right delimiters, so the axis is always shifted
    val axisHeight =
      options.fontMetrics().axisHeight * options.sizeMultiplier

    // Taken from TeX source, tex.web, function make_left_right
    val delimiterFactor = 901
    val delimiterExtend = 5.0 / options.fontMetrics().ptPerEm

    val maxDistFromAxis = Math.max(
      height - axisHeight, depth + axisHeight)

    val totalHeight = Math.max(
      // In real TeX, calculations are done using integral values which are
      // 65536 per pt, or 655360 per em. So, the division here truncates in
      // TeX but doesn't here, producing different results. If we wanted to
      // exactly match TeX's calculation, we could do
      //   Math.floor(655360 * maxDistFromAxis / 500) *
      //    delimiterFactor / 655360
      // (To see the difference, compare
      //    x^{x^{\left(\rule{0.1em}{0.68em}\right)}}
      // in TeX and KaTeX)
      maxDistFromAxis / 500 * delimiterFactor,
      2 * maxDistFromAxis - delimiterExtend)

    // Finally, we defer to `makeCustomSizedDelim` with our calculated total
    // height
    makeCustomSizedDelim(delim, totalHeight, true, options, mode, classes)
  }
}
