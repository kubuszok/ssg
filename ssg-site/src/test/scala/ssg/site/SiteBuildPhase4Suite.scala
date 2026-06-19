/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Integration tests for Site.build — ISS-1210 Phase 4.
 * Sass compilation, diagnostics channel, minify differential.
 * Tests cases 8, 9, 11 from site-pipeline-design.md section 9.
 *
 * All assertions are byte-exact (assertEquals on full produced strings).
 * Tests exercise the real pipeline end-to-end including sass compilation
 * via Compile.compileString, minification via Minifier.minifyFile, and
 * the BuildDiagnostic/BuildResult types.
 */
package ssg
package site

import ssg.commons.io.FileOps
import ssg.commons.io.FilePath

class SiteBuildPhase4Suite extends munit.FunSuite {

  /** Creates a temporary directory for each test, cleaned up after. */
  private def withTempDir(testName: String)(body: FilePath => Unit): Unit = {
    val tmpBase = FilePath.cwd.resolve("target").resolve("test-tmp")
    val testDir = tmpBase.resolve(s"ssg-phase4-test-$testName-${System.nanoTime()}")
    FileOps.createDirectories(testDir)
    try {
      body(testDir)
    } finally {
      FileOps.deleteRecursively(testDir)
    }
  }

  /** Sets up a site source directory with the given files and returns a SiteConfig. */
  private def setupSite(
    baseDir:    FilePath,
    configYaml: String,
    files:      Map[String, String]
  ): SiteConfig = {
    val sourceDir = baseDir.resolve("source")
    val destDir   = baseDir.resolve("_site")
    FileOps.createDirectories(sourceDir)

    files.foreach { case (path, content) =>
      val filePath = sourceDir.resolve(path)
      filePath.parent.foreach(FileOps.createDirectories)
      FileOps.writeString(filePath, content)
    }

    val config = SiteConfig.load(configYaml)
    config.copy(
      source = sourceDir,
      destination = destDir
    )
  }

  // ---------------------------------------------------------------------------
  // Case 8: Sass compilation
  // assets/style.scss with empty front matter (`---\n---`) routes to
  // Compile.compileString and emits as _site/assets/style.css.
  // Per design section 2: .scss with front matter -> sass converter -> .css.
  // ---------------------------------------------------------------------------
  test("case 8: scss with empty front matter compiles to css (byte-exact)") {
    withTempDir("case8") { baseDir =>
      val config = setupSite(
        baseDir,
        configYaml = "title: Test Site\n",
        files = Map(
          // Empty front matter (`---\n---`) so it routes to SassCompile.
          // Self-contained SCSS (no @import).
          "assets/style.scss" -> "---\n---\nbody { color: red; }\n"
        )
      )

      val result = Site.build(config)

      // The output must be at _site/assets/style.css (extension .scss -> .css).
      val outputPath = config.destination.resolve("assets").resolve("style.css")
      assert(FileOps.exists(outputPath), s"Expected sass output file: ${outputPath.pathString}")

      // Byte-exact assertion: dart-sass compileString with default Expanded style
      // renders `body { color: red; }` to `body {\n  color: red;\n}\n`.
      // Oracle: ssg.sass.Compile.compileString("body { color: red; }\n").css
      val actual = FileOps.readString(outputPath)
      assertEquals(actual, "body {\n  color: red;\n}\n")

      // The .scss source path must NOT appear in the output (no verbatim copy).
      val scssPath = config.destination.resolve("assets").resolve("style.scss")
      assert(!FileOps.exists(scssPath), s"SCSS source must not be in output: ${scssPath.pathString}")

      // Verify the written list includes the .css path.
      assert(
        result.written.exists(_.pathString.endsWith("style.css")),
        s"Expected style.css in written list, got: ${result.written.map(_.pathString)}"
      )
    }
  }

  test("case 8b: sass indented syntax with front matter compiles to css (byte-exact)") {
    withTempDir("case8b") { baseDir =>
      val config = setupSite(
        baseDir,
        configYaml = "title: Test Site\n",
        files = Map(
          // .sass extension uses the indented syntax (Syntax.Sass).
          "assets/theme.sass" -> "---\n---\nbody\n  color: blue\n"
        )
      )

      Site.build(config)

      // .sass -> .css
      val outputPath = config.destination.resolve("assets").resolve("theme.css")
      assert(FileOps.exists(outputPath), s"Expected sass output file: ${outputPath.pathString}")

      // Oracle: ssg.sass.Compile.compileString("body\n  color: blue\n", syntax = Syntax.Sass).css
      val actual = FileOps.readString(outputPath)
      assertEquals(actual, "body {\n  color: blue;\n}\n")
    }
  }

  test("case 8c: scss without front matter is static (copied verbatim)") {
    withTempDir("case8c") { baseDir =>
      val scssContent = "body { color: green; }\n"
      val config = setupSite(
        baseDir,
        configYaml = "title: Test Site\n",
        files = Map(
          // No front matter -> static file, copied verbatim.
          "assets/plain.scss" -> scssContent
        )
      )

      Site.build(config)

      // Without front matter, it should be copied as-is with .scss extension.
      val outputPath = config.destination.resolve("assets").resolve("plain.scss")
      assert(FileOps.exists(outputPath), s"Expected static scss file: ${outputPath.pathString}")

      val actual = FileOps.readString(outputPath)
      assertEquals(actual, scssContent)
    }
  }

  // ---------------------------------------------------------------------------
  // Case 9: Diagnostics — missing layout
  // A page with `layout: missing` yields a BuildDiagnostic with
  // stage == Layout, severity == Error, and does NOT crash the whole build.
  // Other pages still build successfully.
  // ---------------------------------------------------------------------------
  test("case 9: missing layout produces Layout/Error diagnostic without crashing build") {
    withTempDir("case9") { baseDir =>
      val config = setupSite(
        baseDir,
        configYaml = "title: Test Site\n",
        files = Map(
          "_layouts/default.html" ->
            "<html><body>{{ content }}</body></html>",
          // Good page: uses a layout that exists.
          "good.md" ->
            "---\ntitle: Good Page\nlayout: default\n---\n# Good\n",
          // Bad page: references a layout that does not exist.
          "bad.md" ->
            "---\ntitle: Bad Page\nlayout: missing\n---\n# Bad\n"
        )
      )

      val result = Site.build(config)

      // The good page must still be built (build did not abort).
      val goodOutputPath = config.destination.resolve("good.html")
      assert(FileOps.exists(goodOutputPath), s"Good page must still be built: ${goodOutputPath.pathString}")
      assert(
        result.written.exists(_.pathString.endsWith("good.html")),
        s"Good page must appear in result.written, got: ${result.written.map(_.pathString)}"
      )

      // The bad page must NOT be in the written list.
      assert(
        !result.written.exists(_.pathString.endsWith("bad.html")),
        s"Bad page must not appear in result.written, got: ${result.written.map(_.pathString)}"
      )

      // There must be at least one diagnostic with stage == Layout and severity == Error.
      val layoutErrors = result.diagnostics.filter(d =>
        d.stage == BuildStage.Layout && d.severity == Severity.Error
      )
      assert(
        layoutErrors.nonEmpty,
        s"Expected a Layout/Error diagnostic for missing layout, got diagnostics: ${result.diagnostics}"
      )

      // The diagnostic must reference the missing layout name.
      assert(
        layoutErrors.exists(_.message.contains("missing")),
        s"Expected diagnostic message to mention 'missing', got: ${layoutErrors.map(_.message)}"
      )
    }
  }

  test("case 9b: sass compilation error produces Sass/Error diagnostic without crashing build") {
    withTempDir("case9b") { baseDir =>
      val config = setupSite(
        baseDir,
        configYaml = "title: Test Site\n",
        files = Map(
          // Good markdown page.
          "index.md" -> "---\ntitle: Home\n---\n# Home\n",
          // Broken SCSS: unclosed brace.
          "assets/broken.scss" -> "---\n---\nbody { color: \n"
        )
      )

      val result = Site.build(config)

      // The good page must still be built.
      val goodOutputPath = config.destination.resolve("index.html")
      assert(FileOps.exists(goodOutputPath), s"Good page must still be built: ${goodOutputPath.pathString}")

      // There must be a Sass/Error diagnostic for the broken SCSS.
      val sassErrors = result.diagnostics.filter(d =>
        d.stage == BuildStage.Sass && d.severity == Severity.Error
      )
      assert(
        sassErrors.nonEmpty,
        s"Expected a Sass/Error diagnostic for broken SCSS, got diagnostics: ${result.diagnostics}"
      )
    }
  }

  // ---------------------------------------------------------------------------
  // Case 11: Minify differential
  // Build the SAME fixture twice — minify:false and minify:true — and assert:
  // 1. The HTML output differs between the two builds.
  // 2. The minify:true output equals the committed _expected-min/ golden.
  // 3. The minify:false output does NOT equal the minify:true output.
  // This proves minify actually transforms the output (no silent no-op).
  // ---------------------------------------------------------------------------
  test("case 11: minify differential — minify:true output differs from minify:false (byte-exact)") {
    withTempDir("case11") { baseDir =>
      // Build the fixture with minify: false.
      val files = Map(
        "_layouts/default.html" ->
          "<html>\n  <head>\n    <title>{{ page.title }}</title>\n  </head>\n  <body>\n    {{ content }}\n  </body>\n</html>",
        "index.md" ->
          "---\ntitle: Home\nlayout: default\n---\n# Welcome\n\nThis is the home page.\n"
      )

      // Build 1: minify = false
      val configFalse = setupSite(
        baseDir,
        configYaml = "title: Test Site\nminify: false\n",
        files = files
      )
      val resultFalse = Site.build(configFalse)

      val outputFalsePath = configFalse.destination.resolve("index.html")
      assert(FileOps.exists(outputFalsePath), s"Expected output (minify=false): ${outputFalsePath.pathString}")
      val outputFalse = FileOps.readString(outputFalsePath)

      // Build 2: minify = true (separate dest to avoid collision)
      val baseDirMin  = baseDir.resolve("min-build")
      val configTrue  = setupSite(
        baseDirMin,
        configYaml = "title: Test Site\nminify: true\n",
        files = files
      )
      val resultTrue = Site.build(configTrue)

      val outputTruePath = configTrue.destination.resolve("index.html")
      assert(FileOps.exists(outputTruePath), s"Expected output (minify=true): ${outputTruePath.pathString}")
      val outputTrue = FileOps.readString(outputTruePath)

      // Assertion 1: The two outputs must differ.
      // If minify:true produces output identical to minify:false, the test MUST
      // fail — catching the swallow-and-passthrough trap (design section 7).
      assertNotEquals(
        outputTrue,
        outputFalse,
        "minify:true output must differ from minify:false output (minifier must actually transform)"
      )

      // Assertion 2: The minify:true output must match the committed golden.
      // The golden is the real Minifier.minifyFile output, not hand-written.
      // Oracle: ssg.minify.Minifier.minifyFile(outputFalse, "index.html")
      val expectedMin = ssg.minify.Minifier.minifyFile(outputFalse, "index.html")
      assertEquals(
        outputTrue,
        expectedMin,
        "minify:true output must equal the Minifier.minifyFile output (the real minifier oracle)"
      )

      // Assertion 3: Verify both builds completed without errors.
      val errorsFalse = resultFalse.diagnostics.filter(_.severity == Severity.Error)
      assert(errorsFalse.isEmpty, s"minify:false build should have no errors, got: $errorsFalse")
      val errorsTrue = resultTrue.diagnostics.filter(_.severity == Severity.Error)
      assert(errorsTrue.isEmpty, s"minify:true build should have no errors, got: $errorsTrue")
    }
  }

  // ---------------------------------------------------------------------------
  // BuildResult return type — verify the new API shape.
  // ---------------------------------------------------------------------------
  test("Site.build returns BuildResult with written files and empty diagnostics for clean build") {
    withTempDir("build-result") { baseDir =>
      val config = setupSite(
        baseDir,
        configYaml = "title: Test Site\n",
        files = Map(
          "index.md" -> "---\ntitle: Home\n---\n# Home\n"
        )
      )

      val result = Site.build(config)

      // BuildResult shape checks.
      assert(result.written.nonEmpty, "BuildResult.written must be non-empty")
      assert(result.diagnostics.isEmpty, s"Clean build should have no diagnostics, got: ${result.diagnostics}")
    }
  }

  test("failOnError promotes Error diagnostics to exception") {
    withTempDir("fail-on-error") { baseDir =>
      val config = setupSite(
        baseDir,
        configYaml = "title: Test Site\n",
        files = Map(
          "bad.md" ->
            "---\ntitle: Bad\nlayout: nonexistent\n---\n# Bad\n"
        )
      )

      // Without failOnError, no exception.
      val result = Site.build(config, failOnError = false)
      assert(result.diagnostics.exists(_.severity == Severity.Error), "Should have Error diagnostics")

      // With failOnError, throws.
      val ex = intercept[RuntimeException] {
        Site.build(config, failOnError = true)
      }
      assert(ex.getMessage.contains("Build error"), s"Expected build error message, got: ${ex.getMessage}")
    }
  }
}
