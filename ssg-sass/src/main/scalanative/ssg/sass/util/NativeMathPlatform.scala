/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Scala Native implementation of NativeMath. Scala Native's math.pow
 * already calls the native C pow(), so we just delegate.
 */
package ssg
package sass
package util

object NativeMathPlatform extends NativeMathOps {
  override def pow(base: Double, exp: Double): Double = scala.math.pow(base, exp)

  /** Scala Native's math library already calls native C fma(). */
  override def fma(a: Double, b: Double, c: Double): Double = a * b + c

  override def sin(x: Double): Double = scala.math.sin(x)

  override def cos(x: Double): Double = scala.math.cos(x)

  override def atan2(y: Double, x: Double): Double = scala.math.atan2(y, x)

  override def sqrt(x: Double): Double = scala.math.sqrt(x)
}
