/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * JVM-only — CompileFile reads from the filesystem (it lives in
 * src/main/scalajvm), so this differential red test writes temp files via
 * java.nio.file. */
package ssg
package sass

import java.nio.file.{ Files, Path }

import scala.language.implicitConversions

/** Differential RED test for ISS-995 ([R0610-P1] bug, `CompileFile.compile` ignores the file extension).
  *
  * `CompileFile.compile(path, style)` (CompileJvm.scala:18) reads the source and calls `Compile.compileString(source, style, Nullable(importer))` — the third positional argument is `importer`, so
  * `syntax` defaults to `Syntax.Scss` and the source URL is never threaded through. A `.sass` (indented) or `.css` file is therefore parsed as SCSS.
  *
  * Oracle — dart-sass (vendored at original-src/dart-sass):
  *   - lib/src/syntax.dart:21-24 `Syntax.forPath`: `.sass` => Syntax.sass (indented), `.css` => Syntax.css, else => Syntax.scss. The port already has the equivalent `Syntax.forPath`
  *     (Syntax.scala:40-43).
  *   - The port's own `Compile.compileString(source, style, syntax = …)` dispatches on syntax (Compile.scala:80-84): Syntax.Sass => SassParser, Syntax.Css => CssParser, Syntax.Scss => ScssParser.
  *
  * Reference oracle: `CompileFile.compile(<x>.sass, style)` MUST equal `Compile.compileString(<sass source>, style, syntax = Syntax.Sass)`, i.e. the extension should select the parser exactly as
  * `Syntax.forPath` says.
  *
  * JVM-scoped because `CompileFile` is JVM-only.
  */
final class CompileFileSyntaxIss995Suite extends munit.FunSuite {

  // A temp dir per test, cleaned up via munit fixture (same pattern as the JVM
  // CompileLoadPathsIss991JvmSuite).
  private val tempDir = FunFixture[Path](
    setup = _ => Files.createTempDirectory("ssg-sass-iss995-"),
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

  // Indented (.sass) syntax: no braces, no semicolons. Valid ONLY as Syntax.Sass;
  // parsing it as SCSS (today's behavior) throws a parse error.
  private val sassSource =
    """|.foo
       |  color: red
       |""".stripMargin

  tempDir.test("ISS-995 RED: a .sass file is parsed with the indented syntax") { dir =>
    val path = writeFile(dir, "indented.sass", sassSource)
    // Oracle: the extension must select SassParser, matching Syntax.forPath.
    val expected = Compile.compileString(sassSource, syntax = Syntax.Sass).css
    val actual   = CompileFile.compile(path.toString).css
    assertEquals(
      actual,
      expected,
      s"CompileFile.compile(*.sass) must equal compileString(.., syntax = Syntax.Sass); got:\n$actual"
    )
  }

  // A Sass `$variable` is rejected by the plain-CSS parser (Syntax.Css) but
  // accepted by the SCSS parser. So a .css file containing one MUST throw today
  // it does not (parsed as SCSS).
  private val cssSource = "$primary: #3498db; .a { color: $primary; }"

  tempDir.test("ISS-995 RED: a .css file is parsed with the plain-CSS syntax") { dir =>
    val path = writeFile(dir, "plain.css", cssSource)
    // Oracle: Syntax.Css rejects Sass variables (see CssParserSuite); since the
    // extension must select CssParser, this MUST throw a SassFormatException.
    intercept[SassFormatException](CompileFile.compile(path.toString))
  }

  tempDir.test("ISS-995 control: a .scss file still compiles as SCSS (passes today)") { dir =>
    val scssSource = ".foo {\n  color: red;\n}\n"
    val path       = writeFile(dir, "normal.scss", scssSource)
    val expected   = Compile.compileString(scssSource, syntax = Syntax.Scss).css
    val actual     = CompileFile.compile(path.toString).css
    assertEquals(actual, expected)
  }
}
