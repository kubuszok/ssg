/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-test-util/src/main/java/com/vladsch/flexmark/test/util/SettableExtractedInstance.java Original: Copyright (c) 2016-2023 Vladimir Schneider Original license: BSD-2-Clause */
package ssg
package md
package test
package util

import ssg.md.Nullable
import ssg.md.util.data.{ DataHolder, DataKey }

import java.util.function.Consumer
import scala.language.implicitConversions

/** Instance based on aggregated options used for spec test settings which itself is part of a settable instance
  *
  * For example: Rendering profile contains HTML, Parser and CSS settings. Rendering profile and its contained settings can be set by spec options. In order to handle this properly rendering profile
  * settable instance is defined with HTML, Parser and CSS extracted settable instances. thus allowing setting options on contained instances directly or through the rendering profile container, while
  * keeping the results consistent.
  *
  * @tparam T
  *   type for the container setting
  * @tparam S
  *   type for the setting
  */
final class SettableExtractedInstance[T, S](
  private val myConsumerKey:   DataKey[Consumer[S]],
  private val myDataExtractor: T => S
) {

  def aggregate(instance: T, dataHolder: DataHolder): Unit =
    if (dataHolder.contains(myConsumerKey)) {
      myConsumerKey.get(dataHolder).accept(myDataExtractor(instance))
    }

  def aggregateActions(dataHolder: DataHolder, other: Nullable[DataHolder], overrides: Nullable[DataHolder]): DataHolder = {
    val hasOther     = other.exists(_.contains(myConsumerKey))
    val hasOverrides = overrides.exists(_.contains(myConsumerKey))

    if (hasOther && hasOverrides) {
      // both, need to combine
      val otherSetter     = myConsumerKey.get(other.get)
      val overridesSetter = myConsumerKey.get(overrides.get)
      dataHolder.toMutable.set(myConsumerKey, otherSetter.andThen(overridesSetter))
    } else {
      dataHolder
    }
  }
}
