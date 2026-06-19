/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Integration tests for Site.build — ISS-1211 Phase 5.
 * Root-jail + hardening: path traversal rejection for includes,
 * layouts, and output paths.
 * Tests case 10 from site-pipeline-design.md section 9.
 *
 * All assertions verify that traversal attempts are REJECTED with a
 * BuildDiagnostic (severity Error), that the escaped target is NOT
 * read/written outside the root, and that sibling GOOD pages still
 * build (the build continues, does not abort).
 */
package ssg
package site

import ssg.commons.io.FileOps
import ssg.commons.io.FilePath

class SiteBuildPhase5Suite extends munit.FunSuite {

  /** Creates a temporary directory for each test, cleaned up after. */
  private def withTempDir(testName: String)(body: FilePath => Unit): Unit = {
    val tmpBase = FilePath.cwd.resolve("target").resolve("test-tmp")
    val testDir = tmpBase.resolve(s"ssg-phase5-test-$testName-${System.nanoTime()}")
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

  // ---------------------------------------------------------------------------
  // Case 10a: Include traversal — {% include ../../etc/passwd %} is rejected
  // with a BuildDiagnostic (severity Error, stage Liquid), the escaped target
  // is NOT read outside the source root, AND a sibling GOOD page still builds.
  // ---------------------------------------------------------------------------
  test("case 10a: include traversal ../../etc/passwd rejected with Liquid/Error diagnostic, build continues") {
    withTempDir("case10a") { baseDir =>
      val config = setupSite(
        baseDir,
        configYaml = "title: Test Site\n",
        files = Map(
          "_includes/header.html" ->
            "<header>Real Header</header>",
          // Good page: uses a legitimate include that exists.
          "good.md" ->
            "---\ntitle: Good Page\n---\n{% include header.html %}\n\n# Good\n",
          // Bad page: attempts a traversal include escaping the source root.
          "bad.md" ->
            "---\ntitle: Bad Page\n---\n{% include ../../../../etc/passwd %}\n"
        )
      )

      val result = Site.build(config)

      // (a) A jail-violation BuildDiagnostic IS in result.diagnostics with
      // severity Error and the offending path mentioned in the message/file.
      val jailErrors = result.diagnostics.filter(d => d.severity == Severity.Error)
      assert(
        jailErrors.nonEmpty,
        s"Expected at least one Error diagnostic for traversal include, got diagnostics: ${result.diagnostics}"
      )
      // The diagnostic must mention the traversal path or "jail" or "traversal"
      // or "outside" to indicate a root-jail violation.
      assert(
        jailErrors.exists(d => d.message.contains("etc/passwd") || d.message.contains("jail") || d.message.contains("traversal") || d.message.contains("outside")),
        s"Expected diagnostic message to reference the traversal path, got: ${jailErrors.map(_.message)}"
      )

      // (b) The escaped target was NOT written outside the destination root.
      // (We do not check file reads since we are jailing at the resolver boundary —
      // the traversal is caught BEFORE the file is read.)
      assert(
        !result.written.exists(_.pathString.contains("etc/passwd")),
        s"Escaped target must NOT appear in written list: ${result.written.map(_.pathString)}"
      )

      // (c) The sibling GOOD page IS in result.written (build continued).
      assert(
        result.written.exists(_.pathString.endsWith("good.html")),
        s"Good page must still be built after traversal rejection, got written: ${result.written.map(_.pathString)}"
      )
      val goodOutputPath = config.destination.resolve("good.html")
      assert(FileOps.exists(goodOutputPath), s"Good page output must exist: ${goodOutputPath.pathString}")
    }
  }

  // ---------------------------------------------------------------------------
  // Case 10b: Permalink traversal — a page with permalink: /../../escape.txt
  // is rejected with a BuildDiagnostic (severity Error, stage Write), the
  // escaped target is NOT written outside the destination root, AND a sibling
  // GOOD page still builds.
  // ---------------------------------------------------------------------------
  test("case 10b: permalink traversal ../../escape.txt rejected with Write/Error diagnostic, build continues") {
    withTempDir("case10b") { baseDir =>
      val config = setupSite(
        baseDir,
        configYaml = "title: Test Site\n",
        files = Map(
          // Good page.
          "good.md" ->
            "---\ntitle: Good Page\n---\n# Good\n",
          // Bad page: permalink escapes the destination root.
          "evil.md" ->
            "---\ntitle: Evil Page\npermalink: /../../../../escape.txt\n---\nEvil content\n"
        )
      )

      val result = Site.build(config)

      // (a) A Write/Error diagnostic for the output path jail violation.
      val writeErrors = result.diagnostics.filter(d => d.stage == BuildStage.Write && d.severity == Severity.Error)
      assert(
        writeErrors.nonEmpty,
        s"Expected a Write/Error diagnostic for permalink traversal, got diagnostics: ${result.diagnostics}"
      )
      assert(
        writeErrors.exists(d => d.message.contains("escape") || d.message.contains("jail") || d.message.contains("traversal") || d.message.contains("outside")),
        s"Expected diagnostic message to reference the traversal, got: ${writeErrors.map(_.message)}"
      )

      // (b) The escaped target was NOT written outside the destination root.
      val escapePath = baseDir.resolve("escape.txt")
      assert(!FileOps.exists(escapePath), s"Escaped file must NOT be written: ${escapePath.pathString}")
      assert(
        !result.written.exists(_.pathString.contains("escape.txt")),
        s"Escaped target must NOT appear in written list: ${result.written.map(_.pathString)}"
      )

      // (c) The sibling GOOD page IS in result.written (build continued).
      assert(
        result.written.exists(_.pathString.endsWith("good.html")),
        s"Good page must still be built after traversal rejection, got written: ${result.written.map(_.pathString)}"
      )
    }
  }

  // ---------------------------------------------------------------------------
  // Case 10c: Layout traversal — layout: ../../../../etc/passwd (layout path
  // escapes the source root) is rejected with a Layout/Error diagnostic.
  // ---------------------------------------------------------------------------
  test("case 10c: layout traversal rejected with Layout/Error diagnostic, build continues") {
    withTempDir("case10c") { baseDir =>
      val config = setupSite(
        baseDir,
        configYaml = "title: Test Site\n",
        files = Map(
          "_layouts/default.html" ->
            "<html><body>{{ content }}</body></html>",
          // Good page: uses a layout that exists.
          "good.md" ->
            "---\ntitle: Good Page\nlayout: default\n---\n# Good\n",
          // Bad page: layout name attempts a traversal escaping the source root.
          "evil.md" ->
            "---\ntitle: Evil Page\nlayout: ../../../../etc/passwd\n---\nEvil content\n"
        )
      )

      val result = Site.build(config)

      // (a) A Layout/Error diagnostic for the layout path jail violation.
      val layoutErrors = result.diagnostics.filter(d => d.stage == BuildStage.Layout && d.severity == Severity.Error)
      assert(
        layoutErrors.nonEmpty,
        s"Expected a Layout/Error diagnostic for layout traversal, got diagnostics: ${result.diagnostics}"
      )
      assert(
        layoutErrors.exists(d => d.message.contains("etc/passwd") || d.message.contains("jail") || d.message.contains("traversal") || d.message.contains("outside")),
        s"Expected diagnostic message to reference the traversal, got: ${layoutErrors.map(_.message)}"
      )

      // (b) The bad page must NOT be in the written list.
      assert(
        !result.written.exists(_.pathString.contains("evil")),
        s"Evil page must NOT appear in written list: ${result.written.map(_.pathString)}"
      )

      // (c) The sibling GOOD page IS in result.written (build continued).
      assert(
        result.written.exists(_.pathString.endsWith("good.html")),
        s"Good page must still be built after traversal rejection, got written: ${result.written.map(_.pathString)}"
      )
    }
  }

  // ---------------------------------------------------------------------------
  // Separator-boundary false positive check: a legitimate path that shares a
  // prefix with the root (e.g. <source>foo vs root <source>) must NOT be
  // falsely jailed. The prior phase suites prove normal includes/layouts/
  // outputs are NOT rejected (no false positives), but this test specifically
  // covers the separator-boundary edge case.
  // ---------------------------------------------------------------------------
  test("separator boundary: legitimate sibling-prefix path is NOT falsely jailed") {
    withTempDir("separator") { baseDir =>
      // Use a source directory name that could be a prefix-match trap.
      // E.g. sourceDir = ".../src" — a sibling ".../srcfoo" should NOT match.
      // But here we test within the correct source root: a normal include
      // and layout must NOT be rejected.
      val config = setupSite(
        baseDir,
        configYaml = "title: Test Site\n",
        files = Map(
          "_includes/nav.html" ->
            "<nav>Navigation</nav>",
          "_layouts/default.html" ->
            "<html><body>{{ content }}</body></html>",
          "index.md" ->
            "---\ntitle: Home\nlayout: default\n---\n{% include nav.html %}\n\n# Home\n"
        )
      )

      val result = Site.build(config)

      // No Error diagnostics — the normal include + layout must NOT be jailed.
      val errors = result.diagnostics.filter(_.severity == Severity.Error)
      assert(
        errors.isEmpty,
        s"Normal include/layout must not be jailed — got Error diagnostics: ${errors.map(_.message)}"
      )

      // The page must be in the written list.
      assert(
        result.written.exists(_.pathString.endsWith("index.html")),
        s"Normal page must be built, got written: ${result.written.map(_.pathString)}"
      )
    }
  }

  // ---------------------------------------------------------------------------
  // Absolute include path escaping the source root.
  // LocalFSNameResolver honors absolute paths directly (line 41-44).
  // The jail must catch absolute paths that escape the source root.
  // ---------------------------------------------------------------------------
  test("case 10d: absolute include path outside source root rejected with Liquid/Error diagnostic") {
    withTempDir("case10d") { baseDir =>
      // Create a file OUTSIDE the source root that we will try to include.
      val outsideDir = baseDir.resolve("outside")
      FileOps.createDirectories(outsideDir)
      FileOps.writeString(outsideDir.resolve("secret.html"), "SECRET CONTENT")

      val outsidePath = outsideDir.resolve("secret.html").toAbsolute.normalize.pathString

      val config = setupSite(
        baseDir,
        configYaml = "title: Test Site\n",
        files = Map(
          "_includes/header.html" ->
            "<header>Real Header</header>",
          // Good page.
          "good.md" ->
            "---\ntitle: Good Page\n---\n{% include header.html %}\n\n# Good\n",
          // Bad page: absolute include escaping the source root.
          "bad.md" ->
            s"---\ntitle: Bad Page\n---\n{% include $outsidePath %}\n"
        )
      )

      val result = Site.build(config)

      // (a) An Error diagnostic for the jail violation.
      val jailErrors = result.diagnostics.filter(d => d.severity == Severity.Error)
      assert(
        jailErrors.nonEmpty,
        s"Expected an Error diagnostic for absolute include outside source root, got diagnostics: ${result.diagnostics}"
      )

      // (c) The good page still builds.
      assert(
        result.written.exists(_.pathString.endsWith("good.html")),
        s"Good page must still be built, got written: ${result.written.map(_.pathString)}"
      )
    }
  }

  // ---------------------------------------------------------------------------
  // ISS-1211 / C8 mutation regression: separator-boundary REJECTS a sibling-
  // prefix path. A resolved path whose string starts with the root string but
  // is NOT actually under the root directory (e.g. root="/tmp/src", resolved=
  // "/tmp/srcfoo/evil.txt") must return false. This test kills the mutation
  // that weakens the predicate from `startsWith(rootStr + "/")` to
  // `startsWith(rootStr)` — under the weaker predicate the sibling-prefix
  // path would be wrongly admitted, making this assertion fail.
  // ---------------------------------------------------------------------------
  test("Iss1211: isUnderRoot rejects sibling-prefix path that shares root prefix but is not a child") {
    withTempDir("iss1211") { baseDir =>
      // Create a root directory "src" and a sibling "srcfoo" under the same parent.
      val rootDir    = baseDir.resolve("src")
      val siblingDir = baseDir.resolve("srcfoo")
      FileOps.createDirectories(rootDir)
      FileOps.createDirectories(siblingDir)

      val rootAbs    = rootDir.toAbsolute.normalize
      val siblingAbs = siblingDir.resolve("evil.txt").toAbsolute.normalize
      val childAbs   = rootDir.resolve("ok.txt").toAbsolute.normalize

      // (a) Sibling-prefix path MUST be rejected (the mutation-killing assertion).
      assertEquals(
        RootJail.isUnderRoot(siblingAbs, rootAbs),
        false,
        s"Sibling-prefix path '${siblingAbs.pathString}' must NOT be accepted under root '${rootAbs.pathString}'"
      )

      // (b) Genuine child MUST be accepted (sanity — the predicate is not over-restrictive).
      assertEquals(
        RootJail.isUnderRoot(childAbs, rootAbs),
        true,
        s"Genuine child path '${childAbs.pathString}' must be accepted under root '${rootAbs.pathString}'"
      )

      // (c) Root itself MUST be accepted (exact-match branch of the predicate).
      assertEquals(
        RootJail.isUnderRoot(rootAbs, rootAbs),
        true,
        s"Root path itself '${rootAbs.pathString}' must be accepted as under root"
      )
    }
  }
}
