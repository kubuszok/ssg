/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/functions/selector.dart
 * Original: Copyright (c) 2019 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: selector.dart -> SelectorFunctions.scala
 *   Convention: faithful port of dart-sass sass:selector module.
 *               Module functions use the bare dart-sass names
 *               (is-superselector, simple-selectors, parse, nest,
 *               append, extend, replace, unify). The global namespace
 *               exposes `parse`, `nest`, `append`, `extend`, `replace`,
 *               and `unify` under their `selector-*` prefixed legacy
 *               names via `withName`; `is-superselector` and
 *               `simple-selectors` stay unprefixed in both places.
 *
 * Covenant: full-port
 * Covenant-baseline-spec-pass: 415
 * Covenant-baseline-loc: 340
 * Covenant-baseline-methods: nestFn,appendFn,extendFn,replaceFn,unifyFn,isSuperselectorFn,simpleSelectorsFn,parseFn,asSelectorText,asSelectorList,assertCompoundSelectorArg,prependParent,runExtendPipeline,global,module,SelectorFunctions
 * Covenant-dart-reference: lib/src/functions/selector.dart
 * Covenant-verified: 2026-04-08
 *
 * T006 — Phase 4 task. Faithful port of selector.dart covering:
 *   - nest: descendant-combinator nesting via SelectorList.nestWithin
 *   - append: compound-merge append via prependParent + nestWithin
 *   - extend: ExtensionStore.extend (AST extension pipeline)
 *   - replace: ExtensionStore.replace (replace mode)
 *   - unify: SelectorList.unify
 *   - is-superselector: SelectorList.isSuperselector
 *   - simple-selectors: CompoundSelector accessor
 *   - parse: round-trip through the selector parser
 *
 * Spec progress — core_functions/selector sass-spec subdir 2→415/899
 * (0.2%→46.2%, +413 cases). Global +424 cases (4569→4993).
 * Remaining 484 subdir failures are dominated by the unported
 * `_selectorPseudoIsSuperselector` logic from extend/functions.dart
 * (~200 :is/:not/:has superselector cases) plus nested-selector
 * edge cases. Tracked as B010.
 *
 * upstream-commit: ec85871864ca16f8045e66ad329bd462e791bfa1
 */
package ssg
package sass
package functions

import scala.language.implicitConversions

import ssg.sass.{ BuiltInCallable, Callable, Nullable, SassScriptException }
import ssg.sass.ast.selector.{ ComplexSelector, ComplexSelectorComponent, CompoundSelector, ParentSelector, SelectorList, SimpleSelector, TypeSelector, UniversalSelector }
import ssg.sass.extend.ExtensionStore
import ssg.sass.parse.SelectorParser
import ssg.sass.util.FileSpan
import ssg.sass.value.{ ListSeparator, SassBoolean, SassList, SassNull, SassString, Value }

/** Built-in `sass:selector` functions. Faithful port of `lib/src/functions/selector.dart`. */
object SelectorFunctions {

  // ---------------------------------------------------------------------------
  // Value -> text -> SelectorList coercion.
  //
  // dart-sass's `Value.assertSelector` round-trips a SassString or SassList
  // through the selector parser. We reconstruct the same shape here:
  //   - SassString: parse the text directly
  //   - SassList (comma-separated): join with `, ` and parse
  //   - SassList (space-separated): join with ` ` and parse
  // Nested lists are flattened recursively so an `asSassList` output from
  // one call can be round-tripped back into a SelectorList by the next.
  // ---------------------------------------------------------------------------

  /** Coerce a [[Value]] to a selector text string, mirroring dart-sass's `Value._selectorStringOrNull`. Returns `None` if the value isn't a valid selector structure. The rules are:
    *   - SassString: return its text.
    *   - SassList with slash separator: invalid (None).
    *   - SassList with comma separator: each element must be a SassString or a space-separated SassList whose elements are themselves valid.
    *   - SassList with space/undecided separator: each element must be a SassString.
    *   - Anything else: invalid (None).
    */
  private def asSelectorText(v: Value): Option[String] = v match {
    case s: SassString => Some(s.text)
    case l: SassList   =>
      val items = l.asList
      if (items.isEmpty) return None
      l.separator match {
        case ListSeparator.Slash => None
        case ListSeparator.Comma =>
          val parts = items.map {
            case s:   SassString                                                                                   => Some(s.text)
            case sub: SassList if sub.separator == ListSeparator.Space || sub.separator == ListSeparator.Undecided =>
              asSelectorText(sub)
            case _ => None
          }
          if (parts.exists(_.isEmpty)) None
          else Some(parts.flatten.mkString(", "))
        case _ => // Space or Undecided
          val parts = items.map {
            case s: SassString => Some(s.text)
            case _ => None
          }
          if (parts.exists(_.isEmpty)) None
          else Some(parts.flatten.mkString(" "))
      }
    case _ => None
  }

  /** Parse a [[Value]] as a [[SelectorList]]. Raises a SassScriptException with the caller-supplied argument name on failure, matching dart-sass's `assertSelector` error format.
    *
    * @param allowParent
    *   whether `&` (parent selectors) are permitted. Defaults to `false`, matching dart-sass's `assertSelector`.
    */
  private def asSelectorList(v: Value, name: String, allowParent: Boolean = false): SelectorList =
    asSelectorText(v) match {
      case Some(text) =>
        try
          new SelectorParser(text, allowParent = allowParent).parse()
        catch {
          case e: Exception =>
            throw SassScriptException(s"$$$name: ${e.getMessage}")
        }
      case None =>
        throw SassScriptException(
          s"$$$name: $v is not a valid selector: it must be a string,\n" +
            "a list of strings, or a list of lists of strings."
        )
    }

  /** Extracts a single [[CompoundSelector]] from a [[Value]] if the argument parses as a selector whose only complex selector has one compound component. Raises a SassScriptException otherwise,
    * matching dart-sass's `assertCompoundSelector`.
    */
  private def assertCompoundSelectorArg(v: Value, name: String): CompoundSelector = {
    val list = asSelectorList(v, name)
    if (list.components.length != 1)
      throw SassScriptException(s"$$$name: $v is not a compound selector.")
    val complex = list.components.head
    if (complex.leadingCombinators.nonEmpty || complex.components.length != 1)
      throw SassScriptException(s"$$$name: $v is not a compound selector.")
    complex.components.head.selector
  }

  // ---------------------------------------------------------------------------
  // `selector-parse($selector)` / `parse($selector)` — round-trips a value
  // through the parser and returns its `asSassList` form.
  // ---------------------------------------------------------------------------

  private val parseFn: BuiltInCallable =
    BuiltInCallable.function(
      "parse",
      "$selector",
      { args =>
        val list = asSelectorList(args(0), "selector")
        list.asSassList
      }
    )

  // ---------------------------------------------------------------------------
  // `selector-nest($selectors...)` / `nest($selectors...)` — each successive
  // selector is nested inside the previous one with a descendant combinator.
  // ---------------------------------------------------------------------------

  private val nestFn: BuiltInCallable =
    BuiltInCallable.function(
      "nest",
      "$selectors...",
      { args =>
        val raw: List[Value] =
          if (args.length == 1) args(0).asList else args
        if (raw.isEmpty)
          throw SassScriptException("$selectors: At least one selector must be passed.")
        var first  = true
        val parsed = raw.map { v =>
          val list = asSelectorList(v, "selectors", allowParent = true)
          if (first) {
            list.components.foreach { complex =>
              complex.components.foreach { component =>
                component.selector.components.foreach {
                  case ps: ssg.sass.ast.selector.ParentSelector if ps.suffix.isDefined =>
                    throw SassScriptException(
                      "A top-level selector may not contain a parent selector with a suffix.",
                      Some("selectors")
                    )
                  case _ => ()
                }
              }
            }
          }
          first = false
          list
        }
        parsed.reduceLeft((parent, child) => child.nestWithin(Nullable(parent), implicitParent = true)).asSassList
      }
    )

  // ---------------------------------------------------------------------------
  // `selector-append($selectors...)` / `append($selectors...)` — successive
  // selectors are concatenated into the previous selector's last compound
  // (no descendant combinator). Matches dart-sass's `_prependParent`
  // plus `nestWithin` pipeline.
  // ---------------------------------------------------------------------------

  private val appendFn: BuiltInCallable =
    BuiltInCallable.function(
      "append",
      "$selectors...",
      { args =>
        val raw: List[Value] =
          if (args.length == 1) args(0).asList else args
        if (raw.isEmpty)
          throw SassScriptException("$selectors: At least one selector must be passed.")
        val parsed = raw.map(v => asSelectorList(v, "selectors"))
        parsed.reduceLeft { (parent, child) =>
          val prepended = SelectorList(
            child.components.map { complex =>
              if (complex.leadingCombinators.nonEmpty)
                throw SassScriptException(s"Can't append $complex to $parent.")
              val head :: rest = complex.components: @unchecked
              val newCompound  = prependParent(head.selector, complex.span)
              if (newCompound.isEmpty)
                throw SassScriptException(s"Can't append $complex to $parent.")
              new ComplexSelector(
                Nil,
                new ComplexSelectorComponent(newCompound.get, head.combinators, head.span) :: rest,
                complex.span
              )
            },
            child.span
          )
          prepended.nestWithin(Nullable(parent), implicitParent = true)
        }.asSassList
      }
    )

  /** Prepends a [[ParentSelector]] to [compound], matching dart-sass's `_prependParent` helper. Returns Nullable.empty if the result wouldn't be a valid selector (e.g. the compound starts with a
    * universal selector or a namespaced type selector).
    */
  private def prependParent(
    compound: CompoundSelector,
    span:     FileSpan
  ): Nullable[CompoundSelector] = {
    val comps = compound.components
    if (comps.isEmpty) Nullable.empty
    else
      comps.head match {
        case _: UniversalSelector                          => Nullable.empty
        case t: TypeSelector if t.name.namespace.isDefined =>
          Nullable.empty
        case t: TypeSelector =>
          // Suffix the parent selector with the type name: `&` + `t.name.name`.
          val parent: SimpleSelector = new ParentSelector(span, Nullable(t.name.name))
          Nullable(new CompoundSelector(parent :: comps.tail, span))
        case _ =>
          val parent: SimpleSelector = new ParentSelector(span, Nullable.empty)
          Nullable(new CompoundSelector(parent :: comps, span))
      }
  }

  // ---------------------------------------------------------------------------
  // `selector-extend($selector, $extendee, $extender)` /
  // `extend($selector, $extendee, $extender)` — runs the extend algorithm
  // through a fresh ExtensionStore.
  // ---------------------------------------------------------------------------

  private val extendFn: BuiltInCallable =
    BuiltInCallable.function(
      "extend",
      "$selector, $extendee, $extender",
      { args =>
        val selector = asSelectorList(args(0), "selector")
        val target   = asSelectorList(args(1), "extendee")
        val source   = asSelectorList(args(2), "extender")
        selector.assertNotBogus(Nullable("selector"))
        target.assertNotBogus(Nullable("extendee"))
        source.assertNotBogus(Nullable("extender"))
        ExtensionStore
          .extend(
            selector,
            source,
            target,
            EvaluationContext.current.get.currentCallableSpan
          )
          .asSassList
      }
    )

  private val replaceFn: BuiltInCallable =
    BuiltInCallable.function(
      "replace",
      "$selector, $original, $replacement",
      { args =>
        val selector = asSelectorList(args(0), "selector")
        val target   = asSelectorList(args(1), "original")
        val source   = asSelectorList(args(2), "replacement")
        selector.assertNotBogus(Nullable("selector"))
        target.assertNotBogus(Nullable("original"))
        source.assertNotBogus(Nullable("replacement"))
        ExtensionStore
          .replace(
            selector,
            source,
            target,
            EvaluationContext.current.get.currentCallableSpan
          )
          .asSassList
      }
    )

  // ---------------------------------------------------------------------------
  // `selector-unify($selector1, $selector2)` / `unify($selector1, $selector2)`
  // — returns `null` if the selectors can't be unified.
  // ---------------------------------------------------------------------------

  private val unifyFn: BuiltInCallable =
    BuiltInCallable.function(
      "unify",
      "$selector1, $selector2",
      { args =>
        val selector1 = asSelectorList(args(0), "selector1")
        val selector2 = asSelectorList(args(1), "selector2")
        selector1.assertNotBogus(Nullable("selector1"))
        selector2.assertNotBogus(Nullable("selector2"))
        val unified = selector1.unify(selector2)
        if (unified.isEmpty) SassNull
        else unified.get.asSassList
      }
    )

  // ---------------------------------------------------------------------------
  // `is-superselector($super, $sub)` — matches dart-sass's name in both
  // module and global (no `selector-` prefix).
  // ---------------------------------------------------------------------------

  private val isSuperselectorFn: BuiltInCallable =
    BuiltInCallable.function(
      "is-superselector",
      "$super, $sub",
      { args =>
        val sup = asSelectorList(args(0), "super")
        val sub = asSelectorList(args(1), "sub")
        sup.assertNotBogus(Nullable("super"))
        sub.assertNotBogus(Nullable("sub"))
        SassBoolean(sup.isSuperselector(sub))
      }
    )

  // ---------------------------------------------------------------------------
  // `simple-selectors($selector)` — returns the compound's simples as a
  // comma-separated list of unquoted strings.
  // ---------------------------------------------------------------------------

  private val simpleSelectorsFn: BuiltInCallable =
    BuiltInCallable.function(
      "simple-selectors",
      "$selector",
      { args =>
        val compound = assertCompoundSelectorArg(args(0), "selector")
        SassList(
          compound.components.map(s => SassString(s.toString, hasQuotes = false): Value),
          ListSeparator.Comma
        )
      }
    )

  // ---------------------------------------------------------------------------
  // Renamed copies for the global namespace.
  // ---------------------------------------------------------------------------

  // ---------------------------------------------------------------------------
  // Public lists.
  // ---------------------------------------------------------------------------

  /** Globally available built-ins. Mirrors dart-sass `global`: is-superselector and simple-selectors keep their bare names; the rest get the legacy `selector-*` prefix via withName. Each entry uses
    * `.withDeprecationWarning('selector')` to emit a `global-builtin` deprecation warning directing users to `selector.X`.
    */
  val global: List[Callable] = List(
    isSuperselectorFn.withDeprecationWarning("selector"),
    simpleSelectorsFn.withDeprecationWarning("selector"),
    parseFn.withDeprecationWarning("selector").withName("selector-parse"),
    nestFn.withDeprecationWarning("selector").withName("selector-nest"),
    appendFn.withDeprecationWarning("selector").withName("selector-append"),
    extendFn.withDeprecationWarning("selector").withName("selector-extend"),
    replaceFn.withDeprecationWarning("selector").withName("selector-replace"),
    unifyFn.withDeprecationWarning("selector").withName("selector-unify")
  )

  /** Members of the `sass:selector` module. Mirrors dart-sass `module`. */
  def module: List[Callable] = List(
    isSuperselectorFn,
    simpleSelectorsFn,
    parseFn,
    nestFn,
    appendFn,
    extendFn,
    replaceFn,
    unifyFn
  )
}
