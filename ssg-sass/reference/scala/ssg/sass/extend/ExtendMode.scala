/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/extend/mode.dart
 * Original: Copyright (c) 2017 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: mode.dart -> ExtendMode.scala
 *   Convention: Dart enum -> Scala 3 enum extending java.lang.Enum
 *
 * Covenant: full-port
 * Covenant-dart-reference: lib/src/extend/mode.dart
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: ec85871864ca16f8045e66ad329bd462e791bfa1
 */
package ssg
package sass
package extend

/** Different modes in which extension can run. */
enum ExtendMode(val modeName: String) extends java.lang.Enum[ExtendMode] {

  /** Normal mode, used with the `@extend` rule.
    *
    * This preserves existing selectors and extends each target individually.
    */
  case Normal extends ExtendMode("normal")

  /** Replace mode, used by the `selector-replace()` function.
    *
    * This replaces existing selectors and requires every target to match to extend a given compound selector.
    */
  case Replace extends ExtendMode("replace")

  /** All-targets mode, used by the `selector-extend()` function.
    *
    * This preserves existing selectors but requires every target to match to extend a given compound selector.
    */
  case AllTargets extends ExtendMode("allTargets")

  override def toString: String = name
}
