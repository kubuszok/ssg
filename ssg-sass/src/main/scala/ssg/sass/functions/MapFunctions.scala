/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/functions/map.dart
 * Original: Copyright (c) 2016 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: map.dart -> MapFunctions.scala
 *   Convention: Phase 9 — implementations of basic map built-ins.
 */
package ssg
package sass
package functions

import ssg.sass.{ BuiltInCallable, Callable }
import ssg.sass.value.{ ListSeparator, SassBoolean, SassList, SassMap, SassNull, Value }

import scala.collection.immutable.ListMap

/** Built-in map functions. */
object MapFunctions {

  private val mapGetFn: BuiltInCallable =
    BuiltInCallable.function("map-get",
                             "$map, $key",
                             { args =>
                               val map = args.head.assertMap()
                               map.contents.getOrElse(args(1), SassNull)
                             }
    )

  private val mapMergeFn: BuiltInCallable =
    BuiltInCallable.function(
      "map-merge",
      "$map1, $map2",
      { args =>
        val m1 = args.head.assertMap()
        val m2 = args(1).assertMap()
        SassMap(m1.contents ++ m2.contents)
      }
    )

  private val mapRemoveFn: BuiltInCallable =
    BuiltInCallable.function(
      "map-remove",
      "$map, $keys...",
      { args =>
        val map = args.head.assertMap()
        val keys: List[Value] =
          if (args.length >= 2) args(1).asList
          else Nil
        val keySet: Set[Value] = keys.toSet
        val filtered = map.contents.filterNot { case (k, _) => keySet.contains(k) }
        SassMap(ListMap.from(filtered))
      }
    )

  private val mapKeysFn: BuiltInCallable =
    BuiltInCallable.function("map-keys",
                             "$map",
                             { args =>
                               val map = args.head.assertMap()
                               SassList(map.contents.keys.toList, ListSeparator.Comma)
                             }
    )

  private val mapValuesFn: BuiltInCallable =
    BuiltInCallable.function("map-values",
                             "$map",
                             { args =>
                               val map = args.head.assertMap()
                               SassList(map.contents.values.toList, ListSeparator.Comma)
                             }
    )

  private val mapHasKeyFn: BuiltInCallable =
    BuiltInCallable.function("map-has-key",
                             "$map, $key",
                             { args =>
                               val map = args.head.assertMap()
                               SassBoolean(map.contents.contains(args(1)))
                             }
    )

  private val mapSetFn: BuiltInCallable =
    BuiltInCallable.function(
      "set",
      "$map, $key, $value",
      { args =>
        val map = args.head.assertMap()
        SassMap(map.contents.updated(args(1), args(2)))
      }
    )

  private def deepMerge(a: SassMap, b: SassMap): SassMap = {
    var merged = a.contents
    for ((k, v) <- b.contents) {
      val existing = merged.get(k)
      (existing, v) match {
        case (Some(em: SassMap), vm: SassMap) =>
          merged = merged.updated(k, deepMerge(em, vm))
        case _ =>
          merged = merged.updated(k, v)
      }
    }
    SassMap(merged)
  }

  private val mapDeepMergeFn: BuiltInCallable =
    BuiltInCallable.function(
      "deep-merge",
      "$map1, $map2",
      { args =>
        val m1 = args.head.assertMap()
        val m2 = args(1).assertMap()
        deepMerge(m1, m2)
      }
    )

  private def deepRemove(map: SassMap, keys: List[Value]): SassMap =
    keys match {
      case Nil      => map
      case k :: Nil =>
        SassMap(map.contents.removed(k))
      case k :: rest =>
        map.contents.get(k) match {
          case Some(inner: SassMap) =>
            SassMap(map.contents.updated(k, deepRemove(inner, rest)))
          case _ => map
        }
    }

  private val mapDeepRemoveFn: BuiltInCallable =
    BuiltInCallable.function(
      "deep-remove",
      "$map, $key, $keys...",
      { args =>
        val map   = args.head.assertMap()
        val first = args(1)
        val rest: List[Value] =
          if (args.length >= 3) args(2).asList else Nil
        deepRemove(map, first :: rest)
      }
    )

  val global: List[Callable] = List(
    mapGetFn,
    mapMergeFn,
    mapRemoveFn,
    mapKeysFn,
    mapValuesFn,
    mapHasKeyFn
  )

  def module: List[Callable] = global ::: List(mapSetFn, mapDeepMergeFn, mapDeepRemoveFn)
}
