/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-test-util/src/main/java/com/vladsch/flexmark/test/util/ComboSpecTestCase.java Original: Copyright (c) 2016-2023 Vladimir Schneider Original license: BSD-2-Clause */
package ssg
package md
package test
package util

import ssg.md.Nullable
import ssg.md.test.util.spec.{ ResourceLocation, SpecExample }
import ssg.md.util.data._

import java.{ util => ju }
import java.util.function.BiFunction
import scala.language.implicitConversions

// JUnit 4: @RunWith(Parameterized.class) — will need adaptation to munit later
abstract class ComboSpecTestCase(
  protected val example: SpecExample,
  optionMap:             Nullable[ju.Map[String, ? <: DataHolder]],
  defaultOptions:        DataHolder*
) extends FullSpecTestCase {

  val CUSTOM_OPTION: DataKey[BiFunction[String, String, DataHolder]] = TestUtils.CUSTOM_OPTION

  protected val optionsMap:       ju.Map[String, DataHolder] = new ju.HashMap[String, DataHolder]()
  protected val myDefaultOptions: Nullable[DataHolder]       = TestUtils.combineDefaultOptions(
    if (defaultOptions.isEmpty) Nullable.empty else Nullable(defaultOptions.toArray)
  )

  optionMap.foreach(m => optionsMap.putAll(m))

  override protected def compoundSections(): Boolean = true

  override def options(option: String): Nullable[DataHolder] =
    TestUtils.processOption(optionsMap, option)

  override protected def specResourceLocation: ResourceLocation = example.resourceLocation

  // JUnit 4: @Test — will need adaptation to munit later
  override def testSpecExample(): Unit =
    if (example.isFullSpecExample) {
      super.testSpecExample()
    } else {
      assertRendering(example)
    }
}

object ComboSpecTestCase {

  def optionsMaps(other: Nullable[ju.Map[String, ? <: DataHolder]], overrides: Nullable[ju.Map[String, ? <: DataHolder]]): Nullable[ju.Map[String, ? <: DataHolder]] =
    TestUtils.optionsMaps(other, overrides)

  def dataHolders(other: Nullable[DataHolder], overrides: Nullable[Array[DataHolder]]): Nullable[Array[DataHolder]] =
    TestUtils.dataHolders(other, overrides)

  def aggregate(other: Nullable[DataHolder], overrides: Nullable[DataHolder]): DataHolder =
    DataSet.aggregate(other, overrides)

  def getTestData(location: ResourceLocation): ju.List[Array[AnyRef]] =
    TestUtils.getTestData(location)
}
