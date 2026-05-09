/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * This is a module for storing settings passed into KaTeX. It correctly handles
 * default settings.
 *
 * Original source: katex src/Settings.ts
 * Original author: Khan Academy and contributors
 * Original license: MIT
 *
 * upstream-commit: 90de9794
 *
 * Migration notes:
 *   Renames: Settings -> Settings (same)
 *   Convention: TypeScript union type for strict -> sealed trait StrictSetting
 *   Idiom: TypeScript union type for trust -> sealed trait TrustSetting
 */
package ssg
package katex

import scala.collection.mutable
import scala.util.boundary
import scala.util.boundary.break

import ssg.commons.Nullable
import ssg.katex.util.Utils

/**
 * Function type for strict mode callback.
 * Returns boolean, string, null, or undefined.
 */
type StrictFunction =
  (String, String, Nullable[SourceLocation.HasLoc]) => Nullable[Boolean | String]

/**
 * Function type for trust callback.
 */
type TrustFunction = AnyTrustContext => Nullable[Boolean]

/**
 * Trust context: discriminated union by command field.
 */
sealed trait AnyTrustContext {
  def command: String
}

object AnyTrustContext {
  final case class HrefContext(
      command: String = "\\href",
      url: String,
      var protocol: Nullable[String] = Nullable.Null
  ) extends AnyTrustContext

  final case class IncludegraphicsContext(
      command: String = "\\includegraphics",
      url: String,
      var protocol: Nullable[String] = Nullable.Null
  ) extends AnyTrustContext

  final case class UrlContext(
      command: String = "\\url",
      url: String,
      var protocol: Nullable[String] = Nullable.Null
  ) extends AnyTrustContext

  final case class HtmlClassContext(
      command: String = "\\htmlClass",
      `class`: String
  ) extends AnyTrustContext

  final case class HtmlIdContext(
      command: String = "\\htmlId",
      id: String
  ) extends AnyTrustContext

  final case class HtmlStyleContext(
      command: String = "\\htmlStyle",
      style: String
  ) extends AnyTrustContext

  final case class HtmlDataContext(
      command: String = "\\htmlData",
      attributes: mutable.Map[String, String]
  ) extends AnyTrustContext
}

/**
 * Sealed trait for the `strict` setting: can be boolean, string enum, or function.
 */
sealed trait StrictSetting

object StrictSetting {
  final case class BoolValue(value: Boolean) extends StrictSetting
  final case class StringValue(value: String) extends StrictSetting // "warn", "ignore", "error"
  final case class FunctionValue(value: StrictFunction) extends StrictSetting

  given Conversion[Boolean, StrictSetting] = BoolValue(_)
  given Conversion[String, StrictSetting] = StringValue(_)
  given Conversion[StrictFunction, StrictSetting] = FunctionValue(_)
}

/**
 * Sealed trait for the `trust` setting: can be boolean or function.
 */
sealed trait TrustSetting

object TrustSetting {
  final case class BoolValue(value: Boolean) extends TrustSetting
  final case class FunctionValue(value: TrustFunction) extends TrustSetting

  given Conversion[Boolean, TrustSetting] = BoolValue(_)
  given Conversion[TrustFunction, TrustSetting] = FunctionValue(_)
}

/**
 * The main Settings object
 *
 * The current options stored are:
 *  - displayMode: Whether the expression should be typeset as inline math
 *                 (false, the default), meaning that the math starts in
 *                 \textstyle and is placed in an inline-block); or as display
 *                 math (true), meaning that the math starts in \displaystyle
 *                 and is placed in a block with vertical margin.
 */
class Settings(
    var displayMode: Boolean = false,
    var output: String = "htmlAndMathml", // "html" | "mathml" | "htmlAndMathml"
    var leqno: Boolean = false,
    var fleqn: Boolean = false,
    var throwOnError: Boolean = true,
    var errorColor: String = "#cc0000",
    macrosInit: Nullable[MacroMap] = Nullable.Null,
    minRuleThicknessInit: Double = 0.0,
    var colorIsTextColor: Boolean = false,
    var strict: StrictSetting = StrictSetting.BoolValue(false),
    var trust: TrustSetting = TrustSetting.BoolValue(false),
    maxSizeInit: Double = Double.PositiveInfinity,
    maxExpandInit: Int = 1000,
    var globalGroup: Boolean = false
) extends ssg.katex.parse.SettingsLike {

  // Process macros: copy to a new mutable map
  var macros: MacroMap = macrosInit match {
    case m if m.isDefined => m.get
    case _                => mutable.Map.empty
  }

  // Process minRuleThickness: clamp to >= 0
  var minRuleThickness: Double = Math.max(0, minRuleThicknessInit)

  // Process maxSize: clamp to >= 0
  var maxSize: Double = Math.max(0, maxSizeInit)

  // Process maxExpand: clamp to >= 0
  var maxExpand: Int = Math.max(0, maxExpandInit)

  /**
   * Report nonstrict (non-LaTeX-compatible) input.
   * Can safely not be called if `this.strict` is false in JavaScript.
   */
  def reportNonstrict(
      errorCode: String,
      errorMsg: String,
      token: Nullable[SourceLocation.HasLoc] = Nullable.Null
  ): Unit = boundary {
    val resolved: Nullable[Boolean | String] = strict match {
      case StrictSetting.FunctionValue(f) =>
        // Allow return value of strict function to be boolean or string
        // (or null/undefined, meaning no further processing).
        f(errorCode, errorMsg, token)
      case StrictSetting.BoolValue(b) => Nullable(b)
      case StrictSetting.StringValue(s) => Nullable(s)
    }

    if (resolved.isEmpty) {
      // null/undefined => no further processing
      break(())
    }

    val v = resolved.get
    v match {
      case b: Boolean =>
        if (!b) {
          // false => ignore
          break(())
        }
        // true => error
        throw new ParseError(
          "LaTeX-incompatible input and strict mode is set to 'error': " +
          s"$errorMsg [$errorCode]", token)
      case s: String =>
        if (s == "ignore") {
          break(())
        } else if (s == "error") {
          throw new ParseError(
            "LaTeX-incompatible input and strict mode is set to 'error': " +
            s"$errorMsg [$errorCode]", token)
        } else if (s == "warn") {
          System.err.println(
            "LaTeX-incompatible input and strict mode is set to 'warn': " +
            s"$errorMsg [$errorCode]")
        } else { // won't happen in type-safe code
          System.err.println(
            "LaTeX-incompatible input and strict mode is set to " +
            s"unrecognized '$s': $errorMsg [$errorCode]")
        }
    }
  }

  /** Overload matching the SettingsLike interface (no token). */
  override def reportNonstrict(errorCode: String, errorMsg: String): Unit = {
    reportNonstrict(errorCode, errorMsg, Nullable.Null)
  }

  /**
   * Check whether to apply strict (LaTeX-adhering) behavior for unusual
   * input (like `\\`).  Unlike `nonstrict`, will not throw an error;
   * instead, "error" translates to a return value of `true`, while "ignore"
   * translates to a return value of `false`.  May still print a warning:
   * "warn" prints a warning and returns `false`.
   * This is for the second category of `errorCode`s listed in the README.
   */
  def useStrictBehavior(
      errorCode: String,
      errorMsg: String,
      token: Nullable[SourceLocation.HasLoc] = Nullable.Null
  ): Boolean = boundary {
    val resolved: Nullable[Boolean | String] = strict match {
      case StrictSetting.FunctionValue(f) =>
        // Allow return value of strict function to be boolean or string
        // (or null/undefined, meaning no further processing).
        // But catch any exceptions thrown by function, treating them
        // like "error".
        try {
          f(errorCode, errorMsg, token)
        } catch {
          case _: Exception =>
            Nullable("error": (Boolean | String))
        }
      case StrictSetting.BoolValue(b) => Nullable(b)
      case StrictSetting.StringValue(s) => Nullable(s)
    }

    if (resolved.isEmpty) {
      break(false)
    }

    val v = resolved.get
    v match {
      case b: Boolean =>
        if (!b) false
        else true
      case s: String =>
        if (s == "ignore") {
          false
        } else if (s == "error") {
          true
        } else if (s == "warn") {
          System.err.println(
            "LaTeX-incompatible input and strict mode is set to 'warn': " +
            s"$errorMsg [$errorCode]")
          false
        } else { // won't happen in type-safe code
          System.err.println(
            "LaTeX-incompatible input and strict mode is set to " +
            s"unrecognized '$s': $errorMsg [$errorCode]")
          false
        }
    }
  }

  /**
   * Check whether to test potentially dangerous input, and return
   * `true` (trusted) or `false` (untrusted).  The sole argument `context`
   * should be an object with `command` field specifying the relevant LaTeX
   * command (as a string starting with `\`), and any other arguments, etc.
   * If `context` has a `url` field, a `protocol` field will automatically
   * get added by this function (changing the specified object).
   */
  def isTrusted(context: AnyTrustContext): Boolean = boundary {
    // If context has a url, compute the protocol
    context match {
      case c: AnyTrustContext.HrefContext =>
        if (c.url.nonEmpty && c.protocol.isEmpty) {
          val protocol = Utils.protocolFromUrl(c.url)
          if (protocol.isEmpty) break(false)
          c.protocol = protocol
        }
      case c: AnyTrustContext.IncludegraphicsContext =>
        if (c.url.nonEmpty && c.protocol.isEmpty) {
          val protocol = Utils.protocolFromUrl(c.url)
          if (protocol.isEmpty) break(false)
          c.protocol = protocol
        }
      case c: AnyTrustContext.UrlContext =>
        if (c.url.nonEmpty && c.protocol.isEmpty) {
          val protocol = Utils.protocolFromUrl(c.url)
          if (protocol.isEmpty) break(false)
          c.protocol = protocol
        }
      case _ => // no url field
    }

    val trustResult: Nullable[Boolean] = trust match {
      case TrustSetting.FunctionValue(f) => f(context)
      case TrustSetting.BoolValue(b) => Nullable(b)
    }
    trustResult.isDefined && trustResult.get
  }
}
