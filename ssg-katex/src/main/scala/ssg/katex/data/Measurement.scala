/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * A measurement value with a number and a unit string.
 *
 * Original source: katex src/units.ts (Measurement type)
 * Original author: Khan Academy and contributors
 * Original license: MIT
 *
 * upstream-commit: 90de9794
 */
package ssg
package katex
package data

/** A measurement value: a number paired with a TeX unit string.
  */
final case class Measurement(number: Double, unit: String)
