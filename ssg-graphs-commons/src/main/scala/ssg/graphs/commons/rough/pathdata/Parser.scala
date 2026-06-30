/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * SVG path `d` tokenizer + parser — Scala 3 port
 *
 * Original source: path-data-parser (src/parser.ts)
 * Original author: pshihn
 * Original license: MIT
 * upstream-commit: 93d3fa8
 *
 * Migration notes:
 *   Renames: TS `Segment` interface -> `final case class Segment`; TS `PathToken`
 *     interface -> private `final case class PathToken`; the numeric token `type`
 *     field -> `tokenType` (`type` is a Scala keyword). `PARAMS` table -> `Params`
 *     (a `Map[String, Int]`). TS `tokenize`/`isType` (module-private functions) ->
 *     private members of `object Parser`.
 *   Convention: `number[]` -> immutable `Vector[Double]`; the defensive array
 *     spreads (`[...data]`) the original needs because JS arrays are mutable are
 *     unnecessary here and collapse to direct reuse of the immutable `Vector`.
 *   Idiom: the legacy `String.prototype.match` + `RegExp.$1` global match state is
 *     ported to explicit anchored `Regex.findPrefixMatchOf` + `group(1)` — the three
 *     patterns are simple/anchored (no lookahead/backref/unicode-prop) so they are
 *     re2- and JS-safe on all three platforms (see cross-platform-regex.md).
 *     `d.substr(n)` -> `d.substring(n)`. `return []` / `return parsePath(...)` ->
 *     `scala.util.boundary`/`break`.
 *   Idiom: the original stores number tokens as `` `${parseFloat(RegExp.$1)}` `` and
 *     parsePath re-parses them with unary `+`. On the regex-validated numeric
 *     substring this `parseFloat` -> stringify -> `Number()` round-trip is a no-op on
 *     the value, so the port stores the raw matched text and parses it once via
 *     `text.toDouble` (= `java.lang.Double.parseDouble`), which accepts the same
 *     literal forms the regex admits (leading `+`, leading `.`, trailing `.`,
 *     exponent). The PathToken text is never serialized as-is — only its numeric
 *     value is consumed — so dropping the intermediate restringification is exact.
 *   Idiom: `serialize` joins JS numbers via `Array.prototype.join`, i.e.
 *     `String(number)`. `jsNum` reproduces ECMA-262 `Number.prototype.toString` for
 *     integer-valued doubles (no `.0`) and finite fractions. Extreme magnitudes that
 *     ECMA would render in exponential notation (|v| >= 1e21 or |v| < 1e-6) fall back
 *     to `java.lang.Double.toString`; such values do not occur in SVG path
 *     coordinates. `serialize` is part of the public API (re-exported by index.ts)
 *     but is not consumed by roughjs (renderer.ts uses only parsePath/normalize/
 *     absolutize).
 *   Convention: the three `throw new Error(...)` calls map to a dedicated
 *     `PathDataParseError` (a `RuntimeException`); they are preserved, never swallowed.
 */
package ssg
package graphs
package commons
package rough
package pathdata

import scala.collection.mutable.ArrayBuffer
import scala.util.boundary
import scala.util.boundary.break

/** A parsed SVG path segment: a command `key` and its numeric parameter `data`. */
final case class Segment(key: String, data: Vector[Double])

/** Error raised by [[Parser.parsePath]] for malformed path data. Mirrors the
  * `throw new Error(...)` calls in the original `parsePath`.
  */
final class PathDataParseError(message: String) extends RuntimeException(message)

/** SVG path `d`-string tokenizer and parser (port of `parser.ts`). */
object Parser {

  private final val COMMAND = 0
  private final val NUMBER  = 1
  private final val EOD     = 2

  /** A lexical token: `tokenType` is one of [[COMMAND]] / [[NUMBER]] / [[EOD]]. */
  private final case class PathToken(tokenType: Int, text: String)

  private val Params: Map[String, Int] =
    Map(
      "A" -> 7,
      "a" -> 7,
      "C" -> 6,
      "c" -> 6,
      "H" -> 1,
      "h" -> 1,
      "L" -> 2,
      "l" -> 2,
      "M" -> 2,
      "m" -> 2,
      "Q" -> 4,
      "q" -> 4,
      "S" -> 4,
      "s" -> 4,
      "T" -> 2,
      "t" -> 2,
      "V" -> 1,
      "v" -> 1,
      "Z" -> 0,
      "z" -> 0
    )

  private val WhitespacePattern = "^([ \\t\\r\\n,]+)".r
  private val CommandPattern    = "^([aAcChHlLmMqQsStTvVzZ])".r
  private val NumberPattern     = "^(([-+]?[0-9]+(\\.[0-9]*)?|[-+]?\\.[0-9]+)([eE][-+]?[0-9]+)?)".r

  private def tokenize(d0: String): Vector[PathToken] = boundary[Vector[PathToken]] {
    var d: String                  = d0
    val tokens: ArrayBuffer[PathToken] = ArrayBuffer.empty
    while (d != "") {
      WhitespacePattern.findPrefixMatchOf(d) match {
        case Some(m) =>
          d = d.substring(m.group(1).length)
        case None =>
          CommandPattern.findPrefixMatchOf(d) match {
            case Some(m) =>
              tokens += PathToken(COMMAND, m.group(1))
              d = d.substring(m.group(1).length)
            case None =>
              NumberPattern.findPrefixMatchOf(d) match {
                case Some(m) =>
                  tokens += PathToken(NUMBER, m.group(1))
                  d = d.substring(m.group(1).length)
                case None =>
                  break(Vector.empty)
              }
          }
      }
    }
    tokens += PathToken(EOD, "")
    tokens.toVector
  }

  private def isType(token: PathToken, tpe: Int): Boolean =
    token.tokenType == tpe

  /** Parse an SVG path `d` string into a list of [[Segment]]s. */
  def parsePath(d: String): Vector[Segment] = boundary[Vector[Segment]] {
    val segments: ArrayBuffer[Segment] = ArrayBuffer.empty
    val tokens: Vector[PathToken]      = tokenize(d)
    var mode: String                   = "BOD"
    var index: Int                     = 0
    var token: PathToken               = tokens(index)
    while (!isType(token, EOD)) {
      var paramsCount: Int          = 0
      val params: ArrayBuffer[Double] = ArrayBuffer.empty
      if (mode == "BOD") {
        if (token.text == "M" || token.text == "m") {
          index += 1
          paramsCount = Params(token.text)
          mode = token.text
        } else {
          break(parsePath("M0,0" + d))
        }
      } else if (isType(token, NUMBER)) {
        paramsCount = Params(mode)
      } else {
        index += 1
        paramsCount = Params(token.text)
        mode = token.text
      }
      if ((index + paramsCount) < tokens.length) {
        var i: Int = index
        while (i < index + paramsCount) {
          val numbeToken: PathToken = tokens(i)
          if (isType(numbeToken, NUMBER)) {
            params += numbeToken.text.toDouble
          } else {
            throw new PathDataParseError("Param not a number: " + mode + "," + numbeToken.text)
          }
          i += 1
        }
        if (Params.contains(mode)) {
          val segment: Segment = Segment(mode, params.toVector)
          segments += segment
          index += paramsCount
          token = tokens(index)
          if (mode == "M") mode = "L"
          if (mode == "m") mode = "l"
        } else {
          throw new PathDataParseError("Bad segment: " + mode)
        }
      } else {
        throw new PathDataParseError("Path data ended short")
      }
    }
    segments.toVector
  }

  /** Serialize a list of [[Segment]]s back to an SVG path `d` string. */
  def serialize(segments: Vector[Segment]): String = {
    val tokens: ArrayBuffer[String] = ArrayBuffer.empty
    for (segment <- segments) {
      val key: String        = segment.key
      val data: Vector[Double] = segment.data
      tokens += key
      key match {
        case "C" | "c" =>
          tokens += jsNum(data(0))
          tokens += s"${jsNum(data(1))},"
          tokens += jsNum(data(2))
          tokens += s"${jsNum(data(3))},"
          tokens += jsNum(data(4))
          tokens += jsNum(data(5))
        case "S" | "s" | "Q" | "q" =>
          tokens += jsNum(data(0))
          tokens += s"${jsNum(data(1))},"
          tokens += jsNum(data(2))
          tokens += jsNum(data(3))
        case _ =>
          data.foreach(d => tokens += jsNum(d))
      }
    }
    tokens.mkString(" ")
  }

  /** Render a finite `Double` the way ECMA-262 `Number.prototype.toString` would for
    * the value range produced by SVG path serialization (integers without a trailing
    * `.0`; finite fractions as their shortest round-trip decimal). See the file
    * header Migration notes for the documented extreme-magnitude fallback.
    */
  private def jsNum(v: Double): String =
    if (v.isNaN) {
      "NaN"
    } else if (v.isPosInfinity) {
      "Infinity"
    } else if (v.isNegInfinity) {
      "-Infinity"
    } else if (v == Math.rint(v) && Math.abs(v) < 1e21) {
      new java.math.BigDecimal(v).toBigInteger.toString
    } else {
      java.lang.Double.toString(v)
    }
}
