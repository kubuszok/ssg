/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-test-util/src/main/java/com/vladsch/flexmark/test/util/ExampleOption.java Original: Copyright (c) 2016-2023 Vladimir Schneider Original license: BSD-2-Clause */
package ssg
package md
package test
package util

import ssg.md.Nullable
import ssg.md.util.sequence.BasedSequence

import java.{ util => ju }
import scala.language.implicitConversions

final class ExampleOption private (
  val optionText:   BasedSequence,
  val optionName:   BasedSequence,
  val customParams: BasedSequence,
  val isBuiltIn:    Boolean,
  val isDisabled:   Boolean,
  val isCustom:     Boolean,
  val isValid:      Boolean
) {

  def getOptionText: String = optionText.toString

  def getOptionName: String = optionName.toString

  def getCustomParams: Nullable[String] =
    if (customParams.isNull) Nullable.empty
    else Nullable(customParams.toString)

  def isIgnore: Boolean = isBuiltIn && optionName.equals(TestUtils.IGNORE_OPTION_NAME)

  def isFail: Boolean = isBuiltIn && optionName.equals(TestUtils.FAIL_OPTION_NAME)

  def isTimed: Boolean = isBuiltIn && optionName.equals(TestUtils.TIMED_OPTION_NAME)

  def isTimedIterations: Boolean = isBuiltIn && optionName.equals(TestUtils.TIMED_ITERATIONS_OPTION_NAME)

  def isEmbedTimed: Boolean = isBuiltIn && optionName.equals(TestUtils.EMBED_TIMED_OPTION_NAME)

  def isFileEol: Boolean = isBuiltIn && optionName.equals(TestUtils.FILE_EOL_OPTION_NAME)

  def isNoFileEol: Boolean = isBuiltIn && optionName.equals(TestUtils.NO_FILE_EOL_OPTION_NAME)
}

object ExampleOption {

  private val BUILT_IN_OPTIONS_SET: ju.HashSet[String] = {
    val set = new ju.HashSet[String]()
    set.add(TestUtils.EMBED_TIMED_OPTION_NAME)
    set.add(TestUtils.FAIL_OPTION_NAME)
    set.add(TestUtils.FILE_EOL_OPTION_NAME)
    set.add(TestUtils.IGNORE_OPTION_NAME)
    set.add(TestUtils.NO_FILE_EOL_OPTION_NAME)
    set.add(TestUtils.TIMED_ITERATIONS_OPTION_NAME)
    set.add(TestUtils.TIMED_OPTION_NAME)
    set
  }

  private def build(option: CharSequence): ExampleOption = {
    var optionName:   BasedSequence = BasedSequence.NULL
    var customParams: BasedSequence = BasedSequence.NULL
    var isDisabled = false
    val optionText = BasedSequence.of(option)

    val pos = optionText.indexOf("[")
    if (pos > 0 && pos < optionText.length && optionText.endsWith("]")) {
      // parameterized, see if there is a handler defined for it
      optionName = optionText.subSequence(0, pos)
      customParams = optionText.subSequence(pos + 1, optionText.length - 1)
    } else {
      optionName = optionText
      customParams = BasedSequence.NULL
    }

    if (optionName.startsWith(TestUtils.DISABLED_OPTION_PREFIX)) {
      optionName = optionName.subSequence(1)
      isDisabled = true
    }

    new ExampleOption(
      optionText,
      optionName,
      customParams,
      BUILT_IN_OPTIONS_SET.contains(optionName.toString) && customParams.isNull,
      isDisabled,
      customParams.isNotNull,
      !optionName.isBlank()
    )
  }

  def of(optionText: CharSequence): ExampleOption = build(optionText)

  private val BUILT_IN_OPTIONS_MAP: ju.HashMap[String, ExampleOption] = {
    val map = new ju.HashMap[String, ExampleOption]()
    map.put(TestUtils.EMBED_TIMED_OPTION_NAME, build(TestUtils.EMBED_TIMED_OPTION_NAME))
    map.put(TestUtils.FAIL_OPTION_NAME, build(TestUtils.FAIL_OPTION_NAME))
    map.put(TestUtils.FILE_EOL_OPTION_NAME, build(TestUtils.FILE_EOL_OPTION_NAME))
    map.put(TestUtils.IGNORE_OPTION_NAME, build(TestUtils.IGNORE_OPTION_NAME))
    map.put(TestUtils.NO_FILE_EOL_OPTION_NAME, build(TestUtils.NO_FILE_EOL_OPTION_NAME))
    // NOTE: this one is not an option to use in spec example but set in data holder
    //    map.put(TestUtils.TIMED_ITERATIONS_OPTION_NAME, build(TestUtils.TIMED_ITERATIONS_OPTION_NAME))
    map.put(TestUtils.TIMED_OPTION_NAME, build(TestUtils.TIMED_OPTION_NAME))
    map
  }

  def getBuiltInOptions: ju.HashMap[String, ExampleOption] =
    new ju.HashMap[String, ExampleOption](BUILT_IN_OPTIONS_MAP)
}
