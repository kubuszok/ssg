/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Differential tests for the ISS-1382 error-contract wiring: ssg-site consumes
 * the shared ssg.commons.Diagnostics envelope (docs/architecture/error-contracts.md
 * section 2.10).
 *
 * These pin the four §2.10 adapter steps:
 *   1. BuildDiagnostic.severity is ssg.commons.Severity (Error maps 1:1, the
 *      wider enum's isAtLeast/Info/Debug are reachable);
 *   2. BuildDiagnostic embeds a ssg.commons.Diagnostic (component "ssg-site" for
 *      native-site stages) with backward-compatible forwarding defs + apply overload;
 *   3. module facades (Compile.compileStringResult) flow position + code + native
 *      cause into BuildDiagnostic.diagnostic "for free";
 *   4. failOnError still promotes any commons-Severity.Error diagnostic to a throw.
 *
 * Failure paths are reached with genuinely-invalid inputs (unclosed SCSS brace,
 * a `layout:` pointing at a nonexistent layout file).
 */
package ssg
package site

import ssg.commons.io.FileOps
import ssg.commons.io.FilePath
import ssg.commons.Diagnostic

import lowlevel.Nullable

class SiteResultFacadeIss1382Suite extends munit.FunSuite {

  /** Creates a temporary directory for each test, cleaned up after. */
  private def withTempDir(testName: String)(body: FilePath => Unit): Unit = {
    val tmpBase = FilePath.cwd.resolve("target").resolve("test-tmp")
    val testDir = tmpBase.resolve(s"ssg-iss1382-$testName-${System.nanoTime()}")
    FileOps.createDirectories(testDir)
    try
      body(testDir)
    finally
      FileOps.deleteRecursively(testDir)
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

  // Step 3: the sass facade (Compile.compileStringResult) flows the mapped
  // FileSpan position, the subclass code, and the native SassException cause
  // into BuildDiagnostic.diagnostic. Genuinely-invalid input: unclosed brace.
  test("ISS-1382: sass compile error flows position + code + native cause into BuildDiagnostic.diagnostic") {
    withTempDir("sass-envelope") { baseDir =>
      val config = setupSite(
        baseDir,
        configYaml = "title: Test Site\n",
        files = Map(
          "assets/broken.scss" -> "---\n---\nbody { color: \n"
        )
      )

      val result = Site.build(config)

      val sassErrors = result.diagnostics.filter(d => d.stage == BuildStage.Sass && d.severity == ssg.commons.Severity.Error)
      assert(sassErrors.nonEmpty, s"Expected a Sass/Error diagnostic, got: ${result.diagnostics}")

      val diag: Diagnostic = sassErrors.head.diagnostic
      // Component stays the producing module's name — the sass Diagnostic is
      // embedded verbatim so its position/code ride along (§2.10 step 3).
      assertEquals(diag.component, "ssg-sass")
      // Position flowed from the SassException's FileSpan (§1.3 sass row).
      assert(diag.position.isDefined, s"Expected a mapped source position, got: ${diag.position}")
      assert(diag.position.exists(_.line.isDefined), s"Expected a 1-based line, got: ${diag.position}")
      // Machine-readable code flowed from the SassException subclass.
      assert(diag.code.isDefined, s"Expected a diagnostic code, got: ${diag.code}")
      // The native exception rides along as the cause (§1.2 rule 5).
      assert(
        diag.cause.exists(_.isInstanceOf[ssg.sass.SassException]),
        s"Expected the native SassException to be preserved as cause, got: ${diag.cause}"
      )
    }
  }

  // Step 2: native-site stages (layout) build a ssg.commons.Diagnostic with
  // component "ssg-site" via the compat apply overload; the forwarding defs
  // keep .message / .severity / .cause working. Invalid input: missing layout.
  test("ISS-1382: native-site diagnostic embeds a commons Diagnostic (component ssg-site) with forwarding defs") {
    withTempDir("site-component") { baseDir =>
      val config = setupSite(
        baseDir,
        configYaml = "title: Test Site\n",
        files = Map(
          "bad.md" -> "---\ntitle: Bad\nlayout: nonexistent\n---\n# Bad\n"
        )
      )

      val result = Site.build(config)

      val layoutErrors = result.diagnostics.filter(d => d.stage == BuildStage.Layout && d.severity == ssg.commons.Severity.Error)
      assert(layoutErrors.nonEmpty, s"Expected a Layout/Error diagnostic, got: ${result.diagnostics}")

      val bd   = layoutErrors.head
      val diag = bd.diagnostic
      assertEquals(diag.component, "ssg-site")
      // No §1.3 row exists for ssg-site, so native-site positions stay None.
      assertEquals(diag.position, None)
      // Forwarding defs delegate to the embedded diagnostic.
      assertEquals(bd.message, diag.message)
      assertEquals(bd.severity, diag.severity)
      // The MissingLayoutException is preserved through the Nullable forwarder.
      assert(bd.cause.toOption.isDefined, "Expected the native throwable via the cause forwarder")
    }
  }

  // Step 1: BuildDiagnostic.severity is the wider ssg.commons.Severity, so
  // Error maps 1:1 and the isAtLeast threshold check is available.
  test("ISS-1382: BuildDiagnostic.severity is ssg.commons.Severity") {
    withTempDir("severity-unify") { baseDir =>
      val config = setupSite(
        baseDir,
        configYaml = "title: Test Site\n",
        files = Map(
          "bad.md" -> "---\ntitle: Bad\nlayout: nonexistent\n---\n# Bad\n"
        )
      )

      val result = Site.build(config)
      val error  = result.diagnostics.find(_.severity == ssg.commons.Severity.Error)
      assert(error.isDefined, s"Expected an Error diagnostic, got: ${result.diagnostics}")
      // The wider enum's threshold API is reachable (proves it is commons.Severity).
      assert(error.get.severity.isAtLeast(ssg.commons.Severity.Warning), "Error must be at least Warning-severe")
      assert(error.get.severity.isAtLeast(ssg.commons.Severity.Error), "Error must be at least Error-severe")
    }
  }

  // Step 4: failOnError keeps its exact meaning against the unified Severity —
  // any commons-Severity.Error diagnostic promotes to a throw.
  test("ISS-1382: failOnError promotes a commons-Severity Error diagnostic to a throw") {
    withTempDir("fail-on-error") { baseDir =>
      val config = setupSite(
        baseDir,
        configYaml = "title: Test Site\n",
        files = Map(
          "bad.md" -> "---\ntitle: Bad\nlayout: nonexistent\n---\n# Bad\n"
        )
      )

      val result = Site.build(config, failOnError = false)
      assert(result.diagnostics.exists(_.severity == ssg.commons.Severity.Error), "Should record an Error diagnostic")

      val ex = intercept[RuntimeException] {
        Site.build(config, failOnError = true)
      }
      assert(ex.getMessage.contains("Build error"), s"Expected a build-error message, got: ${ex.getMessage}")
    }
  }

  // Step 2: the reshaped BuildDiagnostic exposes both the primary
  // apply(file, stage, diagnostic) and the backward-compatible
  // apply(file, stage, severity, message, cause) overload (component "ssg-site").
  test("ISS-1382: BuildDiagnostic apply overloads build and forward consistently") {
    val file = FilePath.cwd.resolve("example.md")

    // Compat overload: builds an embedded ssg-site Diagnostic.
    val viaCompat = BuildDiagnostic(
      file = file,
      stage = BuildStage.Minify,
      severity = ssg.commons.Severity.Warning,
      message = "unoptimized",
      cause = Nullable.empty
    )
    assertEquals(viaCompat.diagnostic.component, "ssg-site")
    assertEquals(viaCompat.severity, ssg.commons.Severity.Warning)
    assertEquals(viaCompat.message, "unoptimized")

    // Primary apply: embeds a pre-built commons Diagnostic verbatim.
    val diag     = Diagnostic.error("ssg-site", "boom")
    val viaEmbed = BuildDiagnostic(file = file, stage = BuildStage.Write, diagnostic = diag)
    assertEquals(viaEmbed.diagnostic, diag)
    assertEquals(viaEmbed.severity, ssg.commons.Severity.Error)
    assertEquals(viaEmbed.message, "boom")
  }
}
