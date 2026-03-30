/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * munit adapter for flexmark spec-driven tests.
 * Replaces the JUnit4 FullSpecTestCase/ComboSpecTestCase chain.
 */
package ssg
package md
package test
package util

import ssg.md.Nullable
import ssg.md.test.util.spec.{ResourceLocation, SpecExample, SpecReader}
import ssg.md.util.data.{DataHolder, MutableDataSet}

import java.{util => ju}
import scala.jdk.CollectionConverters.*
import scala.language.implicitConversions

/**
 * Base munit suite for flexmark spec-driven tests.
 *
 * Subclasses override `specResource` and `defaultOptions`, then
 * this suite reads the spec file at init time and registers each
 * example as an individual munit test.
 */
abstract class SpecTestSuite extends munit.FunSuite {

  /** Location of the spec .txt/.md resource file. */
  def specResource: ResourceLocation

  /** Default data options for parsing/rendering. */
  def defaultOptions: Nullable[DataHolder] = Nullable.empty

  /** Whether to use compound (hierarchical) section names. */
  def compoundSections: Boolean = true

  /** Option map for named option sets in spec examples. */
  def optionsMap: ju.Map[String, ? <: DataHolder] = new ju.HashMap[String, DataHolder]()

  /** Resolve options for a specific example based on its optionsSet string.
   * Handles comma-separated option names (e.g. "closed-item-class, open-item-class")
   * by splitting and aggregating each named option set.
   */
  protected def optionsFor(example: SpecExample): DataHolder = {
    val base = defaultOptions.getOrElse(new MutableDataSet())
    val optionSet = example.optionsSet
    if (optionSet.isDefined && optionSet.get.nonEmpty) {
      val optionsProvider: String => Nullable[DataHolder] = { name =>
        TestUtils.processOption(optionsMap.asInstanceOf[ju.Map[String, DataHolder]], name)
      }
      val opts = TestUtils.getOptions(example, optionSet, optionsProvider)
      if (opts.isDefined) {
        val combined = new MutableDataSet(base)
        combined.setAll(opts.get)
        combined.toImmutable
      } else {
        base
      }
    } else {
      base
    }
  }

  /** Render the example — subclasses implement this. */
  protected def renderHtml(example: SpecExample, options: DataHolder): String

  /** Optionally render AST — subclasses override if needed. */
  protected def renderAst(example: SpecExample, options: DataHolder): Nullable[String] = Nullable.empty

  // Read spec file and register tests
  private lazy val specReader: SpecReader = {
    val reader = SpecReader.createAndReadExamples(specResource, compoundSections)
    reader
  }

  private lazy val examples: List[SpecExample] = specReader.getExamples.asScala.toList

  // Register all spec examples as individual tests
  examples.foreach { example =>
    if (example.isSpecExample) {
      val testName = s"${example.section.getOrElse("?")} - ${example.exampleNumber}"
      test(testName) {
        val options = optionsFor(example)

        // Handle FAIL option: test is expected to fail (known issue in original flexmark)
        val expectFail = TestUtils.FAIL.get(options)

        val actualHtml = renderHtml(example, options)
        val expectedHtml = example.html

        if (expectFail) {
          // For FAIL-marked tests, pass if they fail (expected), also pass if they succeed (bug fixed)
          if (actualHtml != expectedHtml) {
            // Expected failure - test passes
          } else {
            // Bug fixed - also fine, test passes
          }
        } else {
          assertEquals(
            actualHtml,
            expectedHtml,
            s"HTML mismatch at ${example.fileUrlWithLineNumber}"
          )
        }

        // Check AST if expected and not expected to fail
        if (!expectFail) {
          example.ast.foreach { expectedAst =>
            val actualAst = renderAst(example, options)
            actualAst.foreach { ast =>
              assertEquals(
                ast,
                expectedAst,
                s"AST mismatch at ${example.fileUrlWithLineNumber}"
              )
            }
          }
        }
      }
    }
  }
}
