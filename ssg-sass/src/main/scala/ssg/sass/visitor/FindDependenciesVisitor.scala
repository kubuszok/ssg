/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/visitor/find_dependencies.dart
 * Original: Copyright (c) 2018 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: find_dependencies.dart -> FindDependenciesVisitor.scala
 *   Convention: Dart final class with mixin -> Scala final class extending RecursiveStatementVisitor
 *   Idiom: Mutable Sets accumulated during traversal; result returned as
 *          immutable DependencyReport
 */
package ssg
package sass
package visitor

import java.net.URI

import scala.collection.mutable

import ssg.sass.Nullable
import ssg.sass.ast.sass.*

/** Returns [[stylesheet]]'s statically-declared dependencies. */
def findDependencies(stylesheet: Stylesheet): DependencyReport =
  new FindDependenciesVisitor().run(stylesheet)

/** A visitor that traverses a stylesheet and records all its dependencies on
  * other stylesheets.
  */
final class FindDependenciesVisitor extends RecursiveStatementVisitor {
  private val _uses        = mutable.LinkedHashSet.empty[URI]
  private val _forwards    = mutable.LinkedHashSet.empty[URI]
  private val _metaLoadCss = mutable.LinkedHashSet.empty[URI]
  private val _imports     = mutable.LinkedHashSet.empty[URI]

  /** The namespaces under which `sass:meta` has been `@use`d in this
    * stylesheet. An empty `Nullable` namespace means `sass:meta` was loaded
    * without a namespace.
    */
  private val _metaNamespaces = mutable.HashSet.empty[Nullable[String]]

  def run(stylesheet: Stylesheet): DependencyReport = {
    visitStylesheet(stylesheet)
    DependencyReport(
      uses = _uses.toSet,
      forwards = _forwards.toSet,
      metaLoadCss = _metaLoadCss.toSet,
      imports = _imports.toSet
    )
  }

  // These can never contain imports.
  override def visitEachRule(node: EachRule): Unit  = ()
  override def visitForRule(node: ForRule): Unit    = ()
  override def visitIfRule(node: IfRule): Unit      = ()
  override def visitWhileRule(node: WhileRule): Unit = ()
  override protected def visitCallableDeclaration(node: CallableDeclaration): Unit = ()

  override def visitUseRule(node: UseRule): Unit = {
    if (node.url.getScheme != "sass") {
      _uses += node.url
    } else if (node.url.toString == "sass:meta") {
      _metaNamespaces += node.namespace
    }
  }

  override def visitForwardRule(node: ForwardRule): Unit = {
    if (node.url.getScheme != "sass") _forwards += node.url
  }

  override def visitImportRule(node: ImportRule): Unit = {
    for (imp <- node.imports) {
      imp match {
        case di: DynamicImport => _imports += di.url
        case _                 => ()
      }
    }
  }

  override def visitIncludeRule(node: IncludeRule): Unit = {
    // TODO(ssg-sass): handle meta.load-css() with static string arguments.
    // Requires inspecting node.arguments.positional for a single
    // StringExpression(text = Interpolation(asPlain = url)).
    if (node.name == "load-css" && _metaNamespaces.contains(node.namespace)) {
      // Skeleton — full positional-arg inspection deferred.
      ()
    }
  }
}

/** A struct of different types of dependencies a Sass stylesheet can contain.
  *
  * @param uses        all `@use`d URLs (excluding built-in modules)
  * @param forwards    all `@forward`ed URLs (excluding built-in modules)
  * @param metaLoadCss all URLs loaded by `meta.load-css()` calls with static
  *                    string arguments outside of mixins
  * @param imports     all dynamically `@import`ed URLs
  */
final case class DependencyReport(
  uses: Set[URI],
  forwards: Set[URI],
  metaLoadCss: Set[URI],
  imports: Set[URI]
) {

  /** All URLs in [[uses]], [[forwards]], and [[metaLoadCss]]. */
  def modules: Set[URI] = uses ++ forwards ++ metaLoadCss

  /** All URLs in [[uses]], [[forwards]], [[metaLoadCss]], and [[imports]]. */
  def all: Set[URI] = modules ++ imports
}
