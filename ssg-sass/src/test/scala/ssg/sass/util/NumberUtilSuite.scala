/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package sass
package util

final class NumberUtilSuite extends munit.FunSuite {

  test("fuzzyEquals treats close numbers as equal") {
    assert(NumberUtil.fuzzyEquals(1.0, 1.0))
    assert(NumberUtil.fuzzyEquals(1.0, 1.0 + 1e-12))
    assert(!NumberUtil.fuzzyEquals(1.0, 1.1))
  }

  test("fuzzyEquals handles edge cases") {
    assert(NumberUtil.fuzzyEquals(0.0, 0.0))
    assert(!NumberUtil.fuzzyEquals(Double.NaN, Double.NaN))
    assert(NumberUtil.fuzzyEquals(Double.PositiveInfinity, Double.PositiveInfinity))
  }

  test("fuzzyHashCode matches fuzzyEquals") {
    val a = 1.0
    val b = 1.0 + 1e-12
    assert(NumberUtil.fuzzyEquals(a, b))
    assertEquals(NumberUtil.fuzzyHashCode(a), NumberUtil.fuzzyHashCode(b))
  }

  test("fuzzyLessThan is strict") {
    assert(NumberUtil.fuzzyLessThan(1.0, 2.0))
    assert(!NumberUtil.fuzzyLessThan(1.0, 1.0))
    assert(!NumberUtil.fuzzyLessThan(1.0, 1.0 + 1e-12))
  }

  test("fuzzyGreaterThan is strict") {
    assert(NumberUtil.fuzzyGreaterThan(2.0, 1.0))
    assert(!NumberUtil.fuzzyGreaterThan(1.0, 1.0))
  }

  test("fuzzyIsInt detects fuzzy integers") {
    assert(NumberUtil.fuzzyIsInt(1.0))
    assert(NumberUtil.fuzzyIsInt(1.0 + 1e-12))
    assert(!NumberUtil.fuzzyIsInt(1.5))
    assert(!NumberUtil.fuzzyIsInt(Double.NaN))
    assert(!NumberUtil.fuzzyIsInt(Double.PositiveInfinity))
  }

  test("fuzzyAsInt returns int when fuzzy-integer") {
    assertEquals(NumberUtil.fuzzyAsInt(3.0).get, 3)
    assertEquals(NumberUtil.fuzzyAsInt(3.0 + 1e-12).get, 3)
    assert(NumberUtil.fuzzyAsInt(3.5).isEmpty)
  }

  test("fuzzyRound rounds up at X.5") {
    assertEquals(NumberUtil.fuzzyRound(1.5), 2)
    assertEquals(NumberUtil.fuzzyRound(2.5), 3)
    assertEquals(NumberUtil.fuzzyRound(1.4), 1)
    assertEquals(NumberUtil.fuzzyRound(-1.5), -2)
  }

  test("fuzzyInRange checks fuzzy boundaries") {
    assert(NumberUtil.fuzzyInRange(0.5, 0.0, 1.0))
    assert(NumberUtil.fuzzyInRange(0.0, 0.0, 1.0))
    assert(NumberUtil.fuzzyInRange(1.0, 0.0, 1.0))
    assert(!NumberUtil.fuzzyInRange(1.5, 0.0, 1.0))
  }

  test("fuzzyCheckRange clamps at boundaries") {
    assertEquals(NumberUtil.fuzzyCheckRange(0.5, 0.0, 1.0).get, 0.5)
    assertEquals(NumberUtil.fuzzyCheckRange(0.0 + 1e-12, 0.0, 1.0).get, 0.0)
    assert(NumberUtil.fuzzyCheckRange(2.0, 0.0, 1.0).isEmpty)
  }

  test("fuzzyAssertRange throws on out-of-range") {
    assertEquals(NumberUtil.fuzzyAssertRange(0.5, 0, 1), 0.5)
    intercept[IllegalArgumentException] {
      NumberUtil.fuzzyAssertRange(2.0, 0, 1)
    }
  }

  test("moduloLikeSass uses floored division") {
    assertEquals(NumberUtil.moduloLikeSass(5.0, 3.0), 2.0)
    assertEquals(NumberUtil.moduloLikeSass(-5.0, 3.0), 1.0)
    assert(NumberUtil.moduloLikeSass(Double.PositiveInfinity, 3.0).isNaN)
    assert(NumberUtil.moduloLikeSass(5.0, 0.0).isNaN)
  }

  test("clampLikeCss prefers lower bound for NaN") {
    assertEquals(NumberUtil.clampLikeCss(Double.NaN, 0.0, 1.0), 0.0)
    assertEquals(NumberUtil.clampLikeCss(0.5, 0.0, 1.0), 0.5)
    assertEquals(NumberUtil.clampLikeCss(-1.0, 0.0, 1.0), 0.0)
    assertEquals(NumberUtil.clampLikeCss(2.0, 0.0, 1.0), 1.0)
  }

  test("signIncludingZero distinguishes -0.0") {
    assertEquals(NumberUtil.signIncludingZero(-0.0), -1.0)
    assertEquals(NumberUtil.signIncludingZero(0.0), 1.0)
    assertEquals(NumberUtil.signIncludingZero(5.0), 1.0)
    assertEquals(NumberUtil.signIncludingZero(-5.0), -1.0)
  }
}
