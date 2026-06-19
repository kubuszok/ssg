/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Integration tests for Site.build — ISS-1209 Phase 3.
 * Layout chain + includes + permalinks + static passthrough.
 * Tests cases 4-7 from site-pipeline-design.md section 9.
 *
 * All assertions are byte-exact (assertEquals on full produced strings).
 * Tests exercise the real pipeline end-to-end: front matter parsing,
 * Liquid variable resolution, Markdown rendering, layout chain wrapping,
 * include resolution, permalink-driven output paths, and file I/O.
 */
package ssg
package site

import ssg.commons.io.FileOps
import ssg.commons.io.FilePath

class SiteBuildPhase3Suite extends munit.FunSuite {

  /** Creates a temporary directory for each test, cleaned up after. */
  private def withTempDir(testName: String)(body: FilePath => Unit): Unit = {
    val tmpBase = FilePath.cwd.resolve("target").resolve("test-tmp")
    val testDir = tmpBase.resolve(s"ssg-phase3-test-$testName-${System.nanoTime()}")
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
  // Case 4: Nested layout chain
  // A page with `layout: post` -> post.html (which declares `layout: default`)
  // -> default.html (no layout). Proves the full nested chain: default.html
  // wrapper surrounds post.html wrapper surrounds the page body.
  // ---------------------------------------------------------------------------
  test("case 4: nested layout chain post -> default (byte-exact)") {
    withTempDir("case4") { baseDir =>
      val config = setupSite(
        baseDir,
        configYaml = "title: Test Site\n",
        files = Map(
          // default.html: outermost layout, no layout key.
          "_layouts/default.html" ->
            "<html><head><title>{{ page.title }}</title></head><body>{{ content }}</body></html>",
          // post.html: declares layout: default (chains upward).
          "_layouts/post.html" ->
            "---\nlayout: default\n---\n<article>{{ content }}</article>",
          // Page with layout: post. Body is markdown.
          "index.md" ->
            "---\ntitle: Hello World\nlayout: post\n---\n# Welcome\n"
        )
      )

      Site.build(config)

      val outputPath = config.destination.resolve("index.html")
      assert(FileOps.exists(outputPath), s"Expected output file: ${outputPath.pathString}")

      // The render order is:
      // 1. Page body: Liquid (no-op here) -> Markdown: `# Welcome\n` -> `<h1>Welcome</h1>\n`
      // 2. post.html layout: `<article>{{ content }}</article>` with content = `<h1>Welcome</h1>\n`
      //    -> `<article><h1>Welcome</h1>\n</article>`
      // 3. default.html layout: `<html>...<body>{{ content }}</body></html>` with content = above
      //    -> `<html><head><title>Hello World</title></head><body><article><h1>Welcome</h1>\n</article></body></html>`
      val actual   = FileOps.readString(outputPath)
      val expected = "<html><head><title>Hello World</title></head><body><article><h1>Welcome</h1>\n</article></body></html>"
      assertEquals(actual, expected)
    }
  }

  // ---------------------------------------------------------------------------
  // Case 5: Include — {% include header.html %} content appears
  // ISS-1010 is resolved; unquoted dotted include names lex correctly in
  // Jekyll flavor. This is a normal green test, no .fail pin, no ISS-1010
  // citation.
  // ---------------------------------------------------------------------------
  test("case 5: include header.html content appears in output (byte-exact)") {
    withTempDir("case5") { baseDir =>
      val config = setupSite(
        baseDir,
        configYaml = "title: Test Site\n",
        files = Map(
          "_includes/header.html" ->
            "<header>Site Header</header>",
          "_layouts/default.html" ->
            "<html><body>{{ content }}</body></html>",
          "index.md" ->
            "---\ntitle: Home\nlayout: default\n---\n{% include header.html %}\n\n# Home\n"
        )
      )

      Site.build(config)

      val outputPath = config.destination.resolve("index.html")
      assert(FileOps.exists(outputPath), s"Expected output file: ${outputPath.pathString}")

      // The render order is:
      // 1. Liquid resolves {% include header.html %} -> `<header>Site Header</header>`
      //    Body after Liquid: `<header>Site Header</header>\n\n# Home\n`
      // 2. Markdown renders the body. The <header> block is preserved as raw HTML,
      //    the blank line separates it, and `# Home` becomes `<h1>Home</h1>`.
      //    flexmark output: `<header>Site Header</header>\n<h1>Home</h1>\n`
      // 3. Layout wraps: `<html><body>{{ content }}</body></html>`
      val actual   = FileOps.readString(outputPath)
      val expected = "<html><body><header>Site Header</header>\n<h1>Home</h1>\n</body></html>"
      assertEquals(actual, expected)
    }
  }

  // ---------------------------------------------------------------------------
  // Case 6: Permalink — per-page permalink override
  // about.md with `permalink: /about/` -> _site/about/index.html
  // AND page.url == /about/
  // ---------------------------------------------------------------------------
  test("case 6: permalink override about.md -> _site/about/index.html and page.url (byte-exact)") {
    withTempDir("case6") { baseDir =>
      val config = setupSite(
        baseDir,
        configYaml = "title: Test Site\n",
        files = Map(
          "_layouts/default.html" ->
            "<html><body><p>{{ page.url }}</p>{{ content }}</body></html>",
          "about.md" ->
            "---\ntitle: About\nlayout: default\npermalink: /about/\n---\n# About Us\n"
        )
      )

      Site.build(config)

      // The output path must be _site/about/index.html (pretty permalink).
      val outputPath = config.destination.resolve("about").resolve("index.html")
      assert(FileOps.exists(outputPath), s"Expected output file at about/index.html: ${outputPath.pathString}")

      // page.url must be /about/ (the explicit permalink override).
      // The layout renders {{ page.url }} -> `/about/`.
      // Markdown renders `# About Us\n` -> `<h1>About Us</h1>\n`.
      // Layout wraps content.
      val actual   = FileOps.readString(outputPath)
      val expected = "<html><body><p>/about/</p><h1>About Us</h1>\n</body></html>"
      assertEquals(actual, expected)

      // The old source-relative path (about.html) must NOT exist.
      val oldPath = config.destination.resolve("about.html")
      assert(!FileOps.exists(oldPath), s"Old path about.html must not exist: ${oldPath.pathString}")
    }
  }

  // ---------------------------------------------------------------------------
  // Case 7: Static passthrough
  // static.txt (no front matter) copied byte-identical.
  // ---------------------------------------------------------------------------
  test("case 7: static passthrough byte-identical (byte-exact)") {
    withTempDir("case7") { baseDir =>
      val staticContent = "This is static content.\nNo front matter here.\nExact bytes preserved.\n"
      val config = setupSite(
        baseDir,
        configYaml = "title: Test Site\n",
        files = Map(
          "_layouts/default.html" ->
            "<html><body>{{ content }}</body></html>",
          "index.md" ->
            "---\ntitle: Home\nlayout: default\n---\n# Home\n",
          "static.txt" -> staticContent
        )
      )

      Site.build(config)

      // The static file must be copied byte-identical.
      val outputPath = config.destination.resolve("static.txt")
      assert(FileOps.exists(outputPath), s"Expected static output: ${outputPath.pathString}")

      val actual = FileOps.readString(outputPath)
      assertEquals(actual, staticContent)
    }
  }

  // ---------------------------------------------------------------------------
  // Layout cycle detection
  // A layout that references itself (or a cycle a -> b -> a) must throw
  // LayoutCycleException, not infinite-loop.
  // ---------------------------------------------------------------------------
  test("layout cycle detection throws LayoutCycleException") {
    withTempDir("cycle") { baseDir =>
      val config = setupSite(
        baseDir,
        configYaml = "title: Test Site\n",
        files = Map(
          // alpha.html declares layout: beta; beta.html declares layout: alpha -> cycle.
          "_layouts/alpha.html" ->
            "---\nlayout: beta\n---\n<div>alpha:{{ content }}</div>",
          "_layouts/beta.html" ->
            "---\nlayout: alpha\n---\n<div>beta:{{ content }}</div>",
          "page.md" ->
            "---\ntitle: Cycle Test\nlayout: alpha\n---\nBody\n"
        )
      )

      val ex = intercept[Site.LayoutCycleException] {
        Site.build(config)
      }

      // The cycle must include the repeated layout name.
      assert(
        ex.chain.contains("alpha"),
        s"Expected 'alpha' in cycle chain, got: ${ex.chain}"
      )
    }
  }

  // ---------------------------------------------------------------------------
  // Default permalink (no explicit override) — source-relative path
  // index.md -> /index.html, about.md -> /about.html
  // ---------------------------------------------------------------------------
  test("default permalink uses source-relative path (byte-exact)") {
    withTempDir("default-permalink") { baseDir =>
      val config = setupSite(
        baseDir,
        configYaml = "title: Test Site\n",
        files = Map(
          "_layouts/default.html" ->
            "url:{{ page.url }}|{{ content }}",
          "about.md" ->
            "---\ntitle: About\nlayout: default\n---\n# About\n"
        )
      )

      Site.build(config)

      // Without an explicit permalink, about.md -> about.html at the source-relative path.
      val outputPath = config.destination.resolve("about.html")
      assert(FileOps.exists(outputPath), s"Expected output file: ${outputPath.pathString}")

      val actual   = FileOps.readString(outputPath)
      val expected = "url:/about.html|<h1>About</h1>\n"
      assertEquals(actual, expected)
    }
  }

  // ---------------------------------------------------------------------------
  // Single layout (no nesting) — verifies layout application
  // ---------------------------------------------------------------------------
  test("single layout wraps page content (byte-exact)") {
    withTempDir("single-layout") { baseDir =>
      val config = setupSite(
        baseDir,
        configYaml = "title: My Site\n",
        files = Map(
          "_layouts/default.html" ->
            "<html><head><title>{{ page.title }} | {{ site.title }}</title></head><body>{{ content }}</body></html>",
          "index.md" ->
            "---\ntitle: Home\nlayout: default\n---\n# Welcome\n"
        )
      )

      Site.build(config)

      val outputPath = config.destination.resolve("index.html")
      assert(FileOps.exists(outputPath), s"Expected output file: ${outputPath.pathString}")

      val actual   = FileOps.readString(outputPath)
      val expected = "<html><head><title>Home | My Site</title></head><body><h1>Welcome</h1>\n</body></html>"
      assertEquals(actual, expected)
    }
  }

  // ---------------------------------------------------------------------------
  // No layout key — page renders without layout wrapping
  // Proves that the layout chain is optional.
  // ---------------------------------------------------------------------------
  test("page without layout key renders without wrapping (byte-exact)") {
    withTempDir("no-layout") { baseDir =>
      val config = setupSite(
        baseDir,
        configYaml = "title: Test Site\n",
        files = Map(
          "_layouts/default.html" ->
            "<html><body>{{ content }}</body></html>",
          "plain.md" ->
            "---\ntitle: Plain\n---\n# No Layout\n"
        )
      )

      Site.build(config)

      val outputPath = config.destination.resolve("plain.html")
      assert(FileOps.exists(outputPath), s"Expected output file: ${outputPath.pathString}")

      // Without a layout key, the rendered markdown is written directly.
      val actual = FileOps.readString(outputPath)
      assertEquals(actual, "<h1>No Layout</h1>\n")
    }
  }
}
