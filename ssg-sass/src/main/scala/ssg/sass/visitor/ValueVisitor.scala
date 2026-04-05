/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/visitor/interface/value.dart
 * Original: Copyright (c) 2018 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: interface/value.dart → ValueVisitor.scala
 *   Convention: Forward declaration — methods added as value types are ported
 */
package ssg
package sass
package visitor

import ssg.sass.value.*

/** Visitor interface for SassScript value types. */
trait ValueVisitor[T] {
  def visitBoolean(value: SassBoolean): T
  def visitCalculation(value: SassCalculation): T
  def visitColor(value: SassColor): T
  def visitFunction(value: SassFunction): T
  def visitList(value: SassList): T
  def visitMap(value: SassMap): T
  def visitMixin(value: SassMixin): T
  def visitNull(): T
  def visitNumber(value: SassNumber): T
  def visitString(value: SassString): T
}
