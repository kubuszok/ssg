/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/configuration.dart, lib/src/configured_value.dart
 * Original: Copyright (c) 2019 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: configuration.dart -> Configuration.scala (merged)
 *   Convention: Skeleton — public API surface only
 */
package ssg
package sass

import scala.language.implicitConversions
import ssg.sass.ast.AstNode
import ssg.sass.value.Value

/** A set of variables configured for a Sass module via `@use ... with`. */
final class Configuration private (val values: Map[String, ConfiguredValue]) {

  def isEmpty: Boolean = values.isEmpty

  /** Removes and returns the configured value for [name], if any. */
  def remove(name: String): Nullable[ConfiguredValue] =
    values.get(name) match {
      case Some(v) => v
      case scala.None => Nullable.empty
    }

  /** Throws a [[SassException]] if any values remain — i.e. for values that
    * weren't used by the module.
    */
  def throwErrorForUnknownVariables(): Unit = {
    // TODO: implement
  }

  override def toString: String = s"Configuration($values)"
}

object Configuration {

  /** The empty configuration. */
  val empty: Configuration = new Configuration(Map.empty)

  def apply(values: Map[String, ConfiguredValue]): Configuration = new Configuration(values)

  /** An "implicit" configuration — one inherited from forward-with. */
  def implicitConfig(values: Map[String, ConfiguredValue]): Configuration =
    // TODO: distinguish implicit from explicit
    new Configuration(values)
}

/** A single value in a [[Configuration]]. */
final class ConfiguredValue(
  val value: Value,
  val configurationSpan: Nullable[AstNode],
  val assignmentNode: Nullable[AstNode]
) {

  override def toString: String = s"ConfiguredValue($value)"
}

object ConfiguredValue {

  def explicit(value: Value, assignmentNode: Nullable[AstNode] = Nullable.empty): ConfiguredValue =
    new ConfiguredValue(value, Nullable.empty, assignmentNode)

  def implicitValue(value: Value, assignmentNode: Nullable[AstNode] = Nullable.empty): ConfiguredValue =
    new ConfiguredValue(value, Nullable.empty, assignmentNode)
}
