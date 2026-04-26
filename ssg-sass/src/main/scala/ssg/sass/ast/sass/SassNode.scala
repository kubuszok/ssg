/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/ast/sass/node.dart,
 *              lib/src/ast/sass/declaration.dart,
 *              lib/src/ast/sass/dependency.dart,
 *              lib/src/ast/sass/reference.dart,
 *              lib/src/ast/sass/callable_invocation.dart,
 *              lib/src/ast/sass/configured_variable.dart
 * Original: Copyright (c) 2016, 2019, 2021 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: node.dart + declaration.dart + dependency.dart + reference.dart
 *            + callable_invocation.dart + configured_variable.dart -> SassNode.scala
 *   Convention: Dart abstract interface class -> Scala trait
 *   Idiom: ConfiguredVariable is a final case class with span helpers
 *
 * Covenant: full-port
 * Covenant-dart-reference: lib/src/ast/sass/node.dart,
 * Covenant-verified: 2026-04-26
 */
package ssg
package sass
package ast
package sass

import java.net.URI

import ssg.sass.Nullable
import ssg.sass.util.{ FileSpan, initialIdentifier }

/** A node in the abstract syntax tree for an unevaluated Sass or SCSS file. */
trait SassNode extends AstNode

/** A common interface for any node that declares a Sass member. */
trait SassDeclaration extends SassNode {

  /** The name of the declaration, with underscores converted to hyphens. This does not include the `$` for variables.
    */
  def name: String

  /** The span containing this declaration's name. This includes the `$` for variables.
    */
  def nameSpan: FileSpan
}

/** A common interface for [UseRule]s, [ForwardRule]s, and [DynamicImport]s. */
trait SassDependency extends SassNode {

  /** The URL of the dependency this rule loads. */
  def url: URI

  /** The span of the URL for this dependency, including the quotes. */
  def urlSpan: FileSpan
}

/** A common interface for any node that references a Sass member. */
trait SassReference extends SassNode {

  /** The namespace of the member being referenced, or empty if it's referenced without a namespace.
    */
  def namespace: Nullable[String]

  /** The name of the member being referenced, with underscores converted to hyphens. This does not include the `$` for variables.
    */
  def name: String

  /** The span containing this reference's name. For variables, this should include the `$`.
    */
  def nameSpan: FileSpan

  /** The span containing this reference's namespace, empty if [namespace] is empty.
    */
  def namespaceSpan: Nullable[FileSpan]
}

/** An abstract class for invoking a callable (a function or mixin). */
trait CallableInvocation extends SassNode {

  /** The arguments passed to the callable. */
  def arguments: ArgumentList
}

/** A variable configured by a `with` clause in a `@use` or `@forward` rule.
  *
  * @param name
  *   the name of the variable being configured
  * @param expression
  *   the variable's value
  * @param span
  *   the source span
  * @param isGuarded
  *   whether the variable can be further configured by outer modules
  */
final case class ConfiguredVariable(
  name:       String,
  expression: Expression,
  span:       FileSpan,
  isGuarded:  Boolean = false
) extends SassNode
    with SassDeclaration {

  def nameSpan: FileSpan = span.initialIdentifier(includeLeading = 1)

  override def toString: String =
    s"$$$name: $expression${if (isGuarded) " !default" else ""}"
}
