/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/syntax.dart
 * Original: Copyright (c) 2018 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: syntax.dart -> Syntax.scala
 *   Convention: Dart enum -> Scala 3 enum extends java.lang.Enum
 *
 * Covenant: full-port
 * Covenant-dart-reference: lib/src/syntax.dart
 * Covenant-verified: 2026-04-26
 */
package ssg
package sass

/** An enum of syntaxes that Sass can parse. */
enum Syntax(val displayName: String) extends java.lang.Enum[Syntax] {

  /** The CSS-superset SCSS syntax. */
  case Scss extends Syntax("SCSS")

  /** The whitespace-sensitive indented syntax. */
  case Sass extends Syntax("Sass")

  /** The plain CSS syntax, which disallows special Sass features. */
  case Css extends Syntax("CSS")

  override def toString: String = displayName
}

object Syntax {

  /** Returns the default syntax for a file loaded from the given path. */
  def forPath(path: String): Syntax =
    if (path.endsWith(".sass")) Syntax.Sass
    else if (path.endsWith(".css")) Syntax.Css
    else Syntax.Scss
}
