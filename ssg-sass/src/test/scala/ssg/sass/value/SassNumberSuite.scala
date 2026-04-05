/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package sass
package value

import ssg.sass.value.number.*

final class SassNumberSuite extends munit.FunSuite {

  test("SassNumber factory creates UnitlessSassNumber") {
    val n = SassNumber(42.0)
    assert(n.isInstanceOf[UnitlessSassNumber])
    assertEquals(n.value, 42.0)
    assert(!n.hasUnits)
  }

  test("SassNumber factory creates SingleUnitSassNumber") {
    val n = SassNumber(10.0, "px")
    assert(n.isInstanceOf[SingleUnitSassNumber])
    assertEquals(n.numeratorUnits, List("px"))
    assertEquals(n.denominatorUnits, Nil)
  }

  test("SassNumber.withUnits creates ComplexSassNumber") {
    val n = SassNumber.withUnits(10.0, numeratorUnits = List("px"), denominatorUnits = List("s"))
    assert(n.isInstanceOf[ComplexSassNumber])
    assertEquals(n.numeratorUnits, List("px"))
    assertEquals(n.denominatorUnits, List("s"))
  }

  test("SassNumber.assertInt for integer values") {
    assertEquals(SassNumber(3.0).assertInt(), 3)
    intercept[SassScriptException] {
      SassNumber(3.5).assertInt()
    }
  }

  test("SassNumber.isInt detects fuzzy integers") {
    assert(SassNumber(3.0).isInt)
    assert(!SassNumber(3.5).isInt)
  }

  test("SassNumber.asInt returns int for fuzzy-integers") {
    assertEquals(SassNumber(5.0).asInt.get, 5)
    assert(SassNumber(5.5).asInt.isEmpty)
  }

  test("SassNumber.assertNoUnits passes for unitless") {
    SassNumber(1.0).assertNoUnits() // should not throw
  }

  test("SassNumber.assertNoUnits throws for units") {
    intercept[SassScriptException] {
      SassNumber(1.0, "px").assertNoUnits()
    }
  }

  test("SassNumber.hasUnit checks unit") {
    assert(SassNumber(10.0, "px").hasUnit("px"))
    assert(!SassNumber(10.0, "px").hasUnit("em"))
    assert(!SassNumber(10.0).hasUnit("px"))
  }

  test("SassNumber arithmetic: plus") {
    val a = SassNumber(10.0, "px")
    val b = SassNumber(5.0, "px")
    val result = a.plus(b).asInstanceOf[SassNumber]
    assertEquals(result.value, 15.0)
  }

  test("SassNumber arithmetic: minus") {
    val a = SassNumber(10.0, "px")
    val b = SassNumber(3.0, "px")
    val result = a.minus(b).asInstanceOf[SassNumber]
    assertEquals(result.value, 7.0)
  }

  test("SassNumber arithmetic: times") {
    val a = SassNumber(4.0, "px")
    val b = SassNumber(3.0)
    val result = a.times(b).asInstanceOf[SassNumber]
    assertEquals(result.value, 12.0)
  }

  test("SassNumber arithmetic: dividedBy") {
    val a = SassNumber(12.0, "px")
    val b = SassNumber(3.0)
    val result = a.dividedBy(b).asInstanceOf[SassNumber]
    assertEquals(result.value, 4.0)
  }

  test("SassNumber comparison: greaterThan") {
    assert(SassNumber(5.0).greaterThan(SassNumber(3.0)).value)
    assert(!SassNumber(3.0).greaterThan(SassNumber(5.0)).value)
  }

  test("SassNumber comparison: lessThan") {
    assert(SassNumber(3.0).lessThan(SassNumber(5.0)).value)
    assert(!SassNumber(5.0).lessThan(SassNumber(3.0)).value)
  }

  test("SassNumber equality uses fuzzy comparison") {
    val a = SassNumber(1.0)
    val b = SassNumber(1.0 + 1e-12)
    assertEquals(a, b)
  }

  test("SassNumber equality considers units") {
    assertNotEquals(SassNumber(10.0, "px"), SassNumber(10.0, "em"))
    assertEquals(SassNumber(10.0, "px"), SassNumber(10.0, "px"))
  }

  test("SassNumber unit conversion: compatible units") {
    val n = SassNumber(1.0, "in")
    val result = n.coerceValue(List("px"), Nil)
    assert(ssg.sass.util.NumberUtil.fuzzyEquals(result, 96.0))
  }

  test("SassNumber.compatibleWithUnit checks unit compatibility") {
    assert(SassNumber(1.0, "px").compatibleWithUnit("in"))
    assert(!SassNumber(1.0, "px").compatibleWithUnit("deg"))
  }

  test("SassNumber.unaryMinus negates") {
    val n = SassNumber(5.0, "px")
    val result = n.unaryMinus().asInstanceOf[SassNumber]
    assertEquals(result.value, -5.0)
    assertEquals(result.numeratorUnits, List("px"))
  }

  test("SassNumber.unaryPlus preserves") {
    val n = SassNumber(5.0, "px")
    val result = n.unaryPlus().asInstanceOf[SassNumber]
    assertEquals(result.value, 5.0)
  }

  test("SassNumber.assertNumber returns self") {
    val n = SassNumber(1.0)
    assertEquals(n.assertNumber(), n)
  }

  test("SassNumber.withUnits simplifies convertible units") {
    // px/px should simplify to unitless
    val n = SassNumber.withUnits(10.0, numeratorUnits = List("px"), denominatorUnits = List("px"))
    assert(!n.hasUnits)
    assertEquals(n.value, 10.0)
  }
}
