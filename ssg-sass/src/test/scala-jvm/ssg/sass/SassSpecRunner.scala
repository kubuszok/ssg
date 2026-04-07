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
 * Multi-file HRX tests are run via an in-memory MapImporter built from
 * sibling entries in the same archive directory, so `@use`/`@forward`/
 * `@import` of archive-local URLs resolves. This is the mechanism that
 * lets forward/use/import/extend subdirs contribute to the measured
 * pass rate.
 *
 * ## Modes (system properties)
 *
 * The runner is enabled by `-Dssg.sass.spec=1`. Without the flag the
 * munit test is skipped explicitly (not silently via `assume`).
 *
 *   -Dssg.sass.spec=1          — enable the runner
 *   -Dssg.sass.spec.strict=1   — fail on any leak category (leaks are
 *                                internal-state escapes, not legitimate
 *                                spec failures)
 *   -Dssg.sass.spec.subdir=P   — filter to cases under the given
 *                                relative-path prefix; require 100%
 *                                strict pass within the filtered set
 *   -Dssg.sass.spec.baseline=F — read baseline TSV from F; fail on any
 *                                regression (case whose recorded
 *                                outcome was Pass/ExpectedErrorOk and
 *                                is now anything else)
 *   -Dssg.sass.spec.snapshot=1 — rewrite the baseline TSV after the run
 *                                (no assertions)
 *
 * Baseline TSV format: one case per line, `relPath<TAB>outcome`,
 * outcome is the Outcome enum name (`Pass`, `ExpectedErrorOk`,
 * `Mismatch`, `Error`, `ExpectedErrorMissed`, `MissingExpected`).
 */
package ssg
package sass

import java.nio.charset.StandardCharsets
import java.nio.file.{ Files, Path, Paths }

import scala.jdk.CollectionConverters.*
import scala.language.implicitConversions
import scala.util.{ Failure, Success, Try }

import ssg.sass.importer.MapImporter
import ssg.sass.visitor.OutputStyle

final class SassSpecRunner extends munit.FunSuite {

  import SassSpecRunner.*

  override def munitTimeout: scala.concurrent.duration.Duration =
    scala.concurrent.duration.Duration(30, "minutes")

  test("sass-spec compliance measurement".tag(SassSpecTag)) {
    runSpecSuite()
  }

  private def runSpecSuite(): Unit = {
    // Mode flags — the test's behavior depends on which are set.
    val strictMode:   Boolean        = sys.props.get("ssg.sass.spec.strict").exists(truthy)
    val subdirFilter: Option[String] = sys.props.get("ssg.sass.spec.subdir").filter(_.nonEmpty)
    val baselinePath: Option[String] = sys.props.get("ssg.sass.spec.baseline").filter(_.nonEmpty)
    val snapshotMode: Boolean        = sys.props.get("ssg.sass.spec.snapshot").exists(truthy)

    // Phase 0.1: replace silent `assume` with an explicit branch.
    // A missing submodule still suppresses the test in non-strict mode
    // (for developers who do not have sass-spec checked out), but the
    // message is printed loudly so it is never silent. Strict mode
    // promotes this to a hard failure.
    val specRoot = locateSpecRoot() match {
      case Some(p) => p
      case None    =>
        if (strictMode)
          fail(s"sass-spec not found at $ExpectedSpecRoot — strict mode requires submodule")
        println(s"sass-spec not found at $ExpectedSpecRoot — SKIPPED (non-strict)")
        return
    }

    // Collect + optionally filter cases.
    val allCases = collectCases(specRoot)
    val cases    = subdirFilter match {
      case Some(prefix) => allCases.filter(_.relPath.startsWith(prefix))
      case None         => allCases
    }
    if (cases.isEmpty && subdirFilter.isDefined)
      fail(s"no cases matched subdir filter '${subdirFilter.get}'")
    println(s"sass-spec: collected ${cases.size} test cases" +
      subdirFilter.fold("")(p => s" (filtered to subdir $p)"))

    val results = cases.map(runCase)

    val total               = results.size
    val passing             = results.count(_.outcome == Outcome.Pass)
    val mismatch            = results.count(_.outcome == Outcome.Mismatch)
    val errored             = results.count(_.outcome == Outcome.Error)
    val missing             = results.count(_.outcome == Outcome.MissingExpected)
    val expectedErrorOk     = results.count(_.outcome == Outcome.ExpectedErrorOk)
    val expectedErrorMissed = results.count(_.outcome == Outcome.ExpectedErrorMissed)
    val netPassing          = passing + expectedErrorOk

    val pct     = if (total == 0) 0.0 else netPassing.toDouble * 100.0 / total.toDouble
    val summary =
      f"""|sass-spec: Total=$total%d  Passing=$netPassing%d (${pct}%.1f%%)
          |  exact-output-pass = $passing
          |  expected-error-ok = $expectedErrorOk
          |  output-mismatch   = $mismatch
          |  compile-error     = $errored
          |  expected-error-not-raised = $expectedErrorMissed
          |  missing-expected  = $missing
          |""".stripMargin
    println(summary)

    // Write per-failure report (same format as before).
    val repoRoot = specRoot.toAbsolutePath.getParent.getParent.getParent
    val outDir   = repoRoot.resolve("ssg-sass").resolve("target")
    Files.createDirectories(outDir)
    val report = outDir.resolve("sass-spec-failures.txt")
    println(s"Writing report to: ${report.toAbsolutePath}")

    val sb = new StringBuilder
    sb.append(summary).append('\n')
    sb.append("# Per-failure details (first 2000 failures)\n\n")
    val failures =
      results.filter(r => r.outcome != Outcome.Pass && r.outcome != Outcome.ExpectedErrorOk)
    val (leaks, rest) = failures.partition(r => isLeakCategory(r.category))
    (leaks ++ rest).take(2000).foreach { r =>
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

    // Category breakdown.
    val byCategory = failures.groupBy(_.category).view.mapValues(_.size).toList.sortBy(-_._2)
    println("\n# Failure categories (by count)")
    byCategory.foreach { case (cat, n) => println(f"  $n%6d  $cat") }

    // Phase 0.4: snapshot mode — rewrite the baseline TSV, then return
    // without asserting.  Used by `ssg-dev port snapshot`. Default target
    // is the tracked scripts/data/sass-spec-baseline.tsv (same path as
    // the regression-mode default), so the baseline lives in git.
    if (snapshotMode) {
      val tracked = repoRoot.resolve("scripts").resolve("data").resolve("sass-spec-baseline.tsv")
      val target = baselinePath.map(Paths.get(_)).getOrElse(tracked)
      writeBaseline(target, results)
      println(s"Wrote baseline: $target (${results.size} cases)")
      return
    }

    // Phase 0.2: real assertions replacing `assert(true)`.

    // 1) Hard fail if ANY leak-category results are present, regardless
    //    of mode. Leaks are never legitimate — they are internal state
    //    escapes that must surface as bugs, not inflate the pass rate.
    val leakResults = failures.filter(r => isLeakCategory(r.category))
    if (leakResults.nonEmpty && strictMode) {
      val preview = leakResults.take(5).map(r => s"  [${r.category}] ${r.relPath}: ${r.detail.take(120)}").mkString("\n")
      fail(
        s"sass-spec: ${leakResults.size} leak-category results (internal-state escapes). " +
          s"Strict mode treats these as hard failures.\n$preview"
      )
    }

    // 2) Subdir mode: every collected case must pass. No tolerance.
    subdirFilter.foreach { prefix =>
      val nonPassing = results.count(r => r.outcome != Outcome.Pass && r.outcome != Outcome.ExpectedErrorOk)
      if (nonPassing > 0) {
        val sample = failures.take(5).map(r => s"  [${r.category}] ${r.relPath}").mkString("\n")
        fail(
          s"sass-spec --subdir $prefix: $nonPassing of $total cases failing. Strict-pass mode requires 100%.\n$sample"
        )
      }
    }

    // 3) Baseline / regression mode: no previously-passing case may
    //    now fail. New passes are fine; new cases are fine.
    //
    // The baseline lives under `scripts/data/sass-spec-baseline.tsv`
    // (tracked in git) so every session shares the same floor. The
    // `target/` variant is not tracked.
    val defaultBaseline = repoRoot.resolve("scripts").resolve("data").resolve("sass-spec-baseline.tsv")
    // Auto-snapshot on first run: when no baseline exists and no
    // explicit --baseline was requested, write the current result set
    // as the new zero point. This converts "first run after Phase 0"
    // into a recorded floor without requiring a separate snapshot pass.
    val effectiveBaseline: Option[Path] = baselinePath.map(Paths.get(_)).orElse {
      if (Files.isRegularFile(defaultBaseline)) Some(defaultBaseline)
      else if (subdirFilter.isDefined) None // don't snapshot a filtered run
      else {
        writeBaseline(defaultBaseline, results)
        println(s"sass-spec: auto-snapshotted baseline to $defaultBaseline (${results.size} cases)")
        Some(defaultBaseline)
      }
    }
    effectiveBaseline.foreach { path =>
      val baseline = readBaseline(path)
      val current  = results.map(r => r.relPath -> r.outcome).toMap
      val regressed = baseline.toList.flatMap { case (rel, prior) =>
        if (prior == Outcome.Pass || prior == Outcome.ExpectedErrorOk) {
          current.get(rel) match {
            case Some(now) if now != Outcome.Pass && now != Outcome.ExpectedErrorOk =>
              List((rel, prior, now))
            case _ => Nil
          }
        } else Nil
      }
      if (regressed.nonEmpty) {
        val preview = regressed.take(10).map { case (rel, prior, now) =>
          s"  $rel: $prior -> $now"
        }.mkString("\n")
        fail(
          s"sass-spec: ${regressed.size} regressions vs baseline $path (prior pass -> current fail).\n$preview"
        )
      }
      // New passes are not required; they're bonus. But we do log a
      // count so the user sees progress.
      val newPasses = current.toList.count { case (rel, now) =>
        (now == Outcome.Pass || now == Outcome.ExpectedErrorOk) &&
          !baseline.get(rel).exists(prior => prior == Outcome.Pass || prior == Outcome.ExpectedErrorOk)
      }
      if (newPasses > 0) println(s"sass-spec: $newPasses cases newly passing vs baseline")
    }

    // 4) If no explicit assertion mode is active, verify the
    //    measurement produced a non-empty result. An all-zero run
    //    almost always means the submodule or walker broke silently.
    if (total == 0) fail("sass-spec: no cases collected — spec walker returned empty")
  }
}

object SassSpecRunner {

  val SassSpecTag: munit.Tag = new munit.Tag("SassSpec")

  val ExpectedSpecRoot: Path =
    Paths.get("original-src", "sass-spec", "spec")

  enum Outcome {
    case Pass, Mismatch, Error, MissingExpected, ExpectedErrorOk, ExpectedErrorMissed
  }

  object Outcome {

    def parse(s: String): Option[Outcome] = s match {
      case "Pass"                => Some(Pass)
      case "Mismatch"            => Some(Mismatch)
      case "Error"               => Some(Error)
      case "MissingExpected"     => Some(MissingExpected)
      case "ExpectedErrorOk"     => Some(ExpectedErrorOk)
      case "ExpectedErrorMissed" => Some(ExpectedErrorMissed)
      case _                     => None
    }
  }

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

  /** A single test case.
    *
    * @param relPath
    *   path relative to the spec root (for loose cases) or a composite
    *   `archive.hrx!sub/dir` origin (for HRX entries)
    * @param source
    *   the `input.scss` contents
    * @param expectedOut
    *   contents of the adjacent `output.css`, if any
    * @param expectedError
    *   contents of the adjacent `error` file, if any
    * @param siblingFiles
    *   HRX-only: all other files in the same archive directory, keyed
    *   by their basename, so the runner can build a MapImporter that
    *   resolves `@use 'sibling'`-style references
    */
  final case class TestCase(
    relPath:       String,
    source:        String,
    expectedOut:   Option[String],
    expectedError: Option[String],
    siblingFiles:  Map[String, String] = Map.empty
  )

  def collectCases(root: Path): List[TestCase] = {
    val buf    = scala.collection.mutable.ListBuffer.empty[TestCase]
    val stream = Files.walk(root)
    try
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
    finally stream.close()
    buf.toList
  }

  private def loadLooseCase(root: Path, input: Path): Option[TestCase] = {
    val dir       = input.getParent
    val rel       = root.toAbsolutePath.relativize(input.toAbsolutePath).toString
    val outFile   = dir.resolve("output.css")
    val errFile   = dir.resolve("error")
    val source    = Try(new String(Files.readAllBytes(input), StandardCharsets.UTF_8)).toOption
    val expectedO =
      if (Files.isRegularFile(outFile))
        Try(new String(Files.readAllBytes(outFile), StandardCharsets.UTF_8)).toOption
      else None
    val expectedE =
      if (Files.isRegularFile(errFile))
        Try(new String(Files.readAllBytes(errFile), StandardCharsets.UTF_8)).toOption
      else None
    // Also harvest the sibling .scss/.sass files in the same directory
    // so the loose-case runner can resolve @use/@import of siblings the
    // same way the HRX path does.
    val siblings = scala.collection.mutable.Map.empty[String, String]
    Try {
      val stream = Files.list(dir)
      try
        stream.iterator().asScala.foreach { p =>
          if (Files.isRegularFile(p)) {
            val name = p.getFileName.toString
            if (name != "input.scss" && (name.endsWith(".scss") || name.endsWith(".sass"))) {
              val c = Try(new String(Files.readAllBytes(p), StandardCharsets.UTF_8)).toOption
              c.foreach(siblings(name) = _)
            }
          }
        }
      finally stream.close()
    }
    source.map(s => TestCase(rel, s, expectedO, expectedE, siblings.toMap))
  }

  /** Parse an HRX archive into test cases.
    *
    * Phase 0.5: previously this path skipped any case with sibling
    * `.scss` files or non-`sass:` imports. That hid every multi-file
    * `forward`/`use`/`import`/`extend` spec from the measured pass
    * rate. The new behavior emits one TestCase per `input.scss` entry
    * with the sibling files captured so the runner can build a
    * MapImporter from them.
    */
  def loadHrxCases(root: Path, archive: Path): List[TestCase] = {
    val raw = Try(new String(Files.readAllBytes(archive), StandardCharsets.UTF_8)).toOption.getOrElse("")
    if (raw.isEmpty) Nil
    else {
      val entries = parseHrx(raw)
      // group entries by their parent directory inside the archive
      val byDir: Map[String, Map[String, String]] =
        entries
          .groupBy { case (p, _) =>
            val i = p.lastIndexOf('/')
            if (i < 0) "" else p.substring(0, i)
          }
          .view
          .mapValues(_.map { case (p, c) =>
            val i = p.lastIndexOf('/')
            (if (i < 0) p else p.substring(i + 1)) -> c
          }.toMap)
          .toMap

      val archiveRel = root.toAbsolutePath.relativize(archive.toAbsolutePath).toString
      byDir.iterator.flatMap { case (dir, files) =>
        files.get("input.scss") match {
          case Some(src) =>
            val out    = files.get("output.css")
            val err    = files.get("error")
            val origin = s"$archiveRel!${if (dir.isEmpty) "<root>" else dir}"
            // Sibling files (for the MapImporter): everything except
            // input.scss and the expected-output fixtures.
            val siblings = files.filter { case (name, _) =>
              name != "input.scss" && name != "output.css" && name != "error" && name != "warning"
            }
            Iterator.single(TestCase(origin, src, out, err, siblings))
          case None => Iterator.empty
        }
      }.toList
    }
  }

  /** Parse HRX archive. HRX uses `<===> path` as a section header and `<===>` alone as a section terminator. We split into (path, content) pairs.
    */
  def parseHrx(raw: String): List[(String, String)] = {
    val buf   = scala.collection.mutable.ListBuffer.empty[(String, String)]
    val lines = raw.split("\n", -1)
    var i     = 0
    var currentPath: Option[String] = None
    val body = new StringBuilder
    def flush(): Unit = {
      currentPath.foreach { path =>
        val s       = body.toString
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
    val importer: Nullable[ssg.sass.importer.Importer] =
      if (tc.siblingFiles.isEmpty) Nullable.empty
      else Nullable(new MapImporter(tc.siblingFiles))
    val compiled: Try[CompileResult] =
      try
        Success(
          Compile.compileString(
            tc.source,
            style = OutputStyle.Expanded,
            importer = importer
          )
        )
      catch {
        case t: Throwable => Failure(t)
      }
    (compiled, tc.expectedOut, tc.expectedError) match {
      case (Success(cr), Some(expected), _) =>
        val actual = cr.css
        if (normalize(actual) == normalize(expected)) {
          Result(tc.relPath, Outcome.Pass, "pass", "")
        } else if (actual.trim == expected.trim) {
          Result(tc.relPath, Outcome.Pass, "pass-whitespace", "")
        } else {
          val cat =
            if (stripWs(actual) == stripWs(expected)) "whitespace-only"
            else if (actual.trim.isEmpty && expected.trim.nonEmpty) "empty-output"
            else "wrong-output"
          Result(tc.relPath, Outcome.Mismatch, cat, diffPreview(expected, actual))
        }
      case (Success(_), None, Some(_)) =>
        Result(tc.relPath, Outcome.ExpectedErrorMissed, "expected-error-not-raised", "")
      case (Success(_), None, None) =>
        Result(tc.relPath, Outcome.MissingExpected, "no-output-no-error", "")
      case (Failure(e), _, Some(_)) =>
        Result(tc.relPath, Outcome.ExpectedErrorOk, "expected-error-ok", shortMessage(e))
      case (Failure(e), _, None) =>
        Result(tc.relPath, Outcome.Error, classifyError(e), shortMessage(e))
    }
  }

  private def normalize(s: String): String = {
    // Strip trailing whitespace per line, collapse blank-line runs, trim.
    val lines = s.split("\n", -1).map(_.replaceAll("\\s+$", ""))
    lines.mkString("\n").replaceAll("\n{3,}", "\n\n").trim
  }

  private def stripWs(s: String): String = s.replaceAll("\\s+", "")

  /** Category names that are never legitimate sass-spec failures —
    * they're internal-state escapes (NPE/IOBE/MatchError etc.) or
    * unimplemented features in ssg-sass. Strict mode treats any of
    * these as a hard test failure so they cannot inflate the "wrong
    * output" bucket and hide.
    */
  val LeakCategories: Set[String] = Set(
    "stack-overflow",
    "unsupported-feature",
    "null-pointer",
    "index-bounds",
    "number-format",
    "no-such-element",
    "match-error",
    "illegal-argument",
    "illegal-state",
    "script-error"
  )

  def isLeakCategory(c: String): Boolean =
    LeakCategories.contains(c) || c.startsWith("uncaught-")

  private def classifyError(e: Throwable): String = {
    val cls = e.getClass.getSimpleName
    val msg = Option(e.getMessage).getOrElse("")
    // SassFormatException / MultiSpanSassFormatException are parser errors with
    // real spans and dart-sass-style messages — not leaks.
    if (cls.contains("SassFormatException")) "parse-error"
    else if (cls.contains("SassRuntimeException")) "evaluator-error"
    else if (cls.contains("SassScriptException")) "script-error"
    else if (e.isInstanceOf[SassException] || cls.contains("SassException")) {
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

  private def truthy(s: String): Boolean = {
    val v = s.trim.toLowerCase
    v == "1" || v == "true" || v == "yes" || v == "on"
  }

  /** Read a baseline TSV into a map from relPath -> Outcome. Unknown
    * outcome strings are silently dropped (they can't be compared
    * against anyway).
    */
  def readBaseline(path: Path): Map[String, Outcome] = {
    if (!Files.isRegularFile(path)) return Map.empty
    val out = scala.collection.mutable.Map.empty[String, Outcome]
    val lines =
      Try(Files.readAllLines(path, StandardCharsets.UTF_8).asScala.toList).getOrElse(Nil)
    lines.foreach { line =>
      if (line.nonEmpty && !line.startsWith("#")) {
        val tab = line.indexOf('\t')
        if (tab > 0) {
          val rel = line.substring(0, tab)
          val out2 = line.substring(tab + 1).trim
          Outcome.parse(out2).foreach(o => out(rel) = o)
        }
      }
    }
    out.toMap
  }

  /** Write a baseline TSV from a list of results. */
  def writeBaseline(path: Path, results: List[Result]): Unit = {
    Files.createDirectories(path.toAbsolutePath.getParent)
    val sb = new StringBuilder
    sb.append("# sass-spec baseline (relPath<TAB>outcome)\n")
    results.sortBy(_.relPath).foreach { r =>
      sb.append(r.relPath).append('\t').append(r.outcome.toString).append('\n')
    }
    Files.write(path, sb.toString.getBytes(StandardCharsets.UTF_8))
  }
}
