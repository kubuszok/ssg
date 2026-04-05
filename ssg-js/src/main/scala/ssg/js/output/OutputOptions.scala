/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: BSD-2-Clause
 *
 * JavaScript code generator options.
 *
 * Original source: terser lib/output.js (OutputStream options)
 * Original author: Mihai Bazon
 * Original license: BSD-2-Clause
 *
 * Migration notes:
 *   Renames: OutputStream options object -> ssg.js.output.OutputOptions
 *   Convention: Immutable case class with JS-conventional defaults
 *   Idiom: snake_case options from JS -> camelCase in Scala
 */
package ssg
package js
package output

/** Options controlling the JavaScript code generator.
  *
  * @param asciiOnly
  *   Escape non-ASCII characters in strings and regexps
  * @param beautify
  *   Produce indented, human-readable output
  * @param braces
  *   Always use braces for if/for/do/while bodies
  * @param comments
  *   Comment filter: "all", "some" (preserve important), or "false" (strip all). Can also be a regex string like "/regex/flags".
  * @param ecma
  *   Target ECMAScript version (5, 2015, etc.)
  * @param indentLevel
  *   Number of spaces per indentation level (beautify mode)
  * @param indentStart
  *   Number of spaces to indent the top level
  * @param inlineScript
  *   Escape `</script>` and HTML comments in output
  * @param keepNumbers
  *   Preserve original number formatting
  * @param keepQuotedProps
  *   Preserve quotes around property names
  * @param maxLineLen
  *   Maximum line length (0 = no limit)
  * @param preamble
  *   Prepend this string to the output (e.g. a license comment)
  * @param preserveAnnotations
  *   Preserve `@__PURE__`, `@__INLINE__`, `@__NOINLINE__` annotations
  * @param quoteKeys
  *   Always quote object property keys
  * @param quoteStyle
  *   Quote style: 0 = best (fewest escapes), 1 = single, 2 = double, 3 = original
  * @param semicolons
  *   Use semicolons to separate statements (false = use newlines where safe)
  * @param shebang
  *   Preserve `#!` shebang line
  * @param shorthand
  *   Use ES6 shorthand for property/method definitions
  * @param webkit
  *   Work around WebKit bugs
  * @param width
  *   Target line width for beautified output
  * @param wrapIife
  *   Wrap IIFEs in parentheses
  * @param wrapFuncArgs
  *   Wrap function arguments that are functions in parentheses
  */
final case class OutputOptions(
  asciiOnly:           Boolean = false,
  beautify:            Boolean = false,
  braces:              Boolean = false,
  comments:            String = "some",
  ecma:                Int = 5,
  indentLevel:         Int = 4,
  indentStart:         Int = 0,
  inlineScript:        Boolean = true,
  keepNumbers:         Boolean = false,
  keepQuotedProps:     Boolean = false,
  maxLineLen:          Int = 0,
  preamble:            String = "",
  preserveAnnotations: Boolean = false,
  quoteKeys:           Boolean = false,
  quoteStyle:          Int = 0,
  semicolons:          Boolean = true,
  shebang:             Boolean = true,
  shorthand:           Boolean = true,
  webkit:              Boolean = false,
  width:               Int = 80,
  wrapIife:            Boolean = false,
  wrapFuncArgs:        Boolean = false
)
