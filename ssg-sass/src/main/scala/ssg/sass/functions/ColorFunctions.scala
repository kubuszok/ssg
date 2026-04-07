/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/functions/color.dart
 * Original: Copyright (c) 2016 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: color.dart -> ColorFunctions.scala
 *   Convention: Phase 9 — legacy color API built-ins (rgb/hsl + accessors +
 *               manipulation). Arity-overloaded names dispatch inside a single
 *               callback, since the evaluator does a simple name lookup.
 *   Idiom: Legacy accessors (red/hue/...) operate on SassColor by converting
 *          to the appropriate legacy space rather than using the deprecated
 *          accessor methods on SassColor itself.
 */
package ssg
package sass
package functions

import ssg.sass.{ BuiltInCallable, Callable, Nullable, SassScriptException }
import ssg.sass.value.{ SassBoolean, SassColor, SassNull, SassNumber, SassString, Value }
import ssg.sass.value.color.{ ColorSpace, GamutMapMethod, HueInterpolationMethod, InterpolationMethod }
import ssg.sass.util.NumberUtil.{ fuzzyEquals, fuzzyRound }

/** Built-in color functions: rgb, rgba, hsl, hsla, and legacy accessors / manipulation functions (red, green, blue, hue, saturation, lightness, alpha, mix, lighten, darken, saturate, desaturate,
  * opacify, transparentize, adjust-hue, invert, grayscale, complement).
  */
object ColorFunctions {

  // --- Helpers ---

  /** Returns true if [v] is a CSS "special" value whose presence in a color constructor argument list forces dart-sass to preserve the call as an unquoted plain-CSS function string rather than
    * evaluating it. This includes `var(--x)`, `env(--x)`, `attr(...)`, and anything that parses as an unquoted string containing a `(` / CSS function syntax that wouldn't normally reduce to a number.
    */
  private def isSpecialCssValue(v: Value): Boolean = v match {
    case s: SassString if !s.hasQuotes =>
      val t = s.text
      val l = t.toLowerCase
      l.startsWith("var(") || l.startsWith("env(") ||
      l.startsWith("attr(") || l.startsWith("calc(") ||
      l.startsWith("min(") || l.startsWith("max(") ||
      l.startsWith("clamp(") || l.startsWith("element(") ||
      l.startsWith("expression(")
    case _ => false
  }

  /** Clamp a value to `[min, max]`. */
  private def clamp(v: Double, min: Double, max: Double): Double =
    if (v < min) min else if (v > max) max else v

  /** Extract a scalar value from a SassNumber. If the number has a "%" unit, it is scaled by `percentScale` (e.g. 255/100 for RGB channels). Unitless numbers are returned as-is.
    */
  private def scalar(n: SassNumber, percentScale: Double = 1.0): Double =
    if (n.hasUnit("%")) n.value * percentScale / 100.0
    else n.value

  /** Interpret a number in degrees as a hue value (unit-agnostic; deg/rad/grad would require conversion, but for legacy use the numeric value is used verbatim which matches dart-sass's legacy
    * behaviour).
    */
  private def hueOf(n: SassNumber): Double = n.value

  /** The red channel of a color as a 0-255 integer (rounded). */
  private def red255(c: SassColor): Double =
    fuzzyRound(c.toSpace(ColorSpace.rgb).channel0).toDouble

  private def green255(c: SassColor): Double =
    fuzzyRound(c.toSpace(ColorSpace.rgb).channel1).toDouble

  private def blue255(c: SassColor): Double =
    fuzzyRound(c.toSpace(ColorSpace.rgb).channel2).toDouble

  private def hueDeg(c: SassColor): Double =
    c.toSpace(ColorSpace.hsl).channel0

  private def saturationPct(c: SassColor): Double =
    c.toSpace(ColorSpace.hsl).channel1

  private def lightnessPct(c: SassColor): Double =
    c.toSpace(ColorSpace.hsl).channel2

  /** Reconstruct a legacy RGB SassColor from 0-255 channel values, clamped. */
  private def rgbFrom(r: Double, g: Double, b: Double, a: Double = 1.0): SassColor =
    SassColor.rgb(
      Nullable(clamp(r, 0, 255)),
      Nullable(clamp(g, 0, 255)),
      Nullable(clamp(b, 0, 255)),
      Nullable(clamp(a, 0, 1))
    )

  /** Reconstruct a legacy HSL SassColor, with hue wrapped and s/l clamped. */
  private def hslFrom(h: Double, s: Double, l: Double, a: Double = 1.0): SassColor =
    SassColor.hsl(
      Nullable(h),
      Nullable(clamp(s, 0, 100)),
      Nullable(clamp(l, 0, 100)),
      Nullable(clamp(a, 0, 1))
    )

  // --- Constructors ---

  private val rgbFn: BuiltInCallable =
    BuiltInCallable.function(
      "rgb",
      "$red, $green, $blue, $alpha",
      args =>
        // If any argument is a CSS special value (var(--x), attr(...), env(...),
        // or a calc-like expression that can't be evaluated), dart-sass
        // preserves the call as an unquoted plain-CSS function string rather
        // than evaluating it. Detect that here.
        if (args.exists(isSpecialCssValue)) {
          val name = if (args.length == 4) "rgba" else "rgb"
          new SassString(
            s"$name(${args.map(_.toCssString(quote = false)).mkString(", ")})",
            hasQuotes = false
          )
        } else
          args.length match {
            case 1 =>
              // Single-argument form — e.g. `rgb(var(--c))`. Preserved as an
              // unquoted plain-CSS function call.
              new SassString(
                s"rgb(${args(0).toCssString(quote = false)})",
                hasQuotes = false
              )
            case 3 =>
              val r = scalar(args(0).assertNumber(), 255)
              val g = scalar(args(1).assertNumber(), 255)
              val b = scalar(args(2).assertNumber(), 255)
              rgbFrom(r, g, b)
            case 4 =>
              val r = scalar(args(0).assertNumber(), 255)
              val g = scalar(args(1).assertNumber(), 255)
              val b = scalar(args(2).assertNumber(), 255)
              if (args(3) eq SassNull) {
                EvaluationContext.warnForDeprecation(
                  Deprecation.NullAlpha,
                  "Passing null as the alpha channel to rgb()/rgba() is deprecated. Recommendation: use `none` or omit the alpha argument."
                )
              }
              val a = if (args(3) eq SassNull) 1.0 else scalar(args(3).assertNumber())
              rgbFrom(r, g, b, a)
            case 2 =>
              EvaluationContext.warnForDeprecation(
                Deprecation.ColorModuleCompat,
                "Passing a color and alpha to the global rgb()/rgba() is deprecated. Recommendation: color.change($color, $alpha: ...)."
              )
              val color = args(0).assertColor()
              val a     = scalar(args(1).assertNumber())
              color.changeAlpha(clamp(a, 0, 1))
            case n =>
              throw SassScriptException(
                s"Only 2, 3, or 4 arguments allowed for rgb(), was $n."
              )
          }
    )

  private val rgbaFn: BuiltInCallable =
    BuiltInCallable.function("rgba", "$red, $green, $blue, $alpha", rgbFn.callback)

  private val hslFn: BuiltInCallable =
    BuiltInCallable.function(
      "hsl",
      "$hue, $saturation, $lightness, $alpha",
      args =>
        args.length match {
          case 3 =>
            val h = hueOf(args(0).assertNumber())
            val s = args(1).assertNumber().value
            val l = args(2).assertNumber().value
            hslFrom(h, s, l)
          case 4 =>
            val h = hueOf(args(0).assertNumber())
            val s = args(1).assertNumber().value
            val l = args(2).assertNumber().value
            val a = scalar(args(3).assertNumber())
            hslFrom(h, s, l, a)
          case n =>
            throw SassScriptException(
              s"Only 3 or 4 arguments allowed for hsl(), was $n."
            )
        }
    )

  private val hslaFn: BuiltInCallable =
    BuiltInCallable.function("hsla", "$hue, $saturation, $lightness, $alpha", hslFn.callback)

  // --- Modern CSS color constructors ---
  // These accept comma-separated arguments. The modern space-separated / slash
  // syntax (`lab(50% 20 -30 / 0.5)`) is not yet parsed by StylesheetParser, so
  // SCSS sources must use the comma form for now.

  /** Returns the raw numeric value of a SassNumber, stripping `%` (which is treated as "100" for Lab-style percentages — Lab lightness has a 0-100 range, and a/b have implicit ranges that the CSS
    * spec percent-maps).
    */
  private def labChannel(n: SassNumber, percentScale: Double): Double =
    if (n.hasUnit("%")) n.value * percentScale / 100.0
    else n.value

  /** Returns true if [v] is the CSS `none` channel keyword (parsed as an unquoted SassString). */
  private def isNone(v: Value): Boolean = v match {
    case s: SassString => !s.hasQuotes && s.text == "none"
    case _ => false
  }

  /** Interprets [v] as a lab/lch-style channel, returning Nullable.Null for `none`. */
  private def labChannelOrNone(v: Value, percentScale: Double): Nullable[Double] =
    if (isNone(v)) Nullable.Null
    else Nullable(labChannel(v.assertNumber(), percentScale))

  /** Interprets [v] as a hue channel, returning Nullable.Null for `none`. */
  private def hueOrNone(v: Value): Nullable[Double] =
    if (isNone(v)) Nullable.Null
    else Nullable(hueOf(v.assertNumber()))

  /** Interprets [v] as an alpha channel, returning Nullable.Null for `none`, clamped to `[0, 1]`. */
  private def alphaOrNone(v: Value): Nullable[Double] =
    if (isNone(v)) Nullable.Null
    else Nullable(clamp(scalar(v.assertNumber()), 0, 1))

  /** Interprets [v] as a plain numeric channel with no percentage scaling, returning Nullable.Null for `none`. */
  private def numberOrNone(v: Value): Nullable[Double] =
    if (isNone(v)) Nullable.Null
    else Nullable(v.assertNumber().value)

  private val labFn: BuiltInCallable =
    BuiltInCallable.function(
      "lab",
      "$lightness, $a, $b, $alpha: 1",
      { args =>
        val l     = labChannelOrNone(args(0), 100)
        val a     = labChannelOrNone(args(1), 125)
        val b     = labChannelOrNone(args(2), 125)
        val alpha = if (args.length >= 4) alphaOrNone(args(3)) else Nullable(1.0)
        SassColor.lab(l, a, b, alpha)
      }
    )

  private val lchFn: BuiltInCallable =
    BuiltInCallable.function(
      "lch",
      "$lightness, $chroma, $hue, $alpha: 1",
      { args =>
        val l     = labChannelOrNone(args(0), 100)
        val c     = labChannelOrNone(args(1), 150)
        val h     = hueOrNone(args(2))
        val alpha = if (args.length >= 4) alphaOrNone(args(3)) else Nullable(1.0)
        SassColor.lch(l, c, h, alpha)
      }
    )

  private val oklabFn: BuiltInCallable =
    BuiltInCallable.function(
      "oklab",
      "$lightness, $a, $b, $alpha: 1",
      { args =>
        val l     = labChannelOrNone(args(0), 1)
        val a     = labChannelOrNone(args(1), 0.4)
        val b     = labChannelOrNone(args(2), 0.4)
        val alpha = if (args.length >= 4) alphaOrNone(args(3)) else Nullable(1.0)
        SassColor.oklab(l, a, b, alpha)
      }
    )

  private val oklchFn: BuiltInCallable =
    BuiltInCallable.function(
      "oklch",
      "$lightness, $chroma, $hue, $alpha: 1",
      { args =>
        val l     = labChannelOrNone(args(0), 1)
        val c     = labChannelOrNone(args(1), 0.4)
        val h     = hueOrNone(args(2))
        val alpha = if (args.length >= 4) alphaOrNone(args(3)) else Nullable(1.0)
        SassColor.oklch(l, c, h, alpha)
      }
    )

  private val hwbFn: BuiltInCallable =
    BuiltInCallable.function(
      "hwb",
      "$hue, $whiteness, $blackness, $alpha: 1",
      { args =>
        val h     = hueOrNone(args(0))
        val w     = if (isNone(args(1))) Nullable.Null else Nullable(clamp(args(1).assertNumber().value, 0, 100))
        val b     = if (isNone(args(2))) Nullable.Null else Nullable(clamp(args(2).assertNumber().value, 0, 100))
        val alpha = if (args.length >= 4) alphaOrNone(args(3)) else Nullable(1.0)
        SassColor.hwb(h, w, b, alpha)
      }
    )

  /** `color($space, $c1, $c2, $c3, $alpha: 1)` — constructs a color in an explicit non-legacy color space (srgb, display-p3, a98-rgb, etc.).
    */
  private val colorFn: BuiltInCallable =
    BuiltInCallable.function(
      "color",
      "$space, $channel1, $channel2, $channel3, $alpha: 1",
      { args =>
        if (args.length < 4)
          throw SassScriptException(
            s"color() requires at least 4 arguments, was ${args.length}."
          )
        val spaceName = args(0) match {
          case s: SassString => s.text
          case other => other.assertString().text
        }
        val space = ColorSpace.fromName(spaceName)
        val c1    = numberOrNone(args(1))
        val c2    = numberOrNone(args(2))
        val c3    = numberOrNone(args(3))
        val alpha = if (args.length >= 5) alphaOrNone(args(4)) else Nullable(1.0)
        SassColor.forSpaceInternal(space, c1, c2, c3, alpha)
      }
    )

  // --- Accessors ---

  private val redFn: BuiltInCallable =
    BuiltInCallable.function("red", "$color", args => SassNumber(red255(args.head.assertColor())))

  private val greenFn: BuiltInCallable =
    BuiltInCallable.function("green", "$color", args => SassNumber(green255(args.head.assertColor())))

  private val blueFn: BuiltInCallable =
    BuiltInCallable.function("blue", "$color", args => SassNumber(blue255(args.head.assertColor())))

  private val hueFn: BuiltInCallable =
    BuiltInCallable.function("hue", "$color", args => SassNumber(hueDeg(args.head.assertColor()), "deg"))

  private val saturationFn: BuiltInCallable =
    BuiltInCallable.function("saturation", "$color", args => SassNumber(saturationPct(args.head.assertColor()), "%"))

  private val lightnessFn: BuiltInCallable =
    BuiltInCallable.function("lightness", "$color", args => SassNumber(lightnessPct(args.head.assertColor()), "%"))

  private val alphaFn: BuiltInCallable =
    BuiltInCallable.function("alpha", "$color", args => SassNumber(args.head.assertColor().alpha))

  private val opacityFn: BuiltInCallable =
    BuiltInCallable.function("opacity", "$color", args => SassNumber(args.head.assertColor().alpha))

  // --- Manipulation ---

  private val mixFn: BuiltInCallable =
    BuiltInCallable.function(
      "mix",
      "$color1, $color2, $weight: 50%, $space: null",
      { args =>
        val c1     = args(0).assertColor()
        val c2     = args(1).assertColor()
        val weight =
          if (args.length >= 3 && !(args(2) eq SassNull))
            scalar(args(2).assertNumber(), 100) / 100.0
          else 0.5
        val w = clamp(weight, 0, 1)
        // $space: if supplied and not null, perform interpolation in the given
        // non-legacy color space using SassColor.interpolate (which handles the
        // conversions and hue interpolation).
        val spaceArg: Option[String] =
          if (args.length >= 4 && !(args(3) eq SassNull))
            args(3) match {
              case s: SassString => Some(s.text)
              case other => Some(other.assertString().text)
            }
          else None
        spaceArg match {
          case Some(name) =>
            val space  = ColorSpace.fromName(name)
            val method =
              if (space.isPolar)
                InterpolationMethod(space, Nullable(HueInterpolationMethod.Shorter))
              else
                InterpolationMethod(space)
            // SassColor.interpolate uses this-weight: passing `w` means the
            // mix is weighted toward c1 by w, matching the documented mix()
            // semantics where $weight is how much of $color1 is kept.
            c1.interpolate(c2, method, weight = w)
          case None =>
            // dart-sass legacy mix: weight of c1; weight factor adjusted by alpha diff.
            val normalizedWeight = w * 2 - 1
            val alphaDiff        = c1.alpha - c2.alpha
            val combinedWeight   =
              if (normalizedWeight * alphaDiff == -1) normalizedWeight
              else (normalizedWeight + alphaDiff) / (1 + normalizedWeight * alphaDiff)
            val weight1 = (combinedWeight + 1) / 2
            val weight2 = 1 - weight1
            val r1      = c1.toSpace(ColorSpace.rgb).channel0
            val g1      = c1.toSpace(ColorSpace.rgb).channel1
            val b1      = c1.toSpace(ColorSpace.rgb).channel2
            val r2      = c2.toSpace(ColorSpace.rgb).channel0
            val g2      = c2.toSpace(ColorSpace.rgb).channel1
            val b2      = c2.toSpace(ColorSpace.rgb).channel2
            rgbFrom(
              r1 * weight1 + r2 * weight2,
              g1 * weight1 + g2 * weight2,
              b1 * weight1 + b2 * weight2,
              c1.alpha * w + c2.alpha * (1 - w)
            )
        }
      }
    )

  /** Helper for HSL-based manipulation: produce a new color adjusting the given HSL channel by `delta` (clamped to [min,max]).
    */
  private def adjustHsl(color: SassColor, hDelta: Double = 0, sDelta: Double = 0, lDelta: Double = 0): SassColor = {
    val hsl      = color.toSpace(ColorSpace.hsl)
    val newColor = hslFrom(
      hsl.channel0 + hDelta,
      hsl.channel1 + sDelta,
      hsl.channel2 + lDelta,
      color.alpha
    )
    // Preserve original space if possible (legacy: keep RGB in legacy form).
    if (color.space eq ColorSpace.hsl) newColor
    else newColor.toSpace(color.space)
  }

  /** Emit the `color-functions` deprecation for a legacy global color-manipulation function call. */
  private def warnLegacyColorFunction(name: String, modernHint: String): Unit =
    EvaluationContext.warnForDeprecation(
      Deprecation.ColorFunctions,
      s"$name() is deprecated. Suggestion: $modernHint."
    )

  private val lightenFn: BuiltInCallable =
    BuiltInCallable.function(
      "lighten",
      "$color, $amount",
      { args =>
        warnLegacyColorFunction("lighten", "color.adjust($color, $lightness: +$amount)")
        val c   = args(0).assertColor()
        val amt = scalar(args(1).assertNumber(), 100)
        adjustHsl(c, lDelta = amt)
      }
    )

  private val darkenFn: BuiltInCallable =
    BuiltInCallable.function(
      "darken",
      "$color, $amount",
      { args =>
        warnLegacyColorFunction("darken", "color.adjust($color, $lightness: -$amount)")
        val c   = args(0).assertColor()
        val amt = scalar(args(1).assertNumber(), 100)
        adjustHsl(c, lDelta = -amt)
      }
    )

  private val saturateFn: BuiltInCallable =
    BuiltInCallable.function(
      "saturate",
      "$color, $amount",
      { args =>
        warnLegacyColorFunction("saturate", "color.adjust($color, $saturation: +$amount)")
        val c   = args(0).assertColor()
        val amt = scalar(args(1).assertNumber(), 100)
        adjustHsl(c, sDelta = amt)
      }
    )

  private val desaturateFn: BuiltInCallable =
    BuiltInCallable.function(
      "desaturate",
      "$color, $amount",
      { args =>
        warnLegacyColorFunction("desaturate", "color.adjust($color, $saturation: -$amount)")
        val c   = args(0).assertColor()
        val amt = scalar(args(1).assertNumber(), 100)
        adjustHsl(c, sDelta = -amt)
      }
    )

  private val adjustHueFn: BuiltInCallable =
    BuiltInCallable.function(
      "adjust-hue",
      "$color, $degrees",
      { args =>
        val c   = args(0).assertColor()
        val deg = args(1).assertNumber().value
        adjustHsl(c, hDelta = deg)
      }
    )

  private val complementFn: BuiltInCallable =
    BuiltInCallable.function("complement",
                             "$color",
                             { args =>
                               val c = args(0).assertColor()
                               adjustHsl(c, hDelta = 180)
                             }
    )

  private val grayscaleFn: BuiltInCallable =
    BuiltInCallable.function(
      "grayscale",
      "$color",
      { args =>
        val c   = args(0).assertColor()
        val hsl = c.toSpace(ColorSpace.hsl)
        val out = hslFrom(hsl.channel0, 0, hsl.channel2, c.alpha)
        if (c.space eq ColorSpace.hsl) out else out.toSpace(c.space)
      }
    )

  private val invertFn: BuiltInCallable =
    BuiltInCallable.function(
      "invert",
      "$color, $weight: 100%",
      { args =>
        val c = args(0).assertColor()
        val w =
          if (args.length >= 2) scalar(args(1).assertNumber(), 100) / 100.0
          else 1.0
        val rgb      = c.toSpace(ColorSpace.rgb)
        val inverted = rgbFrom(
          255 - rgb.channel0,
          255 - rgb.channel1,
          255 - rgb.channel2,
          c.alpha
        )
        if (w == 1.0) inverted
        else {
          // linear mix between c and inverted by weight w
          val weight1 = clamp(w, 0, 1)
          val weight2 = 1 - weight1
          rgbFrom(
            (255 - rgb.channel0) * weight1 + rgb.channel0 * weight2,
            (255 - rgb.channel1) * weight1 + rgb.channel1 * weight2,
            (255 - rgb.channel2) * weight1 + rgb.channel2 * weight2,
            c.alpha
          )
        }
      }
    )

  private val opacifyFn: BuiltInCallable =
    BuiltInCallable.function(
      "opacify",
      "$color, $amount",
      { args =>
        warnLegacyColorFunction("opacify", "color.adjust($color, $alpha: +$amount)")
        val c   = args(0).assertColor()
        val amt = scalar(args(1).assertNumber())
        c.changeAlpha(clamp(c.alpha + amt, 0, 1))
      }
    )

  private val transparentizeFn: BuiltInCallable =
    BuiltInCallable.function(
      "transparentize",
      "$color, $amount",
      { args =>
        warnLegacyColorFunction("transparentize", "color.adjust($color, $alpha: -$amount)")
        val c   = args(0).assertColor()
        val amt = scalar(args(1).assertNumber())
        c.changeAlpha(clamp(c.alpha - amt, 0, 1))
      }
    )

  private val fadeInFn: BuiltInCallable =
    BuiltInCallable.function("fade-in", "$color, $amount", opacifyFn.callback)

  private val fadeOutFn: BuiltInCallable =
    BuiltInCallable.function("fade-out", "$color, $amount", transparentizeFn.callback)

  // --- change/adjust/scale-color helpers ---

  /** Returns `Some(scalar)` for a SassNumber (with optional `%` scaling), or `None` for `SassNull` / unset positional slots produced by the named-arg binder.
    */
  private def optScalar(v: Value, percentScale: Double = 1.0): Option[Double] =
    if (v eq SassNull) None
    else
      v match {
        case n: SassNumber =>
          Some(if (n.hasUnit("%")) n.value * percentScale / 100.0 else n.value)
        case other =>
          Some(other.assertNumber().value)
      }

  /** Scale `value` toward `min`/`max` by `factor` in [-1,1]. */
  private def scaleToward(value: Double, factor: Double, min: Double, max: Double): Double =
    if (factor > 0) value + (max - value) * factor
    else if (factor < 0) value + (value - min) * factor
    else value

  private val changeColorFn: BuiltInCallable =
    BuiltInCallable.function(
      "change-color",
      "$color, $red, $green, $blue, $hue, $saturation, $lightness, $alpha",
      { args =>
        val c          = args(0).assertColor()
        val red        = if (args.length > 1) optScalar(args(1), 255) else None
        val green      = if (args.length > 2) optScalar(args(2), 255) else None
        val blue       = if (args.length > 3) optScalar(args(3), 255) else None
        val hue        = if (args.length > 4) optScalar(args(4)) else None
        val sat        = if (args.length > 5) optScalar(args(5), 100) else None
        val light      = if (args.length > 6) optScalar(args(6), 100) else None
        val alpha      = if (args.length > 7) optScalar(args(7)) else None
        val touchesRgb = red.isDefined || green.isDefined || blue.isDefined
        val touchesHsl = hue.isDefined || sat.isDefined || light.isDefined
        if (touchesRgb && touchesHsl)
          throw SassScriptException(
            "RGB parameters may not be passed along with HSL parameters."
          )
        if (touchesHsl) {
          val hsl = c.toSpace(ColorSpace.hsl)
          val out = hslFrom(
            hue.getOrElse(hsl.channel0),
            sat.getOrElse(hsl.channel1),
            light.getOrElse(hsl.channel2),
            alpha.getOrElse(c.alpha)
          )
          if (c.space eq ColorSpace.hsl) out else out.toSpace(c.space)
        } else if (touchesRgb) {
          val rgb = c.toSpace(ColorSpace.rgb)
          val out = rgbFrom(
            red.getOrElse(rgb.channel0),
            green.getOrElse(rgb.channel1),
            blue.getOrElse(rgb.channel2),
            alpha.getOrElse(c.alpha)
          )
          if (c.space eq ColorSpace.rgb) out else out.toSpace(c.space)
        } else {
          // Only alpha (or nothing) supplied.
          alpha match {
            case Some(a) => c.changeAlpha(clamp(a, 0, 1))
            case None    => c
          }
        }
      }
    )

  private val adjustColorFn: BuiltInCallable =
    BuiltInCallable.function(
      "adjust-color",
      "$color, $red, $green, $blue, $hue, $saturation, $lightness, $alpha",
      { args =>
        val c          = args(0).assertColor()
        val red        = if (args.length > 1) optScalar(args(1), 255) else None
        val green      = if (args.length > 2) optScalar(args(2), 255) else None
        val blue       = if (args.length > 3) optScalar(args(3), 255) else None
        val hue        = if (args.length > 4) optScalar(args(4)) else None
        val sat        = if (args.length > 5) optScalar(args(5), 100) else None
        val light      = if (args.length > 6) optScalar(args(6), 100) else None
        val alpha      = if (args.length > 7) optScalar(args(7)) else None
        val touchesRgb = red.isDefined || green.isDefined || blue.isDefined
        val touchesHsl = hue.isDefined || sat.isDefined || light.isDefined
        if (touchesRgb && touchesHsl)
          throw SassScriptException(
            "RGB parameters may not be passed along with HSL parameters."
          )
        val newAlpha = clamp(c.alpha + alpha.getOrElse(0.0), 0, 1)
        if (touchesHsl) {
          val out = adjustHsl(
            c,
            hDelta = hue.getOrElse(0.0),
            sDelta = sat.getOrElse(0.0),
            lDelta = light.getOrElse(0.0)
          )
          out.changeAlpha(newAlpha)
        } else if (touchesRgb) {
          val rgb = c.toSpace(ColorSpace.rgb)
          val out = rgbFrom(
            rgb.channel0 + red.getOrElse(0.0),
            rgb.channel1 + green.getOrElse(0.0),
            rgb.channel2 + blue.getOrElse(0.0),
            newAlpha
          )
          if (c.space eq ColorSpace.rgb) out else out.toSpace(c.space)
        } else {
          c.changeAlpha(newAlpha)
        }
      }
    )

  private val scaleColorFn: BuiltInCallable =
    BuiltInCallable.function(
      "scale-color",
      "$color, $red, $green, $blue, $saturation, $lightness, $alpha",
      { args =>
        val c          = args(0).assertColor()
        val red        = if (args.length > 1) optScalar(args(1), 100).map(_ / 100.0) else None
        val green      = if (args.length > 2) optScalar(args(2), 100).map(_ / 100.0) else None
        val blue       = if (args.length > 3) optScalar(args(3), 100).map(_ / 100.0) else None
        val sat        = if (args.length > 4) optScalar(args(4), 100).map(_ / 100.0) else None
        val light      = if (args.length > 5) optScalar(args(5), 100).map(_ / 100.0) else None
        val alpha      = if (args.length > 6) optScalar(args(6), 100).map(_ / 100.0) else None
        val touchesRgb = red.isDefined || green.isDefined || blue.isDefined
        val touchesHsl = sat.isDefined || light.isDefined
        if (touchesRgb && touchesHsl)
          throw SassScriptException(
            "RGB parameters may not be passed along with HSL parameters."
          )
        val newAlpha = alpha.fold(c.alpha)(f => scaleToward(c.alpha, f, 0, 1))
        if (touchesHsl) {
          val hsl = c.toSpace(ColorSpace.hsl)
          val out = hslFrom(
            hsl.channel0,
            sat.fold(hsl.channel1)(f => scaleToward(hsl.channel1, f, 0, 100)),
            light.fold(hsl.channel2)(f => scaleToward(hsl.channel2, f, 0, 100)),
            newAlpha
          )
          if (c.space eq ColorSpace.hsl) out else out.toSpace(c.space)
        } else if (touchesRgb) {
          val rgb = c.toSpace(ColorSpace.rgb)
          val out = rgbFrom(
            red.fold(rgb.channel0)(f => scaleToward(rgb.channel0, f, 0, 255)),
            green.fold(rgb.channel1)(f => scaleToward(rgb.channel1, f, 0, 255)),
            blue.fold(rgb.channel2)(f => scaleToward(rgb.channel2, f, 0, 255)),
            newAlpha
          )
          if (c.space eq ColorSpace.rgb) out else out.toSpace(c.space)
        } else {
          c.changeAlpha(newAlpha)
        }
      }
    )

  // --- Color Module 4 introspection API ---

  /** Parse a `$space` argument as a color-space name, or None for SassNull / omitted. */
  private def optSpace(v: Value): Option[ColorSpace] =
    if (v eq SassNull) None
    else
      v match {
        case s: SassString => Some(ColorSpace.fromName(s.text))
        case other => Some(ColorSpace.fromName(other.assertString().text))
      }

  /** Parse a string argument (for channel / space / method names). Unquoted SassString is the canonical representation; quoted strings are also accepted.
    */
  private def strArg(v: Value): String = v match {
    case s: SassString => s.text
    case other => other.assertString().text
  }

  /** Build a SassNumber for a channel value, attaching the channel's associated unit (e.g. "deg" for hue, "%" for hsl saturation/lightness when not normalized).
    */
  private def channelNumber(value: Double, channel: ssg.sass.value.color.ColorChannel): SassNumber = {
    val unit = channel.associatedUnit
    if (unit.isEmpty) SassNumber(value) else SassNumber(value, unit.get)
  }

  /** color.channel($color, $channel, $space: null) */
  private val channelFn: BuiltInCallable =
    BuiltInCallable.function(
      "channel",
      "$color, $channel, $space: null",
      { args =>
        val color       = args(0).assertColor()
        val channelName = strArg(args(1))
        val spaceOpt    = if (args.length >= 3) optSpace(args(2)) else None
        val viewed      = spaceOpt.fold(color)(sp => color.toSpace(sp))
        if (channelName == "alpha") SassNumber(viewed.alpha)
        else {
          val chs = viewed.space.channels
          val idx =
            if (channelName == chs(0).name) 0
            else if (channelName == chs(1).name) 1
            else if (channelName == chs(2).name) 2
            else
              throw SassScriptException(
                s"Color $viewed doesn't have a channel named \"$channelName\"."
              )
          channelNumber(viewed.channel(channelName), chs(idx))
        }
      }
    )

  /** color.space($color) */
  private val spaceFn: BuiltInCallable =
    BuiltInCallable.function(
      "space",
      "$color",
      args => SassString(args(0).assertColor().space.name, hasQuotes = false)
    )

  /** color.is-legacy($color) */
  private val isLegacyFn: BuiltInCallable =
    BuiltInCallable.function(
      "is-legacy",
      "$color",
      args => SassBoolean(args(0).assertColor().isLegacy)
    )

  /** color.is-in-gamut($color, $space: null) */
  private val isInGamutFn: BuiltInCallable =
    BuiltInCallable.function(
      "is-in-gamut",
      "$color, $space: null",
      { args =>
        val color    = args(0).assertColor()
        val spaceOpt = if (args.length >= 2) optSpace(args(1)) else None
        val viewed   = spaceOpt.fold(color)(sp => color.toSpace(sp))
        SassBoolean(viewed.isInGamut)
      }
    )

  /** color.is-powerless($color, $channel, $space: null) */
  private val isPowerlessFn: BuiltInCallable =
    BuiltInCallable.function(
      "is-powerless",
      "$color, $channel, $space: null",
      { args =>
        val color       = args(0).assertColor()
        val channelName = strArg(args(1))
        val spaceOpt    = if (args.length >= 3) optSpace(args(2)) else None
        val viewed      = spaceOpt.fold(color)(sp => color.toSpace(sp))
        SassBoolean(viewed.isChannelPowerless(channelName))
      }
    )

  /** color.is-missing($color, $channel) */
  private val isMissingFn: BuiltInCallable =
    BuiltInCallable.function(
      "is-missing",
      "$color, $channel",
      { args =>
        val color       = args(0).assertColor()
        val channelName = strArg(args(1))
        SassBoolean(color.isChannelMissing(channelName))
      }
    )

  /** color.to-space($color, $space) */
  private val toSpaceFn: BuiltInCallable =
    BuiltInCallable.function(
      "to-space",
      "$color, $space",
      { args =>
        val color = args(0).assertColor()
        val sp    = ColorSpace.fromName(strArg(args(1)))
        color.toSpace(sp)
      }
    )

  /** color.to-gamut($color, $space: null, $method: null) */
  private val toGamutFn: BuiltInCallable =
    BuiltInCallable.function(
      "to-gamut",
      "$color, $space: null, $method: null",
      { args =>
        val color    = args(0).assertColor()
        val spaceOpt = if (args.length >= 2) optSpace(args(1)) else None
        val methodOpt: Option[GamutMapMethod] =
          if (args.length >= 3 && !(args(2) eq SassNull))
            Some(GamutMapMethod.fromName(strArg(args(2))))
          else None
        val method = methodOpt.getOrElse(GamutMapMethod.localMinde)
        spaceOpt match {
          case None     => color.toGamut(method)
          case Some(sp) =>
            val inSp   = color.toSpace(sp)
            val mapped = inSp.toGamut(method)
            if (color.space eq sp) mapped else mapped.toSpace(color.space)
        }
      }
    )

  /** color.same($color1, $color2) — true if both normalize to the same xyz-d65 value. */
  private val sameFn: BuiltInCallable =
    BuiltInCallable.function(
      "same",
      "$color1, $color2",
      { args =>
        val a      = args(0).assertColor()
        val b      = args(1).assertColor()
        val target = ColorSpace.xyzD65
        val aa     = a.toSpace(target)
        val bb     = b.toSpace(target)
        val equal  =
          fuzzyEquals(aa.channel0, bb.channel0) &&
            fuzzyEquals(aa.channel1, bb.channel1) &&
            fuzzyEquals(aa.channel2, bb.channel2) &&
            fuzzyEquals(aa.alpha, bb.alpha)
        SassBoolean(equal)
      }
    )

  // --- Registration ---

  val global: List[Callable] = List(
    rgbFn,
    rgbaFn,
    hslFn,
    hslaFn,
    hwbFn,
    labFn,
    lchFn,
    oklabFn,
    oklchFn,
    colorFn,
    redFn,
    greenFn,
    blueFn,
    hueFn,
    saturationFn,
    lightnessFn,
    alphaFn,
    opacityFn,
    mixFn,
    lightenFn,
    darkenFn,
    saturateFn,
    desaturateFn,
    adjustHueFn,
    complementFn,
    grayscaleFn,
    invertFn,
    opacifyFn,
    transparentizeFn,
    fadeInFn,
    fadeOutFn,
    changeColorFn,
    adjustColorFn,
    scaleColorFn
  )

  /** Color Module 4 introspection entry points — only registered under the `sass:color` module (not as globals).
    */
  private val moduleOnly: List[Callable] = List(
    channelFn,
    spaceFn,
    isLegacyFn,
    isInGamutFn,
    isPowerlessFn,
    isMissingFn,
    toSpaceFn,
    toGamutFn,
    sameFn
  )

  def module: List[Callable] = global ::: moduleOnly

  /** Stub for any direct color function dispatch. */
  def stub(name: String, args: List[Value]): Value =
    throw new UnsupportedOperationException("Phase 9 stub: color." + name)
}
