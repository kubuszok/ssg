/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-test-util/src/main/java/com/vladsch/flexmark/test/util/SettableInstance.java Original: Copyright (c) 2016-2023 Vladimir Schneider Original license: BSD-2-Clause */
package ssg
package md
package test
package util

import ssg.md.Nullable
import ssg.md.util.data._

import java.util.function.Consumer
import java.{ util => ju }
import scala.language.implicitConversions

/** A one-stop-shop class for setting test instances.
  *
  * A single consumer is called with a DataHolder-registered DataKey key, while extracted instances delegate to their own SettableExtractedInstance consumers. Using one class for both keeps the test
  * API uniform and avoids separate handling for main-test-case settings vs settings that propagate to keeping the results consistent.
  *
  * @tparam T
  *   type for the setting
  */
final class SettableInstance[T](
  private val myConsumerKey:              DataKey[Consumer[T]],
  private val myExtractedInstanceSetters: Nullable[ju.Collection[SettableExtractedInstance[T, ?]]]
) {

  def this(consumerKey: DataKey[Consumer[T]], extractedInstanceSetters: ju.Collection[SettableExtractedInstance[T, ?]]) =
    this(
      consumerKey,
      if (extractedInstanceSetters.size == 0) Nullable.empty[ju.Collection[SettableExtractedInstance[T, ?]]]
      else Nullable(extractedInstanceSetters)
    )

  def this(consumerKey: DataKey[Consumer[T]]) =
    this(consumerKey, Nullable.empty[ju.Collection[SettableExtractedInstance[T, ?]]])

  def setInstanceData(instance: T, dataHolder: Nullable[DataHolder]): T = {
    dataHolder.foreach { dh =>
      if (dh.contains(myConsumerKey)) {
        myConsumerKey.get(dh).accept(instance)
      }
    }
    myExtractedInstanceSetters.foreach { setters =>
      val iter = setters.iterator()
      while (iter.hasNext())
        dataHolder.foreach(dh => iter.next().aggregate(instance, dh))
    }
    instance
  }
}
