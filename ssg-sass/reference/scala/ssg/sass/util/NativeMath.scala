/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Platform-abstracted math operations that need to match the native C library
 * behavior exactly (bit-for-bit). The JVM's Math.pow uses fdlibm, which
 * differs by up to 1 ULP from the native libm pow() used by Dart VM,
 * JavaScript V8, and Scala Native. Since dart-sass spec outputs are generated
 * by the Dart VM (which uses native pow), the JVM platform must call through
 * to the native C pow() to match.
 *
 * Covenant: full-port
 * Covenant-verified: 2026-04-26
 */
package ssg
package sass
package util

/** Platform-specific math operations.
  *
  * Each platform provides a `NativeMathPlatform` object that extends this trait. The shared code accesses it through `NativeMath`.
  */
trait NativeMathOps {

  /** Computes `base` raised to the power `exp`, matching the native C library's `pow()` behavior.
    *
    * On JS and Native, this is equivalent to `scala.math.pow`. On JVM, this calls the native C `pow()` via the Foreign Function & Memory API to avoid the 1-ULP discrepancy in `java.lang.Math.pow`
    * (which uses fdlibm).
    */
  def pow(base: Double, exp: Double): Double

  /** Computes `a * b + c` as a fused multiply-add (single rounding).
    *
    * The Dart VM JIT may fuse chained `a * b + c` operations into FMA instructions on x86-64, producing different rounding than separate multiply and add operations. To match dart-sass spec outputs,
    * we use native FMA on each platform:
    *   - JVM: `java.lang.Math.fma` (uses CPU FMA instruction since JDK 9)
    *   - Native: C `fma()` via scala-native intrinsics
    *   - JS: software emulation (no native FMA available)
    */
  def fma(a: Double, b: Double, c: Double): Double

  /** Computes the sine of `x` (in radians), matching the native C library's `sin()` behavior.
    *
    * On JS and Native, this is equivalent to `scala.math.sin`. On JVM, this calls the native C `sin()` via the Foreign Function & Memory API to avoid up to 1-ULP discrepancy in `java.lang.Math.sin`
    * (which uses fdlibm).
    */
  def sin(x: Double): Double

  /** Computes the cosine of `x` (in radians), matching the native C library's `cos()` behavior.
    *
    * On JS and Native, this is equivalent to `scala.math.cos`. On JVM, this calls the native C `cos()` via the Foreign Function & Memory API to avoid up to 1-ULP discrepancy in `java.lang.Math.cos`
    * (which uses fdlibm).
    */
  def cos(x: Double): Double

  /** Computes the arc tangent of `y/x`, matching the native C library's `atan2()` behavior.
    *
    * On JS and Native, this is equivalent to `scala.math.atan2`. On JVM, this calls the native C `atan2()` via the Foreign Function & Memory API.
    */
  def atan2(y: Double, x: Double): Double

  /** Computes the square root of `x`, matching the native C library's `sqrt()` behavior.
    *
    * On JS and Native, this is equivalent to `scala.math.sqrt`. On JVM, this delegates to `java.lang.Math.sqrt` which is already correctly-rounded (IEEE 754 mandates sqrt is correctly-rounded).
    */
  def sqrt(x: Double): Double
}

/** Platform-delegating math operations for color space conversions.
  *
  * Usage: `NativeMath.pow(base, exp)` instead of `math.pow(base, exp)`.
  */
object NativeMath extends NativeMathOps {
  export NativeMathPlatform.pow
  export NativeMathPlatform.fma
  export NativeMathPlatform.sin
  export NativeMathPlatform.cos
  export NativeMathPlatform.atan2
  export NativeMathPlatform.sqrt
}
