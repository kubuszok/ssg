/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package sass

import ssg.sass.value.SassNumber
import ssg.sass.value.number.{ ComplexSassNumber, SingleUnitSassNumber, UnitlessSassNumber }

/** Tests for compound unit algebra on SassNumber (ISS-002).
  *
  * Covers:
  *   - subclass dispatch from SassNumber.withUnits
  *   - compound multiplication union of numerators
  *   - compound division numerator/denominator split
  *   - automatic cancellation / simplification on construction
  *   - addition / subtraction with cross-unit coercion
  *   - incompatible-unit errors on plus/minus
  *   - coerceValueToUnit across all CSS unit categories
  *   - round-trip conversions (length/angle/time/frequency/resolution)
  */
final class SassNumberSuite extends munit.FunSuite {

  private def approx(a: Double, b: Double, eps: Double = 1e-9): Unit =
    assert(math.abs(a - b) < eps, s"expected $a ≈ $b")

  test("SassNumber.withUnits dispatches to the right subclass") {
    assert(SassNumber.withUnits(1.0).isInstanceOf[UnitlessSassNumber])
    assert(SassNumber.withUnits(1.0, List("px"), Nil).isInstanceOf[SingleUnitSassNumber])
    assert(SassNumber.withUnits(1.0, List("px", "s"), Nil).isInstanceOf[ComplexSassNumber])
    assert(SassNumber.withUnits(1.0, List("px"), List("s")).isInstanceOf[ComplexSassNumber])
  }

  test("(10px * 2s).numeratorUnits = [px, s]") {
    val a      = SassNumber(10.0, "px")
    val b      = SassNumber(2.0, "s")
    val result = a.times(b).asInstanceOf[SassNumber]
    assertEquals(result.value, 20.0)
    assertEquals(result.numeratorUnits.toSet, Set("px", "s"))
    assertEquals(result.denominatorUnits, Nil)
  }

  test("(10px / 1s) has numerator [px] and denominator [s]") {
    val a      = SassNumber(10.0, "px")
    val b      = SassNumber(1.0, "s")
    val result = a.dividedBy(b).asInstanceOf[SassNumber]
    assertEquals(result.value, 10.0)
    assertEquals(result.numeratorUnits, List("px"))
    assertEquals(result.denominatorUnits, List("s"))
  }

  test("(10px*s) / (1px) cancels px, yields 10s") {
    val a      = SassNumber.withUnits(10.0, List("px", "s"), Nil)
    val b      = SassNumber(1.0, "px")
    val result = a.dividedBy(b).asInstanceOf[SassNumber]
    assertEquals(result.value, 10.0)
    assertEquals(result.numeratorUnits, List("s"))
    assertEquals(result.denominatorUnits, Nil)
  }

  test("(10px) * (1/1px) cancels fully to unitless 10") {
    val a      = SassNumber(10.0, "px")
    val b      = SassNumber.withUnits(1.0, Nil, List("px"))
    val result = a.times(b).asInstanceOf[SassNumber]
    assertEquals(result.value, 10.0)
    assert(!result.hasUnits)
    assert(result.isInstanceOf[UnitlessSassNumber])
  }

  test("compound multiplication with cross-type conversion: (1px*s) * (1pt/1ms)") {
    // px*s * pt/ms: pt converts to px (factor 4/3), ms to s (factor 1/1000).
    // Numerators get pt -> converts against existing s? No: s vs ms cancels s/ms.
    // Result shape depends on order; what we care about is the magnitude.
    val a      = SassNumber.withUnits(1.0, List("px", "s"), Nil)
    val b      = SassNumber.withUnits(1.0, List("pt"), List("ms"))
    val result = a.times(b).asInstanceOf[SassNumber]
    // Coerce to px*px/1 to compare against expected: 1px*1s * 1pt/1ms
    //   = 1px * 1s * (1/96 in) * 1000/s  [since 1pt = 4/3 px, 1ms = 1/1000 s]
    //   = 1 px * (4/3 px) * 1000 (units cancel) = 4000/3 px*px
    val coerced = result.coerceValue(List("px", "px"), Nil)
    approx(coerced, 4000.0 / 3.0, 1e-6)
  }

  test("plus coerces compatible units: 10px + 1pt = 11.333px") {
    val r = SassNumber(10.0, "px").plus(SassNumber(1.0, "pt")).asInstanceOf[SassNumber]
    approx(r.value, 10.0 + 4.0 / 3.0, 1e-9)
    assertEquals(r.numeratorUnits, List("px"))
  }

  test("minus with cross-compatible units: 1in - 1cm") {
    val r = SassNumber(1.0, "in").minus(SassNumber(1.0, "cm")).asInstanceOf[SassNumber]
    approx(r.value, 1.0 - 1.0 / 2.54, 1e-9)
    assertEquals(r.numeratorUnits, List("in"))
  }

  test("plus throws on incompatible units: 10px + 1s") {
    val a = SassNumber(10.0, "px")
    val b = SassNumber(1.0, "s")
    intercept[SassScriptException](a.plus(b))
  }

  test("coerceValueToUnit converts 10in → 25.4cm") {
    approx(SassNumber(10.0, "in").coerceValueToUnit("cm"), 25.4, 1e-9)
  }

  test("angle round-trip: 1turn → deg → rad → grad → turn") {
    val one   = SassNumber(1.0, "turn")
    val deg   = one.coerceValueToUnit("deg")
    val rad   = SassNumber(deg, "deg").coerceValueToUnit("rad")
    val grad  = SassNumber(rad, "rad").coerceValueToUnit("grad")
    val turns = SassNumber(grad, "grad").coerceValueToUnit("turn")
    approx(deg, 360.0, 1e-9)
    approx(rad, 2.0 * math.Pi, 1e-9)
    approx(grad, 400.0, 1e-9)
    approx(turns, 1.0, 1e-9)
  }

  test("time round-trip: 1s ↔ 1000ms") {
    approx(SassNumber(1.0, "s").coerceValueToUnit("ms"), 1000.0)
    approx(SassNumber(500.0, "ms").coerceValueToUnit("s"), 0.5)
  }

  test("frequency round-trip: 1kHz ↔ 1000Hz") {
    approx(SassNumber(1.0, "kHz").coerceValueToUnit("Hz"), 1000.0)
    approx(SassNumber(250.0, "Hz").coerceValueToUnit("kHz"), 0.25)
  }

  test("resolution round-trip: dpi/dpcm/dppx") {
    approx(SassNumber(1.0, "dppx").coerceValueToUnit("dpi"), 96.0)
    approx(SassNumber(2.54, "dpcm").coerceValueToUnit("dpi"), 2.54 * 2.54)
    approx(SassNumber(96.0, "dpi").coerceValueToUnit("dppx"), 1.0)
  }

  test("length round-trip across all CSS absolute units") {
    val px = SassNumber(96.0, "px")
    approx(px.coerceValueToUnit("in"), 1.0)
    approx(px.coerceValueToUnit("cm"), 2.54)
    approx(px.coerceValueToUnit("mm"), 25.4)
    approx(px.coerceValueToUnit("q"), 25.4 * 4.0)
    approx(px.coerceValueToUnit("pt"), 72.0)
    approx(px.coerceValueToUnit("pc"), 6.0)
  }

  test("modulo preserves and coerces units: 10px mod 3pt") {
    val r = SassNumber(10.0, "px").modulo(SassNumber(3.0, "pt")).asInstanceOf[SassNumber]
    // 3pt = 4px, so 10px mod 4px = 2px
    approx(r.value, 2.0, 1e-9)
    assertEquals(r.numeratorUnits, List("px"))
  }

  test("withUnits cancels cross-type numerator/denominator automatically") {
    // 1 px*s / (pt*ms) should simplify: px/pt → 1/(4/3)=0.75, s/ms → 1000
    val r = SassNumber.withUnits(1.0, List("px", "s"), List("pt", "ms"))
    // After simplification, both numerator/denominator are empty (unitless).
    assert(!r.hasUnits, s"expected unitless, got ${r.numeratorUnits}/${r.denominatorUnits}")
    approx(r.value, 0.75 * 1000.0, 1e-9)
  }

  test("hasCompatibleUnits vs hasPossiblyCompatibleUnits") {
    assert(SassNumber(1.0, "px").hasCompatibleUnits(SassNumber(1.0, "in")))
    assert(!SassNumber(1.0, "px").hasCompatibleUnits(SassNumber(1.0, "s")))
  }
}
