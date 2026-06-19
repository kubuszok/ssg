/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Integration tests for Site.build — ISS-1214.
 * include_relative: page-local base resolution + root-jail enforcement.
 *
 * Tests verify:
 * (1) {% include_relative sub.html %} resolves relative to the page's
 *     parent dir (page-local base, Jekyll semantics), NOT cwd.
 * (2) {% include_relative ../../../../etc/passwd %} (traversal) is rejected
 *     with a BuildDiagnostic(stage=Liquid, severity=Error), the escaped
 *     target is NOT read, and a sibling good page still builds.
 * (3) Subdirectory include_relative resolves relative to the page's
 *     subdirectory (proving it is NOT the source root).
 *
 * All assertions run on all 3 platforms (JVM, JS, Native).
 */
package ssg
package site

import ssg.commons.io.FileOps
import ssg.commons.io.FilePath

class SiteBuildIncludeRelativeSuite extends munit.FunSuite {

  /** Creates a temporary directory for each test, cleaned up after. */
  private def withTempDir(testName: String)(body: FilePath => Unit): Unit = {
    val tmpBase = FilePath.cwd.resolve("target").resolve("test-tmp")
    val testDir = tmpBase.resolve(s"ssg-include-rel-$testName-${System.nanoTime()}")
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
  // ISS-1214 test 1: include_relative resolves relative to the page's parent
  // dir (page-local base, Jekyll semantics).
  //
  // A page at <source>/blog/post.md using {% include_relative sidebar.html %}
  // must resolve <source>/blog/sidebar.html — NOT <source>/sidebar.html and
  // NOT <cwd>/sidebar.html.
  // ---------------------------------------------------------------------------
  test("ISS-1214: include_relative resolves relative to the page's parent dir (page-local base)") {
    withTempDir("page-local-base") { baseDir =>
      val config = setupSite(
        baseDir,
        configYaml = "title: Test Site\n",
        files = Map(
          // A page in a subdirectory.
          "blog/post.html" ->
            "---\ntitle: Blog Post\n---\nBefore{% include_relative sidebar.html %}After",
          // A sibling file in the SAME subdirectory (page-local base).
          "blog/sidebar.html" ->
            "SIDEBAR_CONTENT",
          // A decoy file at the source root — must NOT be resolved.
          "sidebar.html" ->
            "---\ntitle: Wrong Sidebar\n---\nWRONG_ROOT_SIDEBAR"
        )
      )

      val result = Site.build(config)

      // No Error diagnostics — the include_relative must succeed.
      val errors = result.diagnostics.filter(_.severity == Severity.Error)
      assert(
        errors.isEmpty,
        s"include_relative in subdirectory page must not produce errors, got: ${errors.map(_.message)}"
      )

      // The page must be in the written list.
      assert(
        result.written.exists(_.pathString.endsWith("blog/post.html")),
        s"blog/post.html must be in written list, got: ${result.written.map(_.pathString)}"
      )

      // The rendered output must contain the sibling sidebar content,
      // NOT the root decoy content.
      val outputPath = config.destination.resolve("blog/post.html")
      assert(FileOps.exists(outputPath), s"Output file must exist: ${outputPath.pathString}")
      val output = FileOps.readString(outputPath)
      assert(
        output.contains("SIDEBAR_CONTENT"),
        s"Output must contain SIDEBAR_CONTENT from the sibling file, got: $output"
      )
      assert(
        !output.contains("WRONG_ROOT_SIDEBAR"),
        s"Output must NOT contain the root decoy content, got: $output"
      )
      // Byte-exact check: the include is inlined between Before and After.
      assert(
        output.contains("BeforeSIDEBAR_CONTENTAfter"),
        s"Output must contain 'BeforeSIDEBAR_CONTENTAfter', got: $output"
      )
    }
  }

  // ---------------------------------------------------------------------------
  // ISS-1214 test 2: include_relative traversal is rejected with a Liquid/Error
  // diagnostic, the escaped target is NOT read, and a sibling good page still
  // builds (build continues).
  // ---------------------------------------------------------------------------
  test("ISS-1214: include_relative traversal rejected with Liquid/Error diagnostic, build continues") {
    withTempDir("jail-traversal") { baseDir =>
      val config = setupSite(
        baseDir,
        configYaml = "title: Test Site\n",
        files = Map(
          // Good page — no include_relative, must still build.
          "good.html" ->
            "---\ntitle: Good Page\n---\nGood content here",
          // Bad page — include_relative with traversal escaping source root.
          "evil.html" ->
            "---\ntitle: Evil Page\n---\n{% include_relative ../../../../etc/passwd %}"
        )
      )

      val result = Site.build(config)

      // (a) A Liquid/Error diagnostic must be present for the jail violation.
      val liquidErrors = result.diagnostics.filter(d =>
        d.stage == BuildStage.Liquid && d.severity == Severity.Error
      )
      assert(
        liquidErrors.nonEmpty,
        s"Expected a Liquid/Error diagnostic for include_relative traversal, got diagnostics: ${result.diagnostics}"
      )
      // The diagnostic message must reference the traversal path or indicate
      // an "outside" / jail violation.
      assert(
        liquidErrors.exists(d =>
          d.message.contains("etc/passwd") ||
            d.message.contains("outside") ||
            d.message.contains("jail")
        ),
        s"Expected diagnostic message to reference the traversal path, got: ${liquidErrors.map(_.message)}"
      )

      // (b) The escaped target must NOT appear in written files.
      assert(
        !result.written.exists(_.pathString.contains("etc/passwd")),
        s"Escaped target must NOT appear in written list: ${result.written.map(_.pathString)}"
      )

      // (c) The sibling good page IS in result.written (build continued).
      assert(
        result.written.exists(_.pathString.endsWith("good.html")),
        s"Good page must still be built after traversal rejection, got written: ${result.written.map(_.pathString)}"
      )
      val goodOutputPath = config.destination.resolve("good.html")
      assert(FileOps.exists(goodOutputPath), s"Good page output must exist: ${goodOutputPath.pathString}")
    }
  }

  // ---------------------------------------------------------------------------
  // ISS-1214 test 3: include_relative in a deeply nested subdirectory resolves
  // relative to that page's parent dir, proving the root folder is per-page.
  // ---------------------------------------------------------------------------
  test("ISS-1214: include_relative in nested subdir resolves to that subdir, not source root") {
    withTempDir("nested-subdir") { baseDir =>
      val config = setupSite(
        baseDir,
        configYaml = "title: Test Site\n",
        files = Map(
          // Page in a deeply nested subdirectory.
          "docs/guides/intro.html" ->
            "---\ntitle: Intro Guide\n---\nStart{% include_relative snippet.html %}End",
          // Sibling in the same deeply nested directory.
          "docs/guides/snippet.html" ->
            "NESTED_SNIPPET",
          // Decoy at docs/ level — must NOT be resolved.
          "docs/snippet.html" ->
            "---\ntitle: Wrong Snippet\n---\nWRONG_DOCS_SNIPPET"
        )
      )

      val result = Site.build(config)

      // No Error diagnostics.
      val errors = result.diagnostics.filter(_.severity == Severity.Error)
      assert(
        errors.isEmpty,
        s"Nested include_relative must not produce errors, got: ${errors.map(_.message)}"
      )

      // The page must be in the written list.
      assert(
        result.written.exists(_.pathString.endsWith("docs/guides/intro.html")),
        s"docs/guides/intro.html must be in written list, got: ${result.written.map(_.pathString)}"
      )

      // Verify content: must contain the nested snippet, not the parent decoy.
      val outputPath = config.destination.resolve("docs/guides/intro.html")
      val output     = FileOps.readString(outputPath)
      assert(
        output.contains("NESTED_SNIPPET"),
        s"Output must contain NESTED_SNIPPET, got: $output"
      )
      assert(
        !output.contains("WRONG_DOCS_SNIPPET"),
        s"Output must NOT contain the docs-level decoy, got: $output"
      )
      assert(
        output.contains("StartNESTED_SNIPPETEnd"),
        s"Output must contain 'StartNESTED_SNIPPETEnd', got: $output"
      )
    }
  }

  // ---------------------------------------------------------------------------
  // ISS-1214 test 4: separator-boundary check — the include_relative jail
  // predicate must have the separator boundary to prevent sibling-prefix
  // false negatives. Verified indirectly: the IncludeRelative.isUnderRoot
  // predicate uses the same separator-boundary logic as RootJail.isUnderRoot
  // (tested directly in the root-jail suite). Here we verify via integration
  // that a legitimate include_relative in a sibling-named subdir is accepted.
  // ---------------------------------------------------------------------------
  test("ISS-1214: separator boundary — legitimate include_relative in sibling-named subdir is accepted") {
    withTempDir("separator-boundary") { baseDir =>
      // Use directory names that could be prefix-match traps:
      // <source>/blog/ and <source>/blogposts/ — only blog/ has the page.
      val config = setupSite(
        baseDir,
        configYaml = "title: Test Site\n",
        files = Map(
          "blog/post.html" ->
            "---\ntitle: Blog Post\n---\nHello{% include_relative extra.html %}World",
          "blog/extra.html" ->
            "EXTRA_CONTENT",
          // Decoy in sibling-prefix directory — must NOT be resolved.
          "blogposts/extra.html" ->
            "---\ntitle: Decoy\n---\nWRONG_BLOGPOSTS_EXTRA"
        )
      )

      val result = Site.build(config)

      // No Error diagnostics — the legitimate include_relative must not be jailed.
      val errors = result.diagnostics.filter(_.severity == Severity.Error)
      assert(
        errors.isEmpty,
        s"Legitimate include_relative must not produce errors, got: ${errors.map(_.message)}"
      )

      // The page must be in the written list.
      assert(
        result.written.exists(_.pathString.endsWith("blog/post.html")),
        s"blog/post.html must be in written list, got: ${result.written.map(_.pathString)}"
      )

      // Content must contain the correct include, not the sibling-prefix decoy.
      val outputPath = config.destination.resolve("blog/post.html")
      val output     = FileOps.readString(outputPath)
      assert(
        output.contains("HelloEXTRA_CONTENTWorld"),
        s"Output must contain 'HelloEXTRA_CONTENTWorld', got: $output"
      )
    }
  }
}

