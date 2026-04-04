/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-test-util/src/main/java/com/vladsch/flexmark/test/util/SpecExampleProcessor.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package test
package util

import ssg.md.Nullable
import ssg.md.test.util.spec.SpecExample
import ssg.md.util.data.DataHolder

import scala.language.implicitConversions

trait SpecExampleProcessor {

  /** Customize options for an example
    *
    * @param option
    *   name of the options set to use
    * @return
    *   options or null to use default
    */
  def options(option: String): Nullable[DataHolder]

  /** Allows tests to modify example during reading (DumpSpecReader)
    *
    * @param example
    *   example as it is in the test or spec file
    * @return
    *   modified example if needed
    */
  def checkExample(example: SpecExample): SpecExample = example

  /** Get spec renderer for an example spec
    *
    * @param example
    *   spec example
    * @param exampleOptions
    *   example custom options
    * @return
    *   spec renderer for given example and options
    */
  def getSpecExampleRenderer(example: SpecExample, exampleOptions: Nullable[DataHolder]): SpecExampleRenderer

  /** Called by DumpSpecReader for each example when processing full test spec
    *
    * @param exampleRenderer
    *   example renderer
    * @param exampleParse
    *   example parse state
    * @param exampleOptions
    *   example options
    * @param ignoredTestCase
    *   true if ignored example
    * @param html
    *   html used for comparison to expected html
    * @param ast
    *   ast used for comparison to expected ast
    */
  def addFullSpecExample(
    exampleRenderer: SpecExampleRenderer,
    exampleParse:    SpecExampleParse,
    exampleOptions:  Nullable[DataHolder],
    ignoredTestCase: Boolean,
    html:            String,
    ast:             Nullable[String]
  ): Unit
}
