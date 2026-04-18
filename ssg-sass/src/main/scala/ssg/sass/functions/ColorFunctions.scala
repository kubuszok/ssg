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

import ssg.sass.{ BuiltInCallable, Callable, Nullable, SassScriptException, SassFormatException }
import ssg.sass.Utils.pluralize
import ssg.sass.value.{ ColorFormat, ListSeparator, SassBoolean, SassColor, SassList, SassNull, SassNumber, SassString, Value }
import ssg.sass.value.color.{ ColorChannel, ColorSpace, GamutMapMethod, InterpolationMethod, LinearChannel }
import ssg.sass.util.NumberUtil.{ fuzzyEquals, fuzzyRound }
import ssg.sass.parse.ScssParser

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

  /** Interpret a number as a hue value in degrees. Converts angle-compatible
    * units (rad, grad, turn) to degrees. Unitless values are used as-is.
    */
  private def hueOf(n: SassNumber): Double =
    if (n.compatibleWithUnit("deg")) n.coerceValueToUnit("deg")
    else n.value

  /** The red channel of a color as a 0-255 integer (rounded).
    *
    * Uses standard `.round` to match Dart's `color.red` getter which calls
    * `_legacyChannel(ColorSpace.rgb, 'red').round()`.
    */
  private def red255(c: SassColor): Double =
    math.round(c.toSpace(ColorSpace.rgb).channel0).toDouble

  private def green255(c: SassColor): Double =
    math.round(c.toSpace(ColorSpace.rgb).channel1).toDouble

  private def blue255(c: SassColor): Double =
    math.round(c.toSpace(ColorSpace.rgb).channel2).toDouble

  private def hueDeg(c: SassColor): Double =
    c.toSpace(ColorSpace.hsl).channel0

  private def saturationPct(c: SassColor): Double =
    c.toSpace(ColorSpace.hsl).channel1

  private def lightnessPct(c: SassColor): Double =
    c.toSpace(ColorSpace.hsl).channel2

  /** Reconstruct a legacy RGB SassColor from the rgb()/rgba() function, with ColorFormat.RgbFunction. */
  private def rgbFunctionFrom(r: Double, g: Double, b: Double, a: Double = 1.0): SassColor =
    SassColor.rgbInternal(
      Nullable(clamp(r, 0, 255)),
      Nullable(clamp(g, 0, 255)),
      Nullable(clamp(b, 0, 255)),
      Nullable(clamp(a, 0, 1)),
      Nullable(ColorFormat.RgbFunction)
    )

  /** Reconstruct a legacy RGB SassColor from 0-255 channel values, clamped. */
  private def rgbFrom(r: Double, g: Double, b: Double, a: Double): SassColor =
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

  /** If a special number string is detected in these color spaces, even if they
    * were using the one-argument function syntax, we convert it to the three- or
    * four- argument comma-separated syntax for broader browser compatibility.
    */
  private val _specialCommaSpaces: Set[ColorSpace] = Set(ColorSpace.rgb, ColorSpace.hsl)

  /** Returns a string representation of [name] called with [arguments], as though
    * it were a plain CSS function.
    * Ported from dart-sass `_functionString`.
    */
  private def functionString(name: String, arguments: Iterable[Value]): SassString =
    SassString(
      name + "(" + arguments.map(_.toCssString(quote = false)).mkString(", ") + ")",
      hasQuotes = false
    )

  /** Parses [number] as a percentage or unitless number and returns the value.
    * If [number] has units other than '%', throws a SassScriptException.
    * Ported from dart-sass `_percentageOrUnitless`.
    */
  private def percentageOrUnitless(number: SassNumber, max: Double, name: Option[String]): Double = {
    if (!number.hasUnits) number.value
    else if (number.hasUnit("%")) max * number.value / 100.0
    else
      throw SassScriptException(
        s"Expected $number to have unit \"%\" or no units.",
        name
      )
  }

  /** Parses [text] as either a Sass number or an unquoted Sass string.
    * Ported from dart-sass `_parseNumberOrString`.
    */
  private def parseNumberOrString(text: String): Value = {
    try {
      new ScssParser(text).parseNumber()
    } catch {
      case _: SassFormatException =>
        SassString(text, hasQuotes = false)
    }
  }

  /** Parses [input]'s slash-separated third number and alpha value, if one exists.
    *
    * Returns a tuple of (components, alpha) where components is the space-separated
    * list of components, and alpha is the alpha value if one was specified.
    * Returns None if this channel set couldn't be parsed and should be returned as-is.
    *
    * Throws a SassScriptException if [input] is invalid. If [input] came from a
    * function argument, [name] is the argument name (without the `$`). It's used
    * for error reporting.
    *
    * Ported from dart-sass `_parseSlashChannels`.
    */
  private def parseSlashChannels(input: Value, name: Nullable[String]): Option[(Value, Option[Value])] = {
    val inputList = input.assertCommonListStyle(name, allowSlash = true)

    // Case: slash-separated list with exactly 2 elements
    if (input.separator == ListSeparator.Slash && inputList.length == 2) {
      Some((inputList(0), Some(inputList(1))))
    } else if (input.separator == ListSeparator.Slash) {
      // Slash-separated list with wrong number of elements
      throw SassScriptException(
        s"Only 2 slash-separated elements allowed, but ${inputList.length} " +
          pluralize("was", inputList.length, Nullable("were")) + " passed.",
        name.toOption
      )
    } else {
      // Not a slash-separated list — check for trailing string with embedded slash
      inputList match {
        case init :+ (lastStr: SassString) if !lastStr.hasQuotes =>
          val text  = lastStr.text
          val parts = text.split('/')
          parts.length match {
            case 1 =>
              // No slash in the string
              Some((input, None))
            case 2 =>
              // String contains exactly one slash: split into channel3 and alpha
              val channel3 = parseNumberOrString(parts(0))
              val alpha    = parseNumberOrString(parts(1))
              Some((
                SassList(init.toList :+ channel3, ListSeparator.Space),
                Some(alpha)
              ))
            case _ =>
              // Multiple slashes — can't parse
              None
          }
        case init :+ (lastNum: SassNumber) if lastNum.asSlash.isDefined =>
          // Number with asSlash set (e.g., "10 / 0.5" parsed as a single number)
          val (before, after) = lastNum.asSlash.get
          Some((
            SassList(init.toList :+ before, ListSeparator.Space),
            Some(after)
          ))
        case _ =>
          Some((input, None))
      }
    }
  }

  /** Parses color components from a space-separated list with optional slash alpha syntax.
    *
    * Handles the modern CSS color syntax: `lab(50% 20 -30 / 0.5)` etc.
    * The `from` keyword for relative color syntax causes a passthrough as CSS function string.
    * Returns a CSS function string for unresolvable values (calc, var, etc.).
    *
    * Ported from dart-sass `_parseChannels`.
    */
  private def parseChannels(
    functionName: String,
    input:        Value,
    space:        Option[ColorSpace],
    name:         Nullable[String]
  ): Value = {
    // Special variable like var(--x) — preserve as function string
    if (input.isSpecialVariable) {
      functionString(functionName, List(input))
    } else {
      parseSlashChannels(input, name) match {
        case None =>
          // Couldn't parse slash channels — return as CSS function
          functionString(functionName, List(input))

        case Some((components, alphaValue)) =>
          val componentList = components.assertCommonListStyle(name, allowSlash = false)

          componentList match {
            case Nil =>
              throw SassScriptException("Color component list may not be empty.", name.toOption)

            case (first: SassString) :: _ if !first.hasQuotes && first.text.toLowerCase == "from" =>
              // Relative color syntax with `from` keyword — pass through as CSS
              functionString(functionName, List(input))

            case _ if components.isSpecialVariable =>
              // Components is a special variable — use it directly as channel list
              parseChannelsWithList(functionName, input, components, List(components), alphaValue, space, name)

            case first :: rest =>
              // Parse channels
              val (resolvedSpace, channels) = space match {
                case None =>
                  // No explicit space — first element is the space name
                  val spaceName = first.assertString(name)
                  spaceName.assertUnquoted(name)
                  val sp =
                    if (spaceName.isSpecialVariable) None
                    else Some(ColorSpace.fromName(spaceName.text, name.toOption))

                  // Validate that the color() function doesn't use legacy spaces
                  sp.foreach { s =>
                    if (s == ColorSpace.rgb || s == ColorSpace.hsl || s == ColorSpace.hwb ||
                        s == ColorSpace.lab || s == ColorSpace.lch || s == ColorSpace.oklab ||
                        s == ColorSpace.oklch) {
                      throw SassScriptException(
                        s"The color() function doesn't support the color space $s. Use " +
                          s"the $s() function instead.",
                        name.toOption
                      )
                    }
                  }
                  (sp, rest)

                case Some(sp) =>
                  // Explicit space — all elements are channels
                  (Some(sp), componentList)
              }

              // Validate channel values
              for (i <- channels.indices) {
                val channel = channels(i)
                if (!channel.isSpecialNumber && !channel.isInstanceOf[SassNumber] && !isNone(channel)) {
                  val channelName = resolvedSpace
                    .flatMap(_.channels.lift(i))
                    .map(ch => s"${ch.name} channel")
                    .getOrElse(s"channel ${i + 1}")
                  throw SassScriptException(
                    s"Expected $channelName to be a number, was $channel.",
                    name.toOption
                  )
                }
              }

              parseChannelsWithList(functionName, input, components, channels, alphaValue, resolvedSpace, name)
          }
      }
    }
  }

  /** Helper for parseChannels to process validated channel list.
    * Handles alpha parsing and color construction.
    */
  private def parseChannelsWithList(
    functionName: String,
    input:        Value,
    components:   Value,
    channels:     List[Value],
    alphaValue:   Option[Value],
    space:        Option[ColorSpace],
    name:         Nullable[String]
  ): Value = {
    // Check if alpha is a special number
    if (alphaValue.exists(_.isSpecialNumber)) {
      if (channels.length == 3 && space.exists(_specialCommaSpaces.contains)) {
        functionString(functionName, channels :+ alphaValue.get)
      } else {
        functionString(functionName, List(input))
      }
    } else {
      // Parse alpha value
      val alpha: Nullable[Double] = alphaValue match {
        case None                                                             => Nullable(1.0)
        case Some(s: SassString) if !s.hasQuotes && s.text.toLowerCase == "none" => Nullable.Null
        case Some(v)                                                          =>
          Nullable(clampLikeCss(percentageOrUnitless(v.assertNumber(name), 1, Some("alpha")), 0, 1))
      }

      // `space` will be None if either `components` or `spaceName` is a `var()`.
      // We check this here rather than returning early in those cases so
      // that we can verify `alphaValue` even for colors we can't fully parse.
      space match {
        case None =>
          functionString(functionName, List(input))

        case Some(sp) =>
          // Check if any channel is a special number
          if (channels.exists(_.isSpecialNumber)) {
            if (channels.length == 3 && _specialCommaSpaces.contains(sp)) {
              functionString(functionName, channels ++ alphaValue.toList)
            } else {
              functionString(functionName, List(input))
            }
          } else if (channels.length != 3) {
            throw SassScriptException(
              s"The $sp color space has 3 channels but $input has ${channels.length}.",
              name.toOption
            )
          } else {
            // Build the color from channels
            colorFromChannelsModern(
              sp,
              channels(0) match { case n: SassNumber => Some(n); case _ => None },
              channels(1) match { case n: SassNumber => Some(n); case _ => None },
              channels(2) match { case n: SassNumber => Some(n); case _ => None },
              alpha,
              fromRgbFunction = sp == ColorSpace.rgb
            )
          }
      }
    }
  }

  /** Creates a SassColor from parsed channel values for modern color syntax.
    * Ported from dart-sass `_colorFromChannels` but adapted for parseChannels usage.
    */
  private def colorFromChannelsModern(
    space:           ColorSpace,
    channel0:        Option[SassNumber],
    channel1:        Option[SassNumber],
    channel2:        Option[SassNumber],
    alpha:           Nullable[Double],
    fromRgbFunction: Boolean
  ): SassColor = {
    space match {
      case ColorSpace.hsl =>
        SassColor.hsl(
          channel0.map(n => angleValue(n, "hue")).fold(Nullable.Null: Nullable[Double])(Nullable(_)),
          channelFromValueModern(space.channels(1), forcePercent(channel1), doClamp = true),
          channelFromValueModern(space.channels(2), forcePercent(channel2), doClamp = true),
          alpha
        )

      case ColorSpace.hwb =>
        // Assert whiteness and blackness have % unit
        channel1.foreach(_.assertUnit("%", Nullable("whiteness")))
        channel2.foreach(_.assertUnit("%", Nullable("blackness")))

        var whiteness = channel1.map(_.value)
        var blackness = channel2.map(_.value)

        // Normalize whiteness+blackness if they exceed 100
        (whiteness, blackness) match {
          case (Some(w), Some(b)) if w + b > 100 =>
            val oldWhiteness = w
            whiteness = Some(w / (w + b) * 100)
            blackness = Some(b / (oldWhiteness + b) * 100)
          case _ => // no normalization needed
        }

        SassColor.hwb(
          channel0.map(n => angleValue(n, "hue")).fold(Nullable.Null: Nullable[Double])(Nullable(_)),
          whiteness.fold(Nullable.Null: Nullable[Double])(Nullable(_)),
          blackness.fold(Nullable.Null: Nullable[Double])(Nullable(_)),
          alpha
        )

      case ColorSpace.rgb =>
        val c0 = channelFromValueModern(space.channels(0), channel0, doClamp = true)
        val c1 = channelFromValueModern(space.channels(1), channel1, doClamp = true)
        val c2 = channelFromValueModern(space.channels(2), channel2, doClamp = true)
        if (fromRgbFunction)
          SassColor.rgbInternal(c0, c1, c2, alpha, Nullable(ColorFormat.RgbFunction))
        else
          SassColor.forSpaceInternal(space, c0, c1, c2, alpha)

      case ColorSpace.lab | ColorSpace.oklab =>
        SassColor.forSpaceInternal(
          space,
          channelFromValueModern(space.channels(0), channel0, doClamp = true),
          channelFromValueModern(space.channels(1), channel1, doClamp = false),
          channelFromValueModern(space.channels(2), channel2, doClamp = false),
          alpha
        )

      case _ =>
        // Other color spaces (display-p3, a98-rgb, etc.)
        SassColor.forSpaceInternal(
          space,
          channelFromValueModern(space.channels(0), channel0, doClamp = false),
          channelFromValueModern(space.channels(1), channel1, doClamp = false),
          channelFromValueModern(space.channels(2), channel2, doClamp = false),
          alpha
        )
    }
  }

  /** Convert a channel value using percentageOrUnitless.
    * Returns Nullable.Null for None (missing channel).
    * Ported from dart-sass `_channelFromValue`.
    */
  private def channelFromValueModern(
    channel: ColorChannel,
    value:   Option[SassNumber],
    doClamp: Boolean
  ): Nullable[Double] = value match {
    case None => Nullable.Null
    case Some(n) =>
      val raw = channel match {
        case lc: LinearChannel if lc.requiresPercent && !n.hasUnit("%") =>
          // Channels that require % (e.g., hwb whiteness/blackness) must have % unit
          throw SassScriptException(
            s"Expected $n to have unit \"%\".",
            Some(channel.name)
          )
        case lc: LinearChannel =>
          percentageOrUnitless(n, lc.max, Some(channel.name))
        case _ if channel.isPolarAngle =>
          angleValue(n, channel.name) % 360
        case _ =>
          n.value
      }
      if (doClamp) {
        channel match {
          case lc: LinearChannel if lc.lowerClamped && lc.upperClamped =>
            Nullable(clampLikeCss(raw, lc.min, lc.max))
          case lc: LinearChannel if lc.lowerClamped =>
            Nullable(math.max(raw, lc.min))
          case lc: LinearChannel if lc.upperClamped =>
            Nullable(math.min(raw, lc.max))
          case _ =>
            Nullable(raw)
        }
      } else {
        Nullable(raw)
      }
  }

  /** Returns [number] with unit `'%'` regardless of its original unit.
    * Ported from dart-sass `_forcePercent`.
    */
  private def forcePercent(value: Option[SassNumber]): Option[SassNumber] =
    value.map { n =>
      if (n.numeratorUnits == List("%") && n.denominatorUnits.isEmpty) n
      else SassNumber(n.value, "%")
    }

  // --- Constructors ---

  private def rgbCallback(fnName: String)(args: List[Value]): Value =
    // If any argument is a CSS special value (var(--x), attr(...), env(...),
    // or a calc-like expression that can't be evaluated), dart-sass
    // preserves the call as an unquoted plain-CSS function string rather
    // than evaluating it. Detect that here.
    if (args.exists(v => isSpecialCssValue(v) || v.isSpecialNumber)) {
      functionString(fnName, args)
    } else if (args.length >= 3 && args.take(3).exists(isNone)) {
      // Modern syntax with `none` channels unpacked to multi-arg — route
      // back through parseChannels which handles missing channels correctly.
      val channelList = SassList(args.take(3), ListSeparator.Space)
      val input = if (args.length >= 4) {
        SassList(List(channelList, args(3)), ListSeparator.Slash)
      } else channelList
      parseChannels("rgb", input, Some(ColorSpace.rgb), Nullable("channels"))
    } else
      args.length match {
        case 1 =>
          // Single-argument form: modern CSS color syntax like `rgb(255 0 0)` or
          // `rgb(255 0 0 / 0.5)`. Parse using parseChannels.
          parseChannels("rgb", args(0), Some(ColorSpace.rgb), Nullable("channels"))
        case 3 =>
          val r = scalar(args(0).assertNumber(), 255)
          val g = scalar(args(1).assertNumber(), 255)
          val b = scalar(args(2).assertNumber(), 255)
          rgbFunctionFrom(r, g, b)
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
          rgbFunctionFrom(r, g, b, a)
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

  private val rgbFn: BuiltInCallable =
    BuiltInCallable.function("rgb", "$red, $green, $blue, $alpha", rgbCallback("rgb"))

  private val rgbaFn: BuiltInCallable =
    BuiltInCallable.function("rgba", "$red, $green, $blue, $alpha", rgbCallback("rgba"))

  private def hslCallback(fnName: String)(args: List[Value]): Value =
    if (args.exists(v => isSpecialCssValue(v) || v.isSpecialNumber)) {
      functionString(fnName, args)
    } else if (args.length >= 3 && args.take(3).exists(isNone)) {
      val channelList = SassList(args.take(3), ListSeparator.Space)
      val input = if (args.length >= 4) SassList(List(channelList, args(3)), ListSeparator.Slash) else channelList
      parseChannels("hsl", input, Some(ColorSpace.hsl), Nullable("channels"))
    } else args.length match {
      case 1 =>
        parseChannels("hsl", args(0), Some(ColorSpace.hsl), Nullable("channels"))
      case 2 =>
        if (args(0).isSpecialVariable || args(1).isSpecialVariable) {
          functionString(fnName, args)
        } else {
          throw SassScriptException("Missing argument $lightness.")
        }
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
          s"Only 1, 2, 3, or 4 arguments allowed for hsl(), was $n."
        )
    }

  private val hslFn: BuiltInCallable =
    BuiltInCallable.function("hsl", "$hue, $saturation, $lightness, $alpha", hslCallback("hsl"))

  private val hslaFn: BuiltInCallable =
    BuiltInCallable.function("hsla", "$hue, $saturation, $lightness, $alpha", hslCallback("hsla"))

  // --- Modern CSS color constructors ---
  // These accept comma-separated arguments. The modern space-separated / slash
  // syntax (`lab(50% 20 -30 / 0.5)`) is not yet parsed by StylesheetParser, so
  // SCSS sources must use the comma form for now.

  /** Returns true if [v] is the CSS `none` channel keyword (parsed as an unquoted SassString).
    * Case-insensitive per CSS spec.
    */
  private def isNone(v: Value): Boolean = v match {
    case s: SassString => !s.hasQuotes && s.text.toLowerCase == "none"
    case _ => false
  }

  /** Interprets [v] as a typed channel, using percentageOrUnitless with the
    * channel's max value. Returns Nullable.Null for `none`. Clamps channels
    * that have clamped bounds (e.g., lightness in lab/lch/oklab/oklch).
    */
  private def channelOrNone(v: Value, ch: ColorChannel): Nullable[Double] = {
    if (isNone(v)) Nullable.Null
    else {
      val shouldClamp = ch match {
        case lc: LinearChannel => lc.lowerClamped || lc.upperClamped
        case _                 => false
      }
      channelFromValueModern(ch, Some(v.assertNumber()), doClamp = shouldClamp)
    }
  }

  /** Interprets [v] as a hue channel, returning Nullable.Null for `none`. */
  private def hueOrNone(v: Value): Nullable[Double] =
    if (isNone(v)) Nullable.Null
    else Nullable(hueOf(v.assertNumber()))

  /** Interprets [v] as an alpha channel, returning Nullable.Null for `none`, clamped to `[0, 1]`. */
  private def alphaOrNone(v: Value): Nullable[Double] =
    if (isNone(v)) Nullable.Null
    else Nullable(clamp(scalar(v.assertNumber()), 0, 1))

  /** Helper for modern color functions (lab/lch/oklab/oklch/hwb/color):
    * if any channel or the alpha is a special CSS value, return a passthrough
    * string using modern space-separated syntax with `/` for alpha.
    * Channels are separated by spaces; alpha (if non-default) is appended
    * with `/` directly after the last channel (no spaces around `/`),
    * matching dart-sass behavior.
    */
  private def tryModernPassthrough(name: String, channels: List[Value], alpha: Value): Option[Value] =
    if (channels.exists(v => isSpecialCssValue(v) || v.isSpecialNumber) ||
        isSpecialCssValue(alpha) || alpha.isSpecialNumber) {
      val isDefaultAlpha = alpha match {
        case n: SassNumber => n.value == 1.0 && n.numeratorUnits.isEmpty && n.denominatorUnits.isEmpty
        case _             => false
      }
      val channelStrs = channels.map(_.toCssString(quote = false))
      val str = if (isDefaultAlpha) {
        s"$name(${channelStrs.mkString(" ")})"
      } else {
        // Append alpha to the last channel with / (no spaces), matching dart-sass
        val initStr = if (channelStrs.size > 1) channelStrs.init.mkString(" ") + " " else ""
        s"$name($initStr${channelStrs.last}/${alpha.toCssString(quote = false)})"
      }
      Some(SassString(str, hasQuotes = false))
    } else None

  private val labFn: BuiltInCallable =
    BuiltInCallable.function(
      "lab",
      "$lightness, $a, $b, $alpha: 1",
      { args =>
        args.length match {
          case 1 => parseChannels("lab", args(0), Some(ColorSpace.lab), Nullable("channels"))
          case _ =>
            val alpha = if (args.length >= 4) args(3) else SassNumber(1)
            tryModernPassthrough("lab", args.take(3), alpha).getOrElse {
              val l     = channelOrNone(args(0), ColorSpace.lab.channels(0))
              val a     = channelOrNone(args(1), ColorSpace.lab.channels(1))
              val b     = channelOrNone(args(2), ColorSpace.lab.channels(2))
              val al    = if (args.length >= 4) alphaOrNone(args(3)) else Nullable(1.0)
              SassColor.lab(l, a, b, al)
          }
        }
      }
    )

  private val lchFn: BuiltInCallable =
    BuiltInCallable.function(
      "lch",
      "$lightness, $chroma, $hue, $alpha: 1",
      { args =>
        args.length match {
          case 1 => parseChannels("lch", args(0), Some(ColorSpace.lch), Nullable("channels"))
          case _ =>
            val alpha = if (args.length >= 4) args(3) else SassNumber(1)
            tryModernPassthrough("lch", args.take(3), alpha).getOrElse {
              val l     = channelOrNone(args(0), ColorSpace.lch.channels(0))
              val c     = channelOrNone(args(1), ColorSpace.lch.channels(1))
              val h     = if (isNone(args(2))) Nullable.Null[Double] else Nullable(hueOf(args(2).assertNumber()))
              val al    = if (args.length >= 4) alphaOrNone(args(3)) else Nullable(1.0)
              SassColor.lch(l, c, h, al)
          }
        }
      }
    )

  private val oklabFn: BuiltInCallable =
    BuiltInCallable.function(
      "oklab",
      "$lightness, $a, $b, $alpha: 1",
      { args =>
        args.length match {
          case 1 => parseChannels("oklab", args(0), Some(ColorSpace.oklab), Nullable("channels"))
          case _ =>
            val alpha = if (args.length >= 4) args(3) else SassNumber(1)
            tryModernPassthrough("oklab", args.take(3), alpha).getOrElse {
              val l     = channelOrNone(args(0), ColorSpace.oklab.channels(0))
              val a     = channelOrNone(args(1), ColorSpace.oklab.channels(1))
              val b     = channelOrNone(args(2), ColorSpace.oklab.channels(2))
              val al    = if (args.length >= 4) alphaOrNone(args(3)) else Nullable(1.0)
              SassColor.oklab(l, a, b, al)
          }
        }
      }
    )

  private val oklchFn: BuiltInCallable =
    BuiltInCallable.function(
      "oklch",
      "$lightness, $chroma, $hue, $alpha: 1",
      { args =>
        args.length match {
          case 1 => parseChannels("oklch", args(0), Some(ColorSpace.oklch), Nullable("channels"))
          case _ =>
            val alpha = if (args.length >= 4) args(3) else SassNumber(1)
            tryModernPassthrough("oklch", args.take(3), alpha).getOrElse {
              val l     = channelOrNone(args(0), ColorSpace.oklch.channels(0))
              val c     = channelOrNone(args(1), ColorSpace.oklch.channels(1))
              val h     = if (isNone(args(2))) Nullable.Null[Double] else Nullable(hueOf(args(2).assertNumber()))
              val al    = if (args.length >= 4) alphaOrNone(args(3)) else Nullable(1.0)
              SassColor.oklch(l, c, h, al)
          }
        }
      }
    )

  private val hwbFn: BuiltInCallable =
    BuiltInCallable.function(
      "hwb",
      "$hue, $whiteness, $blackness, $alpha: 1",
      { args =>
        {
          val alpha = if (args.length >= 4) args(3) else SassNumber(1)
          tryModernPassthrough("hwb", args.take(3), alpha)
        }.getOrElse {
          if (args.length >= 3 && args.take(3).exists(isNone)) {
            val channelList = SassList(args.take(3), ListSeparator.Space)
            val input = if (args.length >= 4) SassList(List(channelList, args(3)), ListSeparator.Slash) else channelList
            parseChannels("hwb", input, Some(ColorSpace.hwb), Nullable("channels"))
          } else args.length match {
            case 1 =>
              parseChannels("hwb", args(0), Some(ColorSpace.hwb), Nullable("channels"))
            case _ =>
              val h     = hueOrNone(args(0))
              val w     = if (isNone(args(1))) Nullable.Null else Nullable(clamp(args(1).assertNumber().value, 0, 100))
              val b     = if (isNone(args(2))) Nullable.Null else Nullable(clamp(args(2).assertNumber().value, 0, 100))
              val alpha = if (args.length >= 4) alphaOrNone(args(3)) else Nullable(1.0)
              SassColor.hwb(h, w, b, alpha)
          }
        }
      }
    )

  /** `color($description)` or `color($space, $c1, $c2, $c3, $alpha: 1)` — constructs a color in an explicit
    * non-legacy color space (srgb, display-p3, a98-rgb, etc.). The single-argument form accepts modern CSS
    * color syntax like `color(display-p3 1 0 0 / 0.5)`.
    */
  private val colorFn: BuiltInCallable =
    BuiltInCallable.function(
      "color",
      "$space, $channel1, $channel2, $channel3, $alpha: 1",
      { args =>
        {
          if (args.length >= 4) {
            val alpha = if (args.length >= 5) args(4) else SassNumber(1)
            tryModernPassthrough("color", args.take(4), alpha)
          } else if (args.length >= 2 && args.exists(v => isSpecialCssValue(v) || v.isSpecialNumber)) {
            // When var() fills multiple channels, we get fewer explicit args.
            // Passthrough as a CSS function string with space-separated args.
            val content = SassList(args.toList, ListSeparator.Space)
            Some(functionString("color", List(content)))
          } else None
        }.getOrElse {
          args.length match {
            case 1 =>
              parseChannels("color", args(0), None, Nullable("description"))
            case n if n < 4 =>
              throw SassScriptException(
                s"color() requires at least 4 arguments or a single space-separated description, was $n."
              )
            case _ =>
              val spaceName = args(0) match {
                case s: SassString => s.text
                case other => other.assertString().text
              }
              val space = ColorSpace.fromName(spaceName)
              val c0 = channelOrNone(args(1), space.channels(0))
              val c1 = channelOrNone(args(2), space.channels(1))
              val c2 = channelOrNone(args(3), space.channels(2))
              val alpha = if (args.length >= 5) alphaOrNone(args(4)) else Nullable(1.0)
              SassColor.forSpaceInternal(space, c0, c1, c2, alpha)
          }
        }
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
    BuiltInCallable.function("alpha", "$color", { args =>
      args.head match {
        // IE alpha(opacity=N) filter — no deprecation warning
        case s: SassString if !s.hasQuotes && s.text.contains("=") =>
          SassString(s"alpha(${s.text})", hasQuotes = false)
        case _ =>
          BuiltInCallable.warnForGlobalBuiltIn("color", "alpha")
          SassNumber(args.head.assertColor(Nullable("color")).alpha)
      }
    })

  private val opacityFn: BuiltInCallable =
    BuiltInCallable.function("opacity", "$color", { args =>
      // CSS filter overload: opacity(50%) / opacity(1) / opacity(var(--x))
      if (args.head.isInstanceOf[SassNumber] || args.head.isSpecialNumber) {
        functionString("opacity", args)
      } else {
        BuiltInCallable.warnForGlobalBuiltIn("color", "opacity")
        SassNumber(args.head.assertColor(Nullable("color")).alpha)
      }
    })

  // --- Manipulation ---

  private val mixFn: BuiltInCallable =
    BuiltInCallable.function(
      "mix",
      "$color1, $color2, $weight: 50%, $method: null",
      { args =>
        val c1     = args(0).assertColor()
        val c2     = args(1).assertColor()
        val weight =
          if (args.length >= 3 && !(args(2) eq SassNull))
            scalar(args(2).assertNumber(), 100) / 100.0
          else 0.5
        val w = clamp(weight, 0, 1)
        // $method: if supplied and not null, parse as an interpolation method
        // (color space + optional hue interpolation) and use SassColor.interpolate.
        val methodArg: Option[Value] =
          if (args.length >= 4 && !(args(3) eq SassNull)) Some(args(3))
          else None
        methodArg match {
          case Some(methodValue) =>
            val method = InterpolationMethod.fromValue(methodValue, Nullable("method"))
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
        val c   = args(0).assertColor()
        if (!c.isLegacy) {
          throw SassScriptException(
            "lighten() is only supported for legacy colors. Please use " +
            "color.adjust() instead with an explicit $space argument."
          )
        }
        warnLegacyColorFunction("lighten", "color.adjust($color, $lightness: +$amount)")
        val amt = scalar(args(1).assertNumber(), 100)
        adjustHsl(c, lDelta = amt)
      }
    )

  private val darkenFn: BuiltInCallable =
    BuiltInCallable.function(
      "darken",
      "$color, $amount",
      { args =>
        val c   = args(0).assertColor()
        if (!c.isLegacy) {
          throw SassScriptException(
            "darken() is only supported for legacy colors. Please use " +
            "color.adjust() instead with an explicit $space argument."
          )
        }
        warnLegacyColorFunction("darken", "color.adjust($color, $lightness: -$amount)")
        val amt = scalar(args(1).assertNumber(), 100)
        adjustHsl(c, lDelta = -amt)
      }
    )

  /** Returns true if a value is a number, percentage, or special CSS value that
    * should trigger the CSS filter function overload for saturate/grayscale/etc.
    */
  private def isCssFilterValue(v: Value): Boolean = v match {
    case _: SassNumber       => true
    case s: SassString       => !s.hasQuotes && (s.isSpecialNumber || isSpecialCssValue(s))
    case _ if v.isSpecialNumber => true
    case _                   => false
  }

  private val saturateFn: BuiltInCallable =
    BuiltInCallable.function(
      "saturate",
      "$color, $amount",
      { args =>
        // CSS filter overload: saturate(50%) / saturate(1) / saturate(var(--x))
        // Also handles saturate($amount: 50%) where $color is SassNull (gap fill)
        val isSingleArg = args.length == 1 || (args.length == 2 && (args(1) eq SassNull))
        val cssFilterArg = if (isSingleArg) args(0)
          else if (args.length == 2 && (args(0) eq SassNull)) args(1) // saturate($amount: 50%)
          else null
        if (cssFilterArg != null && isCssFilterValue(cssFilterArg)) {
          SassString(s"saturate(${cssFilterArg.toCssString(quote = false)})", hasQuotes = false)
        } else {
          BuiltInCallable.warnForGlobalBuiltIn("color", "adjust")
          val c = args(0).assertColor()
          if (!c.isLegacy) {
            throw SassScriptException(
              "saturate() is only supported for legacy colors. Please use " +
                "color.adjust() instead with an explicit $space argument."
            )
          }
          warnLegacyColorFunction("saturate", "color.adjust($color, $saturation: +$amount)")
          val amt = scalar(args(1).assertNumber(), 100)
          adjustHsl(c, sDelta = amt)
        }
      }
    )

  private val desaturateFn: BuiltInCallable =
    BuiltInCallable.function(
      "desaturate",
      "$color, $amount",
      { args =>
        val c   = args(0).assertColor()
        if (!c.isLegacy) {
          throw SassScriptException(
            "desaturate() is only supported for legacy colors. Please use " +
            "color.adjust() instead with an explicit $space argument."
          )
        }
        warnLegacyColorFunction("desaturate", "color.adjust($color, $saturation: -$amount)")
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
        if (!c.isLegacy) {
          throw SassScriptException(
            "adjust-hue() is only supported for legacy colors. Please use " +
            "color.adjust() instead with an explicit $space argument."
          )
        }
        val deg = args(1).assertNumber().value
        adjustHsl(c, hDelta = deg)
      }
    )

  private val complementFn: BuiltInCallable =
    BuiltInCallable.function("complement",
                             "$color, $space: null",
                             { args =>
                               val color = args(0).assertColor()
                               val spaceArg = if (args.length >= 2) args(1) else SassNull
                               val space =
                                 if (color.isLegacy && (spaceArg eq SassNull)) ColorSpace.hsl
                                 else ColorSpace.fromName(spaceArg.assertString().text, Some("space"))
                               if (!space.isPolar) {
                                 throw SassScriptException(
                                   s"Color space $space doesn't have a hue channel.",
                                   Some("space")
                                 )
                               }
                               val inSpace = color.toSpace(space, legacyMissing = !(spaceArg eq SassNull))
                               val hueIdx = if (space.isLegacy) 0 else 2
                               val hueChannel = space.channels(hueIdx)
                               val oldHue = if (hueIdx == 0) inSpace.channel0OrNull else inSpace.channel2OrNull
                               val newHue = doAdjustChannel(inSpace, hueChannel, oldHue, Some(SassNumber(180)))
                               val result =
                                 if (hueIdx == 0) SassColor.forSpaceInternal(space, newHue, inSpace.channel1OrNull, inSpace.channel2OrNull, inSpace.alphaOrNull)
                                 else SassColor.forSpaceInternal(space, inSpace.channel0OrNull, inSpace.channel1OrNull, newHue, inSpace.alphaOrNull)
                               result.toSpace(color.space, legacyMissing = false)
                             }
    )

  private val grayscaleFn: BuiltInCallable =
    BuiltInCallable.function(
      "grayscale",
      "$color",
      { args =>
        // CSS filter overload: grayscale(50%) / grayscale(1) / grayscale(var(--x))
        if (args(0).isInstanceOf[SassNumber] || args(0).isSpecialNumber) {
          functionString("grayscale", args)
        } else {
        BuiltInCallable.warnForGlobalBuiltIn("color", "grayscale")
        val c = args(0).assertColor()
        if (c.isLegacy) {
          val hsl = c.toSpace(ColorSpace.hsl)
          SassColor.hsl(
            hsl.channel0OrNull,
            Nullable(0.0),
            hsl.channel2OrNull,
            Nullable(hsl.alpha)
          ).toSpace(c.space, legacyMissing = false)
        } else {
          // For non-legacy colors, convert to oklch and set chroma to 0
          val oklch = c.toSpace(ColorSpace.oklch)
          SassColor.oklch(
            oklch.channel0OrNull,
            Nullable(0.0),
            oklch.channel2OrNull,
            Nullable(oklch.alpha)
          ).toSpace(c.space)
        }
        }
      }
    )

  private val invertFn: BuiltInCallable =
    BuiltInCallable.function(
      "invert",
      "$color, $weight: 100%, $space: null",
      { args =>
        // Emit global-builtin deprecation when not a CSS passthrough
        if (!args(0).isInstanceOf[SassNumber] && !args(0).isSpecialNumber) {
          BuiltInCallable.warnForGlobalBuiltIn("color", "invert")
        }
        // $weight may be SassNull when skipped via named $space parameter
        // (the framework fills gaps with null instead of parsing defaults)
        val weightNumber = if (args(1) eq SassNull) SassNumber(100, "%") else args(1).assertNumber(Nullable("weight"))
        // If first arg is a number or special CSS value, treat as plain CSS invert() filter
        args(0) match {
          case v if isCssFilterValue(v) && !v.isInstanceOf[SassColor] && (weightNumber.value == 100 && weightNumber.hasUnit("%")) =>
            SassString(s"invert(${v.toCssString(quote = false)})", hasQuotes = false)
          case _: SassNumber =>
            if (weightNumber.value != 100 || !weightNumber.hasUnit("%")) {
              throw SassScriptException(
                "Only one argument may be passed to the plain-CSS invert() function."
              )
            }
            SassString(s"invert(${args(0)})", hasQuotes = false)
          case _ =>
            val color = args(0).assertColor(Nullable("color"))
            val spaceArg = if (args.length >= 3) args(2) else SassNull

            if (spaceArg eq SassNull) {
              // Legacy invert: RGB space
              if (!color.isLegacy) {
                throw SassScriptException(
                  s"To use color.invert() with non-legacy color $color, you must provide a $$space.",
                  Some("color")
                )
              }
              val w = scalar(weightNumber, 100) / 100.0
              val rgb = color.toSpace(ColorSpace.rgb)
              val inverted = rgbFrom(255 - rgb.channel0, 255 - rgb.channel1, 255 - rgb.channel2, color.alpha)
              if (fuzzyEquals(w, 1.0)) inverted.toSpace(color.space)
              else mixColors(inverted, color, SassNumber(w * 100, "%")).toSpace(color.space)
            } else {
              // Space-aware invert: parse as interpolation method
              val method = InterpolationMethod.fromValue(spaceArg, Nullable("space"))
              val space = method.space
              val weight = weightNumber.valueInRangeWithUnit(0, 100, "weight", "%") / 100.0
              if (fuzzyEquals(weight, 0)) color
              else {
                val inSpace = color.toSpace(space)
                val inverted = invertInSpace(inSpace, space)
                if (fuzzyEquals(weight, 1)) inverted.toSpace(color.space, legacyMissing = false)
                else color.interpolate(inverted, method, weight = 1 - weight, legacyMissing = false)
              }
            }
        }
      }
    )

  /** Invert a color's channels in the given space. */
  private def invertInSpace(color: SassColor, space: ColorSpace): SassColor = {
    val chs = space.channels
    space match {
      case ColorSpace.hwb =>
        // HWB: invert hue, swap whiteness/blackness
        SassColor.hwb(
          invertChannel(color, chs(0), color.channel0OrNull),
          color.channel2OrNull,
          color.channel1OrNull,
          Nullable(color.alpha)
        )
      case ColorSpace.hsl | ColorSpace.lch | ColorSpace.oklch =>
        // Polar spaces: invert hue + lightness, keep chroma
        SassColor.forSpaceInternal(
          space,
          invertChannel(color, chs(0), color.channel0OrNull),
          color.channel1OrNull,
          invertChannel(color, chs(2), color.channel2OrNull),
          Nullable(color.alpha)
        )
      case _ =>
        // All other spaces: invert all channels
        SassColor.forSpaceInternal(
          space,
          invertChannel(color, chs(0), color.channel0OrNull),
          invertChannel(color, chs(1), color.channel1OrNull),
          invertChannel(color, chs(2), color.channel2OrNull),
          Nullable(color.alpha)
        )
    }
  }

  /** Invert a single channel. */
  private def invertChannel(color: SassColor, channel: ColorChannel, value: Nullable[Double]): Nullable[Double] = {
    if (value.isEmpty) missingChannelError(color, channel.name)
    val v = value.get
    channel match {
      case lc: LinearChannel if lc.min < 0 => Nullable(-v)
      case lc: LinearChannel               => Nullable(lc.max - v)
      case _ if channel.isPolarAngle        => Nullable((v + 180) % 360)
      case _ => throw new UnsupportedOperationException(s"Unknown channel $channel")
    }
  }

  /** Mix two legacy colors using dart-sass's alpha-weighted algorithm. */
  private def mixColors(color1: SassColor, color2: SassColor, weight: SassNumber): SassColor = {
    val rgb1 = color1.toSpace(ColorSpace.rgb)
    val rgb2 = color2.toSpace(ColorSpace.rgb)
    val weightScale = weight.valueInRange(0, 100, Nullable("weight")) / 100.0
    val normalizedWeight = weightScale * 2 - 1
    val alphaDistance = color1.alpha - color2.alpha
    val combinedWeight1 =
      if (normalizedWeight * alphaDistance == -1) normalizedWeight
      else (normalizedWeight + alphaDistance) / (1 + normalizedWeight * alphaDistance)
    val weight1 = (combinedWeight1 + 1) / 2
    val weight2 = 1 - weight1
    SassColor.rgb(
      Nullable(rgb1.channel0 * weight1 + rgb2.channel0 * weight2),
      Nullable(rgb1.channel1 * weight1 + rgb2.channel1 * weight2),
      Nullable(rgb1.channel2 * weight1 + rgb2.channel2 * weight2),
      Nullable(rgb1.alpha * weightScale + rgb2.alpha * (1 - weightScale))
    )
  }

  private val opacifyFn: BuiltInCallable =
    BuiltInCallable.function(
      "opacify",
      "$color, $amount",
      { args =>
        val c   = args(0).assertColor()
        if (!c.isLegacy) {
          throw SassScriptException(
            "opacify() is only supported for legacy colors. Please use " +
            "color.adjust() instead with an explicit $space argument."
          )
        }
        warnLegacyColorFunction("opacify", "color.adjust($color, $alpha: +$amount)")
        val amt = scalar(args(1).assertNumber())
        c.changeAlpha(clamp(c.alpha + amt, 0, 1))
      }
    )

  private val transparentizeFn: BuiltInCallable =
    BuiltInCallable.function(
      "transparentize",
      "$color, $amount",
      { args =>
        val c   = args(0).assertColor()
        if (!c.isLegacy) {
          throw SassScriptException(
            "transparentize() is only supported for legacy colors. Please use " +
            "color.adjust() instead with an explicit $space argument."
          )
        }
        warnLegacyColorFunction("transparentize", "color.adjust($color, $alpha: -$amount)")
        val amt = scalar(args(1).assertNumber())
        c.changeAlpha(clamp(c.alpha - amt, 0, 1))
      }
    )

  private val fadeInFn: BuiltInCallable =
    BuiltInCallable.function("fade-in", "$color, $amount", opacifyFn.callback)

  private val fadeOutFn: BuiltInCallable =
    BuiltInCallable.function("fade-out", "$color, $amount", transparentizeFn.callback)

  // --- change/adjust/scale-color helpers ---
  // Ported from dart-sass _updateComponents / _changeColor / _adjustColor /
  // _scaleColor / _channelForChange / _adjustChannel / _scaleChannel /
  // _sniffLegacyColorSpace / _channelFromValue / _colorFromChannels
  // in lib/src/functions/color.dart (lines 1023-1330, 1843-1957).

  /** Detect legacy color space from channel names in kwargs (backwards compatibility).
    * Ported from dart-sass `_sniffLegacyColorSpace`.
    */
  private def sniffLegacyColorSpace(keywords: scala.collection.Map[String, Value]): Option[ColorSpace] = {
    val keys = keywords.keys
    if (keys.exists(k => k == "red" || k == "green" || k == "blue")) Some(ColorSpace.rgb)
    else if (keys.exists(k => k == "saturation" || k == "lightness")) Some(ColorSpace.hsl)
    else if (keys.exists(k => k == "whiteness" || k == "blackness")) Some(ColorSpace.hwb)
    else if (keys.exists(_ == "hue")) Some(ColorSpace.hsl)
    else None
  }

  /** Convert color to space specified by keyword, or return as-is. */
  private def colorInSpace(color: SassColor, spaceKeyword: Option[Value]): SassColor =
    spaceKeyword match {
      case None    => color
      case Some(v) =>
        val s = v.assertString()
        color.toSpace(ColorSpace.fromName(s.text, Some("space")), legacyMissing = false)
    }

  /** Extract channel value from SassNumber according to channel metadata.
    * Ported from dart-sass `_channelFromValue`.
    */
  private def channelFromValue(channel: ColorChannel, value: Option[SassNumber], doClamp: Boolean): Nullable[Double] =
    value match {
      case None    => Nullable.Null
      case Some(v) =>
        val channelValue: Double = channel match {
          case _: ColorChannel if channel.isPolarAngle =>
            if (v.compatibleWithUnit("deg")) v.coerceValueToUnit("deg")
            else v.value
          case lc: LinearChannel if lc.requiresPercent && !v.hasUnit("%") =>
            throw SassScriptException(
              s"Expected $v to have unit \"%\".",
              Some(channel.name)
            )
          case lc: LinearChannel if v.hasUnit("%") =>
            v.value * lc.max / 100.0
          case _ if v.hasUnits =>
            throw SassScriptException(
              s"Expected $v to have no units or \"%\".",
              Some(channel.name)
            )
          case _ => v.value
        }
        if (doClamp) {
          channel match {
            case lc: LinearChannel if lc.lowerClamped && lc.upperClamped =>
              Nullable(clampLikeCss(channelValue, lc.min, lc.max))
            case lc: LinearChannel if lc.lowerClamped =>
              Nullable(math.max(channelValue, lc.min))
            case lc: LinearChannel if lc.upperClamped =>
              Nullable(math.min(channelValue, lc.max))
            case _ =>
              Nullable(channelValue)
          }
        } else {
          Nullable(channelValue)
        }
    }

  /** Clamp like CSS — NaN prefers the lower bound. */
  private def clampLikeCss(number: Double, lowerBound: Double, upperBound: Double): Double =
    if (number.isNaN) lowerBound else math.max(lowerBound, math.min(upperBound, number))

  /** Create a SassColor from SassNumber channels with unit conversion.
    * Ported from dart-sass `_colorFromChannels`.
    */
  private def colorFromChannels(
    space:   ColorSpace,
    c0:      Option[SassNumber],
    c1:      Option[SassNumber],
    c2:      Option[SassNumber],
    alpha:   Nullable[Double],
    doClamp: Boolean
  ): SassColor =
    SassColor.forSpaceInternal(
      space,
      channelFromValue(space.channels(0), c0, doClamp),
      channelFromValue(space.channels(1), c1, doClamp),
      channelFromValue(space.channels(2), c2, doClamp),
      alpha
    )

  /** Error for modifying a missing channel. */
  private def missingChannelError(color: SassColor, channel: String): Nothing =
    throw SassScriptException(
      s"Because the CSS working group is still deciding on the best behavior, " +
        s"Sass doesn't currently support modifying missing channels (color: " +
        s"${color.toCssString()}).",
      Some(channel)
    )

  // --- _updateComponents (main dispatcher) ---

  /** Core method for change-color, adjust-color, scale-color.
    * Ported from dart-sass `_updateComponents`.
    */
  private def updateComponents(
    args:   List[Value],
    change: Boolean = false,
    adjust: Boolean = false,
    scale:  Boolean = false
  ): SassColor = {
    val originalColor = args(0).assertColor(Nullable("color"))
    val argumentList = args.lift(1) match {
      case Some(al: ssg.sass.value.SassArgumentList) => al
      case None => new ssg.sass.value.SassArgumentList(Nil, scala.collection.immutable.ListMap.empty, ssg.sass.value.ListSeparator.Comma)
      case Some(other) =>
        // Extra positional arg that's not a SassArgumentList — wrap it so
        // the asList.nonEmpty check below triggers the proper error.
        new ssg.sass.value.SassArgumentList(List(other), scala.collection.immutable.ListMap.empty, ssg.sass.value.ListSeparator.Comma)
    }
    if (argumentList.asList.nonEmpty) {
      throw SassScriptException(
        "Only one positional argument is allowed. All other arguments must " +
          "be passed by name."
      )
    }

    val keywords = scala.collection.mutable.LinkedHashMap.from(argumentList.keywords)
    val spaceKeyword = keywords.remove("space")

    val alphaArg = keywords.remove("alpha")

    // For backwards-compatibility, legacy colors can modify channels in any
    // legacy space, with powerless channels treated as 0.
    val color =
      if (spaceKeyword.isEmpty && originalColor.isLegacy && keywords.nonEmpty) {
        sniffLegacyColorSpace(keywords).fold(originalColor) { space =>
          originalColor.toSpace(space, legacyMissing = false)
        }
      } else {
        colorInSpace(originalColor, spaceKeyword)
      }

    val channelInfo = color.space.channels
    val channelArgs = Array.fill[Option[Value]](channelInfo.length)(None)

    for ((name, value) <- keywords) {
      val channelIndex = channelInfo.indexWhere(_.name == name)
      if (channelIndex == -1) {
        throw SassScriptException(
          s"Color space ${color.space} doesn't have a channel with this name.",
          Some(name)
        )
      }
      channelArgs(channelIndex) = Some(value)
    }

    val result =
      if (change) {
        doChangeColor(color, channelArgs.toList, alphaArg)
      } else {
        val channelNumbers = channelArgs.zipWithIndex.map { case (arg, i) =>
          arg.map(v => v.assertNumber(Nullable(channelInfo(i).name)))
        }.toList
        val alphaNumber = alphaArg.map(_.assertNumber(Nullable("alpha")))
        if (scale) doScaleColor(color, channelNumbers, alphaNumber)
        else doAdjustColor(color, channelNumbers, alphaNumber)
      }

    result.toSpace(originalColor.space, legacyMissing = false)
  }

  // --- change-color ---

  /** Ported from dart-sass `_changeColor`. */
  private def doChangeColor(
    color:       SassColor,
    channelArgs: List[Option[Value]],
    alphaArg:    Option[Value]
  ): SassColor = {
    val newAlpha: Nullable[Double] = alphaArg match {
      case None                                               => Nullable(color.alpha)
      case Some(v) if isNone(v)                               => Nullable.Null
      case Some(v)                                            =>
        val n = v.assertNumber(Nullable("alpha"))
        if (!n.hasUnits) Nullable(n.valueInRange(0, 1, Nullable("alpha")))
        else if (n.hasUnit("%")) Nullable(n.valueInRangeWithUnit(0, 100, "alpha", "%") / 100.0)
        else Nullable(n.valueInRange(0, 1, Nullable("alpha")))
    }
    colorFromChannels(
      color.space,
      channelForChange(channelArgs(0), color, 0),
      channelForChange(channelArgs(1), color, 1),
      channelForChange(channelArgs(2), color, 2),
      newAlpha,
      doClamp = false
    )
  }

  /** Ported from dart-sass `_channelForChange`.
    * Returns the SassNumber for a channel, or None for missing/none.
    */
  private def channelForChange(channelArg: Option[Value], color: SassColor, channel: Int): Option[SassNumber] = {
    channelArg match {
      case None =>
        // No argument: preserve existing channel value.
        val chOrNull = color.channelsOrNull(channel)
        if (chOrNull.isEmpty) None
        else {
          val value = chOrNull.get
          val unit: Option[String] =
            if (((color.space eq ColorSpace.hsl) || (color.space eq ColorSpace.hwb)) && channel > 0) Some("%")
            else None
          Some(unit.fold(SassNumber(value))(u => SassNumber(value, u)))
        }
      case Some(v) if isNone(v)     => None
      case Some(v: SassNumber)      => Some(v)
      case Some(v)                  =>
        throw SassScriptException(
          s"$v is not a number or unquoted \"none\".",
          Some(color.space.channels(channel).name)
        )
    }
  }

  // --- scale-color ---

  /** Ported from dart-sass `_scaleColor`. */
  private def doScaleColor(
    color:       SassColor,
    channelArgs: List[Option[SassNumber]],
    alphaArg:    Option[SassNumber]
  ): SassColor =
    SassColor.forSpaceInternal(
      color.space,
      doScaleChannel(color, color.space.channels(0), color.channel0OrNull, channelArgs(0)),
      doScaleChannel(color, color.space.channels(1), color.channel1OrNull, channelArgs(1)),
      doScaleChannel(color, color.space.channels(2), color.channel2OrNull, channelArgs(2)),
      doScaleChannel(color, ColorChannel.alpha, color.alphaOrNull, alphaArg)
    )

  /** Ported from dart-sass `_scaleChannel`. */
  private def doScaleChannel(
    color:     SassColor,
    channel:   ColorChannel,
    oldValue:  Nullable[Double],
    factorArg: Option[SassNumber]
  ): Nullable[Double] = {
    factorArg match {
      case None => oldValue
      case Some(factorNum) =>
        channel match {
          case _: LinearChannel => ()
          case _ =>
            throw SassScriptException("Channel isn't scalable.", Some(channel.name))
        }
        if (oldValue.isEmpty) missingChannelError(color, channel.name)
        val lc = channel.asInstanceOf[LinearChannel]
        factorNum.assertUnit("%", Nullable(channel.name))
        val factor = factorNum.valueInRangeWithUnit(-100, 100, channel.name, "%") / 100.0
        val old = oldValue.get
        if (factor == 0) oldValue
        else if (factor > 0) {
          if (old >= lc.max) oldValue
          else Nullable(old + (lc.max - old) * factor)
        } else {
          if (old <= lc.min) oldValue
          else Nullable(old + (old - lc.min) * factor)
        }
    }
  }

  // --- adjust-color ---

  /** Ported from dart-sass `_adjustColor`. */
  private def doAdjustColor(
    color:       SassColor,
    channelArgs: List[Option[SassNumber]],
    alphaArg:    Option[SassNumber]
  ): SassColor =
    SassColor.forSpaceInternal(
      color.space,
      doAdjustChannel(color, color.space.channels(0), color.channel0OrNull, channelArgs(0)),
      doAdjustChannel(color, color.space.channels(1), color.channel1OrNull, channelArgs(1)),
      doAdjustChannel(color, color.space.channels(2), color.channel2OrNull, channelArgs(2)),
      doAdjustChannel(color, ColorChannel.alpha, color.alphaOrNull, alphaArg)
        .map(alpha => clampLikeCss(alpha, 0, 1))
    )

  /** Ported from dart-sass `_adjustChannel`. */
  private def doAdjustChannel(
    color:         SassColor,
    channel:       ColorChannel,
    oldValue:      Nullable[Double],
    adjustmentArg: Option[SassNumber]
  ): Nullable[Double] = {
    adjustmentArg match {
      case None    => oldValue
      case Some(adjNum) =>
        if (oldValue.isEmpty) missingChannelError(color, channel.name)

        // Normalize the adjustment value to the channel's expected unit.
        var adjustmentNum = adjNum
        (color.space, channel) match {
          case (ColorSpace.hsl | ColorSpace.hwb, _) if channel.isPolarAngle =>
            // Legacy hue: accept any angle unit or unitless
            adjustmentNum = SassNumber(angleValue(adjustmentNum, "hue"))
          case (ColorSpace.hsl, lc: LinearChannel) if lc.name == "saturation" || lc.name == "lightness" =>
            // Legacy saturation/lightness: accept % or unitless
            adjustmentNum = SassNumber(adjustmentNum.value, "%")
          case (_, ColorChannel.alpha) if adjustmentNum.hasUnits =>
            // Alpha with units: treat value as unitless
            adjustmentNum = SassNumber(adjustmentNum.value)
          case _ => ()
        }

        val adjustValue = channelFromValue(channel, Some(adjustmentNum), doClamp = false)
        val result = oldValue.get + adjustValue.getOrElse(0.0)

        // Clamp according to channel's clamping rules.
        channel match {
          case lc: LinearChannel if lc.lowerClamped && result < lc.min =>
            Nullable(if (oldValue.get < lc.min) math.max(oldValue.get, result) else lc.min)
          case lc: LinearChannel if lc.upperClamped && result > lc.max =>
            Nullable(if (oldValue.get > lc.max) math.min(oldValue.get, result) else lc.max)
          case _ =>
            Nullable(result)
        }
    }
  }

  /** Extract angle value in degrees, accepting any angle-compatible unit or unitless.
    * Simplified from dart-sass `_angleValue` (deprecation warnings omitted).
    */
  private def angleValue(number: SassNumber, name: String): Double =
    if (number.compatibleWithUnit("deg")) number.coerceValueToUnit("deg")
    else number.value

  // --- Callables ---

  private val changeColorFn: BuiltInCallable =
    BuiltInCallable.function(
      "change-color",
      "$color, $kwargs...",
      args => updateComponents(args, change = true)
    )

  private val adjustColorFn: BuiltInCallable =
    BuiltInCallable.function(
      "adjust-color",
      "$color, $kwargs...",
      args => updateComponents(args, adjust = true)
    )

  private val scaleColorFn: BuiltInCallable =
    BuiltInCallable.function(
      "scale-color",
      "$color, $kwargs...",
      args => updateComponents(args, scale = true)
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
    * For conventionallyPercent channels with max != 100, the internal value is scaled:
    * e.g. oklab lightness internal 0.1 with max=1 → 0.1 * 100 / 1 = 10%.
    */
  private def channelNumber(value: Double, channel: ssg.sass.value.color.ColorChannel): SassNumber = {
    channel match {
      case lc: LinearChannel if channel.associatedUnit.toOption.contains("%") && lc.max != 100 =>
        // Conventionally-percent channel with max != 100 (e.g. oklab/lab lightness with max=1):
        // scale internal value to percentage. Internal 0.1 with max=1 → 0.1 * 100 / 1 = 10%.
        SassNumber(value * 100.0 / lc.max, "%")
      case _ =>
        val unit = channel.associatedUnit
        if (unit.isEmpty) SassNumber(value) else SassNumber(value, unit.get)
    }
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
        // legacyMissing=false triggers replacement of missing channels
        // with 0 for legacy spaces (the condition in toSpace is inverted).
        color.toSpace(sp, legacyMissing = false)
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

  /** Globally available color built-ins. Mirrors dart-sass `global`.
    *
    * Functions that always exist as CSS natives (rgb, rgba, hsl, hsla, hwb,
    * lab, lch, oklab, oklch, color) do NOT get the `global-builtin` deprecation.
    * Functions with conditional CSS filter overloads (invert, grayscale,
    * saturate, alpha, opacity) emit the `global-builtin` warning inline
    * (only when the argument is a Sass color, not a CSS passthrough). All
    * other legacy functions use `.withDeprecationWarning("color")` or
    * `.withDeprecationWarning("color", "adjust")`.
    */
  val global: List[Callable] = List(
    // ### RGB — channel accessors with deprecation
    redFn.withDeprecationWarning("color"),
    greenFn.withDeprecationWarning("color"),
    blueFn.withDeprecationWarning("color"),
    mixFn.withDeprecationWarning("color"),
    // CSS-native rgb/rgba — no deprecation
    rgbFn,
    rgbaFn,
    // invert has conditional inline warnForGlobalBuiltIn
    invertFn,
    // ### HSL — channel accessors with deprecation
    hueFn.withDeprecationWarning("color"),
    saturationFn.withDeprecationWarning("color"),
    lightnessFn.withDeprecationWarning("color"),
    // CSS-native hsl/hsla — no deprecation
    hslFn,
    hslaFn,
    // grayscale has conditional inline warnForGlobalBuiltIn
    grayscaleFn,
    // Manipulation — point to color.adjust
    adjustHueFn.withDeprecationWarning("color", "adjust"),
    lightenFn.withDeprecationWarning("color", "adjust"),
    darkenFn.withDeprecationWarning("color", "adjust"),
    // saturate has conditional inline warnForGlobalBuiltIn in color branch
    saturateFn,
    desaturateFn.withDeprecationWarning("color", "adjust"),
    // ### Opacity
    opacifyFn.withDeprecationWarning("color", "adjust"),
    fadeInFn.withDeprecationWarning("color", "adjust"),
    transparentizeFn.withDeprecationWarning("color", "adjust"),
    fadeOutFn.withDeprecationWarning("color", "adjust"),
    // alpha/opacity have conditional inline warnForGlobalBuiltIn
    alphaFn,
    opacityFn,
    // ### Color Spaces — CSS-native, no deprecation
    colorFn,
    hwbFn,
    labFn,
    lchFn,
    oklabFn,
    oklchFn,
    complementFn.withDeprecationWarning("color"),
    // ### Miscellaneous
    adjustColorFn.withDeprecationWarning("color", "adjust"),
    scaleColorFn.withDeprecationWarning("color", "scale"),
    changeColorFn.withDeprecationWarning("color", "change")
  )

  // --- Module-only function aliases (without -color suffix) ---

  private val changeFn: BuiltInCallable =
    BuiltInCallable.function("change", "$color, $kwargs...", args => updateComponents(args, change = true))

  private val adjustFn: BuiltInCallable =
    BuiltInCallable.function("adjust", "$color, $kwargs...", args => updateComponents(args, adjust = true))

  private val scaleFn: BuiltInCallable =
    BuiltInCallable.function("scale", "$color, $kwargs...", args => updateComponents(args, scale = true))

  /** color.whiteness($color) — deprecated, use color.channel($color, "whiteness", $space: hwb) */
  private val whitenessFn: BuiltInCallable =
    BuiltInCallable.function("whiteness", "$color", { args =>
      val c = args(0).assertColor()
      SassNumber(c.toSpace(ColorSpace.hwb).channel1, "%")
    })

  /** color.blackness($color) — deprecated, use color.channel($color, "blackness", $space: hwb) */
  private val blacknessFn: BuiltInCallable =
    BuiltInCallable.function("blackness", "$color", { args =>
      val c = args(0).assertColor()
      SassNumber(c.toSpace(ColorSpace.hwb).channel2, "%")
    })

  /** color.ie-hex-str($color) — converts to IE hex format #AARRGGBB */
  private val ieHexStrFn: BuiltInCallable =
    BuiltInCallable.function("ie-hex-str", "$color", { args =>
      val color = args(0).assertColor().toSpace(ColorSpace.rgb).toGamut(GamutMapMethod.localMinde)
      def hexStr(component: Double): String =
        fuzzyRound(component).toInt.toHexString.toUpperCase.reverse.padTo(2, '0').reverse
      SassString(s"#${hexStr(color.alpha * 255)}${hexStr(color.channel0)}${hexStr(color.channel1)}${hexStr(color.channel2)}", hasQuotes = false)
    })

  // Module-specific versions of functions that have CSS filter overloads in
  // global scope but should only accept colors in module scope.
  private val moduleSaturateFn: BuiltInCallable =
    BuiltInCallable.function("saturate", "$color, $amount", { args =>
      val c = args(0).assertColor()
      if (!c.isLegacy) {
        throw SassScriptException(
          "saturate() is only supported for legacy colors. Please use " +
            "color.adjust() instead with an explicit $space argument."
        )
      }
      warnLegacyColorFunction("saturate", "color.adjust($color, $saturation: +$amount)")
      val amt = scalar(args(1).assertNumber(), 100)
      adjustHsl(c, sDelta = amt)
    })

  private val moduleGrayscaleFn: BuiltInCallable =
    BuiltInCallable.function("grayscale", "$color", { args =>
      val c = args(0).assertColor()
      if (c.isLegacy) {
        val hsl = c.toSpace(ColorSpace.hsl)
        SassColor.hsl(hsl.channel0OrNull, Nullable(0.0), hsl.channel2OrNull, Nullable(hsl.alpha))
          .toSpace(c.space, legacyMissing = false)
      } else {
        val oklch = c.toSpace(ColorSpace.oklch)
        SassColor.oklch(oklch.channel0OrNull, Nullable(0.0), oklch.channel2OrNull, Nullable(oklch.alpha))
          .toSpace(c.space)
      }
    })

  private val moduleOpacityFn: BuiltInCallable =
    BuiltInCallable.function("opacity", "$color", args => SassNumber(args.head.assertColor().alpha))

  private val moduleAlphaFn: BuiltInCallable =
    BuiltInCallable.function("alpha", "$color", args => SassNumber(args.head.assertColor().alpha))

  /** Color Module 4 entry points — only registered under the `sass:color` module (not as globals).
    */
  private val moduleOnly: List[Callable] = List(
    // Module-specific overrides (no CSS filter overload):
    moduleSaturateFn,
    moduleGrayscaleFn,
    moduleOpacityFn,
    moduleAlphaFn,
    // Module-only functions:
    changeFn,
    adjustFn,
    scaleFn,
    whitenessFn,
    blacknessFn,
    ieHexStrFn,
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

  def module: List[Callable] = {
    // Module overrides shadow global functions with same name
    val overrideNames = Set("saturate", "grayscale", "opacity", "alpha")
    global.filterNot(c => overrideNames.contains(c.name)) ::: moduleOnly
  }

  /** Fallback for unregistered color function names. */
  def stub(name: String, args: List[Value]): Value =
    throw new UnsupportedOperationException("Unregistered color function: color." + name)
}
