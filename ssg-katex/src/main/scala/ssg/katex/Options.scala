/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * This file contains information about the options that the Parser carries
 * around with it while parsing. Data is held in an `Options` object, and when
 * recursing, a new `Options` object can be created with the `.with*` and
 * `.reset` functions.
 *
 * Original source: katex src/Options.ts
 * Original author: Khan Academy and contributors
 * Original license: MIT
 *
 * upstream-commit: 90de9794
 *
 * Migration notes:
 *   Renames: Options -> Options (same)
 *   Convention: TypeScript union "" -> empty string (same)
 *   Idiom: mutable _fontMetrics cache -> Nullable field
 */
package ssg
package katex

import lowlevel.Nullable
import ssg.katex.data.{ FontMetrics, OptionsLike }

// In these types, "" (empty string) means "no change".
type FontWeight = String // "textbf" | "textmd" | ""
type FontShape  = String // "textit" | "textup" | ""

private val sizeStyleMap: Array[Array[Int]] = Array(
  // Each element contains [textsize, scriptsize, scriptscriptsize].
  // The size mappings are taken from TeX with \normalsize=10pt.
  Array(1, 1, 1), // size1: [5, 5, 5]              \tiny
  Array(2, 1, 1), // size2: [6, 5, 5]
  Array(3, 1, 1), // size3: [7, 5, 5]              \scriptsize
  Array(4, 2, 1), // size4: [8, 6, 5]              \footnotesize
  Array(5, 2, 1), // size5: [9, 6, 5]              \small
  Array(6, 3, 1), // size6: [10, 7, 5]             \normalsize
  Array(7, 4, 2), // size7: [12, 8, 6]             \large
  Array(8, 6, 3), // size8: [14.4, 10, 7]          \Large
  Array(9, 7, 6), // size9: [17.28, 12, 10]        \LARGE
  Array(10, 8, 7), // size10: [20.74, 14.4, 12]     \huge
  Array(11, 10, 9) // size11: [24.88, 20.74, 17.28] \HUGE
)

private val sizeMultipliers: Array[Double] = Array(
  // fontMetrics.js:getGlobalMetrics also uses size indexes, so if
  // you change size indexes, change that function.
  0.5, 0.6, 0.7, 0.8, 0.9, 1.0, 1.2, 1.44, 1.728, 2.074, 2.488
)

private def sizeAtStyle(size: Int, style: Style): Int =
  if (style.size < 2) size else sizeStyleMap(size - 1)(style.size - 1)

/** This is the main options class. It contains the current style, size, color, and font.
  *
  * Options objects should not be modified. To create a new Options with different properties, call a `.having*` method.
  */
class Options(
  val style:              Style,
  val color:              Nullable[String] = Nullable.Null,
  sizeInit:               Int = 0,
  textSizeInit:           Int = 0,
  phantomInit:            Boolean = false,
  fontInit:               String = "",
  fontFamilyInit:         String = "",
  fontWeightInit:         FontWeight = "",
  fontShapeInit:          FontShape = "",
  val maxSize:            Double,
  val minRuleThickness:   Double,
  sizeMultiplierOverride: Double = -1.0 // sentinel: if >= 0, use this; else compute
) extends OptionsLike {

  val size:     Int     = if (sizeInit != 0) sizeInit else Options.BASESIZE
  val textSize: Int     = if (textSizeInit != 0) textSizeInit else size
  val phantom:  Boolean = phantomInit
  // A font family applies to a group of fonts (i.e. SansSerif), while a font
  // represents a specific font (i.e. SansSerif Bold).
  // See: https://tex.stackexchange.com/questions/22350/difference-between-textrm-and-mathrm
  val font:           String     = fontInit
  val fontFamily:     String     = fontFamilyInit
  val fontWeight:     FontWeight = fontWeightInit
  val fontShape:      FontShape  = fontShapeInit
  val sizeMultiplier: Double     =
    if (sizeMultiplierOverride >= 0) sizeMultiplierOverride
    else sizeMultipliers(size - 1)

  private var _fontMetrics: Nullable[FontMetrics] = Nullable.Null

  /** Returns a new options object with the same properties as "this". Properties from "extension" will be copied to the new options object.
    */
  def extend(
    style:            Style = this.style,
    color:            Nullable[String] = this.color,
    size:             Int = this.size,
    textSize:         Int = this.textSize,
    phantom:          Boolean = this.phantom,
    font:             String = this.font,
    fontFamily:       String = this.fontFamily,
    fontWeight:       FontWeight = this.fontWeight,
    fontShape:        FontShape = this.fontShape,
    maxSize:          Double = this.maxSize,
    minRuleThickness: Double = this.minRuleThickness,
    sizeMultiplier:   Double = -1.0
  ): Options =
    new Options(
      style = style,
      color = color,
      sizeInit = size,
      textSizeInit = textSize,
      phantomInit = phantom,
      fontInit = font,
      fontFamilyInit = fontFamily,
      fontWeightInit = fontWeight,
      fontShapeInit = fontShape,
      maxSize = maxSize,
      minRuleThickness = minRuleThickness,
      sizeMultiplierOverride = sizeMultiplier
    )

  /** Return an options object with the given style. If `this.style === style`, returns `this`.
    */
  override def havingStyle(style: Style): Options =
    if (this.style eq style) {
      this
    } else {
      extend(
        style = style,
        size = sizeAtStyle(this.textSize, style)
      )
    }

  /** Return an options object with a cramped version of the current style. If the current style is cramped, returns `this`.
    */
  def havingCrampedStyle(): Options =
    havingStyle(this.style.cramp())

  /** Return an options object with the given size and in at least `\textstyle`. Returns `this` if appropriate.
    */
  def havingSize(size: Int): Options =
    if (this.size == size && this.textSize == size) {
      this
    } else {
      extend(
        style = this.style.text(),
        size = size,
        textSize = size,
        sizeMultiplier = sizeMultipliers(size - 1)
      )
    }

  /** Like `this.havingSize(BASESIZE).havingStyle(style)`. If `style` is omitted, changes to at least `\textstyle`.
    */
  def havingBaseStyle(style: Style): Options = {
    val s        = if (style != null) style else this.style.text() // @nowarn — null check for TS compat
    val wantSize = sizeAtStyle(Options.BASESIZE, s)
    if (
      this.size == wantSize && this.textSize == Options.BASESIZE &&
      (this.style eq s)
    ) {
      this
    } else {
      extend(
        style = s,
        size = wantSize
      )
    }
  }

  /** Remove the effect of sizing changes such as \Huge. Keep the effect of the current style, such as \scriptstyle.
    */
  def havingBaseSizing(): Options = {
    val sz = this.style.id match {
      case 4 | 5 => 3 // normalsize in scriptstyle
      case 6 | 7 => 1 // normalsize in scriptscriptstyle
      case _     => 6 // normalsize in textstyle or displaystyle
    }
    extend(
      style = this.style.text(),
      size = sz
    )
  }

  /** Create a new options object with the given color.
    */
  def withColor(color: String): Options =
    extend(color = Nullable(color))

  /** Create a new options object with "phantom" set to true.
    */
  def withPhantom(): Options =
    extend(phantom = true)

  /** Creates a new options object with the given math font or old text font.
    * @type
    *   {[type]}
    */
  def withFont(font: String): Options =
    extend(font = font)

  /** Create a new options objects with the given fontFamily.
    */
  def withTextFontFamily(fontFamily: String): Options =
    extend(
      fontFamily = fontFamily,
      font = ""
    )

  /** Creates a new options object with the given font weight
    */
  def withTextFontWeight(fontWeight: FontWeight): Options =
    extend(
      fontWeight = fontWeight,
      font = ""
    )

  /** Creates a new options object with the given font weight
    */
  def withTextFontShape(fontShape: FontShape): Options =
    extend(
      fontShape = fontShape,
      font = ""
    )

  /** Return the CSS sizing classes required to switch from enclosing options `oldOptions` to `this`. Returns an array of classes.
    */
  def sizingClasses(oldOptions: Options): Array[String] =
    if (oldOptions.size != this.size) {
      Array("sizing", "reset-size" + oldOptions.size, "size" + this.size)
    } else {
      Array.empty
    }

  /** Return the CSS sizing classes required to switch to the base size. Like `this.havingSize(BASESIZE).sizingClasses(this)`.
    */
  def baseSizingClasses(): Array[String] =
    if (this.size != Options.BASESIZE) {
      Array("sizing", "reset-size" + this.size, "size" + Options.BASESIZE)
    } else {
      Array.empty
    }

  /** Return the font metrics for this size.
    */
  def fontMetrics(): FontMetrics = {
    if (_fontMetrics.isEmpty) {
      _fontMetrics = Nullable(ssg.katex.data.FontMetrics.getGlobalMetrics(this.size))
    }
    _fontMetrics.get
  }

  /** Gets the CSS color of the current options object
    */
  def getColor(): Nullable[String] =
    if (this.phantom) {
      Nullable("transparent")
    } else {
      this.color
    }
}

object Options {

  /** The base size index.
    */
  val BASESIZE: Int = 6
}
