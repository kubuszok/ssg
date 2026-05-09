/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Math/text mode enum for KaTeX typesetting.
 *
 * Original source: katex src/types.ts
 * Original author: Khan Academy and contributors
 * Original license: MIT
 *
 * upstream-commit: 90de9794
 */
package ssg
package katex

/**
 * This file consists only of basic types used in multiple places.
 * For types with javascript, create separate files by themselves.
 */

/** Math/text mode: "math" or "text". */
enum Mode(val value: String) extends java.lang.Enum[Mode] {
  case Math extends Mode("math")
  case Text extends Mode("text")
}

/** LaTeX argument type.
 *   - "size": A size-like thing, such as "1em" or "5ex"
 *   - "color": An html color, like "#abc" or "blue"
 *   - "url": An url string, in which "\" will be ignored
 *            if it precedes [#$%&~_^\{}]
 *   - "raw": A string, allowing single character, percent sign,
 *            and nested braces
 *   - "original": The same type as the environment that the
 *                 function being parsed is in (e.g. used for the
 *                 bodies of functions like \textcolor where the
 *                 first argument is special and the second
 *                 argument is parsed normally)
 *   - Mode: Node group parsed in given mode.
 */
enum ArgType(val value: String) extends java.lang.Enum[ArgType] {
  case Color    extends ArgType("color")
  case Size     extends ArgType("size")
  case Url      extends ArgType("url")
  case Raw      extends ArgType("raw")
  case Original extends ArgType("original")
  case Hbox     extends ArgType("hbox")
  case Primitive extends ArgType("primitive")
  case MathMode extends ArgType("math")
  case TextMode extends ArgType("text")
}

/** LaTeX display style. */
enum StyleStr(val value: String) extends java.lang.Enum[StyleStr] {
  case TextStyle         extends StyleStr("text")
  case Display           extends StyleStr("display")
  case Script            extends StyleStr("script")
  case ScriptScript      extends StyleStr("scriptscript")
}

/** Math font variants. */
enum FontVariant(val value: String) extends java.lang.Enum[FontVariant] {
  case Bold                 extends FontVariant("bold")
  case BoldItalic           extends FontVariant("bold-italic")
  case BoldSansSerif        extends FontVariant("bold-sans-serif")
  case DoubleStruck         extends FontVariant("double-struck")
  case Fraktur              extends FontVariant("fraktur")
  case Italic               extends FontVariant("italic")
  case Monospace            extends FontVariant("monospace")
  case Normal               extends FontVariant("normal")
  case SansSerif            extends FontVariant("sans-serif")
  case SansSerifBoldItalic  extends FontVariant("sans-serif-bold-italic")
  case SansSerifItalic      extends FontVariant("sans-serif-italic")
  case ScriptVariant        extends FontVariant("script")
}

/** Allowable token text for "break" arguments in parser. */
enum BreakToken(val value: String) extends java.lang.Enum[BreakToken] {
  case RightBracket extends BreakToken("]")
  case RightBrace   extends BreakToken("}")
  case EndGroup     extends BreakToken("\\endgroup")
  case Dollar       extends BreakToken("$")
  case CloseParen   extends BreakToken("\\)")
  case Backslash2   extends BreakToken("\\\\")
  case End          extends BreakToken("\\end")
  case EOF          extends BreakToken("EOF")
}
