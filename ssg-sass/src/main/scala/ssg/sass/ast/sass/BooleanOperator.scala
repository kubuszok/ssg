/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/ast/sass/boolean_operator.dart
 * Original: Copyright (c) 2025 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: boolean_operator.dart -> BooleanOperator.scala
 *   Convention: Dart enum -> Scala 3 enum extends java.lang.Enum
 *
 * Covenant: full-port
 * Covenant-dart-reference: lib/src/ast/sass/boolean_operator.dart
 * Covenant-verified: 2026-04-26
 */
package ssg
package sass
package ast
package sass

/** An enum for binary boolean operations.
  *
  * Currently CSS only supports conjunctions (`and`) and disjunctions (`or`).
  */
enum BooleanOperator extends java.lang.Enum[BooleanOperator] {
  case And
  case Or

  override def toString: String = this match {
    case And => "and"
    case Or  => "or"
  }
}
