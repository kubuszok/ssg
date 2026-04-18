/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package sass

import ssg.sass.functions.ColorFunctions
import ssg.sass.value.{ SassBoolean, SassColor, SassNumber, SassString, Value }
import ssg.sass.value.color.ColorSpace

/** Tests for the CSS Color Module 4 introspection API registered under the `sass:color` module: color.channel, color.space, color.is-legacy, color.is-in-gamut, color.is-powerless, color.is-missing,
  * color.to-space, color.to-gamut, color.same.
  */
final class ColorModule4Suite extends munit.FunSuite {

  // Resolve a color built-in by name from the module list (which includes
  // globals plus the new module-only Color-4 entries).
  private def fn(name: String): List[Value] => Value = {
    val cb = ColorFunctions.module
      .collect {
        case b: BuiltInCallable if b.name == name => b
      }
      .headOption
      .getOrElse(fail(s"Could not find color function '$name'"))
    cb.callback
  }

  private def num(d: Double): SassNumber = SassNumber(d)
  private def str(s: String): SassString = SassString(s, hasQuotes = false)

  private def red: SassColor =
    fn("rgb")(List(num(255), num(0), num(0))).asInstanceOf[SassColor]

  test("color.channel(red, red) returns 255 in legacy rgb") {
    val c = red
    val v = fn("channel")(List(c, str("red"))).asInstanceOf[SassNumber]
    assertEqualsDouble(v.value, 255.0, 1e-9)
  }

  test("color.channel(red, red, $space: srgb) returns normalized 1") {
    val c = red
    val v = fn("channel")(List(c, str("red"), str("srgb"))).asInstanceOf[SassNumber]
    assertEqualsDouble(v.value, 1.0, 1e-6)
  }

  test("color.space(red) returns 'rgb' for legacy rgb") {
    val s = fn("space")(List(red)).asInstanceOf[SassString]
    assertEquals(s.text, "rgb")
  }

  test("color.space(lab(50 20 -30)) returns 'lab'") {
    val c = fn("lab")(List(num(50), num(20), num(-30))).asInstanceOf[SassColor]
    val s = fn("space")(List(c)).asInstanceOf[SassString]
    assertEquals(s.text, "lab")
  }

  test("color.is-legacy(red) is true") {
    val b = fn("is-legacy")(List(red)).asInstanceOf[SassBoolean]
    assertEquals(b.value, true)
  }

  test("color.is-legacy(lab(50 20 -30)) is false") {
    val c = fn("lab")(List(num(50), num(20), num(-30))).asInstanceOf[SassColor]
    val b = fn("is-legacy")(List(c)).asInstanceOf[SassBoolean]
    assertEquals(b.value, false)
  }

  test("color.is-in-gamut(rgb(300 0 0)) is false") {
    // Construct a literal out-of-gamut legacy rgb (bypass rgb() clamp).
    val oog = SassColor.rgb(
      Nullable(300.0),
      Nullable(0.0),
      Nullable(0.0)
    )
    val b = fn("is-in-gamut")(List(oog)).asInstanceOf[SassBoolean]
    assertEquals(b.value, false)
  }

  test("color.is-in-gamut(red) is true") {
    val b = fn("is-in-gamut")(List(red)).asInstanceOf[SassBoolean]
    assertEquals(b.value, true)
  }

  test("color.is-powerless(hsl(120 0 50%), hue) is true when saturation is 0") {
    val c = fn("hsl")(List(num(120), num(0), num(50))).asInstanceOf[SassColor]
    val b = fn("is-powerless")(List(c, str("hue"))).asInstanceOf[SassBoolean]
    assertEquals(b.value, true)
  }

  test("color.is-missing(red, red) is false") {
    val b = fn("is-missing")(List(red, str("red"))).asInstanceOf[SassBoolean]
    assertEquals(b.value, false)
  }

  test("color.to-space(red, oklch) returns an oklch color") {
    val c = fn("to-space")(List(red, str("oklch"))).asInstanceOf[SassColor]
    assertEquals(c.space, ColorSpace.oklch)
  }

  test("color.to-gamut(rgb(300 0 0), srgb, local-minde) produces an in-gamut color") {
    val oog = SassColor.rgb(
      Nullable(300.0),
      Nullable(0.0),
      Nullable(0.0)
    )
    // $method is now required per dart-sass spec
    val mapped  = fn("to-gamut")(List(oog, str("srgb"), str("local-minde"))).asInstanceOf[SassColor]
    val inGamut = fn("is-in-gamut")(List(mapped, str("srgb"))).asInstanceOf[SassBoolean]
    assertEquals(inGamut.value, true)
  }

  test("color.same(red, rgb(255 0 0)) is true") {
    val b = fn("same")(List(red, red)).asInstanceOf[SassBoolean]
    assertEquals(b.value, true)
  }

  test("color.same(red, blue) is false") {
    val blue = fn("rgb")(List(num(0), num(0), num(255))).asInstanceOf[SassColor]
    val b    = fn("same")(List(red, blue)).asInstanceOf[SassBoolean]
    assertEquals(b.value, false)
  }
}
