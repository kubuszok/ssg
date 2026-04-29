/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * CommonMark spec conformance test — parses the original CommonMark spec.txt files (v0.28, v0.29, v0.30) and measures how many examples the ssg-md parser/renderer handles correctly.
 *
 * This test is informational: it reports pass/fail counts and conformance percentage without failing the build on individual mismatches (flexmark deliberately deviates from CommonMark in several
 * areas). */
package ssg
package md
package core
package test

import ssg.md.html.HtmlRenderer
import ssg.md.parser.Parser
import ssg.md.test.util.spec.{ ResourceLocation, SpecExample, SpecReader }
import ssg.md.util.data.{ DataHolder, MutableDataSet }

import scala.jdk.CollectionConverters.*
import scala.language.implicitConversions

/** CommonMark spec.txt conformance test.
  *
  * Reads the unmodified CommonMark spec files (v0.28, v0.29, v0.30), parses each example through [[Parser]] and [[HtmlRenderer]] with default CommonMark settings, and reports the conformance
  * percentage.
  *
  * Individual example mismatches do NOT fail the build — flexmark (and therefore ssg-md) intentionally deviates from strict CommonMark in some areas. The summary test asserts only that the
  * conformance rate stays above a minimum threshold, so regressions are caught without blocking on known deviations.
  */
final class CommonMarkSpecConformanceTest extends munit.FunSuite {

  import CommonMarkSpecConformanceTest.*

  test("CommonMark 0.28 conformance summary") {
    val result = runSpec("0.28", SPEC_028_RESOURCE)
    printSummary("0.28", result)
    // v0.28 is the target spec for flexmark — expect full conformance
    assert(result.pct >= 99.0, s"Conformance dropped below 99%: ${result.pct}%")
  }

  test("CommonMark 0.28 per-section conformance") {
    val result = runSpec("0.28", SPEC_028_RESOURCE)
    printPerSection("0.28", result)
  }

  test("CommonMark 0.29 conformance summary") {
    val result = runSpec("0.29", SPEC_029_RESOURCE)
    printSummary("0.29", result)
    // v0.29 may have changes beyond what flexmark targets — use a lower threshold
    assert(result.pct >= 50.0, s"Conformance dropped below 50%: ${result.pct}%")
  }

  test("CommonMark 0.29 per-section conformance") {
    val result = runSpec("0.29", SPEC_029_RESOURCE)
    printPerSection("0.29", result)
  }

  test("CommonMark 0.30 conformance summary") {
    val result = runSpec("0.30", SPEC_030_RESOURCE)
    printSummary("0.30", result)
    // v0.30 may have further changes — use a lower threshold
    assert(result.pct >= 50.0, s"Conformance dropped below 50%: ${result.pct}%")
  }

  test("CommonMark 0.30 per-section conformance") {
    val result = runSpec("0.30", SPEC_030_RESOURCE)
    printPerSection("0.30", result)
  }
}

object CommonMarkSpecConformanceTest {

  private val SPEC_028_RESOURCE = "/ssg/md/test/specs/spec.0.28.txt"
  private val SPEC_029_RESOURCE = "/ssg/md/test/specs/spec.0.29.txt"
  private val SPEC_030_RESOURCE = "/ssg/md/test/specs/spec.0.30.txt"

  /** Parser/renderer options matching the ComboCoreSpecTest baseline: INDENT_SIZE=0, percent-encode URLs, no directional punctuations.
    */
  private val OPTIONS: DataHolder =
    new MutableDataSet().set(HtmlRenderer.INDENT_SIZE, 0).set(Parser.INLINE_DELIMITER_DIRECTIONAL_PUNCTUATIONS, false).set(HtmlRenderer.PERCENT_ENCODE_URLS, true).toImmutable

  private val parser:   Parser       = Parser.builder(OPTIONS).build()
  private val renderer: HtmlRenderer = HtmlRenderer.builder(OPTIONS).build()

  final case class ExampleResult(
    section:       String,
    exampleNumber: Int,
    lineNumber:    Int,
    passed:        Boolean
  )

  final case class SpecResult(
    total:    Int,
    passed:   Int,
    failed:   Int,
    pct:      Double,
    examples: List[ExampleResult]
  )

  private def loadExamples(resource: String): List[SpecExample] = {
    val location = ResourceLocation.of(classOf[CommonMarkSpecConformanceTest], resource)
    val reader   = SpecReader.createAndReadExamples(location, false)
    reader.getExamples.asScala.toList.filter(_.isSpecExample)
  }

  /** Run a single example: parse source, render HTML, compare to expected. */
  private def runExample(example: SpecExample): Boolean = {
    val source   = example.source
    val document = parser.parse(source)
    val actual   = renderer.render(document)
    actual == example.html
  }

  /** Run all examples from a spec resource and return a summary. */
  private def runSpec(version: String, resource: String): SpecResult = {
    val examples = loadExamples(resource)
    val results  = examples.map { example =>
      ExampleResult(
        section = example.section.getOrElse("Unknown"),
        exampleNumber = example.exampleNumber,
        lineNumber = example.lineNumber,
        passed = runExample(example)
      )
    }
    val total  = results.size
    val passed = results.count(_.passed)
    val failed = total - passed
    val pct    = if (total > 0) passed.toDouble / total * 100.0 else 0.0
    SpecResult(total, passed, failed, pct, results)
  }

  private def printSummary(version: String, result: SpecResult): Unit = {
    val report = new StringBuilder()
    report.append(s"\n=== CommonMark $version Spec Conformance ===\n")
    report.append(s"Total examples: ${result.total}\n")
    report.append(s"Passed:         ${result.passed}\n")
    report.append(s"Failed:         ${result.failed}\n")
    report.append(f"Conformance:    ${result.pct}%.1f%%\n")

    val failures = result.examples.filterNot(_.passed)
    if (failures.nonEmpty) {
      report.append("First failures (up to 20):\n")
      failures.take(20).foreach { f =>
        report.append(s"  ${f.section} #${f.exampleNumber} (line ${f.lineNumber})\n")
      }
      if (failures.size > 20) {
        report.append(s"  ... and ${failures.size - 20} more\n")
      }
    }
    report.append("========================================\n")
    println(report.toString())
  }

  private def printPerSection(version: String, result: SpecResult): Unit = {
    val bySection      = result.examples.groupBy(_.section)
    val sectionResults = bySection.toList.sortBy(_._1).map { case (section, sectionExamples) =>
      val sectionTotal  = sectionExamples.size
      val sectionPassed = sectionExamples.count(_.passed)
      val sectionPct    = if (sectionTotal > 0) sectionPassed.toDouble / sectionTotal * 100.0 else 0.0
      (section, sectionTotal, sectionPassed, sectionPct)
    }

    val report = new StringBuilder()
    report.append(s"\n=== CommonMark $version Per-Section Conformance ===\n")
    report.append(f"${"Section"}%-45s ${"Total"}%6s ${"Pass"}%6s ${"Pct"}%7s\n")
    report.append("-" * 66 + "\n")
    sectionResults.foreach { case (section, total, pass, pct) =>
      val truncated = if (section.length > 44) section.take(41) + "..." else section
      report.append(f"$truncated%-45s $total%6d $pass%6d $pct%6.1f%%\n")
    }
    report.append("=" * 66 + "\n")
    println(report.toString())
  }
}
