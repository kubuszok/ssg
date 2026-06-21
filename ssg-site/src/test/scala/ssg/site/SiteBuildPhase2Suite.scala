/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Integration tests for Site.build — ISS-1208 Phase 2.
 * Single-page render, no layout. Tests cases 1-3 from the
 * site-pipeline-design.md section 9, interpreted at the page-body level
 * (no layout wrapping — that is ISS-1209 Phase 3).
 *
 * All assertions are byte-exact (assertEquals on full produced strings).
 * Tests exercise the real pipeline end-to-end: front matter parsing,
 * Liquid variable resolution, Markdown rendering, and file I/O.
 */
package ssg
package site

import ssg.commons.io.FileOps
import ssg.commons.io.FilePath

class SiteBuildPhase2Suite extends munit.FunSuite {

  /** Creates a temporary directory for each test, cleaned up after.
    *
    * Uses FilePath.cwd as the base (cross-platform safe: works on JVM, JS via process.cwd(), and Native). The directory is created under `target/` to keep test artifacts out of the source tree.
    */
  private def withTempDir(testName: String)(body: FilePath => Unit): Unit = {
    val tmpBase = FilePath.cwd.resolve("target").resolve("test-tmp")
    val testDir = tmpBase.resolve(s"ssg-phase2-test-$testName-${System.nanoTime()}")
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

    // Write the _config.yml (consumed by SiteConfig.load, not placed in source).
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
  // Case 1: Markdown + content variable
  // A .md page with a markdown body renders to HTML.
  // `# Hello` -> `<h1>Hello</h1>\n`
  // ---------------------------------------------------------------------------
  test("case 1: markdown page renders to HTML (byte-exact)") {
    withTempDir("case1") { baseDir =>
      val config = setupSite(
        baseDir,
        configYaml = "title: Test Site\n",
        files = Map(
          "index.md" -> "---\ntitle: Home\n---\n# Hello\n"
        )
      )

      val written = Site.build(config).written

      // Verify the output file was written.
      val outputPath = config.destination.resolve("index.html")
      assert(FileOps.exists(outputPath), s"Expected output file: ${outputPath.pathString}")

      // Byte-exact assertion: flexmark renders `# Hello\n` as `<h1>Hello</h1>\n`.
      val actual = FileOps.readString(outputPath)
      assertEquals(actual, "<h1>Hello</h1>\n")

      // Verify the written list includes the output path.
      assert(written.nonEmpty, "Site.build must return at least one written file")
    }
  }

  // ---------------------------------------------------------------------------
  // Case 2: page.title front-matter variable
  // `{{ page.title }}` in the page body resolves the front-matter title.
  // Proves the FrontMatterBridge end-to-end through the render var map.
  // ---------------------------------------------------------------------------
  test("case 2: page.title front-matter variable resolves (byte-exact)") {
    withTempDir("case2") { baseDir =>
      val config = setupSite(
        baseDir,
        configYaml = "title: Test Site\n",
        files = Map(
          "about.md" -> "---\ntitle: About Page\n---\n# {{ page.title }}\n"
        )
      )

      Site.build(config)

      val outputPath = config.destination.resolve("about.html")
      assert(FileOps.exists(outputPath), s"Expected output file: ${outputPath.pathString}")

      // Liquid resolves `{{ page.title }}` to "About Page" first,
      // then Markdown renders `# About Page\n` to `<h1>About Page</h1>\n`.
      val actual = FileOps.readString(outputPath)
      assertEquals(actual, "<h1>About Page</h1>\n")
    }
  }

  // ---------------------------------------------------------------------------
  // Case 3: site.title config variable
  // `{{ site.title }}` resolves from SiteConfig.raw.
  // Proves config load + site.* exposure through the Liquid render var map.
  // ---------------------------------------------------------------------------
  test("case 3: site.title config variable resolves (byte-exact)") {
    withTempDir("case3") { baseDir =>
      val config = setupSite(
        baseDir,
        configYaml = "title: My Awesome Site\n",
        files = Map(
          "index.md" -> "---\ntitle: Home\n---\n# {{ site.title }}\n"
        )
      )

      Site.build(config)

      val outputPath = config.destination.resolve("index.html")
      assert(FileOps.exists(outputPath), s"Expected output file: ${outputPath.pathString}")

      // Liquid resolves `{{ site.title }}` to "My Awesome Site",
      // then Markdown renders `# My Awesome Site\n` to `<h1>My Awesome Site</h1>\n`.
      val actual = FileOps.readString(outputPath)
      assertEquals(actual, "<h1>My Awesome Site</h1>\n")
    }
  }

  // ---------------------------------------------------------------------------
  // Additional pipeline composition tests (ISS-1208).
  // ---------------------------------------------------------------------------

  test("static file without front matter is copied verbatim (byte-exact)") {
    withTempDir("static") { baseDir =>
      val staticContent = "This is a plain text file.\nNo front matter.\n"
      val config        = setupSite(
        baseDir,
        configYaml = "title: Test Site\n",
        files = Map(
          "static.txt" -> staticContent
        )
      )

      Site.build(config)

      val outputPath = config.destination.resolve("static.txt")
      assert(FileOps.exists(outputPath), s"Expected static output: ${outputPath.pathString}")

      val actual = FileOps.readString(outputPath)
      assertEquals(actual, staticContent)
    }
  }

  test("underscore-prefixed top-level directories are excluded") {
    withTempDir("underscore") { baseDir =>
      val config = setupSite(
        baseDir,
        configYaml = "title: Test Site\n",
        files = Map(
          "index.md" -> "---\ntitle: Home\n---\n# Home\n",
          "_layouts/default.html" -> "<html>{{ content }}</html>",
          "_includes/header.html" -> "<header>Header</header>"
        )
      )

      val scanResult = SourceScan.scan(config)

      // index.md should be processed; _layouts/ and _includes/ entries should be excluded.
      assertEquals(scanResult.processed.size, 1)
      assert(
        scanResult.processed.head.pathString.contains("index.md"),
        s"Expected index.md in processed, got: ${scanResult.processed.head.pathString}"
      )

      // The underscore-prefixed files should be in the excluded bucket.
      assert(
        scanResult.excluded.size >= 2,
        s"Expected at least 2 excluded entries for _layouts/ and _includes/, got: ${scanResult.excluded.size}"
      )
    }
  }

  test("dotfiles are excluded from output") {
    withTempDir("dotfiles") { baseDir =>
      val config = setupSite(
        baseDir,
        configYaml = "title: Test Site\n",
        files = Map(
          "index.md" -> "---\ntitle: Home\n---\n# Home\n",
          ".gitignore" -> "*.class\n"
        )
      )

      val scanResult = SourceScan.scan(config)

      // Only index.md should be processed; .gitignore should be excluded.
      assertEquals(scanResult.processed.size, 1)
      assert(
        scanResult.excluded.exists(_.pathString.contains(".gitignore")),
        "Expected .gitignore in excluded bucket"
      )
    }
  }

  test("both page.title and site.title resolve in the same page (byte-exact)") {
    withTempDir("both-vars") { baseDir =>
      val config = setupSite(
        baseDir,
        configYaml = "title: Site Title\n",
        files = Map(
          "index.md" -> "---\ntitle: Page Title\n---\n{{ page.title }} on {{ site.title }}\n"
        )
      )

      Site.build(config)

      val outputPath = config.destination.resolve("index.html")
      val actual     = FileOps.readString(outputPath)

      // Liquid resolves both variables first, then the result is plain text
      // (no markdown heading), so Markdown wraps it in a <p> tag.
      assertEquals(actual, "<p>Page Title on Site Title</p>\n")
    }
  }

  test("markdown extension .markdown is treated same as .md (byte-exact)") {
    withTempDir("dotmarkdown") { baseDir =>
      val config = setupSite(
        baseDir,
        configYaml = "title: Test Site\n",
        files = Map(
          "page.markdown" -> "---\ntitle: Markdown Page\n---\n# {{ page.title }}\n"
        )
      )

      Site.build(config)

      // .markdown -> .html
      val outputPath = config.destination.resolve("page.html")
      assert(FileOps.exists(outputPath), s"Expected output file: ${outputPath.pathString}")

      val actual = FileOps.readString(outputPath)
      assertEquals(actual, "<h1>Markdown Page</h1>\n")
    }
  }

  test("file in subdirectory preserves relative path in output (byte-exact)") {
    withTempDir("subdir") { baseDir =>
      val config = setupSite(
        baseDir,
        configYaml = "title: Test Site\n",
        files = Map(
          "docs/guide.md" -> "---\ntitle: Guide\n---\n# {{ page.title }}\n"
        )
      )

      Site.build(config)

      val outputPath = config.destination.resolve("docs").resolve("guide.html")
      assert(FileOps.exists(outputPath), s"Expected output file: ${outputPath.pathString}")

      val actual = FileOps.readString(outputPath)
      assertEquals(actual, "<h1>Guide</h1>\n")
    }
  }
}
