/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * This module contains general functions that can be used for building
 * different kinds of domTree nodes in a consistent manner.
 *
 * Original source: katex src/buildCommon.ts
 * Original author: Khan Academy and contributors
 * Original license: MIT
 *
 * upstream-commit: 90de9794
 *
 * Migration notes:
 *   Renames: buildCommon -> BuildCommon (object)
 *   Convention: Record<string, {variant, fontName}> -> Map[String, FontMapEntry]
 *   Idiom: TypeScript VListParam union -> sealed trait hierarchy
 *   Idiom: CssStyle mutable JS object -> CssStyle case class with copy()
 */
package ssg
package katex
package build

import scala.collection.mutable.ArrayBuffer
import scala.util.boundary
import scala.util.boundary.break

import ssg.commons.Nullable
import ssg.katex.data.{ CharacterMetrics, FontMetrics, Measurement, Symbols, Units, WideCharacter }
import ssg.katex.parse.AnyParseNode
import ssg.katex.tree.{ Anchor, CssStyle, DocumentFragment, DomSpan, DomTree, HtmlDocumentFragment, HtmlDomNode, PathNode, Span, SvgChildNode, SvgNode, SvgSpan, SymbolNode, VirtualNode }

/** Entry for the fontMap lookup table.
  */
final case class FontMapEntry(variant: FontVariant, fontName: String)

// VList types

sealed trait VListChild {
  def childType: String
}

final case class VListElem(
  elem:           HtmlDomNode,
  marginLeft:     Nullable[String] = Nullable.Null,
  marginRight:    Nullable[String] = Nullable.Null,
  wrapperClasses: Array[String] = Array.empty,
  wrapperStyle:   CssStyle = CssStyle()
) extends VListChild {
  def childType: String = "elem"
}

final case class VListElemAndShift(
  elem:           HtmlDomNode,
  shift:          Double,
  marginLeft:     Nullable[String] = Nullable.Null,
  marginRight:    Nullable[String] = Nullable.Null,
  wrapperClasses: Array[String] = Array.empty,
  wrapperStyle:   CssStyle = CssStyle()
) extends VListChild {
  def childType: String = "elem"
}

final case class VListKern(size: Double) extends VListChild {
  def childType: String = "kern"
}

// A list of child or kern nodes to be stacked on top of each other (i.e. the
// first element will be at the bottom, and the last at the top).

sealed trait VListParam

object VListParam {
  // Each child contains how much it should be shifted downward.
  final case class IndividualShift(
    children: Array[VListElemAndShift]
  ) extends VListParam

  // "top": The positionData specifies the topmost point of the vlist (note this
  //        is expected to be a height, so positive values move up).
  // "bottom": The positionData specifies the bottommost point of the vlist (note
  //           this is expected to be a depth, so positive values move down).
  // "shift": The vlist will be positioned such that its baseline is positionData
  //          away from the baseline of the first child which MUST be an
  //          "elem". Positive values move downwards.
  final case class Positioned(
    positionType: String, // "top" | "bottom" | "shift"
    positionData: Double,
    children:     Array[VListChild]
  ) extends VListParam

  // The vlist is positioned so that its baseline is aligned with the baseline
  // of the first child which MUST be an "elem". This is equivalent to "shift"
  // with positionData=0.
  final case class FirstBaseline(
    children: Array[VListChild]
  ) extends VListParam
}

object BuildCommon {

  /** Looks up the given symbol in fontMetrics, after applying any symbol replacements defined in symbol.js
    */
  private def lookupSymbol(
    value: String,
    // TODO(#963): Use a union type for this.
    fontName: String,
    mode:     Mode
  ): (String, Nullable[CharacterMetrics]) = {
    var v = value
    // Replace the value with its replaced value from symbol.js
    val symbols = Symbols(mode)
    if (symbols.contains(v)) {
      val replacement = symbols(v).replace
      replacement.foreach { r =>
        v = r
      }
    }

    (v, FontMetrics.getCharacterMetrics(v, fontName, mode))
  }

  /** Makes a symbolNode after translation via the list of symbols in symbols.js. Correctly pulls out metrics for the character, and optionally takes a list of classes to be attached to the node.
    *
    * TODO: make argument order closer to makeSpan TODO: add a separate argument for math class (e.g. `mop`, `mbin`), which should if present come first in `classes`. TODO(#953): Make `options`
    * mandatory and always pass it in.
    */
  def makeSymbol(
    value:    String,
    fontName: String,
    mode:     Mode,
    options:  Nullable[Options] = Nullable.Null,
    classes:  ArrayBuffer[String] = ArrayBuffer.empty
  ): SymbolNode = {
    val (lookupValue, metrics) = lookupSymbol(value, fontName, mode)

    val symbolNode = metrics.fold {
      // TODO(emily): Figure out a good way to only print this in development
      // console.warn("No character metrics " +
      //     s"for '$lookupValue' in style '$fontName' and mode '$mode'")
      new SymbolNode(lookupValue, 0, 0, 0, 0, 0, classes.clone())
    } { m =>
      var italic = m.italic
      if (mode == Mode.Text || options.exists(_.font == "mathit")) {
        italic = 0
      }
      new SymbolNode(lookupValue, m.height, m.depth, italic, m.skew, m.width, classes.clone())
    }

    options.foreach { opts =>
      symbolNode.maxFontSize = opts.sizeMultiplier
      if (opts.style.isTight()) {
        symbolNode.classes += "mtight"
      }
      val color = opts.getColor()
      color.foreach { c =>
        symbolNode.style = symbolNode.style.copy(color = Nullable(c))
      }
    }

    symbolNode
  }

  /** Makes a symbol in Main-Regular or AMS-Regular. Used for rel, bin, open, close, inner, and punct.
    */
  def mathsym(
    value:   String,
    mode:    Mode,
    options: Options,
    classes: ArrayBuffer[String] = ArrayBuffer.empty
  ): SymbolNode =
    // Decide what font to render the symbol in by its entry in the symbols
    // table.
    // Have a special case for when the value = \ because the \ is used as a
    // textord in unsupported command errors but cannot be parsed as a regular
    // text ordinal and is therefore not present as a symbol in the symbols
    // table for text, as well as a special case for boldsymbol because it
    // can be used for bold + and -
    if (
      options.font == "boldsymbol" &&
      lookupSymbol(value, "Main-Bold", mode)._2.isDefined
    ) {
      makeSymbol(value, "Main-Bold", mode, Nullable(options), classes ++ ArrayBuffer("mathbf"))
    } else if (value == "\\" || Symbols(mode)(value).font == "main") {
      makeSymbol(value, "Main-Regular", mode, Nullable(options), classes)
    } else {
      makeSymbol(value, "AMS-Regular", mode, Nullable(options), classes ++ ArrayBuffer("amsrm"))
    }

  /** Determines which of the two font names (Main-Bold and Math-BoldItalic) and corresponding style tags (mathbf or boldsymbol) to use for font "boldsymbol", depending on the symbol. Use this
    * function instead of fontMap for font "boldsymbol".
    */
  private def boldsymbol(
    value:   String,
    mode:    Mode,
    options: Options,
    classes: ArrayBuffer[String],
    ordType: String // "mathord" | "textord"
  ): (String, String) = // (fontName, fontClass)
    if (
      ordType != "textord" &&
      lookupSymbol(value, "Math-BoldItalic", mode)._2.isDefined
    ) {
      ("Math-BoldItalic", "boldsymbol")
    } else {
      // Some glyphs do not exist in Math-BoldItalic so we need to use
      // Main-Bold instead.
      ("Main-Bold", "mathbf")
    }

  /** Makes either a mathord or textord in the correct font and color.
    */
  def makeOrd(
    group:   AnyParseNode,
    options: Options,
    ordType: String // "mathord" | "textord"
  ): HtmlDomNode = {
    val mode = group.mode
    val text: String = group match {
      case sp: ssg.katex.parse.SymbolParseNode => sp.text
      case _ => throw new Error("makeOrd called on non-symbol node")
    }

    val classes = ArrayBuffer("mord")

    // Math mode or Old font (i.e. \rm)
    val isFont       = mode == Mode.Math || (mode == Mode.Text && options.font.nonEmpty)
    val fontOrFamily = if (isFont) options.font else options.fontFamily

    boundary[HtmlDomNode] {
      var wideFontName  = ""
      var wideFontClass = ""
      if (text.length > 0 && text.charAt(0).toInt == 0xd835) {
        val (wfn, wfc) = WideCharacter.wideCharacterFont(text, mode)
        wideFontName = wfn
        wideFontClass = wfc
      }
      if (wideFontName.nonEmpty) {
        // surrogate pairs get special treatment
        break(makeSymbol(text, wideFontName, mode, Nullable(options), classes ++ ArrayBuffer(wideFontClass)))
      } else if (fontOrFamily.nonEmpty) {
        var fontName    = ""
        var fontClasses = ArrayBuffer.empty[String]
        if (fontOrFamily == "boldsymbol") {
          val (fn, fc) = boldsymbol(text, mode, options, classes, ordType)
          fontName = fn
          fontClasses = ArrayBuffer(fc)
        } else if (isFont) {
          fontName = fontMap(fontOrFamily).fontName
          fontClasses = ArrayBuffer(fontOrFamily)
        } else {
          fontName = retrieveTextFontName(fontOrFamily, options.fontWeight, options.fontShape)
          fontClasses = ArrayBuffer(fontOrFamily, options.fontWeight, options.fontShape)
        }

        if (lookupSymbol(text, fontName, mode)._2.isDefined) {
          break(makeSymbol(text, fontName, mode, Nullable(options), classes ++ fontClasses))
        } else if (
          Symbols.ligatures.contains(text) &&
          fontName.length >= 10 && fontName.substring(0, 10) == "Typewriter"
        ) {
          // Deconstruct ligatures in monospace fonts (\texttt, \tt).
          val parts = ArrayBuffer.empty[HtmlDomNode]
          var i     = 0
          while (i < text.length) {
            parts += makeSymbol(text.charAt(i).toString, fontName, mode, Nullable(options), classes ++ fontClasses)
            i += 1
          }
          break(makeFragment(parts))
        }
      }

      // Makes a symbol in the default font for mathords and textords.
      if (ordType == "mathord") {
        makeSymbol(text, "Math-Italic", mode, Nullable(options), classes ++ ArrayBuffer("mathnormal"))
      } else if (ordType == "textord") {
        val symbolMap = Symbols(mode)
        val font: String = if (symbolMap.contains(text)) symbolMap(text).font else ""
        if (font == "ams") {
          val fn = retrieveTextFontName("amsrm", options.fontWeight, options.fontShape)
          makeSymbol(text, fn, mode, Nullable(options), classes ++ ArrayBuffer("amsrm", options.fontWeight, options.fontShape))
        } else if (font == "main" || font.isEmpty) {
          val fn = retrieveTextFontName("textrm", options.fontWeight, options.fontShape)
          makeSymbol(text, fn, mode, Nullable(options), classes ++ ArrayBuffer(options.fontWeight, options.fontShape))
        } else { // fonts added by plugins
          val fn = retrieveTextFontName(font, options.fontWeight, options.fontShape)
          // We add font name as a css class
          makeSymbol(text, fn, mode, Nullable(options), classes ++ ArrayBuffer(fn, options.fontWeight, options.fontShape))
        }
      } else {
        throw new Error("unexpected type: " + ordType + " in makeOrd")
      }
    }
  }

  /** Returns true if subsequent symbolNodes have the same classes, skew, maxFont, and styles. For mathnormal text, the left node must also have zero italic correction so we don't lose spacing between
    * combined glyphs.
    */
  private def canCombine(prev: SymbolNode, next: SymbolNode): Boolean = boundary {
    if (
      DomTree.createClass(prev.classes) != DomTree.createClass(next.classes)
      || prev.skew != next.skew
      || prev.maxFontSize != next.maxFontSize
      || (prev.italic != 0 && prev.hasClass("mathnormal"))
    ) {
      break(false)
    }

    // If prev and next both are just "mbin"s or "mord"s we don't combine them
    // so that the proper spacing can be preserved.
    if (prev.classes.length == 1) {
      val cls = prev.classes(0)
      if (cls == "mbin" || cls == "mord") {
        break(false)
      }
    }

    // Compare styles
    val prevStyle = prev.style
    val nextStyle = next.style
    if (prevStyle != nextStyle) {
      break(false)
    }

    true
  }

  /** Combine consecutive domTree.symbolNodes into a single symbolNode. Note: this function mutates the argument.
    */
  def tryCombineChars(chars: ArrayBuffer[HtmlDomNode]): ArrayBuffer[HtmlDomNode] = {
    var i = 0
    while (i < chars.length - 1) {
      (chars(i), chars(i + 1)) match {
        case (prev: SymbolNode, next: SymbolNode) if canCombine(prev, next) =>
          prev.text += next.text
          prev.height = Math.max(prev.height, next.height)
          prev.depth = Math.max(prev.depth, next.depth)
          // Use the last character's italic correction since we use
          // it to add padding to the right of the span created from
          // the combined characters.
          prev.italic = next.italic
          chars.remove(i + 1)
          i -= 1
        case _ => // not combinable
      }
      i += 1
    }
    chars
  }

  /** Calculate the height, depth, and maxFontSize of an element based on its children.
    */
  private def sizeElementFromChildren(
    elem:     HtmlDomNode,
    children: collection.Seq[? <: VirtualNode & HtmlDomNode]
  ): Unit = {
    var height      = 0.0
    var depth       = 0.0
    var maxFontSize = 0.0

    var i = 0
    while (i < children.length) {
      val child = children(i)
      if (child.height > height) {
        height = child.height
      }
      if (child.depth > depth) {
        depth = child.depth
      }
      if (child.maxFontSize > maxFontSize) {
        maxFontSize = child.maxFontSize
      }
      i += 1
    }

    elem.height = height
    elem.depth = depth
    elem.maxFontSize = maxFontSize
  }

  /** Makes a span with the given list of classes, list of children, and options.
    *
    * TODO(#953): Ensure that `options` is always provided (currently some call sites don't pass it) and make the type below mandatory. TODO: add a separate argument for math class (e.g. `mop`,
    * `mbin`), which should if present come first in `classes`.
    */
  def makeSpan(
    classes:  ArrayBuffer[String] = ArrayBuffer.empty,
    children: ArrayBuffer[HtmlDomNode] = ArrayBuffer.empty,
    options:  Nullable[Options] = Nullable.Null,
    style:    CssStyle = CssStyle()
  ): DomSpan = {
    val span = new Span[HtmlDomNode](classes, children, options, style)

    sizeElementFromChildren(span, span.children)

    span
  }

  // SVG one is simpler -- doesn't require height, depth, max-font setting.
  // This is also a separate method for typesafety.
  def makeSvgSpan(
    classes:  ArrayBuffer[String] = ArrayBuffer.empty,
    children: ArrayBuffer[SvgNode] = ArrayBuffer.empty,
    options:  Nullable[Options] = Nullable.Null,
    style:    CssStyle = CssStyle()
  ): SvgSpan = new Span[SvgNode](classes, children, options, style)

  def makeLineSpan(
    className: String,
    options:   Options,
    thickness: Nullable[Double] = Nullable.Null
  ): DomSpan = {
    val line = makeSpan(ArrayBuffer(className), ArrayBuffer.empty, Nullable(options))
    line.height = Math.max(
      thickness.getOrElse(options.fontMetrics().defaultRuleThickness),
      options.minRuleThickness
    )
    line.style = line.style.copy(borderBottomWidth = Nullable(Units.makeEm(line.height)))
    line.maxFontSize = 1.0
    line
  }

  /** Makes an anchor with the given href, list of classes, list of children, and options.
    */
  def makeAnchor(
    href:     String,
    classes:  ArrayBuffer[String],
    children: ArrayBuffer[HtmlDomNode],
    options:  Options
  ): Anchor = {
    val anchor = new Anchor(href, classes, children, options)

    sizeElementFromChildren(anchor, anchor.children)

    anchor
  }

  /** Makes a document fragment with the given list of children.
    */
  def makeFragment(
    children: ArrayBuffer[HtmlDomNode]
  ): HtmlDocumentFragment = {
    val fragment = new DocumentFragment[HtmlDomNode](children.toIndexedSeq)

    sizeElementFromChildren(fragment, fragment.children)

    fragment
  }

  /** Wraps group in a span if it's a document fragment, allowing to apply classes and styles
    */
  def wrapFragment(
    group:   HtmlDomNode,
    options: Options
  ): HtmlDomNode =
    group match {
      case _: DocumentFragment[?] =>
        makeSpan(ArrayBuffer.empty, ArrayBuffer(group), Nullable(options))
      case _ => group
    }

  // Computes the updated `children` list and the overall depth.
  //
  // This helper function for makeVList makes it easier to enforce type safety by
  // allowing early exits (returns) in the logic.
  private def getVListChildrenAndDepth(params: VListParam): (Array[VListChild], Double) =
    params match {
      case VListParam.IndividualShift(oldChildren) =>
        val children = ArrayBuffer.empty[VListChild]
        children += oldChildren(0)

        // Add in kerns to the list of params.children to get each element to be
        // shifted to the correct specified shift
        val depth   = -oldChildren(0).shift - oldChildren(0).elem.depth
        var currPos = depth
        var i       = 1
        while (i < oldChildren.length) {
          val diff = -oldChildren(i).shift - currPos -
            oldChildren(i).elem.depth
          val size = diff -
            (oldChildren(i - 1).elem.height +
              oldChildren(i - 1).elem.depth)

          currPos = currPos + diff
          children += VListKern(size)
          children += oldChildren(i)
          i += 1
        }

        (children.toArray, depth)

      case VListParam.Positioned(positionType, positionData, paramChildren) =>
        val depth: Double = positionType match {
          case "top" =>
            // We always start at the bottom, so calculate the bottom by adding up
            // all the sizes
            var bottom = positionData
            var i      = 0
            while (i < paramChildren.length) {
              paramChildren(i) match {
                case VListKern(s) => bottom -= s
                case e: VListElem         => bottom -= e.elem.height + e.elem.depth
                case e: VListElemAndShift => bottom -= e.elem.height + e.elem.depth
              }
              i += 1
            }
            bottom
          case "bottom" =>
            -positionData
          case "shift" =>
            paramChildren(0) match {
              case e: VListElem =>
                -e.elem.depth - positionData
              case e: VListElemAndShift =>
                -e.elem.depth - positionData
              case _ =>
                throw new Error("First child must have type \"elem\".")
            }
          case _ =>
            throw new Error(s"Invalid positionType $positionType.")
        }

        (paramChildren, depth)

      case VListParam.FirstBaseline(paramChildren) =>
        paramChildren(0) match {
          case e: VListElem =>
            (paramChildren, -e.elem.depth)
          case e: VListElemAndShift =>
            (paramChildren, -e.elem.depth)
          case _ =>
            throw new Error("First child must have type \"elem\".")
        }
    }

  /** Makes a vertical list by stacking elements and kerns on top of each other. Allows for many different ways of specifying the positioning method.
    *
    * See VListParam documentation above.
    */
  def makeVList(params: VListParam, options: Options): DomSpan = {
    val (children, depth) = getVListChildrenAndDepth(params)

    // Create a strut that is taller than any list item. The strut is added to
    // each item, where it will determine the item's baseline. Since it has
    // `overflow:hidden`, the strut's top edge will sit on the item's line box's
    // top edge and the strut's bottom edge will sit on the item's baseline,
    // with no additional line-height spacing. This allows the item baseline to
    // be positioned precisely without worrying about font ascent and
    // line-height.
    var pstrutSize = 0.0
    var i          = 0
    while (i < children.length) {
      children(i) match {
        case e: VListElem =>
          pstrutSize = Math.max(pstrutSize, Math.max(e.elem.maxFontSize, e.elem.height))
        case e: VListElemAndShift =>
          pstrutSize = Math.max(pstrutSize, Math.max(e.elem.maxFontSize, e.elem.height))
        case _ => // kern
      }
      i += 1
    }
    pstrutSize += 2
    val pstrut = makeSpan(ArrayBuffer("pstrut"))
    pstrut.style = pstrut.style.copy(height = Nullable(Units.makeEm(pstrutSize)))

    // Create a new list of actual children at the correct offsets
    val realChildren = ArrayBuffer.empty[HtmlDomNode]
    var minPos       = depth
    var maxPos       = depth
    var currPos      = depth
    i = 0
    while (i < children.length) {
      children(i) match {
        case VListKern(size) =>
          currPos += size

        case child: VListElem =>
          val elem = child.elem
          val cls  = ArrayBuffer.from(child.wrapperClasses)
          val sty  = child.wrapperStyle

          val childWrap = makeSpan(cls, ArrayBuffer(pstrut, elem), Nullable.Null, sty)
          childWrap.style = childWrap.style.copy(
            top = Nullable(Units.makeEm(-pstrutSize - currPos - elem.depth))
          )
          child.marginLeft.foreach { ml =>
            childWrap.style = childWrap.style.copy(marginLeft = Nullable(ml))
          }
          child.marginRight.foreach { mr =>
            childWrap.style = childWrap.style.copy(marginRight = Nullable(mr))
          }

          realChildren += childWrap
          currPos += elem.height + elem.depth

        case child: VListElemAndShift =>
          val elem = child.elem
          val cls  = ArrayBuffer.from(child.wrapperClasses)
          val sty  = child.wrapperStyle

          val childWrap = makeSpan(cls, ArrayBuffer(pstrut, elem), Nullable.Null, sty)
          childWrap.style = childWrap.style.copy(
            top = Nullable(Units.makeEm(-pstrutSize - currPos - elem.depth))
          )
          child.marginLeft.foreach { ml =>
            childWrap.style = childWrap.style.copy(marginLeft = Nullable(ml))
          }
          child.marginRight.foreach { mr =>
            childWrap.style = childWrap.style.copy(marginRight = Nullable(mr))
          }

          realChildren += childWrap
          currPos += elem.height + elem.depth
      }
      minPos = Math.min(minPos, currPos)
      maxPos = Math.max(maxPos, currPos)
      i += 1
    }

    // The vlist contents go in a table-cell with `vertical-align:bottom`.
    // This cell's bottom edge will determine the containing table's baseline
    // without overly expanding the containing line-box.
    val vlist = makeSpan(ArrayBuffer("vlist"), realChildren)
    vlist.style = vlist.style.copy(height = Nullable(Units.makeEm(maxPos)))

    // A second row is used if necessary to represent the vlist's depth.
    val rows: ArrayBuffer[HtmlDomNode] = if (minPos < 0) {
      // We will define depth in an empty span with display: table-cell.
      // It should render with the height that we define. But Chrome, in
      // contenteditable mode only, treats that span as if it contains some
      // text content. And that min-height over-rides our desired height.
      // So we put another empty span inside the depth strut span.
      val emptySpan  = makeSpan()
      val depthStrut = makeSpan(ArrayBuffer("vlist"), ArrayBuffer[HtmlDomNode](emptySpan))
      depthStrut.style = depthStrut.style.copy(height = Nullable(Units.makeEm(-minPos)))

      // Safari wants the first row to have inline content; otherwise it
      // puts the bottom of the *second* row on the baseline.
      val topStrut = makeSpan(ArrayBuffer("vlist-s"), ArrayBuffer[HtmlDomNode](new SymbolNode("​")))

      ArrayBuffer(
        makeSpan(ArrayBuffer("vlist-r"), ArrayBuffer[HtmlDomNode](vlist, topStrut)),
        makeSpan(ArrayBuffer("vlist-r"), ArrayBuffer[HtmlDomNode](depthStrut))
      )
    } else {
      ArrayBuffer(makeSpan(ArrayBuffer("vlist-r"), ArrayBuffer[HtmlDomNode](vlist)))
    }

    val vtable = makeSpan(ArrayBuffer("vlist-t"), rows)
    if (rows.length == 2) {
      vtable.classes += "vlist-t2"
    }
    vtable.height = maxPos
    vtable.depth = -minPos
    vtable
  }

  // Glue is a concept from TeX which is a flexible space between elements in
  // either a vertical or horizontal list. In KaTeX, at least for now, it's
  // static space between elements in a horizontal layout.
  def makeGlue(measurement: Measurement, options: Options): DomSpan = {
    // Make an empty span for the space
    val rule = makeSpan(ArrayBuffer("mspace"), ArrayBuffer.empty, Nullable(options))
    val size = Units.calculateSize(measurement, options)
    rule.style = rule.style.copy(marginRight = Nullable(Units.makeEm(size)))
    rule
  }

  // Takes font options, and returns the appropriate fontLookup name
  private def retrieveTextFontName(
    fontFamily: String,
    fontWeight: String,
    fontShape:  String
  ): String = {
    val baseFontName = fontFamily match {
      case "amsrm"  => "AMS"
      case "textrm" => "Main"
      case "textsf" => "SansSerif"
      case "texttt" => "Typewriter"
      case other    => other // use fonts added by a plugin
    }

    val fontStylesName =
      if (fontWeight == "textbf" && fontShape == "textit") "BoldItalic"
      else if (fontWeight == "textbf") "Bold"
      else if (fontWeight == "textit") "Italic"
      else "Regular"

    s"$baseFontName-$fontStylesName"
  }

  /** Maps TeX font commands to objects containing:
    *   - variant: string used for "mathvariant" attribute in buildMathML.js
    *   - fontName: the "style" parameter to fontMetrics.getCharacterMetrics
    */
  // A map between tex font commands an MathML mathvariant attribute values
  val fontMap: Map[String, FontMapEntry] = Map(
    // styles
    "mathbf" -> FontMapEntry(FontVariant.Bold, "Main-Bold"),
    "mathrm" -> FontMapEntry(FontVariant.Normal, "Main-Regular"),
    "textit" -> FontMapEntry(FontVariant.Italic, "Main-Italic"),
    "mathit" -> FontMapEntry(FontVariant.Italic, "Main-Italic"),
    "mathnormal" -> FontMapEntry(FontVariant.Italic, "Math-Italic"),
    "mathsfit" -> FontMapEntry(FontVariant.SansSerifItalic, "SansSerif-Italic"),
    // "boldsymbol" is missing because they require the use of multiple fonts:
    // Math-BoldItalic and Main-Bold.  This is handled by a special case in
    // makeOrd which ends up calling boldsymbol.

    // families
    "mathbb" -> FontMapEntry(FontVariant.DoubleStruck, "AMS-Regular"),
    "mathcal" -> FontMapEntry(FontVariant.ScriptVariant, "Caligraphic-Regular"),
    "mathfrak" -> FontMapEntry(FontVariant.Fraktur, "Fraktur-Regular"),
    "mathscr" -> FontMapEntry(FontVariant.ScriptVariant, "Script-Regular"),
    "mathsf" -> FontMapEntry(FontVariant.SansSerif, "SansSerif-Regular"),
    "mathtt" -> FontMapEntry(FontVariant.Monospace, "Typewriter-Regular")
  )

  val svgData: Map[String, (String, Double, Double)] = Map(
    //   path, width, height
    "vec" -> ("vec", 0.471, 0.714), // values from the font glyph
    "oiintSize1" -> ("oiintSize1", 0.957, 0.499), // oval to overlay the integrand
    "oiintSize2" -> ("oiintSize2", 1.472, 0.659),
    "oiiintSize1" -> ("oiiintSize1", 1.304, 0.499),
    "oiiintSize2" -> ("oiiintSize2", 1.98, 0.659)
  )

  def staticSvg(value: String, options: Options): SvgSpan = {
    // Create a span with inline SVG for the element.
    val (pathName, width, height) = svgData(value)
    val path                      = new PathNode(pathName)
    val svgNode                   = new SvgNode(
      ArrayBuffer[SvgChildNode](path),
      scala.collection.mutable.LinkedHashMap(
        "width" -> Units.makeEm(width),
        "height" -> Units.makeEm(height),
        // Override CSS rule `.katex svg { width: 100% }`
        "style" -> ("width:" + Units.makeEm(width)),
        "viewBox" -> ("0 0 " + (1000 * width).toInt + " " + (1000 * height).toInt),
        "preserveAspectRatio" -> "xMinYMin"
      )
    )
    val span = makeSvgSpan(ArrayBuffer("overlay"), ArrayBuffer(svgNode), Nullable(options))
    span.height = height
    span.style = span.style.copy(
      height = Nullable(Units.makeEm(height)),
      width = Nullable(Units.makeEm(width))
    )
    span
  }
}
