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

import ssg.sass.{BuiltInCallable, Callable, Nullable, SassScriptException}
import ssg.sass.value.{SassColor, SassNumber, Value}
import ssg.sass.value.color.ColorSpace
import ssg.sass.util.NumberUtil.fuzzyRound

/** Built-in color functions: rgb, rgba, hsl, hsla, and legacy accessors /
  * manipulation functions (red, green, blue, hue, saturation, lightness,
  * alpha, mix, lighten, darken, saturate, desaturate, opacify, transparentize,
  * adjust-hue, invert, grayscale, complement).
  */
object ColorFunctions {

  // --- Helpers ---

  /** Clamp a value to `[min, max]`. */
  private def clamp(v: Double, min: Double, max: Double): Double =
    if (v < min) min else if (v > max) max else v

  /** Extract a scalar value from a SassNumber. If the number has a "%"
    * unit, it is scaled by `percentScale` (e.g. 255/100 for RGB channels).
    * Unitless numbers are returned as-is.
    */
  private def scalar(n: SassNumber, percentScale: Double = 1.0): Double = {
    if (n.hasUnit("%")) n.value * percentScale / 100.0
    else n.value
  }

  /** Interpret a number in degrees as a hue value (unit-agnostic; deg/rad/grad
    * would require conversion, but for legacy use the numeric value is used
    * verbatim which matches dart-sass's legacy behaviour).
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
    BuiltInCallable.function("rgb", "$args...", { args =>
      args.length match {
        case 3 =>
          val r = scalar(args(0).assertNumber(), 255)
          val g = scalar(args(1).assertNumber(), 255)
          val b = scalar(args(2).assertNumber(), 255)
          rgbFrom(r, g, b)
        case 4 =>
          val r = scalar(args(0).assertNumber(), 255)
          val g = scalar(args(1).assertNumber(), 255)
          val b = scalar(args(2).assertNumber(), 255)
          val a = scalar(args(3).assertNumber())
          rgbFrom(r, g, b, a)
        case 2 =>
          val color = args(0).assertColor()
          val a = scalar(args(1).assertNumber())
          color.changeAlpha(clamp(a, 0, 1))
        case n =>
          throw SassScriptException(
            s"Only 2, 3, or 4 arguments allowed for rgb(), was $n."
          )
      }
    })

  private val rgbaFn: BuiltInCallable =
    BuiltInCallable.function("rgba", "$args...", rgbFn.callback)

  private val hslFn: BuiltInCallable =
    BuiltInCallable.function("hsl", "$args...", { args =>
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
    })

  private val hslaFn: BuiltInCallable =
    BuiltInCallable.function("hsla", "$args...", hslFn.callback)

  // --- Accessors ---

  private val redFn: BuiltInCallable =
    BuiltInCallable.function("red", "$color", { args =>
      SassNumber(red255(args.head.assertColor()))
    })

  private val greenFn: BuiltInCallable =
    BuiltInCallable.function("green", "$color", { args =>
      SassNumber(green255(args.head.assertColor()))
    })

  private val blueFn: BuiltInCallable =
    BuiltInCallable.function("blue", "$color", { args =>
      SassNumber(blue255(args.head.assertColor()))
    })

  private val hueFn: BuiltInCallable =
    BuiltInCallable.function("hue", "$color", { args =>
      SassNumber(hueDeg(args.head.assertColor()), "deg")
    })

  private val saturationFn: BuiltInCallable =
    BuiltInCallable.function("saturation", "$color", { args =>
      SassNumber(saturationPct(args.head.assertColor()), "%")
    })

  private val lightnessFn: BuiltInCallable =
    BuiltInCallable.function("lightness", "$color", { args =>
      SassNumber(lightnessPct(args.head.assertColor()), "%")
    })

  private val alphaFn: BuiltInCallable =
    BuiltInCallable.function("alpha", "$color", { args =>
      SassNumber(args.head.assertColor().alpha)
    })

  private val opacityFn: BuiltInCallable =
    BuiltInCallable.function("opacity", "$color", { args =>
      SassNumber(args.head.assertColor().alpha)
    })

  // --- Manipulation ---

  private val mixFn: BuiltInCallable =
    BuiltInCallable.function("mix", "$color1, $color2, $weight: 50%", { args =>
      val c1 = args(0).assertColor()
      val c2 = args(1).assertColor()
      val weight =
        if (args.length >= 3) scalar(args(2).assertNumber(), 100) / 100.0
        else 0.5
      val w = clamp(weight, 0, 1)
      // dart-sass legacy mix: weight of c1; weight factor adjusted by alpha diff.
      val normalizedWeight = w * 2 - 1
      val alphaDiff = c1.alpha - c2.alpha
      val combinedWeight =
        if (normalizedWeight * alphaDiff == -1) normalizedWeight
        else (normalizedWeight + alphaDiff) / (1 + normalizedWeight * alphaDiff)
      val weight1 = (combinedWeight + 1) / 2
      val weight2 = 1 - weight1
      val r1 = c1.toSpace(ColorSpace.rgb).channel0
      val g1 = c1.toSpace(ColorSpace.rgb).channel1
      val b1 = c1.toSpace(ColorSpace.rgb).channel2
      val r2 = c2.toSpace(ColorSpace.rgb).channel0
      val g2 = c2.toSpace(ColorSpace.rgb).channel1
      val b2 = c2.toSpace(ColorSpace.rgb).channel2
      rgbFrom(
        r1 * weight1 + r2 * weight2,
        g1 * weight1 + g2 * weight2,
        b1 * weight1 + b2 * weight2,
        c1.alpha * w + c2.alpha * (1 - w)
      )
    })

  /** Helper for HSL-based manipulation: produce a new color adjusting the
    * given HSL channel by `delta` (clamped to [min,max]).
    */
  private def adjustHsl(color: SassColor,
                        hDelta: Double = 0,
                        sDelta: Double = 0,
                        lDelta: Double = 0): SassColor = {
    val hsl = color.toSpace(ColorSpace.hsl)
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

  private val lightenFn: BuiltInCallable =
    BuiltInCallable.function("lighten", "$color, $amount", { args =>
      val c = args(0).assertColor()
      val amt = scalar(args(1).assertNumber(), 100)
      adjustHsl(c, lDelta = amt)
    })

  private val darkenFn: BuiltInCallable =
    BuiltInCallable.function("darken", "$color, $amount", { args =>
      val c = args(0).assertColor()
      val amt = scalar(args(1).assertNumber(), 100)
      adjustHsl(c, lDelta = -amt)
    })

  private val saturateFn: BuiltInCallable =
    BuiltInCallable.function("saturate", "$color, $amount", { args =>
      val c = args(0).assertColor()
      val amt = scalar(args(1).assertNumber(), 100)
      adjustHsl(c, sDelta = amt)
    })

  private val desaturateFn: BuiltInCallable =
    BuiltInCallable.function("desaturate", "$color, $amount", { args =>
      val c = args(0).assertColor()
      val amt = scalar(args(1).assertNumber(), 100)
      adjustHsl(c, sDelta = -amt)
    })

  private val adjustHueFn: BuiltInCallable =
    BuiltInCallable.function("adjust-hue", "$color, $degrees", { args =>
      val c = args(0).assertColor()
      val deg = args(1).assertNumber().value
      adjustHsl(c, hDelta = deg)
    })

  private val complementFn: BuiltInCallable =
    BuiltInCallable.function("complement", "$color", { args =>
      val c = args(0).assertColor()
      adjustHsl(c, hDelta = 180)
    })

  private val grayscaleFn: BuiltInCallable =
    BuiltInCallable.function("grayscale", "$color", { args =>
      val c = args(0).assertColor()
      val hsl = c.toSpace(ColorSpace.hsl)
      val out = hslFrom(hsl.channel0, 0, hsl.channel2, c.alpha)
      if (c.space eq ColorSpace.hsl) out else out.toSpace(c.space)
    })

  private val invertFn: BuiltInCallable =
    BuiltInCallable.function("invert", "$color, $weight: 100%", { args =>
      val c = args(0).assertColor()
      val w =
        if (args.length >= 2) scalar(args(1).assertNumber(), 100) / 100.0
        else 1.0
      val rgb = c.toSpace(ColorSpace.rgb)
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
    })

  private val opacifyFn: BuiltInCallable =
    BuiltInCallable.function("opacify", "$color, $amount", { args =>
      val c = args(0).assertColor()
      val amt = scalar(args(1).assertNumber())
      c.changeAlpha(clamp(c.alpha + amt, 0, 1))
    })

  private val transparentizeFn: BuiltInCallable =
    BuiltInCallable.function("transparentize", "$color, $amount", { args =>
      val c = args(0).assertColor()
      val amt = scalar(args(1).assertNumber())
      c.changeAlpha(clamp(c.alpha - amt, 0, 1))
    })

  private val fadeInFn: BuiltInCallable =
    BuiltInCallable.function("fade-in", "$color, $amount", opacifyFn.callback)

  private val fadeOutFn: BuiltInCallable =
    BuiltInCallable.function("fade-out", "$color, $amount", transparentizeFn.callback)

  // --- Registration ---

  val global: List[Callable] = List(
    rgbFn, rgbaFn, hslFn, hslaFn,
    redFn, greenFn, blueFn, hueFn,
    saturationFn, lightnessFn, alphaFn, opacityFn,
    mixFn, lightenFn, darkenFn, saturateFn, desaturateFn,
    adjustHueFn, complementFn, grayscaleFn, invertFn,
    opacifyFn, transparentizeFn, fadeInFn, fadeOutFn
  )

  def module: List[Callable] = global

  /** Stub for any direct color function dispatch. */
  def stub(name: String, args: List[Value]): Value =
    throw new UnsupportedOperationException("Phase 9 stub: color." + name)
}
