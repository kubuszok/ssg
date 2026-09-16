/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/ast/selector/qualified_name.dart
 * Original: Copyright (c) 2016 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: qualified_name.dart -> QualifiedName.scala
 *   Convention: Dart final class -> Scala final case class
 *   Idiom: Nullable namespace instead of null String?
 *
 * Covenant: full-port
 * Covenant-dart-reference: lib/src/ast/selector/qualified_name.dart
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: ec85871864ca16f8045e66ad329bd462e791bfa1
 */
package ssg
package sass
package ast
package selector

import ssg.sass.Nullable
import ssg.sass.Nullable.*

import scala.language.implicitConversions

/** A [qualified name](https://www.w3.org/TR/css3-namespace/#css-qnames).
  *
  * @param name
  *   the identifier name
  * @param namespace
  *   the namespace name. If empty, `name` belongs to the default namespace. If it's the empty string, `name` belongs to no namespace. If it's `*`, `name` belongs to any namespace. Otherwise, `name`
  *   belongs to the given namespace.
  */
final case class QualifiedName(
  name:      String,
  namespace: Nullable[String] = Nullable.Null
) {

  override def toString: String =
    if (namespace.isDefined) s"${namespace.get}|$name"
    else name
}
