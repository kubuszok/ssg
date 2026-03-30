/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-test-util/src/main/java/com/vladsch/flexmark/test/util/LoadUnloadDataKeyAggregator.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package test
package util

import ssg.md.Nullable
import ssg.md.util.data._
import ssg.md.util.misc.Extension

import java.{util => ju}
import java.util.Collections
import scala.language.implicitConversions

class LoadUnloadDataKeyAggregator private () extends DataKeyAggregator {

  override def aggregate(combined: DataHolder): DataHolder = {
    if (combined.contains(LoadUnloadDataKeyAggregator.LOAD_EXTENSIONS) || combined.contains(LoadUnloadDataKeyAggregator.UNLOAD_EXTENSIONS)) {
      // have something to work with, or at least clean
      if (combined.contains(SharedDataKeys.EXTENSIONS) || combined.contains(LoadUnloadDataKeyAggregator.LOAD_EXTENSIONS)) {
        val extensions = SharedDataKeys.EXTENSIONS.get(combined)
        val loadExtensions = LoadUnloadDataKeyAggregator.LOAD_EXTENSIONS.get(combined)
        val unloadExtensions = LoadUnloadDataKeyAggregator.UNLOAD_EXTENSIONS.get(combined)

        if (!loadExtensions.isEmpty || !unloadExtensions.isEmpty && !extensions.isEmpty) {
          val resolvedExtensions = new ju.LinkedHashSet[Extension](extensions)
          resolvedExtensions.addAll(loadExtensions)
          resolvedExtensions.removeIf((extension: Extension) => unloadExtensions.contains(extension.getClass))
          combined.toMutable
            .remove(LoadUnloadDataKeyAggregator.LOAD_EXTENSIONS)
            .remove(LoadUnloadDataKeyAggregator.UNLOAD_EXTENSIONS)
            .set(SharedDataKeys.EXTENSIONS, new ju.ArrayList[Extension](resolvedExtensions).asInstanceOf[ju.Collection[Extension]])
            .toImmutable
        } else {
          combined.toMutable.remove(LoadUnloadDataKeyAggregator.LOAD_EXTENSIONS).remove(LoadUnloadDataKeyAggregator.UNLOAD_EXTENSIONS)
        }
      } else {
        combined.toMutable.remove(LoadUnloadDataKeyAggregator.LOAD_EXTENSIONS).remove(LoadUnloadDataKeyAggregator.UNLOAD_EXTENSIONS)
      }
    } else {
      combined
    }
  }

  override def aggregateActions(combined: DataHolder, other: DataHolder, overrides: DataHolder): DataHolder = {
    var result = combined
    if (other.contains(LoadUnloadDataKeyAggregator.LOAD_EXTENSIONS) && overrides.contains(LoadUnloadDataKeyAggregator.LOAD_EXTENSIONS)) {
      // have to combine these
      val loadExtensions = new ju.ArrayList[Extension](LoadUnloadDataKeyAggregator.LOAD_EXTENSIONS.get(other))
      loadExtensions.addAll(LoadUnloadDataKeyAggregator.LOAD_EXTENSIONS.get(overrides))
      result = result.toMutable.set(LoadUnloadDataKeyAggregator.LOAD_EXTENSIONS, loadExtensions.asInstanceOf[ju.Collection[Extension]])
    }

    if (other.contains(LoadUnloadDataKeyAggregator.UNLOAD_EXTENSIONS) && overrides.contains(LoadUnloadDataKeyAggregator.UNLOAD_EXTENSIONS)) {
      // have to combine these
      val unloadExtensions = new ju.ArrayList[Class[? <: Extension]](LoadUnloadDataKeyAggregator.UNLOAD_EXTENSIONS.get(other))
      unloadExtensions.addAll(LoadUnloadDataKeyAggregator.UNLOAD_EXTENSIONS.get(overrides))
      result = result.toMutable.set(LoadUnloadDataKeyAggregator.UNLOAD_EXTENSIONS, unloadExtensions.asInstanceOf[ju.Collection[Class[? <: Extension]]])
    }
    result
  }

  override def clean(combined: DataHolder): DataHolder = {
    if (combined.contains(LoadUnloadDataKeyAggregator.LOAD_EXTENSIONS) || combined.contains(LoadUnloadDataKeyAggregator.UNLOAD_EXTENSIONS)) {
      combined.toMutable.remove(LoadUnloadDataKeyAggregator.LOAD_EXTENSIONS).remove(LoadUnloadDataKeyAggregator.UNLOAD_EXTENSIONS)
    } else {
      combined
    }
  }

  override def invokeAfterSet(): Nullable[Set[Class[?]]] = Nullable.empty
}

object LoadUnloadDataKeyAggregator {

  val UNLOAD_EXTENSIONS: DataKey[ju.Collection[Class[? <: Extension]]] =
    new DataKey[ju.Collection[Class[? <: Extension]]]("UNLOAD_EXTENSIONS", Collections.emptyList[Class[? <: Extension]]())

  val LOAD_EXTENSIONS: DataKey[ju.Collection[Extension]] =
    new DataKey[ju.Collection[Extension]]("LOAD_EXTENSIONS", Collections.emptyList[Extension]())

  private val INSTANCE: LoadUnloadDataKeyAggregator = new LoadUnloadDataKeyAggregator()

  // Register on class load
  DataSet.registerDataKeyAggregator(INSTANCE)
}
