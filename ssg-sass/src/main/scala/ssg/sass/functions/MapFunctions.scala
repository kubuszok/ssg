/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/functions/map.dart
 * Original: Copyright (c) 2019 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: map.dart -> MapFunctions.scala
 *   Convention: faithful port of dart-sass sass:map module. Module
 *               functions use bare names (get, set, merge, remove,
 *               keys, values, has-key, deep-merge, deep-remove);
 *               the global alias keeps the `map-*` prefix for the
 *               six deprecated globals via withName.
 *
 * Covenant: full-port
 * Covenant-baseline-spec-pass: 113
 * Covenant-baseline-loc: 372
 * Covenant-baseline-methods: getFn,setFn,mergeFn,removeFn,keysFn,valuesFn,hasKeyFn,deepMergeFn,deepRemoveFn,modify,deepMergeImpl,withName,module,global,restAfter,MapFunctions
 * Covenant-dart-reference: lib/src/functions/map.dart
 * Covenant-verified: 2026-04-08
 *
 * T003 — Phase 4 task. Faithful port of map.dart covering:
 *   - get/has-key with $keys... nested traversal
 *   - set and merge overloaded with both 3-arg + splat forms
 *   - remove overloaded with zero-keys + splat forms
 *   - deep-merge with identical-skip optimization
 *   - deep-remove using _modify with addNesting=false
 *   - _modify helper for nested-path mutation
 *
 * Status: core_functions/map sass-spec subdir 91→113/127 (71.7%→89.0%).
 * Remaining 14 failures map to:
 *   - 9 × B004 (argument arity validation: error/wrong_name and
 *     error/too_many_args tests across all six built-ins)
 *   - 1 × B004 (positional-and-named conflict on remove)
 *   - 2 × B006 (empty list rendering: keys/values empty case)
 *   - 2 × NEW B-task (single-element non-bracketed comma list
 *     rendering: keys/values single-element case shows `(1,)` instead
 *     of `1`)
 */
package ssg
package sass
package functions

import scala.collection.immutable.ListMap
import scala.language.implicitConversions

import ssg.sass.{ BuiltInCallable, Callable, SassScriptException }
import ssg.sass.value.{ ListSeparator, SassArgumentList, SassBoolean, SassList, SassMap, SassNull, Value }

/** Built-in `sass:map` functions. Faithful port of `lib/src/functions/map.dart`. */
object MapFunctions {

  /** Resolve the rest-arg slice for a callback whose declared signature
    * uses `$args...`. ssg-sass's binder may pass the splat as either:
    *
    *   - A single trailing positional value that is a [[SassArgumentList]]
    *     (the canonical splat shape — used when the call site itself
    *     splatted, e.g. `set(map, $list...)`).
    *   - The raw positional values themselves (when the call site passed
    *     them inline, e.g. `set(map, k1, k2, v)`).
    *
    * Both shapes have to be handled because the binder behavior is not
    * yet fully unified. Returns `args.drop(skip)` collapsed to the rest
    * elements.
    */
  private def restAfter(args: List[Value], skip: Int): List[Value] = {
    val tail = args.drop(skip)
    tail match {
      case List(single: SassArgumentList) => single.asList
      case _                              => tail
    }
  }

  // ---------------------------------------------------------------------------
  // Base callables (bare names — what `map.X` resolves to).
  // ---------------------------------------------------------------------------

  private val getFn: BuiltInCallable =
    BuiltInCallable.function(
      "get",
      "$map, $key, $keys...",
      { args =>
        var map = args(0).assertMap("map")
        val keys: List[Value] = args(1) :: restAfter(args, 2)
        // Walk all but the last key; at each step the current value
        // must itself be a map or we return null.
        val last          = keys.last
        val intermediates = keys.init
        var bailed        = false
        val it            = intermediates.iterator
        while (!bailed && it.hasNext) {
          val key = it.next()
          map.contents.get(key) match {
            case Some(nested: SassMap) => map = nested
            case _                     => bailed = true
          }
        }
        if (bailed) SassNull
        else map.contents.getOrElse(last, SassNull)
      }
    )

  private val setFn: BuiltInCallable =
    BuiltInCallable.overloadedFunction(
      "set",
      Map(
        "$map, $key, $value" -> { (args: List[Value]) =>
          val map = args(0).assertMap("map")
          modify(map, List(args(1)), _ => args(2))
        },
        "$map, $args..." -> { (args: List[Value]) =>
          val map     = args(0).assertMap("map")
          val argList = restAfter(args, 1)
          argList match {
            case Nil      =>
              throw SassScriptException("Expected $args to contain a key.")
            case _ :: Nil =>
              throw SassScriptException("Expected $args to contain a value.")
            case _        =>
              val keys  = argList.init
              val value = argList.last
              modify(map, keys, _ => value)
          }
        }
      )
    )

  private val mergeFn: BuiltInCallable =
    BuiltInCallable.overloadedFunction(
      "merge",
      Map(
        "$map1, $map2" -> { (args: List[Value]) =>
          val map1 = args(0).assertMap("map1")
          val map2 = args(1).assertMap("map2")
          SassMap(map1.contents ++ map2.contents)
        },
        "$map1, $args..." -> { (args: List[Value]) =>
          val map1    = args(0).assertMap("map1")
          val argList = restAfter(args, 1)
          argList match {
            case Nil      =>
              throw SassScriptException("Expected $args to contain a key.")
            case _ :: Nil =>
              throw SassScriptException("Expected $args to contain a map.")
            case _        =>
              val keys = argList.init
              val last = argList.last
              val map2 = last.assertMap("map2")
              modify(
                map1,
                keys,
                oldValue => {
                  oldValue.tryMap() match {
                    case None             => map2
                    case Some(nestedMap)  =>
                      SassMap(nestedMap.contents ++ map2.contents)
                  }
                }
              )
          }
        }
      )
    )

  private val removeFn: BuiltInCallable =
    BuiltInCallable.overloadedFunction(
      "remove",
      Map(
        // The zero-keys overload is necessary because the splat-carrying
        // overload below has an explicit $key parameter and therefore
        // rejects single-argument calls like `remove(($a: 1))`.
        "$map" -> { (args: List[Value]) =>
          args(0).assertMap("map")
        },
        "$map, $key, $keys..." -> { (args: List[Value]) =>
          val map               = args(0).assertMap("map")
          val keys: List[Value] = args(1) :: restAfter(args, 2)
          val keySet            = keys.toSet
          val filtered = map.contents.filterNot { case (k, _) => keySet.contains(k) }
          SassMap(ListMap.from(filtered))
        }
      )
    )

  private val keysFn: BuiltInCallable =
    BuiltInCallable.function(
      "keys",
      "$map",
      args => SassList(args(0).assertMap("map").contents.keys.toList, ListSeparator.Comma)
    )

  private val valuesFn: BuiltInCallable =
    BuiltInCallable.function(
      "values",
      "$map",
      args => SassList(args(0).assertMap("map").contents.values.toList, ListSeparator.Comma)
    )

  private val hasKeyFn: BuiltInCallable =
    BuiltInCallable.function(
      "has-key",
      "$map, $key, $keys...",
      { args =>
        var map               = args(0).assertMap("map")
        val keys: List[Value] = args(1) :: restAfter(args, 2)
        val last              = keys.last
        val intermediates     = keys.init
        var bailed            = false
        val it                = intermediates.iterator
        while (!bailed && it.hasNext) {
          val key = it.next()
          map.contents.get(key) match {
            case Some(nested: SassMap) => map = nested
            case _                     => bailed = true
          }
        }
        if (bailed) SassBoolean.sassFalse
        else SassBoolean(map.contents.contains(last))
      }
    )

  private val deepMergeFn: BuiltInCallable =
    BuiltInCallable.function(
      "deep-merge",
      "$map1, $map2",
      { args =>
        val map1 = args(0).assertMap("map1")
        val map2 = args(1).assertMap("map2")
        deepMergeImpl(map1, map2)
      }
    )

  private val deepRemoveFn: BuiltInCallable =
    BuiltInCallable.function(
      "deep-remove",
      "$map, $key, $keys...",
      { args =>
        val map               = args(0).assertMap("map")
        val keys: List[Value] = args(1) :: restAfter(args, 2)
        val last              = keys.last
        val pathKeys          = keys.init
        modify(
          map,
          pathKeys,
          { value =>
            value.tryMap() match {
              case Some(nestedMap) if nestedMap.contents.contains(last) =>
                SassMap(nestedMap.contents - last)
              case _ =>
                value
            }
          },
          addNesting = false
        )
      }
    )

  // ---------------------------------------------------------------------------
  // Private helpers (exact port of dart-sass `_modify` and `_deepMergeImpl`).
  // ---------------------------------------------------------------------------

  /** Updates the specified value in [map] by applying the [modifyFn] callback
    * to it, then returns the resulting map.
    *
    * If more than one key is provided, this means the map targeted for update
    * is nested within [map]. The multiple [keys] form a path of nested maps
    * that leads to the targeted value, which is passed to [modifyFn].
    *
    * If any value along the path (other than the last one) is not a map and
    * [addNesting] is `true`, this creates nested maps to match [keys] and
    * passes the empty map to [modifyFn]. Otherwise, this fails and returns
    * [map] with no changes.
    *
    * If no keys are provided, this passes [map] directly to [modifyFn] and
    * returns the result.
    */
  private def modify(
    map:        SassMap,
    keys:       List[Value],
    modifyFn:   Value => Value,
    addNesting: Boolean = true
  ): Value = {
    val it = keys.iterator
    def modifyNestedMap(current: SassMap): SassMap = {
      var mutableMap = current.contents
      val key        = it.next()
      if (!it.hasNext) {
        val oldValue = mutableMap.getOrElse(key, SassNull)
        mutableMap = mutableMap.updated(key, modifyFn(oldValue))
        return SassMap(mutableMap)
      }
      val nestedMap: Option[SassMap] =
        mutableMap.get(key).flatMap(_.tryMap())
      if (nestedMap.isEmpty && !addNesting) return SassMap(mutableMap)
      val nextMap = nestedMap.getOrElse(SassMap.empty)
      mutableMap = mutableMap.updated(key, modifyNestedMap(nextMap))
      SassMap(mutableMap)
    }
    if (it.hasNext) modifyNestedMap(map)
    else modifyFn(map)
  }

  /** Merges [map1] and [map2], with values in [map2] taking precedence.
    *
    * If both [map1] and [map2] have a map value associated with the same
    * key, this recursively merges those maps as well.
    */
  private def deepMergeImpl(map1: SassMap, map2: SassMap): SassMap = {
    if (map1.contents.isEmpty) return map2
    if (map2.contents.isEmpty) return map1
    var result = map1.contents
    for ((key, value) <- map2.contents) {
      val leftMap  = result.get(key).flatMap(_.tryMap())
      val rightMap = value.tryMap()
      (leftMap, rightMap) match {
        case (Some(resultMap), Some(valueMap)) =>
          val merged = deepMergeImpl(resultMap, valueMap)
          // dart-sass uses `identical(merged, resultMap)` as an
          // optimization to avoid redundant updates when the merge
          // was a no-op. The Scala equivalent uses reference equality
          // via `eq`, which matches the Dart semantics.
          if (merged eq resultMap) ()
          else result = result.updated(key, merged)
        case _ =>
          result = result.updated(key, value)
      }
    }
    SassMap(result)
  }

  // ---------------------------------------------------------------------------
  // Public lists.
  // ---------------------------------------------------------------------------

  /** Globally available built-ins. Mirrors dart-sass `global`. Note `set`,
    * `deep-merge`, and `deep-remove` are NOT in the global; only the six
    * deprecated `map-*` aliases are.
    * Each entry uses `.withDeprecationWarning('map').withName("map-*")`
    * to emit a `global-builtin` deprecation warning directing users to `map.X`.
    */
  val global: List[Callable] = List(
    getFn.withDeprecationWarning("map").withName("map-get"),
    mergeFn.withDeprecationWarning("map").withName("map-merge"),
    removeFn.withDeprecationWarning("map").withName("map-remove"),
    keysFn.withDeprecationWarning("map").withName("map-keys"),
    valuesFn.withDeprecationWarning("map").withName("map-values"),
    hasKeyFn.withDeprecationWarning("map").withName("map-has-key")
  )

  /** Members of the `sass:map` module. Mirrors dart-sass `module`. */
  def module: List[Callable] = List(
    getFn,
    setFn,
    mergeFn,
    removeFn,
    keysFn,
    valuesFn,
    hasKeyFn,
    deepMergeFn,
    deepRemoveFn
  )
}
