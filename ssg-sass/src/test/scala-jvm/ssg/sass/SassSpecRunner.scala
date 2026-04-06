/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * JVM-only — sass-spec compliance runner.
 *
 * Walks `original-src/sass-spec/spec/` for `input.scss` files (both
 * loose files on disk and entries inside HRX archives), compiles each
 * via `Compile.compileString`, compares output to the adjacent
 * `output.css`, and prints a summary plus a per-failure log to
 * `ssg-sass/target/sass-spec-failures.txt`.
 *
 * HRX tests that reference sibling files (e.g. `@use 'other'`) are
 * skipped — we only run self-contained cases (single input.scss +
 * output.css or error) since we do not set up an in-memory importer
 * for the archive.
 *
 * This is a measurement harness, not an assertion harness: the single
 * munit test always passes. Enable with -Dssg.sass.spec=1.
 */
package ssg
package sass

import java.nio.charset.StandardCharsets
import java.nio.file.{ Files, Path, Paths }

import scala.jdk.CollectionConverters.*
import scala.util.{ Failure, Success, Try }

import ssg.sass.visitor.OutputStyle

final class SassSpecRunner extends munit.FunSuite {

  import SassSpecRunner.*

  override def munitTimeout: scala.concurrent.duration.Duration =
    scala.concurrent.duration.Duration(30, "minutes")

  test("sass-spec compliance measurement".tag(SassSpecTag)) {
    val specRoot = locateSpecRoot()
    assume(specRoot.isDefined, s"sass-spec not found at $ExpectedSpecRoot — skipping")
    val root = specRoot.get

    val cases = collectCases(root)
    println(s"sass-spec: collected ${cases.size} self-contained test cases")
    val results = cases.map(runCase)

    val total    = results.size
    val passing  = results.count(_.outcome == Outcome.Pass)
    val mismatch = results.count(_.outcome == Outcome.Mismatch)
    val errored  = results.count(_.outcome == Outcome.Error)
    val missing  = results.count(_.outcome == Outcome.MissingExpected)
    val expectedErrorOk = results.count(_.outcome == Outcome.ExpectedErrorOk)
    val expectedErrorMissed = results.count(_.outcome == Outcome.ExpectedErrorMissed)

    val pct = if (total == 0) 0.0 else (passing + expectedErrorOk).toDouble * 100.0 / total.toDouble
    val summary =
      f"""|sass-spec: Total=$total%d  Passing=${passing + expectedErrorOk}%d (${pct}%.1f%%)
          |  exact-output-pass = $passing
          |  expected-error-ok = $expectedErrorOk
          |  output-mismatch   = $mismatch
          |  compile-error     = $errored
          |  expected-error-not-raised = $expectedErrorMissed
          |  missing-expected  = $missing
          |""".stripMargin
    println(summary)

    // Write report beside the spec root's sibling ssg-sass/target dir.
    // `root` is .../original-src/sass-spec/spec, so repoRoot = root/../../..
    val repoRoot = root.toAbsolutePath.getParent.getParent.getParent
    val outDir   = repoRoot.resolve("ssg-sass").resolve("target")
    Files.createDirectories(outDir)
    val report = outDir.resolve("sass-spec-failures.txt")
    println(s"Writing report to: ${report.toAbsolutePath}")

    val sb = new StringBuilder
    sb.append(summary).append('\n')
    sb.append("# Per-failure details (first 2000 failures)\n\n")
    val failures = results.filter(r => r.outcome != Outcome.Pass && r.outcome != Outcome.ExpectedErrorOk)
    failures.take(2000).foreach { r =>
      sb.append("## ").append(r.relPath).append('\n')
      sb.append("outcome: ").append(r.outcome).append('\n')
      sb.append("category: ").append(r.category).append('\n')
      if (r.detail.nonEmpty) {
        sb.append("detail:\n")
        r.detail.linesIterator.take(20).foreach(l => sb.append("  ").append(l).append('\n'))
      }
      sb.append('\n')
    }
    Files.write(report, sb.toString.getBytes(StandardCharsets.UTF_8))
    println(s"Wrote ${failures.size} failure entries to $report")

    // Category breakdown for the report.
    val byCategory = failures.groupBy(_.category).view.mapValues(_.size).toList.sortBy(-_._2)
    println("\n# Failure categories (by count)")
    byCategory.foreach { case (cat, n) => println(f"  $n%6d  $cat") }

    // Always pass — this is measurement.
    assert(true)
  }
}

object SassSpecRunner {

  val SassSpecTag: munit.Tag = new munit.Tag("SassSpec")

  val ExpectedSpecRoot: Path =
    Paths.get("original-src", "sass-spec", "spec")

  enum Outcome { case Pass, Mismatch, Error, MissingExpected, ExpectedErrorOk, ExpectedErrorMissed }

  final case class Result(
    relPath:  String,
    outcome:  Outcome,
    category: String,
    detail:   String
  )

  /** Try a few candidate roots in case cwd is the module or the repo root. */
  def locateSpecRoot(): Option[Path] = {
    val candidates = List(
      Paths.get("original-src", "sass-spec", "spec"),
      Paths.get("..", "original-src", "sass-spec", "spec"),
      Paths.get("/Users/dev/Workspaces/GitHub/ssg/original-src/sass-spec/spec")
    )
    candidates.find(Files.isDirectory(_))
  }

  /** A single test case — source, optional expected output, optional
    * expected error. `origin` is a human-readable location (file path
    * or "archive.hrx!sub/path").
    */
  final case class TestCase(
    origin:        String,
    source:        String,
    expectedOut:   Option[String],
    expectedError: Option[String]
  )

  def collectCases(root: Path): List[TestCase] = {
    val buf = scala.collection.mutable.ListBuffer.empty[TestCase]
    val stream = Files.walk(root)
    try {
      stream.iterator().asScala.foreach { p =>
        if (Files.isRegularFile(p)) {
          val name = p.getFileName.toString
          if (name == "input.scss") {
            loadLooseCase(root, p).foreach(buf += _)
          } else if (name.endsWith(".hrx")) {
            loadHrxCases(root, p).foreach(buf += _)
          }
        }
      }
    } finally stream.close()
    buf.toList
  }

  private def loadLooseCase(root: Path, input: Path): Option[TestCase] = {
    val dir        = input.getParent
    val rel        = root.toAbsolutePath.relativize(input.toAbsolutePath).toString
    val outFile    = dir.resolve("output.css")
    val errFile    = dir.resolve("error")
    val source     = Try(new String(Files.readAllBytes(input), StandardCharsets.UTF_8)).toOption
    val expectedO  = if (Files.isRegularFile(outFile)) Try(new String(Files.readAllBytes(outFile), StandardCharsets.UTF_8)).toOption else None
    val expectedE  = if (Files.isRegularFile(errFile)) Try(new String(Files.readAllBytes(errFile), StandardCharsets.UTF_8)).toOption else None
    source.map(s => TestCase(rel, s, expectedO, expectedE))
  }

  /** Parse an HRX archive into test cases. Only emits self-contained
    * cases: one `.../input.scss` where the only sibling `.scss`/`.sass`
    * file in the same directory is that input. This avoids requiring
    * an in-memory importer for cross-file tests.
    */
  def loadHrxCases(root: Path, archive: Path): List[TestCase] = {
    val raw = Try(new String(Files.readAllBytes(archive), StandardCharsets.UTF_8)).toOption.getOrElse("")
    if (raw.isEmpty) Nil
    else {
    val entries = parseHrx(raw)
    // group entries by their parent directory inside the archive
    val byDir: Map[String, Map[String, String]] =
      entries.groupBy { case (p, _) =>
        val i = p.lastIndexOf('/')
        if (i < 0) "" else p.substring(0, i)
      }.view.mapValues(_.map { case (p, c) =>
        val i = p.lastIndexOf('/')
        (if (i < 0) p else p.substring(i + 1)) -> c
      }.toMap).toMap

    val archiveRel = root.toAbsolutePath.relativize(archive.toAbsolutePath).toString
    byDir.iterator.flatMap { case (dir, files) =>
      files.get("input.scss") match {
        case Some(src) =>
          // only consider self-contained: no other .scss/.sass siblings
          val otherSass = files.keysIterator.exists { n =>
            n != "input.scss" && (n.endsWith(".scss") || n.endsWith(".sass"))
          }
          // also require: no @use/@forward/@import referencing non-builtin modules
          val hasExternalImport = ExternalImportRegex.findFirstIn(src).isDefined
          if (otherSass || hasExternalImport) Iterator.empty
          else {
            val out = files.get("output.css")
            val err = files.get("error")
            val origin = s"$archiveRel!${if (dir.isEmpty) "<root>" else dir}"
            Iterator.single(TestCase(origin, src, out, err))
          }
        case None => Iterator.empty
      }
    }.toList
    }
  }

  /** Matches `@use`/`@forward`/`@import` of a non-sass: URL, e.g.
    * `@use 'foo'` or `@import "bar/baz";`. Built-in `sass:*` modules
    * are fine.
    */
  private val ExternalImportRegex =
    """@(?:use|forward|import)\s+["']((?!sass:)[^"']+)["']""".r

  /** Parse HRX archive. HRX uses `<===> path` as a section header and
    * `<===>` alone as a section terminator. We split into (path,
    * content) pairs.
    */
  def parseHrx(raw: String): List[(String, String)] = {
    val buf = scala.collection.mutable.ListBuffer.empty[(String, String)]
    val lines = raw.split("\n", -1)
    var i = 0
    var currentPath: Option[String] = None
    val body = new StringBuilder
    def flush(): Unit = {
      currentPath.foreach { path =>
        val s = body.toString
        val trimmed = if (s.endsWith("\n")) s.dropRight(1) else s
        buf += (path -> trimmed)
      }
      currentPath = None
      body.clear()
    }
    while (i < lines.length) {
      val line = lines(i)
      if (line.startsWith("<===>")) {
        flush()
        val rest = line.substring(5).trim
        if (rest.nonEmpty) currentPath = Some(rest)
      } else if (currentPath.isDefined) {
        if (body.nonEmpty) body.append('\n')
        body.append(line)
      }
      i += 1
    }
    flush()
    // Filter out pure-text dividers (e.g. the "===...===" visual rules)
    buf.filter { case (p, _) => !p.isEmpty }.toList
  }

  def runCase(tc: TestCase): Result = {
    val compiled: Try[CompileResult] =
      try Success(Compile.compileString(tc.source, style = OutputStyle.Expanded))
      catch {
        case t: Throwable => Failure(t)
      }
    (compiled, tc.expectedOut, tc.expectedError) match {
      case (Success(cr), Some(expected), _) =>
        val actual = cr.css
        if (normalize(actual) == normalize(expected)) {
          Result(tc.origin, Outcome.Pass, "pass", "")
        } else if (actual.trim == expected.trim) {
          Result(tc.origin, Outcome.Pass, "pass-whitespace", "")
        } else {
          val cat =
            if (stripWs(actual) == stripWs(expected)) "whitespace-only"
            else if (actual.trim.isEmpty && expected.trim.nonEmpty) "empty-output"
            else "wrong-output"
          Result(tc.origin, Outcome.Mismatch, cat, diffPreview(expected, actual))
        }
      case (Success(_), None, Some(_)) =>
        Result(tc.origin, Outcome.ExpectedErrorMissed, "expected-error-not-raised", "")
      case (Success(_), None, None) =>
        Result(tc.origin, Outcome.MissingExpected, "no-output-no-error", "")
      case (Failure(e), _, Some(_)) =>
        Result(tc.origin, Outcome.ExpectedErrorOk, "expected-error-ok", shortMessage(e))
      case (Failure(e), _, None) =>
        Result(tc.origin, Outcome.Error, classifyError(e), shortMessage(e))
    }
  }

  private def normalize(s: String): String = {
    // Strip trailing whitespace per line, collapse blank-line runs, trim.
    val lines = s.split("\n", -1).map(_.replaceAll("\\s+$", ""))
    lines.mkString("\n").replaceAll("\n{3,}", "\n\n").trim
  }

  private def stripWs(s: String): String = s.replaceAll("\\s+", "")

  private def classifyError(e: Throwable): String = {
    val cls = e.getClass.getSimpleName
    val msg = Option(e.getMessage).getOrElse("")
    if (cls.contains("SassException")) {
      if (msg.contains("parse") || msg.contains("Parse") || msg.contains("expected")) "parse-error"
      else "evaluator-error"
    } else if (cls.contains("StackOverflow")) "stack-overflow"
    else if (cls.contains("UnsupportedOperation")) "unsupported-feature"
    else if (cls.contains("NullPointer")) "null-pointer"
    else if (cls.contains("IndexOutOfBounds")) "index-bounds"
    else if (cls.contains("NumberFormat")) "number-format"
    else if (cls.contains("NoSuchElement")) "no-such-element"
    else if (cls.contains("MatchError")) "match-error"
    else if (cls.contains("IllegalArgument")) "illegal-argument"
    else if (cls.contains("IllegalState")) "illegal-state"
    else "uncaught-" + cls
  }

  private def shortMessage(e: Throwable): String = {
    val first = Option(e.getMessage).getOrElse(e.getClass.getName)
    first.linesIterator.take(3).mkString(" | ")
  }

  private def diffPreview(expected: String, actual: String): String = {
    val e = expected.linesIterator.take(6).mkString("\\n")
    val a = actual.linesIterator.take(6).mkString("\\n")
    s"expected: $e\n    actual: $a"
  }
}
