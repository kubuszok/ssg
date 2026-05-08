/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/callable.dart
 *              lib/src/callable/plain_css.dart, lib/src/callable/user_defined.dart
 * Original: Copyright (c) 2016 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: callable.dart -> Callable.scala (merged family)
 *   Convention: Skeleton — public API surface only
 *   Idiom: Scala 3 trait + concrete subclasses
 *
 * Covenant: full-port
 * Covenant-dart-reference: lib/src/callable.dart
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: ec85871864ca16f8045e66ad329bd462e791bfa1
 */
package ssg
package sass

import ssg.sass.ast.sass.{ CallableDeclaration, ParameterList, Statement }
import ssg.sass.value.Value

/** An interface for functions and mixins that can be invoked from Sass by passing in arguments.
  */
trait Callable {

  /** The name of this callable. */
  def name: String
}

object Callable {

  /** Creates a function [[Callable]] with the given name, arguments signature and callback.
    *
    * Note: the `arguments` string is parsed into a [[ParameterList]] inside [[BuiltInCallable]].
    */
  def function(name: String, arguments: String, callback: List[Value] => Value): Callable =
    BuiltInCallable.function(name, arguments, callback)

  /// Creates a callable with a single [signature] and a single [callback].
  ///
  /// Throws a [SassFormatException] if parsing fails.
  def fromSignature(
    signature:     String,
    callback:      List[Value] => Value,
    requireParens: Boolean = true
  ): Callable = {
    val (name, declaration) = parseSignature(signature, requireParens)
    BuiltInCallable.parsed(name, declaration, callback)
  }

  /// Parses a function signature string into a (name, ParameterList) pair.
  ///
  /// If [requireParens] is `false`, this allows parentheses to be omitted.
  ///
  /// Throws a [SassFormatException] if parsing fails.
  private[sass] def parseSignature(
    signature:     String,
    requireParens: Boolean = true
  ): (String, ParameterList) =
    try
      new ssg.sass.parse.ScssParser(signature).parseSignature(requireParens = requireParens)
    catch {
      case e: SassException =>
        throw new SassException(
          s"""Invalid signature "$signature": ${e.getMessage}""",
          e.span
        )
    }
}

/** A callable defined in Dart/Scala code, wrapping a native function. */
final class BuiltInCallable(
  val name:           String,
  val parameters:     Nullable[ParameterList],
  val callback:       List[Value] => Value,
  val acceptsContent: Boolean = false,
  val signature:      String = "",
  /** True when this callable was built via [[BuiltInCallable.overloadedFunction]] — i.e. the `callback` is an overload dispatcher that picks between multiple textual signatures at runtime. The
    * canonical `signature` field on the callable is just the longest-named one for named-arg binding; the actual arity constraint lives inside the dispatcher, so the evaluator's per-call arity check
    * must skip overloaded callables entirely (the dispatcher will raise its own "no matching overload" error if needed).
    */
  val isOverloaded: Boolean = false,
  /** All overload signatures (non-empty only when [[isOverloaded]] is true). Used by the evaluator to find the right overload for named-arg binding.
    */
  val allSignatures: List[String] = Nil,
  /** Parsed overloads: each entry is a (ParameterList, Callback) tuple.
    * Non-empty only when [[isOverloaded]] is true. Port of dart-sass `_overloads`.
    */
  val overloads: List[(ParameterList, List[Value] => Value)] = Nil
) extends Callable {

  /** Positional parameter names and their optional default-expression text, derived from the textual [[signature]] (e.g. `"$color, $amount: 1"` → `List(("color", None), ("amount", Some("1")))`).
    * Underscores are normalized to hyphens to match Sass name conventions. Rest parameters (`$args...`) are ignored here. Returns an empty list when the signature is a rest-only form such as
    * `"$args..."`.
    */
  lazy val parameterEntries: List[(String, Option[String])] = {
    val trimmed = signature.trim
    if (trimmed.isEmpty) Nil
    else {
      val parts = scala.collection.mutable.ListBuffer.empty[String]
      val buf   = new StringBuilder()
      var depth = 0
      var i     = 0
      while (i < trimmed.length) {
        val c = trimmed.charAt(i)
        if (c == '(' || c == '[') { depth += 1; buf.append(c) }
        else if (c == ')' || c == ']') { depth -= 1; buf.append(c) }
        else if (c == ',' && depth == 0) {
          parts += buf.toString().trim
          buf.setLength(0)
        } else buf.append(c)
        i += 1
      }
      if (buf.nonEmpty) parts += buf.toString().trim
      parts.toList.flatMap { raw =>
        // Split default value: `$x: expr`
        val (nameRaw, defaultOpt) = raw.indexOf(':') match {
          case -1  => (raw, None)
          case idx => (raw.substring(0, idx).trim, Some(raw.substring(idx + 1).trim))
        }
        // Skip rest parameters `$args...` — they don't bind a fixed name.
        if (nameRaw.endsWith("...")) None
        else if (nameRaw.startsWith("$")) Some((nameRaw.substring(1).replace('_', '-'), defaultOpt))
        else None
      }
    }
  }

  /** Positional parameter names. */
  lazy val parameterNames: List[String] = parameterEntries.map(_._1)

  /** Raw default-expression text for each declared parameter (None if required). */
  lazy val parameterDefaults: List[Option[String]] = parameterEntries.map(_._2)

  /** Whether this callable's signature ends in a rest parameter (`$args...` or `$kwargs...`). Rest callables accept any number of positional arguments beyond the declared slots, so they are exempt
    * from the `_checkBuiltInArity` positional-count validation in the evaluator.
    *
    * Rest parameters are detected by scanning the raw signature for a trailing `...` inside a top-level (depth-0) argument. Both the positional-rest (`$x, $args...`) and the keyword-rest (`$x,
    * $kwargs...`) forms are treated as rest.
    */
  lazy val hasRestParameter: Boolean = {
    val trimmed = signature.trim
    if (trimmed.isEmpty) false
    else {
      // Quick scan for `...` at depth 0 — avoids a full re-parse.
      var depth   = 0
      var i       = 0
      var sawRest = false
      while (!sawRest && i < trimmed.length) {
        val c = trimmed.charAt(i)
        if (c == '(' || c == '[') depth += 1
        else if (c == ')' || c == ']') depth -= 1
        else if (
          depth == 0 && c == '.' && i + 2 < trimmed.length
          && trimmed.charAt(i + 1) == '.' && trimmed.charAt(i + 2) == '.'
        ) {
          sawRest = true
        }
        i += 1
      }
      sawRest
    }
  }

  /** Selects the overload matching the given call-site shape.
    * Port of dart-sass `BuiltInCallable.callbackFor`.
    */
  def callbackFor(positional: Int, names: Set[String]): (ParameterList, List[Value] => Value) = {
    if (overloads.nonEmpty) {
      import scala.util.boundary, boundary.break
      boundary {
        var fuzzyMatch: (ParameterList, List[Value] => Value) = null
        var minMismatchDistance: Int = Int.MaxValue
        for (overload <- overloads) {
          if (overload._1.matches(positional, names)) break(overload)
          val mismatchDistance = overload._1.parameters.length - positional
          if (minMismatchDistance == Int.MaxValue ||
            math.abs(mismatchDistance) < math.abs(minMismatchDistance) ||
            (math.abs(mismatchDistance) == math.abs(minMismatchDistance) && mismatchDistance >= 0)) {
            minMismatchDistance = mismatchDistance
            fuzzyMatch = overload
          }
        }
        if (fuzzyMatch != null) fuzzyMatch
        else throw new IllegalStateException(s"BuiltInCallable $name may not have empty overloads.")
      }
    } else {
      (parameters.getOrElse(ParameterList.parse(s"@function $name($signature) {")), callback)
    }
  }

  /** Returns a copy of this callable with the given [newName]. Port of dart-sass `BuiltInCallable.withName`.
    */
  def withName(newName: String): BuiltInCallable =
    new BuiltInCallable(newName, parameters, callback, acceptsContent, signature, isOverloaded, allSignatures, overloads)

  /** Returns a copy of this callable that emits a deprecation warning directing users to the module form of the function. Port of dart-sass `BuiltInCallable.withDeprecationWarning`.
    */
  def withDeprecationWarning(module: String, newName: String = ""): BuiltInCallable = {
    val effectiveName = if (newName.nonEmpty) newName else name
    def wrap(cb: List[Value] => Value): List[Value] => Value = { args =>
      BuiltInCallable.warnForGlobalBuiltIn(module, effectiveName)
      cb(args)
    }
    val wrappedOverloads = overloads.map { case (pl, cb) => (pl, wrap(cb)) }
    new BuiltInCallable(name, parameters, wrap(callback), acceptsContent, signature, isOverloaded, allSignatures, wrappedOverloads)
  }

  override def toString: String = s"BuiltInCallable($name)"
}

object BuiltInCallable {

  /** Emits a deprecation warning for a global built-in function that is now available as function [name] in built-in module [module]. Port of dart-sass `warnForGlobalBuiltIn` in
    * `lib/src/callable/async_built_in.dart`.
    */
  def warnForGlobalBuiltIn(module: String, name: String): Unit =
    EvaluationContext.warnForDeprecation(
      Deprecation.GlobalBuiltin,
      s"Global built-in functions are deprecated and will be removed in Dart Sass 3.0.0.\n" +
        s"Use $module.$name instead.\n\n" +
        "More info and automated migrator: https://sass-lang.com/d/import"
    )

  /// Creates a callable with a single parsed [parameters] declaration and a
  /// single [callback].
  def parsed(
    name:           String,
    parameters:     ParameterList,
    callback:       List[Value] => Value,
    acceptsContent: Boolean = false
  ): BuiltInCallable =
    BuiltInCallable(name, Nullable(parameters), callback, acceptsContent)

  def function(name: String, arguments: String, callback: List[Value] => Value): BuiltInCallable = {
    val params = ParameterList.parse(s"@function $name($arguments) {")
    BuiltInCallable(name, Nullable(params), callback, signature = arguments)
  }

  def mixin(
    name:           String,
    arguments:      String,
    callback:       List[Value] => Value,
    acceptsContent: Boolean = false
  ): BuiltInCallable = {
    val params = ParameterList.parse(s"@mixin $name($arguments) {")
    BuiltInCallable(
      name,
      Nullable(params),
      args => { callback(args); ssg.sass.value.SassNull },
      acceptsContent,
      signature = arguments
    )
  }

  /** Builds a built-in callable that dispatches by arity.
    *
    * Each entry of `overloads` maps a textual signature (e.g. `"$color"`, `"$color, $amount"`, `"$args..."`) to its callback. At call time the overload whose signature accepts the runtime argument
    * count is selected:
    *
    *   - First, an exact match (positional count == declared parameter count) wins.
    *   - Otherwise, an overload that has more declared parameters (the rest are assumed to have defaults) wins.
    *   - Otherwise, the rest-parameter overload (`$args...`) wins.
    *   - If nothing matches, an `IllegalArgumentException` is thrown.
    *
    * The resulting callable's textual `signature` is taken from the first non-rest overload (or the first overload if all are rest), so named-argument binding still works for the common case of a
    * direct call against the canonical positional shape.
    */
  def overloadedFunction(
    name:      String,
    overloads: Map[String, List[Value] => Value]
  ): BuiltInCallable = {
    val parsedOverloads: List[(ParameterList, List[Value] => Value)] =
      overloads.toList.map { case (signature, cb) =>
        val params =
          try ParameterList.parse(s"@function $name($signature) {")
          catch { case _: Exception => _fallbackParseSignature(name, signature) }
        (params, cb)
      }

    val dispatch: List[Value] => Value = args => {
      val n = args.length
      parsedOverloads.find(_._1.matches(n, Set.empty))
        .orElse(parsedOverloads.find(_._1.restParameter.isDefined))
        .map(_._2(args))
        .getOrElse {
          throw new IllegalArgumentException(
            s"No overload of $name matches ${args.length} argument(s)"
          )
        }
    }

    val candidates = overloads.keys.toList
    def namedSlotCount(sig: String): Int = {
      val parts = sig.split(',').map(_.trim)
      parts.count(p => p.startsWith("$") && !p.endsWith("..."))
    }
    val canonicalSig: String =
      if (candidates.isEmpty) ""
      else
        candidates
          .map(sig => (sig, namedSlotCount(sig), if (sig.trim.endsWith("...")) 0 else 1))
          .sortBy(t => (-t._2, -t._3))
          .head._1
    val canonicalParams =
      try ParameterList.parse(s"@function $name($canonicalSig) {")
      catch { case _: Exception => _fallbackParseSignature(name, canonicalSig) }
    BuiltInCallable(
      name,
      Nullable(canonicalParams),
      dispatch,
      signature = canonicalSig,
      isOverloaded = true,
      allSignatures = candidates,
      overloads = parsedOverloads
    )
  }

  private def _fallbackParseSignature(name: String, signature: String): ParameterList = {
    val trimmed = signature.trim
    if (trimmed.isEmpty) return ParameterList.empty(util.FileSpan.synthetic("<built-in>"))
    val parts = scala.collection.mutable.ListBuffer.empty[String]
    val buf   = new StringBuilder()
    var depth = 0
    var i     = 0
    while (i < trimmed.length) {
      val c = trimmed.charAt(i)
      if (c == '(' || c == '[') { depth += 1; buf.append(c) }
      else if (c == ')' || c == ']') { depth -= 1; buf.append(c) }
      else if (c == ',' && depth == 0) { parts += buf.toString().trim; buf.setLength(0) }
      else buf.append(c)
      i += 1
    }
    if (buf.nonEmpty) parts += buf.toString().trim
    val partList = parts.toList
    val hasRest  = partList.lastOption.exists(_.endsWith("..."))
    val effective = if (hasRest) partList.init else partList
    val params = effective.flatMap { raw =>
      val nameRaw = raw.indexOf(':') match {
        case -1  => raw
        case idx => raw.substring(0, idx).trim
      }
      if (nameRaw.startsWith("$")) {
        val pname = nameRaw.substring(1).replace('_', '-')
        val defaultValue: Nullable[ast.sass.Expression] =
          if (raw.indexOf(':') >= 0) Nullable(ast.sass.StringExpression.plain("0", util.FileSpan.synthetic("<default>")))
          else Nullable.empty
        Some(new ast.sass.Parameter(pname, util.FileSpan.synthetic("<built-in>"), defaultValue))
      } else None
    }
    val restParam: Nullable[String] =
      if (hasRest) partList.last.replace("...", "").trim match {
        case s if s.startsWith("$") => Nullable(s.substring(1).replace('_', '-'))
        case _                      => Nullable.empty
      }
      else Nullable.empty
    new ParameterList(params, util.FileSpan.synthetic("<built-in>"), restParam)
  }
}

/** Name-aware overload dispatch. Port of dart-sass `BuiltInCallable.callbackFor` in `lib/src/callable/built_in.dart`.
  *
  * Each overload is declared with its textual signature (e.g. `"$color, $amount"`) and a callback. At call time the dispatcher picks the overload whose declared positional parameter set is a superset
  * of the caller's positional count AND whose parameter names are a superset of the caller's named keys. This lets a single function name back signatures like `rgb($red, $green, $blue)` vs
  * `rgb($color, $alpha)` where the split is purely by the shape of the caller's arguments.
  */
object BuiltInOverloadDispatch {

  final case class Overload(
    parameterNames: List[String],
    hasRest:        Boolean,
    callback:       (List[Value], Map[String, Value]) => Value
  )

  private def parseSignature(signature: String): Overload = {
    val trimmed = signature.trim
    if (trimmed.isEmpty) return Overload(Nil, hasRest = false, (_, _) => throw new IllegalStateException("empty overload invoked"))
    val parts = scala.collection.mutable.ListBuffer.empty[String]
    val buf   = new StringBuilder()
    var depth = 0
    var i     = 0
    while (i < trimmed.length) {
      val c = trimmed.charAt(i)
      if (c == '(' || c == '[') { depth += 1; buf.append(c) }
      else if (c == ')' || c == ']') { depth -= 1; buf.append(c) }
      else if (c == ',' && depth == 0) { parts += buf.toString().trim; buf.setLength(0) }
      else buf.append(c)
      i += 1
    }
    if (buf.nonEmpty) parts += buf.toString().trim
    val partList  = parts.toList
    val hasRest   = partList.lastOption.exists(_.endsWith("..."))
    val effective =
      (if (hasRest) partList.init else partList).flatMap { raw =>
        val withoutDefault = raw.indexOf(':') match {
          case -1  => raw
          case idx => raw.substring(0, idx).trim
        }
        if (withoutDefault.startsWith("$")) Some(withoutDefault.substring(1).replace('_', '-'))
        else None
      }
    Overload(effective, hasRest, (_, _) => throw new IllegalStateException("overload has no callback attached"))
  }

  /** Given [raw] overload signatures paired with callbacks, select the overload for a call site with [positional] positional arguments and [named] named keys. Matches dart-sass semantics: the chosen
    * overload's parameter-name set must be a superset of [named], and its arity (plus optional rest) must accept [positional].length.
    */
  def select(
    name:       String,
    overloads:  Seq[(String, (List[Value], Map[String, Value]) => Value)],
    positional: List[Value],
    named:      Map[String, Value]
  ): Value = {
    val parsed = overloads.toList.map { case (sig, cb) =>
      val o = parseSignature(sig)
      o.copy(callback = cb)
    }
    val nameKeys = named.keySet
    val n        = positional.length
    // Exact arity, named keys subset of declared names.
    val exact = parsed.find { o =>
      !o.hasRest && o.parameterNames.length >= n && nameKeys.subsetOf(o.parameterNames.toSet)
    }
    val resty = exact.orElse(parsed.find { o =>
      o.hasRest && nameKeys.subsetOf(o.parameterNames.toSet)
    })
    resty match {
      case Some(o) => o.callback(positional, named)
      case None    =>
        throw new IllegalArgumentException(
          s"No overload of $name matches ${positional.length} positional and named ${nameKeys.mkString("{", ",", "}")}"
        )
    }
  }
}

/** A callable that delegates to another callable but reports a different name. Used by `@forward ... as prefix-*` to expose an existing function or mixin under a prefixed name without losing the
  * original's body.
  *
  * The evaluator's mixin/function dispatchers unwrap [[AliasedCallable]] via `underlying` before inspecting the callable kind, so an aliased [[UserDefinedCallable]] remains invocable and an aliased
  * [[BuiltInCallable]] still routes to its native callback.
  */
final class AliasedCallable(
  override val name: String,
  val underlying:    Callable
) extends Callable {

  override def toString: String = s"AliasedCallable($name -> $underlying)"

  override def equals(other: Any): Boolean = other match {
    case that: AliasedCallable => this.name == that.name && this.underlying == that.underlying
    case _ => false
  }

  override def hashCode(): Int = name.hashCode ^ underlying.hashCode()
}

object AliasedCallable {

  /** Returns [callable] unchanged if its name already matches [newName]; otherwise wraps it in an [[AliasedCallable]].
    */
  def apply(newName: String, callable: Callable): Callable =
    if (newName == callable.name) callable
    else
      callable match {
        case bic: BuiltInCallable =>
          // BuiltInCallable has a name-independent callback so we can
          // clone it with the new name directly and avoid a wrapper
          // class. This keeps `match { case bic: BuiltInCallable => ...}`
          // dispatches working without requiring unwrapping.
          new BuiltInCallable(newName, bic.parameters, bic.callback, bic.acceptsContent, bic.signature, bic.isOverloaded)
        case already: AliasedCallable =>
          // Alias-of-alias collapses to a single wrapper pointing at the
          // innermost underlying callable.
          new AliasedCallable(newName, already.underlying)
        case other =>
          new AliasedCallable(newName, other)
      }

  /** Peels all [[AliasedCallable]] layers off of [callable], returning the innermost underlying callable. Returns the argument unchanged when [callable] isn't aliased.
    */
  def unwrap(callable: Callable): Callable = callable match {
    case alias: AliasedCallable => unwrap(alias.underlying)
    case other => other
  }
}

/** A callable that emits a plain-CSS function call. */
final class PlainCssCallable(val name: String) extends Callable {

  override def toString: String = s"PlainCssCallable($name)"

  override def equals(other: Any): Boolean = other match {
    case that: PlainCssCallable => this.name == that.name
    case _ => false
  }

  override def hashCode(): Int = name.hashCode
}

/** A callable defined in user Sass source via `@function` or `@mixin`.
  *
  * `declaration` is typed as the loose [[Statement]] base for backwards compatibility, but in practice it is always a [[CallableDeclaration]] (FunctionRule, MixinRule, or ContentBlock). The [[name]]
  * is taken from the declaration when possible.
  */
final class UserDefinedCallable[E](
  val declaration:  Statement, // The Sass AST node (FunctionRule or MixinRule)
  val environment:  E,
  val inDependency: Boolean = false
) extends Callable {

  val name: String = declaration match {
    case cd: CallableDeclaration => cd.name
    case _ => "user-defined"
  }

  override def toString: String = s"UserDefinedCallable($name)"
}

/** Context utilities for [[UserDefinedCallable]]. */
object UserDefinedCallable {

  def apply[E](
    declaration:  Statement,
    environment:  E,
    inDependency: Boolean = false
  ): UserDefinedCallable[E] = new UserDefinedCallable(declaration, environment, inDependency)
}
