/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * This file contains information and classes for the various kinds of styles
 * used in TeX. It provides a generic `Style` class, which holds information
 * about a specific style. It then provides instances of all the different kinds
 * of styles possible, and provides functions to move between them and get
 * information about them.
 *
 * Original source: katex src/Style.ts
 * Original author: Khan Academy and contributors
 * Original license: MIT
 *
 * upstream-commit: 90de9794
 */
package ssg
package katex

/** The main style class. Contains a unique id for the style, a size (which is the same for cramped and uncramped version of a style), and a cramped flag.
  */
final class Style private (val id: Int, val size: Int, val cramped: Boolean) {

  /** Get the style of a superscript given a base in the current style.
    */
  def sup(): Style =
    Style.styles(Style.supTable(id))

  /** Get the style of a subscript given a base in the current style.
    */
  def sub(): Style =
    Style.styles(Style.subTable(id))

  /** Get the style of a fraction numerator given the fraction in the current style.
    */
  def fracNum(): Style =
    Style.styles(Style.fracNumTable(id))

  /** Get the style of a fraction denominator given the fraction in the current style.
    */
  def fracDen(): Style =
    Style.styles(Style.fracDenTable(id))

  /** Get the cramped version of a style (in particular, cramping a cramped style doesn't change the style).
    */
  def cramp(): Style =
    Style.styles(Style.crampTable(id))

  /** Get a text or display version of this style.
    */
  def text(): Style =
    Style.styles(Style.textTable(id))

  /** Return true if this style is tightly spaced (scriptstyle/scriptscriptstyle)
    */
  def isTight(): Boolean =
    size >= 2

  override def toString: String = s"Style(id=$id, size=$size, cramped=$cramped)"
}

object Style {

  // IDs of the different styles
  private val D   = 0
  private val Dc  = 1
  private val T   = 2
  private val Tc  = 3
  private val S   = 4
  private val Sc  = 5
  private val SS  = 6
  private val SSc = 7

  // Instances of the different styles
  private val styles: Array[Style] = Array(
    new Style(D, 0, false),
    new Style(Dc, 0, true),
    new Style(T, 1, false),
    new Style(Tc, 1, true),
    new Style(S, 2, false),
    new Style(Sc, 2, true),
    new Style(SS, 3, false),
    new Style(SSc, 3, true)
  )

  // Lookup tables for switching from one style to another
  private val supTable:     Array[Int] = Array(S, Sc, S, Sc, SS, SSc, SS, SSc)
  private val subTable:     Array[Int] = Array(Sc, Sc, Sc, Sc, SSc, SSc, SSc, SSc)
  private val fracNumTable: Array[Int] = Array(T, Tc, S, Sc, SS, SSc, SS, SSc)
  private val fracDenTable: Array[Int] = Array(Tc, Tc, Sc, Sc, SSc, SSc, SSc, SSc)
  private val crampTable:   Array[Int] = Array(Dc, Dc, Tc, Tc, Sc, Sc, SSc, SSc)
  private val textTable:    Array[Int] = Array(D, Dc, T, Tc, T, Tc, T, Tc)

  // We only export some of the styles.
  val DISPLAY:      Style = styles(D)
  val TEXT:         Style = styles(T)
  val SCRIPT:       Style = styles(S)
  val SCRIPTSCRIPT: Style = styles(SS)
}
