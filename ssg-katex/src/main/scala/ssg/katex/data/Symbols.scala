/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * This file holds a list of all no-argument functions and single-character
 * symbols (like 'a' or ';').
 *
 * For each of the symbols, there are three properties they can have:
 * - font (required): the font to be used for this symbol. Either "main" (the
 *     normal font), or "ams" (the ams fonts).
 * - group (required): the ParseNode group type the symbol should have (i.e.
 *     "textord", "mathord", etc).
 *     See https://github.com/KaTeX/KaTeX/wiki/Examining-TeX#group-types
 * - replace: the character that this symbol or function should be
 *   replaced with (i.e. "\phi" has a replace value of "ϕ", the phi
 *   character in the main font).
 *
 * The outermost map in the table indicates what mode the symbols should be
 * accepted in (e.g. "math" or "text").
 *
 * Original source: katex src/symbols.ts
 * Original author: Khan Academy and contributors
 * Original license: MIT
 *
 * upstream-commit: 90de9794
 */
package ssg
package katex
package data

import lowlevel.Nullable

/** Symbol info: font, group, and optional replacement character.
  */
final case class SymbolInfo(font: String, group: String, replace: Nullable[String])

/** The symbol table. Maps mode -> name -> SymbolInfo.
  */
object Symbols {

  // Some of these have a "-token" suffix since these are also used as `ParseNode`
  // types for raw text tokens, and we want to avoid conflicts with higher-level
  // `ParseNode` types. These `ParseNode`s are constructed within `Parser` by
  // looking up the `symbols` map.
  val ATOMS: Map[String, Int] = Map(
    "bin" -> 1,
    "close" -> 1,
    "inner" -> 1,
    "open" -> 1,
    "punct" -> 1,
    "rel" -> 1
  )

  val NON_ATOMS: Map[String, Int] = Map(
    "accent-token" -> 1,
    "mathord" -> 1,
    "op-token" -> 1,
    "spacing" -> 1,
    "textord" -> 1
  )

  /** The mutable symbol maps, keyed by mode then by name. */
  /** The mutable symbol maps, keyed by mode then by name. */
  val mathMap: scala.collection.mutable.Map[String, SymbolInfo] =
    scala.collection.mutable.Map.empty
  val textMap: scala.collection.mutable.Map[String, SymbolInfo] =
    scala.collection.mutable.Map.empty

  // Public accessors using original JS names
  def math: scala.collection.mutable.Map[String, SymbolInfo] = mathMap
  def text: scala.collection.mutable.Map[String, SymbolInfo] = textMap

  private val mathSymbols: scala.collection.mutable.Map[String, SymbolInfo] = mathMap
  private val textSymbols: scala.collection.mutable.Map[String, SymbolInfo] = textMap

  private def symbolsForMode(mode: String): scala.collection.mutable.Map[String, SymbolInfo] =
    if (mode == "math") mathSymbols else textSymbols

  /** Look up a symbol by mode and name, returning Nullable. */
  def getSymbol(mode: Mode, name: String): Nullable[SymbolInfo] = {
    val m = if (mode == Mode.Math) mathSymbols else textSymbols
    m.get(name) match {
      case Some(info) => Nullable(info)
      case None       => Nullable.Null
    }
  }

  /** Get the symbols map for a given mode. */
  def apply(mode: Mode): scala.collection.mutable.Map[String, SymbolInfo] =
    if (mode == Mode.Math) mathSymbols else textSymbols

  /** Get the symbols map for a given mode string. */
  def apply(mode: String): scala.collection.mutable.Map[String, SymbolInfo] =
    symbolsForMode(mode)

  /** `acceptUnicodeChar = true` is only applicable if `replace` is set. */
  def defineSymbol(
    mode:              String,
    font:              String,
    group:             String,
    replace:           Nullable[String],
    name:              String,
    acceptUnicodeChar: Boolean = false
  ): Unit = {
    val info = SymbolInfo(font, group, replace)
    symbolsForMode(mode)(name) = info

    if (acceptUnicodeChar && replace.isDefined) {
      symbolsForMode(mode)(replace.get) = info
    }
  }

  // Some abbreviations for commonly used strings.
  // This helps minify the code, and also spotting typos using jshint.

  // modes:
  private val mathMode = "math"
  private val textMode = "text"

  // fonts:
  private val main = "main"
  private val ams  = "ams"

  // groups:
  private val accent  = "accent-token"
  private val bin     = "bin"
  private val close   = "close"
  private val inner   = "inner"
  private val mathord = "mathord"
  private val op      = "op-token"
  private val open    = "open"
  private val punct   = "punct"
  private val rel     = "rel"
  private val spacing = "spacing"
  private val textord = "textord"

  // These ligatures are detected and created in Parser's `formLigatures`.
  val ligatures: Map[String, Boolean] = Map(
    "--" -> true,
    "---" -> true,
    "``" -> true,
    "''" -> true
  )

  // We add these Latin-1 letters as symbols for backwards-compatibility,
  // but they are not actually in the font, nor are they supported by the
  // Unicode accent mechanism, so they fall back to Times font and look ugly.
  // TODO(edemaine): Fix this.
  val extraLatin: String = "ÐÞþ"

  // ── Symbol table initialization ────────────────────────────────────────

  private def initSymbols(): Unit = {
    // Now comes the symbol table

    // Relation Symbols
    defineSymbol(mathMode, main, rel, Nullable("≡"), "\\equiv", true)
    defineSymbol(mathMode, main, rel, Nullable("≺"), "\\prec", true)
    defineSymbol(mathMode, main, rel, Nullable("≻"), "\\succ", true)
    defineSymbol(mathMode, main, rel, Nullable("∼"), "\\sim", true)
    defineSymbol(mathMode, main, rel, Nullable("⊥"), "\\perp")
    defineSymbol(mathMode, main, rel, Nullable("⪯"), "\\preceq", true)
    defineSymbol(mathMode, main, rel, Nullable("⪰"), "\\succeq", true)
    defineSymbol(mathMode, main, rel, Nullable("≃"), "\\simeq", true)
    defineSymbol(mathMode, main, rel, Nullable("∣"), "\\mid", true)
    defineSymbol(mathMode, main, rel, Nullable("≪"), "\\ll", true)
    defineSymbol(mathMode, main, rel, Nullable("≫"), "\\gg", true)
    defineSymbol(mathMode, main, rel, Nullable("≍"), "\\asymp", true)
    defineSymbol(mathMode, main, rel, Nullable("∥"), "\\parallel")
    defineSymbol(mathMode, main, rel, Nullable("⋈"), "\\bowtie", true)
    defineSymbol(mathMode, main, rel, Nullable("⌣"), "\\smile", true)
    defineSymbol(mathMode, main, rel, Nullable("⊑"), "\\sqsubseteq", true)
    defineSymbol(mathMode, main, rel, Nullable("⊒"), "\\sqsupseteq", true)
    defineSymbol(mathMode, main, rel, Nullable("≐"), "\\doteq", true)
    defineSymbol(mathMode, main, rel, Nullable("⌢"), "\\frown", true)
    defineSymbol(mathMode, main, rel, Nullable("∋"), "\\ni", true)
    defineSymbol(mathMode, main, rel, Nullable("∝"), "\\propto", true)
    defineSymbol(mathMode, main, rel, Nullable("⊢"), "\\vdash", true)
    defineSymbol(mathMode, main, rel, Nullable("⊣"), "\\dashv", true)
    defineSymbol(mathMode, main, rel, Nullable("∋"), "\\owns")

    // Punctuation
    defineSymbol(mathMode, main, punct, Nullable("."), "\\ldotp")
    defineSymbol(mathMode, main, punct, Nullable("⋅"), "\\cdotp")
    // The KaTeX fonts do not contain U+00B7. Use the centered dot glyph at U+22C5
    // in both modes, but keep math-mode punctuation spacing only in math mode.
    defineSymbol(mathMode, main, punct, Nullable("⋅"), "·")
    defineSymbol(textMode, main, textord, Nullable("⋅"), "·")

    // Misc Symbols
    defineSymbol(mathMode, main, textord, Nullable("#"), "\\#")
    defineSymbol(textMode, main, textord, Nullable("#"), "\\#")
    defineSymbol(mathMode, main, textord, Nullable("&"), "\\&")
    defineSymbol(textMode, main, textord, Nullable("&"), "\\&")
    defineSymbol(mathMode, main, textord, Nullable("ℵ"), "\\aleph", true)
    defineSymbol(mathMode, main, textord, Nullable("∀"), "\\forall", true)
    defineSymbol(mathMode, main, textord, Nullable("ℏ"), "\\hbar", true)
    defineSymbol(mathMode, main, textord, Nullable("∃"), "\\exists", true)
    defineSymbol(mathMode, main, textord, Nullable("∇"), "\\nabla", true)
    defineSymbol(mathMode, main, textord, Nullable("♭"), "\\flat", true)
    defineSymbol(mathMode, main, textord, Nullable("ℓ"), "\\ell", true)
    defineSymbol(mathMode, main, textord, Nullable("♮"), "\\natural", true)
    defineSymbol(mathMode, main, textord, Nullable("♣"), "\\clubsuit", true)
    defineSymbol(mathMode, main, textord, Nullable("℘"), "\\wp", true)
    defineSymbol(mathMode, main, textord, Nullable("♯"), "\\sharp", true)
    defineSymbol(mathMode, main, textord, Nullable("♢"), "\\diamondsuit", true)
    defineSymbol(mathMode, main, textord, Nullable("ℜ"), "\\Re", true)
    defineSymbol(mathMode, main, textord, Nullable("♡"), "\\heartsuit", true)
    defineSymbol(mathMode, main, textord, Nullable("ℑ"), "\\Im", true)
    defineSymbol(mathMode, main, textord, Nullable("♠"), "\\spadesuit", true)
    defineSymbol(mathMode, main, textord, Nullable("§"), "\\S", true)
    defineSymbol(textMode, main, textord, Nullable("§"), "\\S")
    defineSymbol(mathMode, main, textord, Nullable("¶"), "\\P", true)
    defineSymbol(textMode, main, textord, Nullable("¶"), "\\P")

    // Math and Text
    defineSymbol(mathMode, main, textord, Nullable("†"), "\\dag")
    defineSymbol(textMode, main, textord, Nullable("†"), "\\dag")
    defineSymbol(textMode, main, textord, Nullable("†"), "\\textdagger")
    defineSymbol(mathMode, main, textord, Nullable("‡"), "\\ddag")
    defineSymbol(textMode, main, textord, Nullable("‡"), "\\ddag")
    defineSymbol(textMode, main, textord, Nullable("‡"), "\\textdaggerdbl")

    // Large Delimiters
    defineSymbol(mathMode, main, close, Nullable("⎱"), "\\rmoustache", true)
    defineSymbol(mathMode, main, open, Nullable("⎰"), "\\lmoustache", true)
    defineSymbol(mathMode, main, close, Nullable("⟯"), "\\rgroup", true)
    defineSymbol(mathMode, main, open, Nullable("⟮"), "\\lgroup", true)

    // Binary Operators
    defineSymbol(mathMode, main, bin, Nullable("∓"), "\\mp", true)
    defineSymbol(mathMode, main, bin, Nullable("⊖"), "\\ominus", true)
    defineSymbol(mathMode, main, bin, Nullable("⊎"), "\\uplus", true)
    defineSymbol(mathMode, main, bin, Nullable("⊓"), "\\sqcap", true)
    defineSymbol(mathMode, main, bin, Nullable("∗"), "\\ast")
    defineSymbol(mathMode, main, bin, Nullable("⊔"), "\\sqcup", true)
    defineSymbol(mathMode, main, bin, Nullable("◯"), "\\bigcirc", true)
    defineSymbol(mathMode, main, bin, Nullable("∙"), "\\bullet", true)
    defineSymbol(mathMode, main, bin, Nullable("‡"), "\\ddagger")
    defineSymbol(mathMode, main, bin, Nullable("≀"), "\\wr", true)
    defineSymbol(mathMode, main, bin, Nullable("⨿"), "\\amalg")
    defineSymbol(mathMode, main, bin, Nullable("&"), "\\And") // from amsmath

    // Arrow Symbols
    defineSymbol(mathMode, main, rel, Nullable("⟵"), "\\longleftarrow", true)
    defineSymbol(mathMode, main, rel, Nullable("⇐"), "\\Leftarrow", true)
    defineSymbol(mathMode, main, rel, Nullable("⟸"), "\\Longleftarrow", true)
    defineSymbol(mathMode, main, rel, Nullable("⟶"), "\\longrightarrow", true)
    defineSymbol(mathMode, main, rel, Nullable("⇒"), "\\Rightarrow", true)
    defineSymbol(mathMode, main, rel, Nullable("⟹"), "\\Longrightarrow", true)
    defineSymbol(mathMode, main, rel, Nullable("↔"), "\\leftrightarrow", true)
    defineSymbol(mathMode, main, rel, Nullable("⟷"), "\\longleftrightarrow", true)
    defineSymbol(mathMode, main, rel, Nullable("⇔"), "\\Leftrightarrow", true)
    defineSymbol(mathMode, main, rel, Nullable("⟺"), "\\Longleftrightarrow", true)
    defineSymbol(mathMode, main, rel, Nullable("↦"), "\\mapsto", true)
    defineSymbol(mathMode, main, rel, Nullable("⟼"), "\\longmapsto", true)
    defineSymbol(mathMode, main, rel, Nullable("↗"), "\\nearrow", true)
    defineSymbol(mathMode, main, rel, Nullable("↩"), "\\hookleftarrow", true)
    defineSymbol(mathMode, main, rel, Nullable("↪"), "\\hookrightarrow", true)
    defineSymbol(mathMode, main, rel, Nullable("↘"), "\\searrow", true)
    defineSymbol(mathMode, main, rel, Nullable("↼"), "\\leftharpoonup", true)
    defineSymbol(mathMode, main, rel, Nullable("⇀"), "\\rightharpoonup", true)
    defineSymbol(mathMode, main, rel, Nullable("↙"), "\\swarrow", true)
    defineSymbol(mathMode, main, rel, Nullable("↽"), "\\leftharpoondown", true)
    defineSymbol(mathMode, main, rel, Nullable("⇁"), "\\rightharpoondown", true)
    defineSymbol(mathMode, main, rel, Nullable("↖"), "\\nwarrow", true)
    defineSymbol(mathMode, main, rel, Nullable("⇌"), "\\rightleftharpoons", true)

    // AMS Negated Binary Relations
    defineSymbol(mathMode, ams, rel, Nullable("≮"), "\\nless", true)
    // Symbol names preceded by "@" each have a corresponding macro.
    defineSymbol(mathMode, ams, rel, Nullable(""), "\\@nleqslant")
    defineSymbol(mathMode, ams, rel, Nullable(""), "\\@nleqq")
    defineSymbol(mathMode, ams, rel, Nullable("⪇"), "\\lneq", true)
    defineSymbol(mathMode, ams, rel, Nullable("≨"), "\\lneqq", true)
    defineSymbol(mathMode, ams, rel, Nullable(""), "\\@lvertneqq")
    defineSymbol(mathMode, ams, rel, Nullable("⋦"), "\\lnsim", true)
    defineSymbol(mathMode, ams, rel, Nullable("⪉"), "\\lnapprox", true)
    defineSymbol(mathMode, ams, rel, Nullable("⊀"), "\\nprec", true)
    // unicode-math maps ⋠ to \npreccurlyeq. We'll use the AMS synonym.
    defineSymbol(mathMode, ams, rel, Nullable("⋠"), "\\npreceq", true)
    defineSymbol(mathMode, ams, rel, Nullable("⋨"), "\\precnsim", true)
    defineSymbol(mathMode, ams, rel, Nullable("⪹"), "\\precnapprox", true)
    defineSymbol(mathMode, ams, rel, Nullable("≁"), "\\nsim", true)
    defineSymbol(mathMode, ams, rel, Nullable(""), "\\@nshortmid")
    defineSymbol(mathMode, ams, rel, Nullable("∤"), "\\nmid", true)
    defineSymbol(mathMode, ams, rel, Nullable("⊬"), "\\nvdash", true)
    defineSymbol(mathMode, ams, rel, Nullable("⊭"), "\\nvDash", true)
    defineSymbol(mathMode, ams, rel, Nullable("⋪"), "\\ntriangleleft")
    defineSymbol(mathMode, ams, rel, Nullable("⋬"), "\\ntrianglelefteq", true)
    defineSymbol(mathMode, ams, rel, Nullable("⊊"), "\\subsetneq", true)
    defineSymbol(mathMode, ams, rel, Nullable(""), "\\@varsubsetneq")
    defineSymbol(mathMode, ams, rel, Nullable("⫋"), "\\subsetneqq", true)
    defineSymbol(mathMode, ams, rel, Nullable(""), "\\@varsubsetneqq")
    defineSymbol(mathMode, ams, rel, Nullable("≯"), "\\ngtr", true)
    defineSymbol(mathMode, ams, rel, Nullable(""), "\\@ngeqslant")
    defineSymbol(mathMode, ams, rel, Nullable(""), "\\@ngeqq")
    defineSymbol(mathMode, ams, rel, Nullable("⪈"), "\\gneq", true)
    defineSymbol(mathMode, ams, rel, Nullable("≩"), "\\gneqq", true)
    defineSymbol(mathMode, ams, rel, Nullable(""), "\\@gvertneqq")
    defineSymbol(mathMode, ams, rel, Nullable("⋧"), "\\gnsim", true)
    defineSymbol(mathMode, ams, rel, Nullable("⪊"), "\\gnapprox", true)
    defineSymbol(mathMode, ams, rel, Nullable("⊁"), "\\nsucc", true)
    // unicode-math maps ⋡ to \nsucccurlyeq. We'll use the AMS synonym.
    defineSymbol(mathMode, ams, rel, Nullable("⋡"), "\\nsucceq", true)
    defineSymbol(mathMode, ams, rel, Nullable("⋩"), "\\succnsim", true)
    defineSymbol(mathMode, ams, rel, Nullable("⪺"), "\\succnapprox", true)
    // unicode-math maps ≆ to \simneqq. We'll use the AMS synonym.
    defineSymbol(mathMode, ams, rel, Nullable("≆"), "\\ncong", true)
    defineSymbol(mathMode, ams, rel, Nullable(""), "\\@nshortparallel")
    defineSymbol(mathMode, ams, rel, Nullable("∦"), "\\nparallel", true)
    defineSymbol(mathMode, ams, rel, Nullable("⊯"), "\\nVDash", true)
    defineSymbol(mathMode, ams, rel, Nullable("⋫"), "\\ntriangleright")
    defineSymbol(mathMode, ams, rel, Nullable("⋭"), "\\ntrianglerighteq", true)
    defineSymbol(mathMode, ams, rel, Nullable(""), "\\@nsupseteqq")
    defineSymbol(mathMode, ams, rel, Nullable("⊋"), "\\supsetneq", true)
    defineSymbol(mathMode, ams, rel, Nullable(""), "\\@varsupsetneq")
    defineSymbol(mathMode, ams, rel, Nullable("⫌"), "\\supsetneqq", true)
    defineSymbol(mathMode, ams, rel, Nullable(""), "\\@varsupsetneqq")
    defineSymbol(mathMode, ams, rel, Nullable("⊮"), "\\nVdash", true)
    defineSymbol(mathMode, ams, rel, Nullable("⪵"), "\\precneqq", true)
    defineSymbol(mathMode, ams, rel, Nullable("⪶"), "\\succneqq", true)
    defineSymbol(mathMode, ams, rel, Nullable(""), "\\@nsubseteqq")
    defineSymbol(mathMode, ams, bin, Nullable("⊴"), "\\unlhd")
    defineSymbol(mathMode, ams, bin, Nullable("⊵"), "\\unrhd")

    // AMS Negated Arrows
    defineSymbol(mathMode, ams, rel, Nullable("↚"), "\\nleftarrow", true)
    defineSymbol(mathMode, ams, rel, Nullable("↛"), "\\nrightarrow", true)
    defineSymbol(mathMode, ams, rel, Nullable("⇍"), "\\nLeftarrow", true)
    defineSymbol(mathMode, ams, rel, Nullable("⇏"), "\\nRightarrow", true)
    defineSymbol(mathMode, ams, rel, Nullable("↮"), "\\nleftrightarrow", true)
    defineSymbol(mathMode, ams, rel, Nullable("⇎"), "\\nLeftrightarrow", true)

    // AMS Misc
    defineSymbol(mathMode, ams, rel, Nullable("△"), "\\vartriangle")
    defineSymbol(mathMode, ams, textord, Nullable("ℏ"), "\\hslash")
    defineSymbol(mathMode, ams, textord, Nullable("▽"), "\\triangledown")
    defineSymbol(mathMode, ams, textord, Nullable("◊"), "\\lozenge")
    defineSymbol(mathMode, ams, textord, Nullable("Ⓢ"), "\\circledS")
    defineSymbol(mathMode, ams, textord, Nullable("®"), "\\circledR")
    defineSymbol(textMode, ams, textord, Nullable("®"), "\\circledR")
    defineSymbol(mathMode, ams, textord, Nullable("∡"), "\\measuredangle", true)
    defineSymbol(mathMode, ams, textord, Nullable("∄"), "\\nexists")
    defineSymbol(mathMode, ams, textord, Nullable("℧"), "\\mho")
    defineSymbol(mathMode, ams, textord, Nullable("Ⅎ"), "\\Finv", true)
    defineSymbol(mathMode, ams, textord, Nullable("⅁"), "\\Game", true)
    defineSymbol(mathMode, ams, textord, Nullable("‵"), "\\backprime")
    defineSymbol(mathMode, ams, textord, Nullable("▲"), "\\blacktriangle")
    defineSymbol(mathMode, ams, textord, Nullable("▼"), "\\blacktriangledown")
    defineSymbol(mathMode, ams, textord, Nullable("■"), "\\blacksquare")
    defineSymbol(mathMode, ams, textord, Nullable("⧫"), "\\blacklozenge")
    defineSymbol(mathMode, ams, textord, Nullable("★"), "\\bigstar")
    defineSymbol(mathMode, ams, textord, Nullable("∢"), "\\sphericalangle", true)
    defineSymbol(mathMode, ams, textord, Nullable("∁"), "\\complement", true)
    // unicode-math maps U+F0 to \matheth. We map to AMS function \eth
    defineSymbol(mathMode, ams, textord, Nullable("ð"), "\\eth", true)
    defineSymbol(textMode, main, textord, Nullable("ð"), "ð")
    defineSymbol(mathMode, ams, textord, Nullable("╱"), "\\diagup")
    defineSymbol(mathMode, ams, textord, Nullable("╲"), "\\diagdown")
    defineSymbol(mathMode, ams, textord, Nullable("□"), "\\square")
    defineSymbol(mathMode, ams, textord, Nullable("□"), "\\Box")
    defineSymbol(mathMode, ams, textord, Nullable("◊"), "\\Diamond")
    // unicode-math maps U+A5 to \mathyen. We map to AMS function \yen
    defineSymbol(mathMode, ams, textord, Nullable("¥"), "\\yen", true)
    defineSymbol(textMode, ams, textord, Nullable("¥"), "\\yen", true)
    defineSymbol(mathMode, ams, textord, Nullable("✓"), "\\checkmark", true)
    defineSymbol(textMode, ams, textord, Nullable("✓"), "\\checkmark")

    // AMS Hebrew
    defineSymbol(mathMode, ams, textord, Nullable("ℶ"), "\\beth", true)
    defineSymbol(mathMode, ams, textord, Nullable("ℸ"), "\\daleth", true)
    defineSymbol(mathMode, ams, textord, Nullable("ℷ"), "\\gimel", true)

    // AMS Greek
    defineSymbol(mathMode, ams, textord, Nullable("ϝ"), "\\digamma", true)
    defineSymbol(mathMode, ams, textord, Nullable("ϰ"), "\\varkappa")

    // AMS Delimiters
    defineSymbol(mathMode, ams, open, Nullable("┌"), "\\@ulcorner", true)
    defineSymbol(mathMode, ams, close, Nullable("┐"), "\\@urcorner", true)
    defineSymbol(mathMode, ams, open, Nullable("└"), "\\@llcorner", true)
    defineSymbol(mathMode, ams, close, Nullable("┘"), "\\@lrcorner", true)

    // AMS Binary Relations
    defineSymbol(mathMode, ams, rel, Nullable("≦"), "\\leqq", true)
    defineSymbol(mathMode, ams, rel, Nullable("⩽"), "\\leqslant", true)
    defineSymbol(mathMode, ams, rel, Nullable("⪕"), "\\eqslantless", true)
    defineSymbol(mathMode, ams, rel, Nullable("≲"), "\\lesssim", true)
    defineSymbol(mathMode, ams, rel, Nullable("⪅"), "\\lessapprox", true)
    defineSymbol(mathMode, ams, rel, Nullable("≊"), "\\approxeq", true)
    defineSymbol(mathMode, ams, bin, Nullable("⋖"), "\\lessdot")
    defineSymbol(mathMode, ams, rel, Nullable("⋘"), "\\lll", true)
    defineSymbol(mathMode, ams, rel, Nullable("≶"), "\\lessgtr", true)
    defineSymbol(mathMode, ams, rel, Nullable("⋚"), "\\lesseqgtr", true)
    defineSymbol(mathMode, ams, rel, Nullable("⪋"), "\\lesseqqgtr", true)
    defineSymbol(mathMode, ams, rel, Nullable("≑"), "\\doteqdot")
    defineSymbol(mathMode, ams, rel, Nullable("≓"), "\\risingdotseq", true)
    defineSymbol(mathMode, ams, rel, Nullable("≒"), "\\fallingdotseq", true)
    defineSymbol(mathMode, ams, rel, Nullable("∽"), "\\backsim", true)
    defineSymbol(mathMode, ams, rel, Nullable("⋍"), "\\backsimeq", true)
    defineSymbol(mathMode, ams, rel, Nullable("⫅"), "\\subseteqq", true)
    defineSymbol(mathMode, ams, rel, Nullable("⋐"), "\\Subset", true)
    defineSymbol(mathMode, ams, rel, Nullable("⊏"), "\\sqsubset", true)
    defineSymbol(mathMode, ams, rel, Nullable("≼"), "\\preccurlyeq", true)
    defineSymbol(mathMode, ams, rel, Nullable("⋞"), "\\curlyeqprec", true)
    defineSymbol(mathMode, ams, rel, Nullable("≾"), "\\precsim", true)
    defineSymbol(mathMode, ams, rel, Nullable("⪷"), "\\precapprox", true)
    defineSymbol(mathMode, ams, rel, Nullable("⊲"), "\\vartriangleleft")
    defineSymbol(mathMode, ams, rel, Nullable("⊴"), "\\trianglelefteq")
    defineSymbol(mathMode, ams, rel, Nullable("⊨"), "\\vDash", true)
    defineSymbol(mathMode, ams, rel, Nullable("⊪"), "\\Vvdash", true)
    defineSymbol(mathMode, ams, rel, Nullable("⌣"), "\\smallsmile")
    defineSymbol(mathMode, ams, rel, Nullable("⌢"), "\\smallfrown")
    defineSymbol(mathMode, ams, rel, Nullable("≏"), "\\bumpeq", true)
    defineSymbol(mathMode, ams, rel, Nullable("≎"), "\\Bumpeq", true)
    defineSymbol(mathMode, ams, rel, Nullable("≧"), "\\geqq", true)
    defineSymbol(mathMode, ams, rel, Nullable("⩾"), "\\geqslant", true)
    defineSymbol(mathMode, ams, rel, Nullable("⪖"), "\\eqslantgtr", true)
    defineSymbol(mathMode, ams, rel, Nullable("≳"), "\\gtrsim", true)
    defineSymbol(mathMode, ams, rel, Nullable("⪆"), "\\gtrapprox", true)
    defineSymbol(mathMode, ams, bin, Nullable("⋗"), "\\gtrdot")
    defineSymbol(mathMode, ams, rel, Nullable("⋙"), "\\ggg", true)
    defineSymbol(mathMode, ams, rel, Nullable("≷"), "\\gtrless", true)
    defineSymbol(mathMode, ams, rel, Nullable("⋛"), "\\gtreqless", true)
    defineSymbol(mathMode, ams, rel, Nullable("⪌"), "\\gtreqqless", true)
    defineSymbol(mathMode, ams, rel, Nullable("≖"), "\\eqcirc", true)
    defineSymbol(mathMode, ams, rel, Nullable("≗"), "\\circeq", true)
    defineSymbol(mathMode, ams, rel, Nullable("≜"), "\\triangleq", true)
    defineSymbol(mathMode, ams, rel, Nullable("∼"), "\\thicksim")
    defineSymbol(mathMode, ams, rel, Nullable("≈"), "\\thickapprox")
    defineSymbol(mathMode, ams, rel, Nullable("⫆"), "\\supseteqq", true)
    defineSymbol(mathMode, ams, rel, Nullable("⋑"), "\\Supset", true)
    defineSymbol(mathMode, ams, rel, Nullable("⊐"), "\\sqsupset", true)
    defineSymbol(mathMode, ams, rel, Nullable("≽"), "\\succcurlyeq", true)
    defineSymbol(mathMode, ams, rel, Nullable("⋟"), "\\curlyeqsucc", true)
    defineSymbol(mathMode, ams, rel, Nullable("≿"), "\\succsim", true)
    defineSymbol(mathMode, ams, rel, Nullable("⪸"), "\\succapprox", true)
    defineSymbol(mathMode, ams, rel, Nullable("⊳"), "\\vartriangleright")
    defineSymbol(mathMode, ams, rel, Nullable("⊵"), "\\trianglerighteq")
    defineSymbol(mathMode, ams, rel, Nullable("⊩"), "\\Vdash", true)
    defineSymbol(mathMode, ams, rel, Nullable("∣"), "\\shortmid")
    defineSymbol(mathMode, ams, rel, Nullable("∥"), "\\shortparallel")
    defineSymbol(mathMode, ams, rel, Nullable("≬"), "\\between", true)
    defineSymbol(mathMode, ams, rel, Nullable("⋔"), "\\pitchfork", true)
    defineSymbol(mathMode, ams, rel, Nullable("∝"), "\\varpropto")
    defineSymbol(mathMode, ams, rel, Nullable("◀"), "\\blacktriangleleft")
    // unicode-math says that \therefore is a mathord atom.
    // We kept the amssymb atom type, which is rel.
    defineSymbol(mathMode, ams, rel, Nullable("∴"), "\\therefore", true)
    defineSymbol(mathMode, ams, rel, Nullable("∍"), "\\backepsilon")
    defineSymbol(mathMode, ams, rel, Nullable("▶"), "\\blacktriangleright")
    // unicode-math says that \because is a mathord atom.
    // We kept the amssymb atom type, which is rel.
    defineSymbol(mathMode, ams, rel, Nullable("∵"), "\\because", true)
    defineSymbol(mathMode, ams, rel, Nullable("⋘"), "\\llless")
    defineSymbol(mathMode, ams, rel, Nullable("⋙"), "\\gggtr")
    defineSymbol(mathMode, ams, bin, Nullable("⊲"), "\\lhd")
    defineSymbol(mathMode, ams, bin, Nullable("⊳"), "\\rhd")
    defineSymbol(mathMode, ams, rel, Nullable("≂"), "\\eqsim", true)
    defineSymbol(mathMode, main, rel, Nullable("⋈"), "\\Join")
    defineSymbol(mathMode, ams, rel, Nullable("≑"), "\\Doteq", true)

    // AMS Binary Operators
    defineSymbol(mathMode, ams, bin, Nullable("∔"), "\\dotplus", true)
    defineSymbol(mathMode, ams, bin, Nullable("∖"), "\\smallsetminus")
    defineSymbol(mathMode, ams, bin, Nullable("⋒"), "\\Cap", true)
    defineSymbol(mathMode, ams, bin, Nullable("⋓"), "\\Cup", true)
    defineSymbol(mathMode, ams, bin, Nullable("⩞"), "\\doublebarwedge", true)
    defineSymbol(mathMode, ams, bin, Nullable("⊟"), "\\boxminus", true)
    defineSymbol(mathMode, ams, bin, Nullable("⊞"), "\\boxplus", true)
    defineSymbol(mathMode, ams, bin, Nullable("⋇"), "\\divideontimes", true)
    defineSymbol(mathMode, ams, bin, Nullable("⋉"), "\\ltimes", true)
    defineSymbol(mathMode, ams, bin, Nullable("⋊"), "\\rtimes", true)
    defineSymbol(mathMode, ams, bin, Nullable("⋋"), "\\leftthreetimes", true)
    defineSymbol(mathMode, ams, bin, Nullable("⋌"), "\\rightthreetimes", true)
    defineSymbol(mathMode, ams, bin, Nullable("⋏"), "\\curlywedge", true)
    defineSymbol(mathMode, ams, bin, Nullable("⋎"), "\\curlyvee", true)
    defineSymbol(mathMode, ams, bin, Nullable("⊝"), "\\circleddash", true)
    defineSymbol(mathMode, ams, bin, Nullable("⊛"), "\\circledast", true)
    defineSymbol(mathMode, ams, bin, Nullable("⋅"), "\\centerdot")
    defineSymbol(mathMode, ams, bin, Nullable("⊺"), "\\intercal", true)
    defineSymbol(mathMode, ams, bin, Nullable("⋒"), "\\doublecap")
    defineSymbol(mathMode, ams, bin, Nullable("⋓"), "\\doublecup")
    defineSymbol(mathMode, ams, bin, Nullable("⊠"), "\\boxtimes", true)

    // AMS Arrows
    // Note: unicode-math maps ⇢ to their own function \rightdasharrow.
    // We'll map it to AMS function \dashrightarrow. It produces the same atom.
    defineSymbol(mathMode, ams, rel, Nullable("⇢"), "\\dashrightarrow", true)
    // unicode-math maps ⇠ to \leftdasharrow. We'll use the AMS synonym.
    defineSymbol(mathMode, ams, rel, Nullable("⇠"), "\\dashleftarrow", true)
    defineSymbol(mathMode, ams, rel, Nullable("⇇"), "\\leftleftarrows", true)
    defineSymbol(mathMode, ams, rel, Nullable("⇆"), "\\leftrightarrows", true)
    defineSymbol(mathMode, ams, rel, Nullable("⇚"), "\\Lleftarrow", true)
    defineSymbol(mathMode, ams, rel, Nullable("↞"), "\\twoheadleftarrow", true)
    defineSymbol(mathMode, ams, rel, Nullable("↢"), "\\leftarrowtail", true)
    defineSymbol(mathMode, ams, rel, Nullable("↫"), "\\looparrowleft", true)
    defineSymbol(mathMode, ams, rel, Nullable("⇋"), "\\leftrightharpoons", true)
    defineSymbol(mathMode, ams, rel, Nullable("↶"), "\\curvearrowleft", true)
    // unicode-math maps ↺ to \acwopencirclearrow. We'll use the AMS synonym.
    defineSymbol(mathMode, ams, rel, Nullable("↺"), "\\circlearrowleft", true)
    defineSymbol(mathMode, ams, rel, Nullable("↰"), "\\Lsh", true)
    defineSymbol(mathMode, ams, rel, Nullable("⇈"), "\\upuparrows", true)
    defineSymbol(mathMode, ams, rel, Nullable("↿"), "\\upharpoonleft", true)
    defineSymbol(mathMode, ams, rel, Nullable("⇃"), "\\downharpoonleft", true)
    defineSymbol(mathMode, main, rel, Nullable("⊶"), "\\origof", true) // not in font
    defineSymbol(mathMode, main, rel, Nullable("⊷"), "\\imageof", true) // not in font
    defineSymbol(mathMode, ams, rel, Nullable("⊸"), "\\multimap", true)
    defineSymbol(mathMode, ams, rel, Nullable("↭"), "\\leftrightsquigarrow", true)
    defineSymbol(mathMode, ams, rel, Nullable("⇉"), "\\rightrightarrows", true)
    defineSymbol(mathMode, ams, rel, Nullable("⇄"), "\\rightleftarrows", true)
    defineSymbol(mathMode, ams, rel, Nullable("↠"), "\\twoheadrightarrow", true)
    defineSymbol(mathMode, ams, rel, Nullable("↣"), "\\rightarrowtail", true)
    defineSymbol(mathMode, ams, rel, Nullable("↬"), "\\looparrowright", true)
    defineSymbol(mathMode, ams, rel, Nullable("↷"), "\\curvearrowright", true)
    // unicode-math maps ↻ to \cwopencirclearrow. We'll use the AMS synonym.
    defineSymbol(mathMode, ams, rel, Nullable("↻"), "\\circlearrowright", true)
    defineSymbol(mathMode, ams, rel, Nullable("↱"), "\\Rsh", true)
    defineSymbol(mathMode, ams, rel, Nullable("⇊"), "\\downdownarrows", true)
    defineSymbol(mathMode, ams, rel, Nullable("↾"), "\\upharpoonright", true)
    defineSymbol(mathMode, ams, rel, Nullable("⇂"), "\\downharpoonright", true)
    defineSymbol(mathMode, ams, rel, Nullable("⇝"), "\\rightsquigarrow", true)
    defineSymbol(mathMode, ams, rel, Nullable("⇝"), "\\leadsto")
    defineSymbol(mathMode, ams, rel, Nullable("⇛"), "\\Rrightarrow", true)
    defineSymbol(mathMode, ams, rel, Nullable("↾"), "\\restriction")

    defineSymbol(mathMode, main, textord, Nullable("‘"), "`")
    defineSymbol(mathMode, main, textord, Nullable("$"), "\\$")
    defineSymbol(textMode, main, textord, Nullable("$"), "\\$")
    defineSymbol(textMode, main, textord, Nullable("$"), "\\textdollar")
    defineSymbol(mathMode, main, textord, Nullable("%"), "\\%")
    defineSymbol(textMode, main, textord, Nullable("%"), "\\%")
    defineSymbol(mathMode, main, textord, Nullable("_"), "\\_")
    defineSymbol(textMode, main, textord, Nullable("_"), "\\_")
    defineSymbol(textMode, main, textord, Nullable("_"), "\\textunderscore")
    defineSymbol(mathMode, main, textord, Nullable("∠"), "\\angle", true)
    defineSymbol(mathMode, main, textord, Nullable("∞"), "\\infty", true)
    defineSymbol(mathMode, main, textord, Nullable("′"), "\\prime")
    defineSymbol(mathMode, main, textord, Nullable("△"), "\\triangle")
    defineSymbol(mathMode, main, textord, Nullable("Γ"), "\\Gamma", true)
    defineSymbol(mathMode, main, textord, Nullable("Δ"), "\\Delta", true)
    defineSymbol(mathMode, main, textord, Nullable("Θ"), "\\Theta", true)
    defineSymbol(mathMode, main, textord, Nullable("Λ"), "\\Lambda", true)
    defineSymbol(mathMode, main, textord, Nullable("Ξ"), "\\Xi", true)
    defineSymbol(mathMode, main, textord, Nullable("Π"), "\\Pi", true)
    defineSymbol(mathMode, main, textord, Nullable("Σ"), "\\Sigma", true)
    defineSymbol(mathMode, main, textord, Nullable("Υ"), "\\Upsilon", true)
    defineSymbol(mathMode, main, textord, Nullable("Φ"), "\\Phi", true)
    defineSymbol(mathMode, main, textord, Nullable("Ψ"), "\\Psi", true)
    defineSymbol(mathMode, main, textord, Nullable("Ω"), "\\Omega", true)
    defineSymbol(mathMode, main, textord, Nullable("A"), "Α")
    defineSymbol(mathMode, main, textord, Nullable("B"), "Β")
    defineSymbol(mathMode, main, textord, Nullable("E"), "Ε")
    defineSymbol(mathMode, main, textord, Nullable("Z"), "Ζ")
    defineSymbol(mathMode, main, textord, Nullable("H"), "Η")
    defineSymbol(mathMode, main, textord, Nullable("I"), "Ι")
    defineSymbol(mathMode, main, textord, Nullable("K"), "Κ")
    defineSymbol(mathMode, main, textord, Nullable("M"), "Μ")
    defineSymbol(mathMode, main, textord, Nullable("N"), "Ν")
    defineSymbol(mathMode, main, textord, Nullable("O"), "Ο")
    defineSymbol(mathMode, main, textord, Nullable("P"), "Ρ")
    defineSymbol(mathMode, main, textord, Nullable("T"), "Τ")
    defineSymbol(mathMode, main, textord, Nullable("X"), "Χ")
    defineSymbol(mathMode, main, textord, Nullable("¬"), "\\neg", true)
    defineSymbol(mathMode, main, textord, Nullable("¬"), "\\lnot")
    defineSymbol(mathMode, main, textord, Nullable("⊤"), "\\top")
    defineSymbol(mathMode, main, textord, Nullable("⊥"), "\\bot")
    defineSymbol(mathMode, main, textord, Nullable("∅"), "\\emptyset")
    defineSymbol(mathMode, ams, textord, Nullable("∅"), "\\varnothing")
    defineSymbol(mathMode, main, mathord, Nullable("α"), "\\alpha", true)
    defineSymbol(mathMode, main, mathord, Nullable("β"), "\\beta", true)
    defineSymbol(mathMode, main, mathord, Nullable("γ"), "\\gamma", true)
    defineSymbol(mathMode, main, mathord, Nullable("δ"), "\\delta", true)
    defineSymbol(mathMode, main, mathord, Nullable("ϵ"), "\\epsilon", true)
    defineSymbol(mathMode, main, mathord, Nullable("ζ"), "\\zeta", true)
    defineSymbol(mathMode, main, mathord, Nullable("η"), "\\eta", true)
    defineSymbol(mathMode, main, mathord, Nullable("θ"), "\\theta", true)
    defineSymbol(mathMode, main, mathord, Nullable("ι"), "\\iota", true)
    defineSymbol(mathMode, main, mathord, Nullable("κ"), "\\kappa", true)
    defineSymbol(mathMode, main, mathord, Nullable("λ"), "\\lambda", true)
    defineSymbol(mathMode, main, mathord, Nullable("μ"), "\\mu", true)
    defineSymbol(mathMode, main, mathord, Nullable("ν"), "\\nu", true)
    defineSymbol(mathMode, main, mathord, Nullable("ξ"), "\\xi", true)
    defineSymbol(mathMode, main, mathord, Nullable("ο"), "\\omicron", true)
    defineSymbol(mathMode, main, mathord, Nullable("π"), "\\pi", true)
    defineSymbol(mathMode, main, mathord, Nullable("ρ"), "\\rho", true)
    defineSymbol(mathMode, main, mathord, Nullable("σ"), "\\sigma", true)
    defineSymbol(mathMode, main, mathord, Nullable("τ"), "\\tau", true)
    defineSymbol(mathMode, main, mathord, Nullable("υ"), "\\upsilon", true)
    defineSymbol(mathMode, main, mathord, Nullable("ϕ"), "\\phi", true)
    defineSymbol(mathMode, main, mathord, Nullable("χ"), "\\chi", true)
    defineSymbol(mathMode, main, mathord, Nullable("ψ"), "\\psi", true)
    defineSymbol(mathMode, main, mathord, Nullable("ω"), "\\omega", true)
    defineSymbol(mathMode, main, mathord, Nullable("ε"), "\\varepsilon", true)
    defineSymbol(mathMode, main, mathord, Nullable("ϑ"), "\\vartheta", true)
    defineSymbol(mathMode, main, mathord, Nullable("ϖ"), "\\varpi", true)
    defineSymbol(mathMode, main, mathord, Nullable("ϱ"), "\\varrho", true)
    defineSymbol(mathMode, main, mathord, Nullable("ς"), "\\varsigma", true)
    defineSymbol(mathMode, main, mathord, Nullable("φ"), "\\varphi", true)
    defineSymbol(mathMode, main, bin, Nullable("∗"), "*", true)
    defineSymbol(mathMode, main, bin, Nullable("+"), "+")
    defineSymbol(mathMode, main, bin, Nullable("−"), "-", true)
    defineSymbol(mathMode, main, bin, Nullable("⋅"), "\\cdot", true)
    defineSymbol(mathMode, main, bin, Nullable("∘"), "\\circ", true)
    defineSymbol(mathMode, main, bin, Nullable("÷"), "\\div", true)
    defineSymbol(mathMode, main, bin, Nullable("±"), "\\pm", true)
    defineSymbol(mathMode, main, bin, Nullable("×"), "\\times", true)
    defineSymbol(mathMode, main, bin, Nullable("∩"), "\\cap", true)
    defineSymbol(mathMode, main, bin, Nullable("∪"), "\\cup", true)
    defineSymbol(mathMode, main, bin, Nullable("∖"), "\\setminus", true)
    defineSymbol(mathMode, main, bin, Nullable("∧"), "\\land")
    defineSymbol(mathMode, main, bin, Nullable("∨"), "\\lor")
    defineSymbol(mathMode, main, bin, Nullable("∧"), "\\wedge", true)
    defineSymbol(mathMode, main, bin, Nullable("∨"), "\\vee", true)
    defineSymbol(mathMode, main, textord, Nullable("√"), "\\surd")
    defineSymbol(mathMode, main, open, Nullable("⟨"), "\\langle", true)
    defineSymbol(mathMode, main, open, Nullable("∣"), "\\lvert")
    defineSymbol(mathMode, main, open, Nullable("∥"), "\\lVert")
    defineSymbol(mathMode, main, close, Nullable("?"), "?")
    defineSymbol(mathMode, main, close, Nullable("!"), "!")
    defineSymbol(mathMode, main, close, Nullable("⟩"), "\\rangle", true)
    defineSymbol(mathMode, main, close, Nullable("∣"), "\\rvert")
    defineSymbol(mathMode, main, close, Nullable("∥"), "\\rVert")
    defineSymbol(mathMode, main, rel, Nullable("="), "=")
    defineSymbol(mathMode, main, rel, Nullable(":"), ":")
    defineSymbol(mathMode, main, rel, Nullable("≈"), "\\approx", true)
    defineSymbol(mathMode, main, rel, Nullable("≅"), "\\cong", true)
    defineSymbol(mathMode, main, rel, Nullable("≥"), "\\ge")
    defineSymbol(mathMode, main, rel, Nullable("≥"), "\\geq", true)
    defineSymbol(mathMode, main, rel, Nullable("←"), "\\gets")
    defineSymbol(mathMode, main, rel, Nullable(">"), "\\gt", true)
    defineSymbol(mathMode, main, rel, Nullable("∈"), "\\in", true)
    defineSymbol(mathMode, main, rel, Nullable(""), "\\@not")
    defineSymbol(mathMode, main, rel, Nullable("⊂"), "\\subset", true)
    defineSymbol(mathMode, main, rel, Nullable("⊃"), "\\supset", true)
    defineSymbol(mathMode, main, rel, Nullable("⊆"), "\\subseteq", true)
    defineSymbol(mathMode, main, rel, Nullable("⊇"), "\\supseteq", true)
    defineSymbol(mathMode, ams, rel, Nullable("⊈"), "\\nsubseteq", true)
    defineSymbol(mathMode, ams, rel, Nullable("⊉"), "\\nsupseteq", true)
    defineSymbol(mathMode, main, rel, Nullable("⊨"), "\\models")
    defineSymbol(mathMode, main, rel, Nullable("←"), "\\leftarrow", true)
    defineSymbol(mathMode, main, rel, Nullable("≤"), "\\le")
    defineSymbol(mathMode, main, rel, Nullable("≤"), "\\leq", true)
    defineSymbol(mathMode, main, rel, Nullable("<"), "\\lt", true)
    defineSymbol(mathMode, main, rel, Nullable("→"), "\\rightarrow", true)
    defineSymbol(mathMode, main, rel, Nullable("→"), "\\to")
    defineSymbol(mathMode, ams, rel, Nullable("≱"), "\\ngeq", true)
    defineSymbol(mathMode, ams, rel, Nullable("≰"), "\\nleq", true)
    defineSymbol(mathMode, main, spacing, Nullable(" "), "\\ ")
    defineSymbol(mathMode, main, spacing, Nullable(" "), "\\space")
    // Ref: LaTeX Source 2e: \DeclareRobustCommand{\nobreakspace}{%
    defineSymbol(mathMode, main, spacing, Nullable(" "), "\\nobreakspace")
    defineSymbol(textMode, main, spacing, Nullable(" "), "\\ ")
    defineSymbol(textMode, main, spacing, Nullable(" "), " ")
    defineSymbol(textMode, main, spacing, Nullable(" "), "\\space")
    defineSymbol(textMode, main, spacing, Nullable(" "), "\\nobreakspace")
    defineSymbol(mathMode, main, spacing, Nullable.Null, "\\nobreak")
    defineSymbol(mathMode, main, spacing, Nullable.Null, "\\allowbreak")
    defineSymbol(mathMode, main, punct, Nullable(","), ",")
    defineSymbol(mathMode, main, punct, Nullable(";"), ";")
    defineSymbol(mathMode, ams, bin, Nullable("⊼"), "\\barwedge", true)
    defineSymbol(mathMode, ams, bin, Nullable("⊻"), "\\veebar", true)
    defineSymbol(mathMode, main, bin, Nullable("⊙"), "\\odot", true)
    defineSymbol(mathMode, main, bin, Nullable("⊕"), "\\oplus", true)
    defineSymbol(mathMode, main, bin, Nullable("⊗"), "\\otimes", true)
    defineSymbol(mathMode, main, textord, Nullable("∂"), "\\partial", true)
    defineSymbol(mathMode, main, bin, Nullable("⊘"), "\\oslash", true)
    defineSymbol(mathMode, ams, bin, Nullable("⊚"), "\\circledcirc", true)
    defineSymbol(mathMode, ams, bin, Nullable("⊡"), "\\boxdot", true)
    defineSymbol(mathMode, main, bin, Nullable("△"), "\\bigtriangleup")
    defineSymbol(mathMode, main, bin, Nullable("▽"), "\\bigtriangledown")
    defineSymbol(mathMode, main, bin, Nullable("†"), "\\dagger")
    defineSymbol(mathMode, main, bin, Nullable("⋄"), "\\diamond")
    defineSymbol(mathMode, main, bin, Nullable("⋆"), "\\star")
    defineSymbol(mathMode, main, bin, Nullable("◃"), "\\triangleleft")
    defineSymbol(mathMode, main, bin, Nullable("▹"), "\\triangleright")
    defineSymbol(mathMode, main, open, Nullable("{"), "\\{")
    defineSymbol(textMode, main, textord, Nullable("{"), "\\{")
    defineSymbol(textMode, main, textord, Nullable("{"), "\\textbraceleft")
    defineSymbol(mathMode, main, close, Nullable("}"), "\\}")
    defineSymbol(textMode, main, textord, Nullable("}"), "\\}")
    defineSymbol(textMode, main, textord, Nullable("}"), "\\textbraceright")
    defineSymbol(mathMode, main, open, Nullable("{"), "\\lbrace")
    defineSymbol(mathMode, main, close, Nullable("}"), "\\rbrace")
    defineSymbol(mathMode, main, open, Nullable("["), "\\lbrack", true)
    defineSymbol(textMode, main, textord, Nullable("["), "\\lbrack", true)
    defineSymbol(mathMode, main, close, Nullable("]"), "\\rbrack", true)
    defineSymbol(textMode, main, textord, Nullable("]"), "\\rbrack", true)
    defineSymbol(mathMode, main, open, Nullable("("), "\\lparen", true)
    defineSymbol(mathMode, main, close, Nullable(")"), "\\rparen", true)
    defineSymbol(textMode, main, textord, Nullable("<"), "\\textless", true) // in T1 fontenc
    defineSymbol(textMode, main, textord, Nullable(">"), "\\textgreater", true) // in T1 fontenc
    defineSymbol(mathMode, main, open, Nullable("⌊"), "\\lfloor", true)
    defineSymbol(mathMode, main, close, Nullable("⌋"), "\\rfloor", true)
    defineSymbol(mathMode, main, open, Nullable("⌈"), "\\lceil", true)
    defineSymbol(mathMode, main, close, Nullable("⌉"), "\\rceil", true)
    defineSymbol(mathMode, main, textord, Nullable("\\"), "\\backslash")
    defineSymbol(mathMode, main, textord, Nullable("∣"), "|")
    defineSymbol(mathMode, main, textord, Nullable("∣"), "\\vert")
    defineSymbol(textMode, main, textord, Nullable("|"), "\\textbar", true) // in T1 fontenc
    defineSymbol(mathMode, main, textord, Nullable("∥"), "\\|")
    defineSymbol(mathMode, main, textord, Nullable("∥"), "\\Vert")
    defineSymbol(textMode, main, textord, Nullable("∥"), "\\textbardbl")
    defineSymbol(textMode, main, textord, Nullable("~"), "\\textasciitilde")
    defineSymbol(textMode, main, textord, Nullable("\\"), "\\textbackslash")
    defineSymbol(textMode, main, textord, Nullable("^"), "\\textasciicircum")
    defineSymbol(mathMode, main, rel, Nullable("↑"), "\\uparrow", true)
    defineSymbol(mathMode, main, rel, Nullable("⇑"), "\\Uparrow", true)
    defineSymbol(mathMode, main, rel, Nullable("↓"), "\\downarrow", true)
    defineSymbol(mathMode, main, rel, Nullable("⇓"), "\\Downarrow", true)
    defineSymbol(mathMode, main, rel, Nullable("↕"), "\\updownarrow", true)
    defineSymbol(mathMode, main, rel, Nullable("⇕"), "\\Updownarrow", true)
    defineSymbol(mathMode, main, op, Nullable("∐"), "\\coprod")
    defineSymbol(mathMode, main, op, Nullable("⋁"), "\\bigvee")
    defineSymbol(mathMode, main, op, Nullable("⋀"), "\\bigwedge")
    defineSymbol(mathMode, main, op, Nullable("⨄"), "\\biguplus")
    defineSymbol(mathMode, main, op, Nullable("⋂"), "\\bigcap")
    defineSymbol(mathMode, main, op, Nullable("⋃"), "\\bigcup")
    defineSymbol(mathMode, main, op, Nullable("∫"), "\\int")
    defineSymbol(mathMode, main, op, Nullable("∫"), "\\intop")
    defineSymbol(mathMode, main, op, Nullable("∬"), "\\iint")
    defineSymbol(mathMode, main, op, Nullable("∭"), "\\iiint")
    defineSymbol(mathMode, main, op, Nullable("∏"), "\\prod")
    defineSymbol(mathMode, main, op, Nullable("∑"), "\\sum")
    defineSymbol(mathMode, main, op, Nullable("⨂"), "\\bigotimes")
    defineSymbol(mathMode, main, op, Nullable("⨁"), "\\bigoplus")
    defineSymbol(mathMode, main, op, Nullable("⨀"), "\\bigodot")
    defineSymbol(mathMode, main, op, Nullable("∮"), "\\oint")
    defineSymbol(mathMode, main, op, Nullable("∯"), "\\oiint")
    defineSymbol(mathMode, main, op, Nullable("∰"), "\\oiiint")
    defineSymbol(mathMode, main, op, Nullable("⨆"), "\\bigsqcup")
    defineSymbol(mathMode, main, op, Nullable("∫"), "\\smallint")
    defineSymbol(textMode, main, inner, Nullable("…"), "\\textellipsis")
    defineSymbol(mathMode, main, inner, Nullable("…"), "\\mathellipsis")
    defineSymbol(textMode, main, inner, Nullable("…"), "\\ldots", true)
    defineSymbol(mathMode, main, inner, Nullable("…"), "\\ldots", true)
    defineSymbol(mathMode, main, inner, Nullable("⋯"), "\\@cdots", true)
    defineSymbol(mathMode, main, inner, Nullable("⋱"), "\\ddots", true)
    // \vdots is a macro that uses one of these two symbols (with made-up names):
    defineSymbol(mathMode, main, textord, Nullable("⋮"), "\\varvdots")
    defineSymbol(textMode, main, textord, Nullable("⋮"), "\\varvdots")
    defineSymbol(mathMode, main, accent, Nullable("ˊ"), "\\acute")
    defineSymbol(mathMode, main, accent, Nullable("ˋ"), "\\grave")
    defineSymbol(mathMode, main, accent, Nullable("¨"), "\\ddot")
    defineSymbol(mathMode, main, accent, Nullable("~"), "\\tilde")
    defineSymbol(mathMode, main, accent, Nullable("ˉ"), "\\bar")
    defineSymbol(mathMode, main, accent, Nullable("˘"), "\\breve")
    defineSymbol(mathMode, main, accent, Nullable("ˇ"), "\\check")
    defineSymbol(mathMode, main, accent, Nullable("^"), "\\hat")
    defineSymbol(mathMode, main, accent, Nullable("⃗"), "\\vec")
    defineSymbol(mathMode, main, accent, Nullable("˙"), "\\dot")
    defineSymbol(mathMode, main, accent, Nullable("˚"), "\\mathring")
    // \imath and \jmath should be invariant to \mathrm, \mathbf, etc., so use PUA
    defineSymbol(mathMode, main, mathord, Nullable(""), "\\@imath")
    defineSymbol(mathMode, main, mathord, Nullable(""), "\\@jmath")
    defineSymbol(mathMode, main, textord, Nullable("ı"), "ı")
    defineSymbol(mathMode, main, textord, Nullable("ȷ"), "ȷ")
    defineSymbol(textMode, main, textord, Nullable("ı"), "\\i", true)
    defineSymbol(textMode, main, textord, Nullable("ȷ"), "\\j", true)
    defineSymbol(textMode, main, textord, Nullable("ß"), "\\ss", true)
    defineSymbol(textMode, main, textord, Nullable("æ"), "\\ae", true)
    defineSymbol(textMode, main, textord, Nullable("œ"), "\\oe", true)
    defineSymbol(textMode, main, textord, Nullable("ø"), "\\o", true)
    defineSymbol(textMode, main, textord, Nullable("Æ"), "\\AE", true)
    defineSymbol(textMode, main, textord, Nullable("Œ"), "\\OE", true)
    defineSymbol(textMode, main, textord, Nullable("Ø"), "\\O", true)
    defineSymbol(textMode, main, accent, Nullable("ˊ"), "\\'") // acute
    defineSymbol(textMode, main, accent, Nullable("ˋ"), "\\`") // grave
    defineSymbol(textMode, main, accent, Nullable("ˆ"), "\\^") // circumflex
    defineSymbol(textMode, main, accent, Nullable("˜"), "\\~") // tilde
    defineSymbol(textMode, main, accent, Nullable("ˉ"), "\\=") // macron
    defineSymbol(textMode, main, accent, Nullable("˘"), "\\u") // breve
    defineSymbol(textMode, main, accent, Nullable("˙"), "\\.") // dot above
    defineSymbol(textMode, main, accent, Nullable("¸"), "\\c") // cedilla
    defineSymbol(textMode, main, accent, Nullable("˚"), "\\r") // ring above
    defineSymbol(textMode, main, accent, Nullable("ˇ"), "\\v") // caron
    defineSymbol(textMode, main, accent, Nullable("¨"), "\\\"") // diaeresis
    defineSymbol(textMode, main, accent, Nullable("˝"), "\\H") // double acute
    defineSymbol(textMode, main, accent, Nullable("◯"), "\\textcircled") // \bigcirc glyph

    defineSymbol(textMode, main, textord, Nullable("–"), "--", true)
    defineSymbol(textMode, main, textord, Nullable("–"), "\\textendash")
    defineSymbol(textMode, main, textord, Nullable("—"), "---", true)
    defineSymbol(textMode, main, textord, Nullable("—"), "\\textemdash")
    defineSymbol(textMode, main, textord, Nullable("‘"), "`", true)
    defineSymbol(textMode, main, textord, Nullable("‘"), "\\textquoteleft")
    defineSymbol(textMode, main, textord, Nullable("’"), "'", true)
    defineSymbol(textMode, main, textord, Nullable("’"), "\\textquoteright")
    defineSymbol(textMode, main, textord, Nullable("“"), "``", true)
    defineSymbol(textMode, main, textord, Nullable("“"), "\\textquotedblleft")
    defineSymbol(textMode, main, textord, Nullable("”"), "''", true)
    defineSymbol(textMode, main, textord, Nullable("”"), "\\textquotedblright")
    //  \degree from gensymb package
    defineSymbol(mathMode, main, textord, Nullable("°"), "\\degree", true)
    defineSymbol(textMode, main, textord, Nullable("°"), "\\degree")
    // \textdegree from inputenc package
    defineSymbol(textMode, main, textord, Nullable("°"), "\\textdegree", true)
    // TODO: In LaTeX, \pounds can generate a different character in text and math
    // mode, but among our fonts, only Main-Regular defines this character "163".
    defineSymbol(mathMode, main, textord, Nullable("£"), "\\pounds")
    defineSymbol(mathMode, main, textord, Nullable("£"), "\\mathsterling", true)
    defineSymbol(textMode, main, textord, Nullable("£"), "\\pounds")
    defineSymbol(textMode, main, textord, Nullable("£"), "\\textsterling", true)
    defineSymbol(mathMode, ams, textord, Nullable("✠"), "\\maltese")
    defineSymbol(textMode, ams, textord, Nullable("✠"), "\\maltese")

    // There are lots of symbols which are the same, so we add them in afterwards.
    // All of these are textords in math mode
    val mathTextSymbols = "0123456789/@.\""
    for (i <- 0 until mathTextSymbols.length) {
      val ch = mathTextSymbols.charAt(i).toString
      defineSymbol(mathMode, main, textord, Nullable(ch), ch)
    }

    // All of these are textords in text mode
    val textSymbolsStr = "0123456789!@*()-=+\";:?/.,"
    for (i <- 0 until textSymbolsStr.length) {
      val ch = textSymbolsStr.charAt(i).toString
      defineSymbol(textMode, main, textord, Nullable(ch), ch)
    }

    // All of these are textords in text mode, and mathords in math mode
    val letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
    for (i <- 0 until letters.length) {
      val ch = letters.charAt(i).toString
      defineSymbol(mathMode, main, mathord, Nullable(ch), ch)
      defineSymbol(textMode, main, textord, Nullable(ch), ch)
    }

    // Blackboard bold and script letters in Unicode range
    defineSymbol(mathMode, ams, textord, Nullable("C"), "ℂ") // blackboard bold
    defineSymbol(textMode, ams, textord, Nullable("C"), "ℂ")
    defineSymbol(mathMode, ams, textord, Nullable("H"), "ℍ")
    defineSymbol(textMode, ams, textord, Nullable("H"), "ℍ")
    defineSymbol(mathMode, ams, textord, Nullable("N"), "ℕ")
    defineSymbol(textMode, ams, textord, Nullable("N"), "ℕ")
    defineSymbol(mathMode, ams, textord, Nullable("P"), "ℙ")
    defineSymbol(textMode, ams, textord, Nullable("P"), "ℙ")
    defineSymbol(mathMode, ams, textord, Nullable("Q"), "ℚ")
    defineSymbol(textMode, ams, textord, Nullable("Q"), "ℚ")
    defineSymbol(mathMode, ams, textord, Nullable("R"), "ℝ")
    defineSymbol(textMode, ams, textord, Nullable("R"), "ℝ")
    defineSymbol(mathMode, ams, textord, Nullable("Z"), "ℤ")
    defineSymbol(textMode, ams, textord, Nullable("Z"), "ℤ")
    defineSymbol(mathMode, main, mathord, Nullable("h"), "ℎ") // italic h, Planck constant
    defineSymbol(textMode, main, mathord, Nullable("h"), "ℎ")

    // The next loop loads wide (surrogate pair) characters.
    // We support some letters in the Unicode range U+1D400 to U+1D7FF,
    // Mathematical Alphanumeric Symbols.
    // Some editors do not deal well with wide characters. So don't write the
    // string into this file. Instead, create the string from the surrogate pair.
    for (i <- 0 until letters.length) {
      val ch = letters.charAt(i).toString

      // The hex numbers in the next line are a surrogate pair.
      // 0xD835 is the high surrogate for all letters in the range we support.
      // 0xDC00 is the low surrogate for bold A.
      var wideChar = new String(Array[Char](0xd835.toChar, (0xdc00 + i).toChar))
      defineSymbol(mathMode, main, mathord, Nullable(ch), wideChar) // A-Z a-z bold
      defineSymbol(textMode, main, textord, Nullable(ch), wideChar)

      wideChar = new String(Array[Char](0xd835.toChar, (0xdc34 + i).toChar))
      defineSymbol(mathMode, main, mathord, Nullable(ch), wideChar) // A-Z a-z italic
      defineSymbol(textMode, main, textord, Nullable(ch), wideChar)

      wideChar = new String(Array[Char](0xd835.toChar, (0xdc68 + i).toChar))
      defineSymbol(mathMode, main, mathord, Nullable(ch), wideChar) // A-Z a-z bold italic
      defineSymbol(textMode, main, textord, Nullable(ch), wideChar)

      wideChar = new String(Array[Char](0xd835.toChar, (0xdd04 + i).toChar))
      defineSymbol(mathMode, main, mathord, Nullable(ch), wideChar) // A-Z a-z Fraktur
      defineSymbol(textMode, main, textord, Nullable(ch), wideChar)

      wideChar = new String(Array[Char](0xd835.toChar, (0xdd6c + i).toChar))
      defineSymbol(mathMode, main, mathord, Nullable(ch), wideChar) // A-Z a-z bold Fraktur
      defineSymbol(textMode, main, textord, Nullable(ch), wideChar)

      wideChar = new String(Array[Char](0xd835.toChar, (0xdda0 + i).toChar))
      defineSymbol(mathMode, main, mathord, Nullable(ch), wideChar) // A-Z a-z sans-serif
      defineSymbol(textMode, main, textord, Nullable(ch), wideChar)

      wideChar = new String(Array[Char](0xd835.toChar, (0xddd4 + i).toChar))
      defineSymbol(mathMode, main, mathord, Nullable(ch), wideChar) // A-Z a-z sans bold
      defineSymbol(textMode, main, textord, Nullable(ch), wideChar)

      wideChar = new String(Array[Char](0xd835.toChar, (0xde08 + i).toChar))
      defineSymbol(mathMode, main, mathord, Nullable(ch), wideChar) // A-Z a-z sans italic
      defineSymbol(textMode, main, textord, Nullable(ch), wideChar)

      wideChar = new String(Array[Char](0xd835.toChar, (0xde70 + i).toChar))
      defineSymbol(mathMode, main, mathord, Nullable(ch), wideChar) // A-Z a-z monospace
      defineSymbol(textMode, main, textord, Nullable(ch), wideChar)

      if (i < 26) {
        // KaTeX fonts have only capital letters for blackboard bold and script.
        // See exception for k below.
        wideChar = new String(Array[Char](0xd835.toChar, (0xdd38 + i).toChar))
        defineSymbol(mathMode, main, mathord, Nullable(ch), wideChar) // A-Z double struck
        defineSymbol(textMode, main, textord, Nullable(ch), wideChar)

        wideChar = new String(Array[Char](0xd835.toChar, (0xdc9c + i).toChar))
        defineSymbol(mathMode, main, mathord, Nullable(ch), wideChar) // A-Z script
        defineSymbol(textMode, main, textord, Nullable(ch), wideChar)
      }

      // TODO: Add bold script when it is supported by a KaTeX font.
    }
    // "k" is the only double struck lower case letter in the KaTeX fonts.
    val kDoubleStruck = new String(Array[Char](0xd835.toChar, 0xdd5c.toChar))
    defineSymbol(mathMode, main, mathord, Nullable("k"), kDoubleStruck) // k double struck
    defineSymbol(textMode, main, textord, Nullable("k"), kDoubleStruck)

    // Next, some wide character numerals
    for (i <- 0 until 10) {
      val ch = i.toString

      var wideChar = new String(Array[Char](0xd835.toChar, (0xdfce + i).toChar))
      defineSymbol(mathMode, main, mathord, Nullable(ch), wideChar) // 0-9 bold
      defineSymbol(textMode, main, textord, Nullable(ch), wideChar)

      wideChar = new String(Array[Char](0xd835.toChar, (0xdfe2 + i).toChar))
      defineSymbol(mathMode, main, mathord, Nullable(ch), wideChar) // 0-9 sans serif
      defineSymbol(textMode, main, textord, Nullable(ch), wideChar)

      wideChar = new String(Array[Char](0xd835.toChar, (0xdfec + i).toChar))
      defineSymbol(mathMode, main, mathord, Nullable(ch), wideChar) // 0-9 bold sans
      defineSymbol(textMode, main, textord, Nullable(ch), wideChar)

      wideChar = new String(Array[Char](0xd835.toChar, (0xdff6 + i).toChar))
      defineSymbol(mathMode, main, mathord, Nullable(ch), wideChar) // 0-9 monospace
      defineSymbol(textMode, main, textord, Nullable(ch), wideChar)
    }

    // We add these Latin-1 letters as symbols for backwards-compatibility,
    // but they are not actually in the font, nor are they supported by the
    // Unicode accent mechanism, so they fall back to Times font and look ugly.
    // TODO(edemaine): Fix this.
    for (i <- 0 until extraLatin.length) {
      val ch = extraLatin.charAt(i).toString
      defineSymbol(mathMode, main, mathord, Nullable(ch), ch)
      defineSymbol(textMode, main, textord, Nullable(ch), ch)
    }
  }

  // Initialize symbols eagerly
  initSymbols()
}
