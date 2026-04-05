/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/ast/sass/at_root_query.dart
 * Original: Copyright (c) 2016 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: at_root_query.dart -> AtRootQuery.scala
 *   Convention: Dart final class -> Scala final class
 *   Idiom: factory constructor -> apply in companion object;
 *          excludes(CssParentNode) deferred to Phase 5
 */
package ssg
package sass
package ast
package sass

/** A query for the `@at-root` rule.
  *
  * @param names   the names of the rules included or excluded by this query
  * @param include whether the query includes or excludes rules with the
  *                specified names
  */
final class AtRootQuery(
  val names: Set[String],
  val include: Boolean
) {

  /** Whether this includes or excludes *all* rules. */
  private val _all: Boolean = names.contains("all")

  /** Whether this includes or excludes style rules. */
  private val _rule: Boolean = names.contains("rule")

  /** Whether this excludes style rules. Note that this takes [include]
    * into account.
    */
  def excludesStyleRules: Boolean = (_all || _rule) != include

  /** Returns whether `this` excludes an at-rule with the given [name]. */
  def excludesName(name: String): Boolean =
    (_all || names.contains(name)) != include
}

object AtRootQuery {

  /** The default at-root query, which excludes only style rules.
    * In dart-sass, the default uses `_rule = true` with empty names,
    * so `excludesStyleRules` = `(false || true) != false` = `true`.
    * We achieve the same by including "rule" in names.
    */
  val defaultQuery: AtRootQuery = new AtRootQuery(Set("rule"), include = false)
}
