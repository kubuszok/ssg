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
 *   Convention: Dart mutable map -> Scala mutable.Map for in-place remove()
 *   Idiom: ExplicitConfiguration extends Configuration
 */
package ssg
package sass

import scala.collection.mutable
import scala.language.implicitConversions
import ssg.sass.ast.AstNode
import ssg.sass.ast.sass.ForwardRule
import ssg.sass.util.{ FileLocation, FileSpan, LimitedMapView, SourceFile, UnprefixedMapView }
import ssg.sass.value.Value

/// A set of variables meant to configure a module by overriding its
/// `!default` declarations.
///
/// A configuration may be either *implicit*, meaning that it's either empty or
/// created by importing a file containing a `@forward` rule; or *explicit*,
/// meaning that it's created by passing a `with` clause to a `@use` rule.
/// Explicit configurations have spans associated with them and are represented
/// by the [ExplicitConfiguration] subclass.
class Configuration private[sass] (
  private[sass] val _values:           mutable.Map[String, ConfiguredValue],
  private val __originalConfiguration: Nullable[Configuration]
) {

  /// A map from variable names (without `$`) to values.
  ///
  /// This map may not be modified directly. To remove a value from this
  /// configuration, use the [remove] method.
  def values: Map[String, ConfiguredValue] = _values.toMap

  /// The configuration from which this was modified with `@forward ... with`.
  ///
  /// This reference serves as an opaque ID.
  private[sass] def _originalConfiguration: Configuration =
    __originalConfiguration.getOrElse(this)

  /// Returns whether `this` and [that] [Configuration]s have the same
  /// [_originalConfiguration].
  ///
  /// An implicit configuration will always return `false` because it was not
  /// created through another configuration.
  ///
  /// [ExplicitConfiguration]s and configurations created [throughForward]
  /// will be considered to have the same original config if they were created
  /// as a copy from the same base configuration.
  def sameOriginal(that: Configuration): Boolean =
    _originalConfiguration eq that._originalConfiguration

  def isEmpty: Boolean = _values.isEmpty

  /** Whether this configuration is implicit (not created with an explicit `with` clause). Implicit configurations are ignored when a module has already been loaded.
    */
  def isImplicit: Boolean = !this.isInstanceOf[ExplicitConfiguration]

  /// Removes a variable with [name] from this configuration, returning it.
  ///
  /// If no such variable exists in this configuration, returns null.
  def remove(name: String): Nullable[ConfiguredValue] =
    if (isEmpty) Nullable.empty
    else {
      _values.remove(name) match {
        case Some(v)    => Nullable(v)
        case scala.None => Nullable.empty
      }
    }

  /// Creates a new configuration from this one based on a `@forward` rule.
  def throughForward(forward: ForwardRule): Configuration = {
    if (isEmpty) return this
    var newValues: mutable.Map[String, ConfiguredValue] = _values

    // Only allow variables that are visible through the `@forward` to be
    // configured. These views support [Map.remove] so we can mark when a
    // configuration variable is used by removing it even when the underlying
    // map is wrapped.
    forward.prefix.foreach { prefix =>
      newValues = new UnprefixedMapView[ConfiguredValue](newValues, prefix)
    }

    forward.shownVariables.foreach { shownVariables =>
      newValues = LimitedMapView.safelist(newValues, shownVariables)
    }
    if (forward.shownVariables.isEmpty) {
      forward.hiddenVariables.foreach { hiddenVariables =>
        if (hiddenVariables.nonEmpty) {
          newValues = LimitedMapView.blocklist(newValues, hiddenVariables)
        }
      }
    }
    _withValues(newValues)
  }

  /// Returns a copy of `this` [Configuration] with the given [values] map.
  ///
  /// The copy will have the same [_originalConfiguration] as `this` config.
  protected def _withValues(newValues: mutable.Map[String, ConfiguredValue]): Configuration =
    new Configuration(newValues, Nullable(_originalConfiguration))

  /** Throws a [[SassException]] if any values remain — i.e. for values that weren't used by the module. Implicit configurations are ignored: an unused forwarded `with` clause is not an error.
    */
  def throwErrorForUnknownVariables(): Unit = {
    if (isImplicit || _values.isEmpty) return
    val names  = _values.keys.toList.sorted.map("$" + _).mkString(", ")
    val plural = if (_values.size == 1) "variable" else "variables"
    val file   = new SourceFile(Nullable.empty, "")
    val loc    = FileLocation(file, 0, 0, 0)
    val span   = FileSpan(file, loc, loc)
    throw new SassException(
      s"$names was not declared with !default in the @used module (no such configurable $plural).",
      span
    )
  }

  override def toString: String = {
    val entries = _values.map { case (name, value) => s"$$$name: $value" }
    "(" + entries.mkString(",") + ")"
  }
}

object Configuration {

  /// The empty configuration, which indicates that the module has not been
  /// configured.
  ///
  /// Empty configurations are always considered implicit, since they are
  /// ignored if the module has already been loaded.
  val empty: Configuration = new Configuration(mutable.Map.empty, Nullable.Null)

  def apply(values: Map[String, ConfiguredValue]): Configuration =
    new Configuration(mutable.Map.from(values), Nullable.Null)

  /// Creates an implicit configuration with the given [values].
  def implicitConfig(values: Map[String, ConfiguredValue]): Configuration =
    new Configuration(mutable.Map.from(values), Nullable.Null)
}

/// A [Configuration] that was created with an explicit `with` clause of a
/// `@use` rule.
///
/// Both types of configuration pass through `@forward` rules, but explicit
/// configurations will cause an error if attempting to use them on a module
/// that has already been loaded, while implicit configurations will be
/// silently ignored in this case.
final class ExplicitConfiguration(
  values: mutable.Map[String, ConfiguredValue],
  /// The node whose span indicates where the configuration was declared.
  val nodeWithSpan:      AstNode,
  originalConfiguration: Nullable[Configuration] = Nullable.Null
) extends Configuration(values, originalConfiguration) {

  /// Returns a copy of `this` with the given [values] map.
  ///
  /// The copy will have the same [_originalConfiguration] as `this` config.
  override protected def _withValues(newValues: mutable.Map[String, ConfiguredValue]): Configuration =
    new ExplicitConfiguration(newValues, nodeWithSpan, Nullable(_originalConfiguration))
}

object ExplicitConfiguration {

  /// Creates a base [ExplicitConfiguration] with a [values] map and a
  /// [nodeWithSpan].
  def apply(values: Map[String, ConfiguredValue], nodeWithSpan: AstNode): ExplicitConfiguration =
    new ExplicitConfiguration(mutable.Map.from(values), nodeWithSpan)
}

/** A single value in a [[Configuration]]. */
final class ConfiguredValue(
  val value:             Value,
  val configurationSpan: Nullable[AstNode],
  val assignmentNode:    Nullable[AstNode]
) {

  override def toString: String = s"ConfiguredValue($value)"
}

object ConfiguredValue {

  def explicit(value: Value, assignmentNode: Nullable[AstNode] = Nullable.empty): ConfiguredValue =
    new ConfiguredValue(value, Nullable.empty, assignmentNode)

  def implicitValue(value: Value, assignmentNode: Nullable[AstNode] = Nullable.empty): ConfiguredValue =
    new ConfiguredValue(value, Nullable.empty, assignmentNode)
}
