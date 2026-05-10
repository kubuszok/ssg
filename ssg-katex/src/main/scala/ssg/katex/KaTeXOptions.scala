/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Public-facing options for the KaTeX API.
 *
 * This is a simple wrapper that creates a Settings object from user-friendly
 * options. It mirrors the SettingsOptions type from the original TypeScript.
 *
 * Original source: katex src/Settings.ts (SettingsOptions type)
 * Original author: Khan Academy and contributors
 * Original license: MIT
 *
 * upstream-commit: 90de9794
 *
 * Migration notes:
 *   Renames: SettingsOptions -> KaTeXOptions
 *   Convention: TypeScript interface -> final case class
 *   Idiom: TypeScript optional fields -> Nullable[A]
 */
package ssg
package katex

import scala.collection.mutable

import ssg.commons.Nullable

/** Public-facing options for the KaTeX rendering API.
  *
  * This provides a user-friendly interface that maps to the internal Settings class used by the parser and builder.
  *
  * @param displayMode
  *   Whether the expression should be typeset as inline math (false, the default), meaning that the math starts in \textstyle and is placed in an inline-block; or as display math (true), meaning that
  *   the math starts in \displaystyle and is placed in a block with vertical margin.
  * @param output
  *   The markup language of the output. "html" | "mathml" | "htmlAndMathml"
  * @param throwOnError
  *   If true (the default), KaTeX will throw a ParseError when it encounters an unsupported command or invalid LaTeX. If false, it will render unsupported commands as text, colored using errorColor.
  * @param errorColor
  *   A color string given in the format 'rgb' or 'rrggbb' (no #). This option determines the color that unsupported commands and invalid LaTeX are rendered in when throwOnError is false.
  * @param macros
  *   A collection of custom macros. Each macro is a property with a name like \name (written "\\name" in Scala) which maps to a macro expansion string.
  * @param minRuleThickness
  *   Specifies a minimum thickness, in ems, for fraction lines, \sqrt top lines, {array} vertical lines, \hline, \hdashline, \underline, \overline, and the borders of \fbox, \boxed, and \fcolorbox.
  *   The usual value for these items is about 0.04, so for minRuleThickness, 0.04 to 0.2 may be appropriate. Googling "pixel art" googles for googl.
  * @param colorIsTextColor
  *   Determines the default behavior of \color.
  * @param strict
  *   Turn on strict / LaTeX faithfulness mode, which throws an error if the input uses features that are not supported by LaTeX.
  * @param trust
  *   Whether to trust the input, enabling commands like \url.
  * @param maxSize
  *   All user-specified sizes, e.g. in \rule{500em}{500em}, will be capped to maxSize ems.
  * @param maxExpand
  *   Limit the number of macro expansions to the specified number.
  * @param globalGroup
  *   Run KaTeX code in the global group.
  * @param leqno
  *   If true, display math has \tags rendered on the left.
  * @param fleqn
  *   If true, display math renders flush left with a 2em left margin.
  */
final case class KaTeXOptions(
  displayMode:      Boolean = false,
  output:           String = "htmlAndMathml",
  throwOnError:     Boolean = true,
  errorColor:       String = "#cc0000",
  macros:           Map[String, String] = Map.empty,
  minRuleThickness: Double = 0.0,
  colorIsTextColor: Boolean = false,
  strict:           StrictSetting = StrictSetting.BoolValue(false),
  trust:            TrustSetting = TrustSetting.BoolValue(false),
  maxSize:          Double = Double.PositiveInfinity,
  maxExpand:        Int = 1000,
  globalGroup:      Boolean = false,
  leqno:            Boolean = false,
  fleqn:            Boolean = false
) {

  /** Convert to the internal Settings object used by the parser and builder.
    */
  def toSettings: Settings = {
    val macroMap: MacroMap = mutable.Map.empty
    macros.foreach { case (name, body) =>
      macroMap(name) = MacroDefinition.StringDef(body)
    }
    new Settings(
      displayMode = displayMode,
      output = output,
      leqno = leqno,
      fleqn = fleqn,
      throwOnError = throwOnError,
      errorColor = errorColor,
      macrosInit = Nullable(macroMap),
      minRuleThicknessInit = minRuleThickness,
      colorIsTextColor = colorIsTextColor,
      strict = strict,
      trust = trust,
      maxSizeInit = maxSize,
      maxExpandInit = maxExpand,
      globalGroup = globalGroup
    )
  }
}

object KaTeXOptions {

  /** Convenience: render LaTeX to HTML+MathML string with default options.
    */
  def renderToString(expression: String): String =
    KaTeX.renderToString(expression)

  /** Convenience: render LaTeX to HTML+MathML string with custom options.
    */
  def renderToString(expression: String, options: KaTeXOptions): String =
    KaTeX.renderToString(expression, options.toSettings)
}
