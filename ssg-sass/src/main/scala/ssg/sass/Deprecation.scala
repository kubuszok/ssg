/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/deprecation.dart
 * Original: Copyright (c) 2024 Google LLC.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: deprecation.dart -> Deprecation.scala
 *   Convention: Dart enum -> Scala 3 enum; pub_semver -> simple string version
 *   Idiom: Version.parse replaced with string comparison
 */
package ssg
package sass

import ssg.sass.Nullable
import ssg.sass.Nullable.*

import scala.language.implicitConversions

/** A deprecated feature in the Sass language. */
enum Deprecation(
  val id: String,
  val deprecatedIn: Nullable[String],
  val description: Nullable[String] = Nullable.Null,
  val obsoleteIn: Nullable[String] = Nullable.Null,
  val isFuture: Boolean = false
) extends java.lang.Enum[Deprecation] {

  case CallString extends Deprecation("call-string", "0.0.0", "Passing a string directly to meta.call().")
  case Elseif extends Deprecation("elseif", "1.3.2", "@elseif.")
  case MozDocument extends Deprecation("moz-document", "1.7.2", "@-moz-document.")
  case RelativeCanonical extends Deprecation("relative-canonical", "1.14.2", "Imports using relative canonical URLs.")
  case NewGlobal extends Deprecation("new-global", "1.17.2", "Declaring new variables with !global.")
  case ColorModuleCompat extends Deprecation("color-module-compat", "1.23.0", "Using color module functions in place of plain CSS functions.")
  case SlashDiv extends Deprecation("slash-div", "1.33.0", "/ operator for division.")
  case BogusCombinators extends Deprecation("bogus-combinators", "1.54.0", "Leading, trailing, and repeated combinators.")
  case StrictUnary extends Deprecation("strict-unary", "1.55.0", "Ambiguous + and - operators.")
  case FunctionUnits extends Deprecation("function-units", "1.56.0", "Passing invalid units to built-in functions.")
  case DuplicateVarFlags extends Deprecation("duplicate-var-flags", "1.62.0", "Using !default or !global multiple times for one variable.")
  case NullAlpha extends Deprecation("null-alpha", "1.62.3", "Passing null as alpha in the API.")
  case AbsPercent extends Deprecation("abs-percent", "1.65.0", "Passing percentages to the Sass abs() function.")
  case FsImporterCwd extends Deprecation("fs-importer-cwd", "1.73.0", "Using the current working directory as an implicit load path.")
  case CssFunctionMixin extends Deprecation("css-function-mixin", "1.76.0", "Function and mixin names beginning with --.", obsoleteIn = "1.94.0")
  case MixedDecls extends Deprecation("mixed-decls", "1.77.7", "Declarations after or between nested rules.", obsoleteIn = "1.92.0")
  case FeatureExists extends Deprecation("feature-exists", "1.78.0", "meta.feature-exists")
  case Color4Api extends Deprecation("color-4-api", "1.79.0", "Certain uses of built-in sass:color functions.")
  case ColorFunctions extends Deprecation("color-functions", "1.79.0", "Using global color functions instead of sass:color.")
  case LegacyJsApi extends Deprecation("legacy-js-api", "1.79.0", "Legacy JS API.")
  case Import extends Deprecation("import", "1.80.0", "@import rules.")
  case GlobalBuiltin extends Deprecation("global-builtin", "1.80.0", "Global built-in functions that are available in sass: modules.")
  case TypeFunction extends Deprecation("type-function", "1.86.0", "Functions named \"type\".", obsoleteIn = "1.92.0")
  case CompileStringRelativeUrl extends Deprecation("compile-string-relative-url", "1.88.0", "Passing a relative url to compileString().")
  case MisplacedRest extends Deprecation("misplaced-rest", "1.91.0", "A rest parameter before a positional or named parameter.")
  case WithPrivate extends Deprecation("with-private", "1.92.0", "Configuring private variables in @use, @forward, or load-css().")
  case IfFunction extends Deprecation("if-function", "1.95.0", "The Sass if($condition, $if-true, $if-false) function.")
  case FunctionName extends Deprecation("function-name", "1.98.0", "Uppercase reserved function names.")
  case UserAuthored extends Deprecation("user-authored", Nullable.Null)

  override def toString: String = id
}

object Deprecation {
  /** Returns the deprecation with the given ID, or None. */
  def fromId(id: String): Option[Deprecation] =
    Deprecation.values.find(_.id == id)
}
