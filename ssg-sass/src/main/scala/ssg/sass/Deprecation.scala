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
 *   Convention: Dart enum -> Scala 3 enum; pub_semver -> Version case class
 *   Idiom: Version implements Ordered[Version] for semver comparison
 *
 * Covenant: full-port
 * Covenant-dart-reference: lib/src/deprecation.dart
 * Covenant-verified: 2026-04-26
 */
package ssg
package sass

import ssg.sass.Nullable
import ssg.sass.Nullable.*

import scala.language.implicitConversions

/** A semantic version with major, minor, and patch components.
  *
  * Implements proper semver comparison where 1.10.0 > 1.9.0.
  */
final case class Version(major: Int, minor: Int, patch: Int) extends Ordered[Version] {

  override def compare(that: Version): Int = {
    val majorCmp = this.major.compare(that.major)
    if (majorCmp != 0) majorCmp
    else {
      val minorCmp = this.minor.compare(that.minor)
      if (minorCmp != 0) minorCmp
      else this.patch.compare(that.patch)
    }
  }

  override def toString: String = s"$major.$minor.$patch"
}

object Version {

  /** Parses a version string like "1.23.0" into a Version. */
  def parse(s: String): Version = {
    val parts = s.split('.')
    require(parts.length >= 1, s"Invalid version string: $s")
    val major = parts(0).toInt
    val minor = if (parts.length > 1) parts(1).toInt else 0
    val patch = if (parts.length > 2) parts(2).toInt else 0
    Version(major, minor, patch)
  }

  /** Try to parse a version string, returning None on failure. */
  def tryParse(s: String): Option[Version] =
    try Some(parse(s))
    catch { case _: Exception => None }
}

/** A deprecated feature in the Sass language. */
enum Deprecation(
  val id:           String,
  val deprecatedIn: Nullable[Version],
  val description:  Nullable[String] = Nullable.Null,
  val obsoleteIn:   Nullable[Version] = Nullable.Null,
  val isFuture:     Boolean = false
) extends java.lang.Enum[Deprecation] {

  case CallString extends Deprecation("call-string", Version(0, 0, 0), "Passing a string directly to meta.call().")
  case Elseif extends Deprecation("elseif", Version(1, 3, 2), "@elseif.")
  case MozDocument extends Deprecation("moz-document", Version(1, 7, 2), "@-moz-document.")
  case RelativeCanonical extends Deprecation("relative-canonical", Version(1, 14, 2), "Imports using relative canonical URLs.")
  case NewGlobal extends Deprecation("new-global", Version(1, 17, 2), "Declaring new variables with !global.")
  case ColorModuleCompat extends Deprecation("color-module-compat", Version(1, 23, 0), "Using color module functions in place of plain CSS functions.")
  case SlashDiv extends Deprecation("slash-div", Version(1, 33, 0), "/ operator for division.")
  case BogusCombinators extends Deprecation("bogus-combinators", Version(1, 54, 0), "Leading, trailing, and repeated combinators.")
  case StrictUnary extends Deprecation("strict-unary", Version(1, 55, 0), "Ambiguous + and - operators.")
  case FunctionUnits extends Deprecation("function-units", Version(1, 56, 0), "Passing invalid units to built-in functions.")
  case DuplicateVarFlags extends Deprecation("duplicate-var-flags", Version(1, 62, 0), "Using !default or !global multiple times for one variable.")
  case NullAlpha extends Deprecation("null-alpha", Version(1, 62, 3), "Passing null as alpha in the API.")
  case AbsPercent extends Deprecation("abs-percent", Version(1, 65, 0), "Passing percentages to the Sass abs() function.")
  case FsImporterCwd extends Deprecation("fs-importer-cwd", Version(1, 73, 0), "Using the current working directory as an implicit load path.")
  case CssFunctionMixin extends Deprecation("css-function-mixin", Version(1, 76, 0), "Function and mixin names beginning with --.", obsoleteIn = Version(1, 94, 0))
  case MixedDecls extends Deprecation("mixed-decls", Version(1, 77, 7), "Declarations after or between nested rules.", obsoleteIn = Version(1, 92, 0))
  case FeatureExists extends Deprecation("feature-exists", Version(1, 78, 0), "meta.feature-exists")
  case Color4Api extends Deprecation("color-4-api", Version(1, 79, 0), "Certain uses of built-in sass:color functions.")
  case ColorFunctions extends Deprecation("color-functions", Version(1, 79, 0), "Using global color functions instead of sass:color.")
  case LegacyJsApi extends Deprecation("legacy-js-api", Version(1, 79, 0), "Legacy JS API.")
  case Import extends Deprecation("import", Version(1, 80, 0), "@import rules.")
  case GlobalBuiltin extends Deprecation("global-builtin", Version(1, 80, 0), "Global built-in functions that are available in sass: modules.")
  case TypeFunction extends Deprecation("type-function", Version(1, 86, 0), "Functions named \"type\".", obsoleteIn = Version(1, 92, 0))
  case CompileStringRelativeUrl extends Deprecation("compile-string-relative-url", Version(1, 88, 0), "Passing a relative url to compileString().")
  case MisplacedRest extends Deprecation("misplaced-rest", Version(1, 91, 0), "A rest parameter before a positional or named parameter.")
  case WithPrivate extends Deprecation("with-private", Version(1, 92, 0), "Configuring private variables in @use, @forward, or load-css().")
  case IfFunction extends Deprecation("if-function", Version(1, 95, 0), "The Sass if($condition, $if-true, $if-false) function.")
  case FunctionName extends Deprecation("function-name", Version(1, 98, 0), "Uppercase reserved function names.")
  case UserAuthored extends Deprecation("user-authored", Nullable.Null)

  /// @deprecated This deprecation name was never actually used.
  case CalcInterp extends Deprecation("calc-interp", Nullable.Null)

  override def toString: String = id
}

object Deprecation {

  /// @deprecated Use DuplicateVarFlags instead.
  val duplicateVariableFlags: Deprecation = Deprecation.DuplicateVarFlags

  /** Returns the deprecation with the given ID, or None. */
  def fromId(id: String): Option[Deprecation] =
    Deprecation.values.find(_.id == id)

  /** Returns the set of all deprecations done in or before [version].
    *
    * A deprecation is included if:
    *   - Its deprecatedIn version is <= the given version
    *   - It has not become obsolete (obsoleteIn is null)
    */
  def forVersion(version: Version): Set[Deprecation] = {
    val result = Set.newBuilder[Deprecation]
    var i      = 0
    val values = Deprecation.values
    while (i < values.length) {
      val deprecation = values(i)
      deprecation.deprecatedIn.foreach { deprecatedIn =>
        if (deprecatedIn <= version && deprecation.obsoleteIn.isEmpty) {
          result += deprecation
        }
      }
      i += 1
    }
    result.result()
  }
}
