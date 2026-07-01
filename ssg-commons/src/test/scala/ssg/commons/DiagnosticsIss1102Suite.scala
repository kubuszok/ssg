/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package commons

/** ISS-1102 [R0610]: shared diagnostics/result envelope for SSG module facades.
  *
  * Structure/value assertions on Severity, SourcePosition, Diagnostic, and DiagResult. Every test pins an exact value or exact structure (never mere non-emptiness), so plausible mutations of
  * Diagnostics.scala — flipping the severity order, counting Warnings as errors, dropping diagnostics in flatMap/sequence, off-by-one in render — each fail a named test. Runs on all three platforms
  * (src/test/scala/).
  */
final class DiagnosticsIss1102Suite extends munit.FunSuite {

  // ---- Severity -----------------------------------------------------------

  test("ISS-1102: Severity is declared in ascending order Debug < Info < Warning < Error") {
    // Ordinal order is load-bearing: isAtLeast and any threshold filtering rely on it.
    assertEquals(Severity.Debug.ordinal, 0)
    assertEquals(Severity.Info.ordinal, 1)
    assertEquals(Severity.Warning.ordinal, 2)
    assertEquals(Severity.Error.ordinal, 3)
  }

  test("ISS-1102: Severity.isAtLeast compares by severity, inclusively") {
    assert(Severity.Error.isAtLeast(Severity.Warning))
    assert(Severity.Warning.isAtLeast(Severity.Warning))
    assert(!Severity.Info.isAtLeast(Severity.Warning))
    assert(!Severity.Debug.isAtLeast(Severity.Info))
  }

  test("ISS-1102: Severity.label is the stable lowercase name") {
    assertEquals(Severity.Debug.label, "debug")
    assertEquals(Severity.Info.label, "info")
    assertEquals(Severity.Warning.label, "warning")
    assertEquals(Severity.Error.label, "error")
  }

  // ---- SourcePosition -----------------------------------------------------

  test("ISS-1102: SourcePosition.lineColumn sets exactly line and column") {
    val pos = SourcePosition.lineColumn(3, 7)
    assertEquals(pos, SourcePosition(source = None, line = Some(3), column = Some(7), endLine = None, endColumn = None, offset = None, endOffset = None))
  }

  test("ISS-1102: SourcePosition.inSource sets source, line, and column") {
    val pos = SourcePosition.inSource("input.scss", 3, 7)
    assertEquals(pos, SourcePosition(source = Some("input.scss"), line = Some(3), column = Some(7)))
  }

  test("ISS-1102: SourcePosition.offsetRange sets exactly the [start, end) offsets") {
    val pos = SourcePosition.offsetRange(5, 9)
    assertEquals(pos, SourcePosition(offset = Some(5), endOffset = Some(9)))
  }

  test("ISS-1102: SourcePosition.Unknown carries no information") {
    assertEquals(SourcePosition.Unknown, SourcePosition(None, None, None, None, None, None, None))
  }

  test("ISS-1102: SourcePosition.render prefers source:line:column") {
    assertEquals(SourcePosition.inSource("input.scss", 3, 7).render, "input.scss:3:7")
  }

  test("ISS-1102: SourcePosition.render labels anonymous input as <input>") {
    assertEquals(SourcePosition.lineColumn(3, 7).render, "<input>:3:7")
  }

  test("ISS-1102: SourcePosition.render with line but no column omits the column") {
    assertEquals(SourcePosition(source = Some("a.liquid"), line = Some(12)).render, "a.liquid:12")
  }

  test("ISS-1102: SourcePosition.render falls back to @start..end offsets when no line is known") {
    assertEquals(SourcePosition.offsetRange(5, 9).render, "<input>@5..9")
  }

  test("ISS-1102: SourcePosition.render with only a start offset renders @start") {
    assertEquals(SourcePosition(offset = Some(5)).render, "<input>@5")
  }

  test("ISS-1102: SourcePosition.render of Unknown is just the anonymous label") {
    assertEquals(SourcePosition.Unknown.render, "<input>")
  }

  // ---- Diagnostic ---------------------------------------------------------

  test("ISS-1102: Diagnostic.error and Diagnostic.warning set exactly their severity") {
    assertEquals(Diagnostic.error("ssg-sass", "boom").severity, Severity.Error)
    assertEquals(Diagnostic.warning("ssg-minify", "degraded").severity, Severity.Warning)
    assertEquals(Diagnostic.error("ssg-sass", "boom").component, "ssg-sass")
    assertEquals(Diagnostic.error("ssg-sass", "boom").message, "boom")
  }

  test("ISS-1102: Diagnostic.render is [component] severity: message (position)") {
    val diag = Diagnostic.error("ssg-sass", "Expected \")\".", position = Some(SourcePosition.inSource("input.scss", 3, 8)))
    assertEquals(diag.render, "[ssg-sass] error: Expected \")\". (input.scss:3:8)")
  }

  test("ISS-1102: Diagnostic.render omits the position suffix when position is absent") {
    assertEquals(Diagnostic.warning("ssg-js", "JS compression failed").render, "[ssg-js] warning: JS compression failed")
  }

  test("ISS-1102: Diagnostic.fromThrowable takes the throwable's message and keeps it as cause") {
    val boom = new RuntimeException("kaput")
    val diag = Diagnostic.fromThrowable(Severity.Error, "ssg-liquid", boom, code = Some("parse-error"))
    assertEquals(diag.severity, Severity.Error)
    assertEquals(diag.component, "ssg-liquid")
    assertEquals(diag.message, "kaput")
    assertEquals(diag.code, Some("parse-error"))
    assertEquals(diag.cause, Some(boom))
  }

  test("ISS-1102: Diagnostic.fromThrowable falls back to the class name when the throwable has no message") {
    val diag = Diagnostic.fromThrowable(Severity.Error, "ssg-md", new IllegalStateException())
    assertEquals(diag.message, "java.lang.IllegalStateException")
  }

  test("ISS-1102: Diagnostic.causeChain follows getCause links oldest-wrapper-first") {
    val root   = new RuntimeException("root")
    val middle = new RuntimeException("middle", root)
    val outer  = new RuntimeException("outer", middle)
    val diag   = Diagnostic.fromThrowable(Severity.Error, "ssg-site", outer)
    assertEquals(diag.causeChain, List(outer, middle, root))
  }

  test("ISS-1102: Diagnostic.causeChain is empty without a cause") {
    assertEquals(Diagnostic.error("ssg-graphviz", "bad token").causeChain, List.empty[Throwable])
  }

  test("ISS-1102: Diagnostic.causeChain is capped at MaxCauseDepth on cyclic chains") {
    val a = new RuntimeException("a")
    val b = new RuntimeException("b", a)
    a.initCause(b) // a -> b -> a -> ... cycle
    val chain = Diagnostic.fromThrowable(Severity.Error, "ssg-site", a).causeChain
    assertEquals(chain.length, Diagnostic.MaxCauseDepth)
    assertEquals(chain.take(3), List[Throwable](a, b, a))
  }

  // ---- DiagResult: classification -----------------------------------------

  test("ISS-1102: success is success, not degraded, not failure, and Right in Either form") {
    val result = DiagResult.success("css")
    assertEquals(result.value, Some("css"))
    assertEquals(result.diagnostics, Vector.empty[Diagnostic])
    assert(result.isSuccess)
    assert(!result.isDegraded)
    assert(!result.isFailure)
    assertEquals(result.toEither, Right("css"))
  }

  test("ISS-1102: failure has no value and keeps its diagnostics in Either form") {
    val diag   = Diagnostic.error("ssg-sass", "Expected \")\".")
    val result = DiagResult.failure(diag)
    assertEquals(result.value, None)
    assert(result.isFailure)
    assert(!result.isSuccess)
    assert(!result.isDegraded)
    assertEquals(result.toEither, Left(Vector(diag)))
  }

  test("ISS-1102: degraded has a value AND error diagnostics (the mermaid/minify fallback contract)") {
    val diag   = Diagnostic.error("ssg-mermaid", "Parse error on line 2", position = Some(SourcePosition.lineColumn(2, 5)))
    val result = DiagResult.degraded("<svg>error diagram</svg>", diag)
    assertEquals(result.value, Some("<svg>error diagram</svg>"))
    assert(result.isDegraded)
    assert(!result.isSuccess)
    assert(!result.isFailure)
    assertEquals(result.diagnostics, Vector(diag))
  }

  test("ISS-1102: a warning-only result is a success, not degraded (only Error severity degrades)") {
    val warning = Diagnostic.warning("ssg-sass", "deprecation: slash-div")
    val result  = DiagResult.success("css").withDiagnostic(warning)
    assert(result.isSuccess)
    assert(!result.isDegraded)
    assert(!result.hasErrors)
    assertEquals(result.warnings, Vector(warning))
    assertEquals(result.errors, Vector.empty[Diagnostic])
  }

  test("ISS-1102: errors/warnings filter by exact severity, preserving order") {
    val e1     = Diagnostic.error("ssg-liquid", "e1")
    val w1     = Diagnostic.warning("ssg-liquid", "w1")
    val e2     = Diagnostic.error("ssg-liquid", "e2")
    val result = DiagResult(Some("out"), Vector(e1, w1, e2))
    assertEquals(result.errors, Vector(e1, e2))
    assertEquals(result.warnings, Vector(w1))
    assert(result.hasErrors)
  }

  // ---- DiagResult: combinators --------------------------------------------

  test("ISS-1102: map transforms the value and keeps diagnostics untouched") {
    val warning = Diagnostic.warning("ssg-md", "w")
    val result  = DiagResult.success(21).withDiagnostic(warning).map(_ * 2)
    assertEquals(result.value, Some(42))
    assertEquals(result.diagnostics, Vector(warning))
  }

  test("ISS-1102: flatMap appends the second step's diagnostics after the first's, in order") {
    val w1     = Diagnostic.warning("ssg-liquid", "w1")
    val w2     = Diagnostic.warning("ssg-md", "w2")
    val result = DiagResult.success("a").withDiagnostic(w1).flatMap(a => DiagResult.success(a + "b").withDiagnostic(w2))
    assertEquals(result.value, Some("ab"))
    assertEquals(result.diagnostics, Vector(w1, w2))
  }

  test("ISS-1102: flatMap on failure skips the step and keeps the accumulated diagnostics") {
    val diag    = Diagnostic.error("ssg-liquid", "parse failed")
    var stepRan = false
    val result  = DiagResult.failure(diag).flatMap { (_: String) =>
      stepRan = true
      DiagResult.success("never")
    }
    assertEquals(stepRan, false)
    assertEquals(result.value, None)
    assertEquals(result.diagnostics, Vector(diag))
  }

  test("ISS-1102: withDiagnostics appends in order after existing diagnostics") {
    val w1     = Diagnostic.warning("ssg-site", "w1")
    val w2     = Diagnostic.warning("ssg-site", "w2")
    val w3     = Diagnostic.warning("ssg-site", "w3")
    val result = DiagResult.success(1).withDiagnostic(w1).withDiagnostics(List(w2, w3))
    assertEquals(result.diagnostics, Vector(w1, w2, w3))
  }

  test("ISS-1102: getOrElse returns the value on success and the fallback on failure") {
    assertEquals(DiagResult.success("out").getOrElse("fallback"), "out")
    assertEquals(DiagResult.failure(Diagnostic.error("ssg-js", "e")).getOrElse("fallback"), "fallback")
  }

  test("ISS-1102: fold gives both branches access to the diagnostics") {
    val w = Diagnostic.warning("ssg-sass", "w")
    val e = Diagnostic.error("ssg-sass", "e")
    assertEquals(DiagResult.success("css").withDiagnostic(w).fold(ds => s"failed:${ds.length}", (v, ds) => s"$v:${ds.length}"), "css:1")
    assertEquals(DiagResult.failure(e).fold(ds => s"failed:${ds.length}", (v, ds) => s"$v:${ds.length}"), "failed:1")
  }

  // ---- DiagResult: adapters -----------------------------------------------

  test("ISS-1102: fromEither maps Right to a clean success (the ssg-highlight adapter shape)") {
    val result = DiagResult.fromEither(Right("html"): Either[String, String])(e => Diagnostic.error("ssg-highlight", e))
    assertEquals(result, DiagResult(Some("html"), Vector.empty))
  }

  test("ISS-1102: fromEither maps Left through toDiagnostic to a failure") {
    val result = DiagResult.fromEither(Left("UnknownLanguage"): Either[String, String])(e => Diagnostic.error("ssg-highlight", e, code = Some(e)))
    assertEquals(result.value, None)
    assertEquals(result.diagnostics, Vector(Diagnostic.error("ssg-highlight", "UnknownLanguage", code = Some("UnknownLanguage"))))
  }

  test("ISS-1102: sequence of all-successes yields the values in order with all diagnostics concatenated") {
    val w1     = Diagnostic.warning("ssg-md", "w1")
    val w2     = Diagnostic.warning("ssg-sass", "w2")
    val result = DiagResult.sequence(List(DiagResult.success(1).withDiagnostic(w1), DiagResult.success(2), DiagResult.success(3).withDiagnostic(w2)))
    assertEquals(result.value, Some(Vector(1, 2, 3)))
    assertEquals(result.diagnostics, Vector(w1, w2))
  }

  test("ISS-1102: sequence with one failure has no value but retains EVERY diagnostic (site-build accumulation contract)") {
    val w1     = Diagnostic.warning("ssg-md", "w1")
    val e1     = Diagnostic.error("ssg-liquid", "e1")
    val result = DiagResult.sequence(List(DiagResult.success(1).withDiagnostic(w1), DiagResult.failure(e1), DiagResult.success(3)))
    assertEquals(result.value, None)
    assertEquals(result.diagnostics, Vector(w1, e1))
    assert(result.isFailure)
  }

  test("ISS-1102: sequence of nothing is an empty success") {
    val result = DiagResult.sequence(List.empty[DiagResult[Int]])
    assertEquals(result.value, Some(Vector.empty[Int]))
    assertEquals(result.diagnostics, Vector.empty[Diagnostic])
  }
}
