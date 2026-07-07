/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * JVM-only — CompileFile reads from the filesystem (it lives in
 * src/main/scalajvm), so this differential test writes temp files via
 * java.nio.file. */
package ssg
package sass

import java.nio.file.{ Files, Path }

import ssg.commons.Severity
import ssg.sass.visitor.OutputStyle

import scala.language.implicitConversions

/** Differential tests for the ISS-1375 JVM mirror `CompileFile.compileResult` (docs/architecture/error-contracts.md §2.3).
  *
  * `CompileFile.compileResult` delegates to `CompileFile.compile` and wraps it in the shared `DiagResult` envelope exactly as `Compile.compileStringResult` wraps `compileString` (same `spanPosition`
  * / `codeFor` helpers). Because `CompileFile.compile` threads the file URL into `compileString` (CompileJvm.scala:32-38), the failure diagnostic's position also carries a non-empty `source`, unlike
  * the anonymous in-memory path — this suite pins that.
  */
final class CompileFileResultIss1375Suite extends munit.FunSuite {

  private val tempDir = FunFixture[Path](
    setup = _ => Files.createTempDirectory("ssg-sass-iss1375-"),
    teardown = dir =>
      if (Files.exists(dir)) {
        Files.walk(dir).sorted(java.util.Comparator.reverseOrder[Path]()).forEach(p => Files.deleteIfExists(p))
      }
  )

  private def writeFile(dir: Path, name: String, contents: String): Path = {
    val path = dir.resolve(name)
    Files.write(path, contents.getBytes(java.nio.charset.StandardCharsets.UTF_8))
    path
  }

  tempDir.test("ISS-1375: CompileFile.compileResult is a clean success carrying the same CSS as CompileFile.compile") { dir =>
    val path   = writeFile(dir, "ok.scss", ".foo {\n  color: red;\n}\n")
    val legacy = CompileFile.compile(path.toString)
    val result = CompileFile.compileResult(path.toString)

    assert(result.isSuccess, s"a clean compile must be a success, got $result")
    assertEquals(result.diagnostics, Vector.empty)
    assertEquals(result.value.map(_.css), Some(legacy.css))
  }

  tempDir.test("ISS-1375: CompileFile.compileResult maps a runtime error to a failure with the file URL as the position source") { dir =>
    // `1px + 1em` has incompatible units — a SassRuntimeException at evaluation time.
    val path   = writeFile(dir, "bad.scss", ".x {\n  width: 1px + 1em;\n}\n")
    val legacy = intercept[ssg.sass.SassRuntimeException](CompileFile.compile(path.toString))
    // The entry file URL is threaded through, so the raw span carries it.
    assert(legacy.span.sourceUrl.toOption.isDefined, "the file URL must be threaded into the span")

    val result = CompileFile.compileResult(path.toString)
    assert(result.isFailure, s"a compile error must produce a failure, got $result")
    assertEquals(result.value, None)
    val d = result.diagnostics.head
    assertEquals(d.severity, Severity.Error)
    assertEquals(d.component, "ssg-sass")
    assertEquals(d.code, Some("runtime-error"))
    assert(d.cause.exists(_.isInstanceOf[ssg.sass.SassRuntimeException]), s"cause: ${d.cause}")
    val pos = d.position.getOrElse(fail("expected a position"))
    // 0-based span line 1 -> 1-based 2 (the `width:` line); source is the file URL.
    assertEquals(pos.source, legacy.span.sourceUrl.toOption)
    assertEquals(pos.line, Some(legacy.span.start.line + 1))
    assertEquals(pos.column, Some(legacy.span.start.column + 1))
    assertEquals(pos.offset, Some(legacy.span.start.offset))
  }

  tempDir.test("ISS-1375: CompileFile.compileResult threads the style parameter") { dir =>
    val path   = writeFile(dir, "styled.scss", ".x {\n  color: red;\n}\n")
    val result = CompileFile.compileResult(path.toString, OutputStyle.Compressed)
    assertEquals(result.value.map(_.css), Some(CompileFile.compile(path.toString, OutputStyle.Compressed).css))
    assert(result.value.exists(_.css == ".x{color:red}"), s"expected compressed CSS, got ${result.value.map(_.css)}")
  }
}
