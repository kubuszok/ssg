/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/module.dart (EnvironmentModule class)
 * Original: Copyright (c) 2019 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: module.dart EnvironmentModule -> EnvironmentModule.scala
 *   Convention: Adapter wrapping an Environment as a Module[Callable].
 *               Pure addition — not yet wired into EvaluateVisitor. Future
 *               work can replace Environment.namespaces (Map[String,Environment])
 *               with Map[String, Module[Callable]] using this adapter.
 */
package ssg
package sass

import scala.language.implicitConversions
import ssg.sass.ast.css.CssStylesheet
import ssg.sass.extend.ExtensionStore
import ssg.sass.value.Value

/** A [[Module]] backed by an [[Environment]].
  *
  * Delegates variable/function/mixin lookups to the wrapped [[Environment]], so modifications flow through to the underlying environment.
  */
final class EnvironmentModule(
  val env:                            Environment,
  val url:                            Nullable[String],
  val css:                            CssStylesheet,
  val extensionStore:                 ExtensionStore,
  val transitivelyContainsCss:        Boolean = false,
  val transitivelyContainsExtensions: Boolean = false
) extends Module[Callable] {

  def variables: Map[String, Value] = env.variableEntries.toMap

  def functions: Map[String, Callable] =
    env.functionValues.map(c => c.name -> c).toMap

  def mixins: Map[String, Callable] =
    env.mixinValues.map(c => c.name -> c).toMap

  def variableIdentities: Set[String] = variables.keySet
  def functionIdentities: Set[String] = functions.keySet
  def mixinIdentities:    Set[String] = mixins.keySet

  def setVariable(name: String, value: Value): Unit = {
    if (!env.variableExists(name))
      throw new IllegalArgumentException(s"Undefined variable: $$$name")
    env.setVariable(name, value)
  }
}

object EnvironmentModule {

  /** Wraps an existing [[Environment]] as a [[Module]]. */
  def apply(
    env:            Environment,
    url:            Nullable[String] = Nullable.empty,
    css:            Nullable[CssStylesheet] = Nullable.empty,
    extensionStore: Nullable[ExtensionStore] = Nullable.empty
  ): EnvironmentModule =
    new EnvironmentModule(
      env = env,
      url = url,
      css = css.getOrElse(CssStylesheet.empty(url)),
      extensionStore = extensionStore.getOrElse(ExtensionStore.empty)
    )

  /** Creates an empty module backed by a fresh built-in [[Environment]]. */
  def empty(url: Nullable[String] = Nullable.empty): EnvironmentModule =
    apply(env = Environment.withBuiltins(), url = url)
}
