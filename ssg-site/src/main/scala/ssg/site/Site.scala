/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Site pipeline orchestrator — layout chain, includes, permalinks (ISS-1209).
 *
 * This is an SSG-native module (not a port of an external library).
 * See docs/architecture/site-pipeline-design.md sections 3, 6, 9 for design.
 *
 * Scope: SourceScan -> FrontMatter -> Liquid -> Markdown -> LayoutChain
 *        -> OutputWriter, with includes resolved under _includes/ and
 *        permalink-driven output paths.
 * Sass conversion + minify + diagnostics: ISS-1210.
 * Root-jail + hardening: ISS-1211.
 */
package ssg
package site

import ssg.commons.io.FileOps
import ssg.commons.io.FilePath
import ssg.data.DataView
import ssg.liquid.TemplateParser
import ssg.liquid.antlr.LocalFSNameResolver
import ssg.md.html.HtmlRenderer
import ssg.md.parser.Parser

import java.util.{ HashMap => JHashMap }

import scala.collection.immutable.VectorMap

/** Orchestrates the site build pipeline.
  *
  * Composes SourceScan, Liquid, and Markdown rendering into an end-to-end page pipeline. Renders pages through Liquid (with `site.*` + `page.*` variables), then Markdown (for `.md`/`.markdown`
  * files), then wraps in the layout chain (Jekyll nested layouts). Includes are resolved under `_includes/` via `LocalFSNameResolver`. Output paths are derived from the permalink style or an explicit
  * per-page `permalink:` front-matter override.
  */
object Site {

  /** The set of file extensions that route through the Markdown pipeline.
    *
    * Per design section 2: `.md` and `.markdown` are the two common Markdown extensions. Other Jekyll `markdown_ext` values (mkdown, mkdn, mkd) are a named follow-up.
    */
  private val markdownExtensions: Set[String] = Set(".md", ".markdown")

  /** Thrown when a layout cycle is detected during layout chain resolution.
    *
    * The full BuildDiagnostic channel lands in ISS-1210. The cycle-guard uses a thrown exception with a clear message, caught by the integration test.
    */
  final class LayoutCycleException(val chain: Vector[String]) extends RuntimeException(s"Layout cycle detected: ${chain.mkString(" -> ")}")

  /** Builds the site from the given configuration.
    *
    * Scans the source directory, renders processed files through the Liquid + Markdown + layout-chain pipeline, and writes the result to the destination. Static files are copied verbatim. Includes
    * are resolved under `<source>/<includesDir>/`. Output paths are derived from `page.url` (permalink style or explicit per-page `permalink:` override).
    *
    * @param config
    *   the site configuration (source, destination, raw config for site.* vars)
    * @return
    *   the list of output file paths written
    */
  def build(config: SiteConfig): Vector[FilePath] = {
    val sourceAbs = config.source.toAbsolute.normalize
    val destAbs   = config.destination.toAbsolute.normalize

    // Ensure the destination directory exists.
    FileOps.createDirectories(destAbs)

    // Scan the source tree.
    val scanResult = SourceScan.scan(config)

    val writtenBuilder = Vector.newBuilder[FilePath]

    // Build the site-level DataView for Liquid `site.*` variables.
    val siteDataView = config.raw

    // Configure the Liquid template parser with Jekyll flavor and an includes
    // resolver rooted at <source>/<includesDir>/ (design section 6).
    val includesRoot = sourceAbs.resolve(config.includesDir).pathString
    val liquidParser = TemplateParser.Builder().withFlavor(config.flavor.liquidFlavor).withNameResolver(new LocalFSNameResolver(includesRoot)).build()

    // Create the Markdown parser and renderer.
    val mdParser   = Parser.builder().build()
    val mdRenderer = HtmlRenderer.builder().build()

    // Process each file with front matter.
    scanResult.processed.foreach { filePath =>
      val content      = FileOps.readString(filePath)
      val relativePath = SourceScan.relativize(sourceAbs, filePath)

      // Parse front matter.
      val (frontMatter, body) = FrontMatterBridge.parse(content)

      // Determine the output extension based on the input extension.
      val ext        = extractExtension(relativePath)
      val isMarkdown = markdownExtensions.contains(ext.toLowerCase)

      // Compute the output extension for this file (used by permalink resolution).
      val outputExt = if (isMarkdown) ".html" else ext

      // Compute page.url from the permalink style or explicit per-page override
      // (design section 6 Permalinks Q3 DECIDED).
      val pageUrl = computePageUrl(frontMatter, relativePath, outputExt, config)

      // Derive the output file path from page.url.
      val outputRelativePath = urlToOutputPath(pageUrl)

      // Build the page-level DataView for Liquid `page.*` variables,
      // including computed page.url and page.path.
      val pageDataView = buildPageDataView(frontMatter, relativePath, pageUrl)

      // Build the Liquid render variable map: site.* + page.*
      val variables = new JHashMap[String, DataView]()
      variables.put("site", siteDataView)
      variables.put("page", pageDataView)

      // Step 1: Render through Liquid.
      val template    = liquidParser.parse(body)
      val afterLiquid = template.render(variables)

      // Step 2: If markdown file, render through Markdown.
      val afterMarkdown = if (isMarkdown) {
        val document = mdParser.parse(afterLiquid)
        mdRenderer.render(document)
      } else {
        afterLiquid
      }

      // Step 3: Layout chain — wrap the rendered content in layouts
      // (design section 6). Render order: page body first, then wrap
      // upward through nested layouts until a layout has no `layout` key.
      val rendered = applyLayoutChain(
        afterMarkdown,
        frontMatter,
        pageDataView,
        siteDataView,
        sourceAbs,
        config,
        liquidParser
      )

      // Write the rendered output.
      OutputWriter.write(destAbs, outputRelativePath, rendered)
      writtenBuilder += destAbs.resolve(outputRelativePath)
    }

    // Copy static files verbatim.
    scanResult.static.foreach { filePath =>
      val relativePath = SourceScan.relativize(sourceAbs, filePath)
      OutputWriter.copyStatic(destAbs, relativePath, filePath)
      writtenBuilder += destAbs.resolve(relativePath)
    }

    writtenBuilder.result()
  }

  /** Computes `page.url` for a page based on the permalink style and any explicit override.
    *
    * Per design section 6 (Permalinks Q3 DECIDED): an explicit per-page `permalink:` front-matter key takes precedence. Without an explicit override, for regular pages (v1 scope — no collections),
    * the `date` style resolves to the source-relative path with the output extension (since category/date placeholders are empty for non-collection pages).
    *
    * @return
    *   the page URL (root-relative, e.g. `/about/` or `/index.html`)
    */
  private def computePageUrl(
    frontMatter:  DataView,
    relativePath: String,
    outputExt:    String,
    config:       SiteConfig
  ): String = {
    val fmMap = frontMatter.asMap.toOption.getOrElse(VectorMap.empty)
    // Check for an explicit per-page permalink override.
    fmMap.get("permalink").flatMap(_.asString.toOption) match {
      case Some(explicit) =>
        // Honor the explicit permalink (design section 6).
        val normalized = if (explicit.startsWith("/")) explicit else "/" + explicit
        normalized
      case scala.None =>
        // Default: source-relative path with output extension (design section 6:
        // for regular pages, `date` style resolves to source-relative path since
        // category/date placeholders are empty for non-collection pages).
        val outputPath = changeExtension(relativePath, outputExt)
        "/" + outputPath
    }
  }

  /** Converts a `page.url` into a relative output path for writing to the destination directory.
    *
    * Pretty-style URLs ending in `/` map to `<path>/index.html` (e.g. `/about/` -> `about/index.html`). Other URLs map directly (e.g. `/index.html` -> `index.html`).
    */
  private def urlToOutputPath(pageUrl: String): String = {
    // Strip leading slash for relative path.
    val stripped = if (pageUrl.startsWith("/")) pageUrl.substring(1) else pageUrl
    // Pretty permalinks: trailing slash -> append index.html.
    if (stripped.endsWith("/")) {
      stripped + "index.html"
    } else if (stripped.isEmpty) {
      "index.html"
    } else {
      stripped
    }
  }

  /** Applies the layout chain to rendered page content (design section 6).
    *
    * Render order: the page body is already rendered (Liquid + Markdown). This method looks up the `layout` key in front matter, loads the layout template from `<source>/<layoutsDir>/`, renders it
    * with `content` (the page body) + `page` + `site` + `layout` in scope, and chains upward if the layout itself declares a `layout` key. A visited-set guards against cycles.
    *
    * @return
    *   the final rendered content after all layouts have been applied
    */
  private def applyLayoutChain(
    initialContent:  String,
    pageFrontMatter: DataView,
    pageDataView:    DataView,
    siteDataView:    DataView,
    sourceAbs:       FilePath,
    config:          SiteConfig,
    liquidParser:    TemplateParser
  ): String = {
    val layoutKey = config.flavor.layoutKey
    var content   = initialContent
    var currentFm = pageFrontMatter

    // Cycle guard: track visited layout names (design section 6).
    var visited      = Set.empty[String]
    var visitedChain = Vector.empty[String]

    // Walk up the layout chain.
    var layoutName = extractStringFromDataView(currentFm, layoutKey)
    while (layoutName.isDefined) {
      val name = layoutName.get

      // Cycle detection.
      if (visited.contains(name)) {
        throw new LayoutCycleException(visitedChain :+ name)
      }
      visited = visited + name
      visitedChain = visitedChain :+ name

      // Load the layout file from <source>/<layoutsDir>/<name>.html
      // (design section 6).
      val layoutPath = sourceAbs.resolve(config.layoutsDir).resolve(name + ".html")
      val layoutRaw  = FileOps.readString(layoutPath)

      // Parse the layout's own front matter (layouts can have front matter,
      // including a `layout:` key for nested layouts).
      val (layoutFm, layoutBody) = FrontMatterBridge.parse(layoutRaw)

      // Build the variable map for rendering the layout template:
      // content = rendered page/inner-layout body
      // page = the original page's DataView (page.* variables)
      // site = the site-level DataView (site.* variables)
      // layout = the layout's own front matter (layout.* variables)
      val variables = new JHashMap[String, DataView]()
      variables.put("content", DataView.from(content))
      variables.put("page", pageDataView)
      variables.put("site", siteDataView)
      variables.put("layout", layoutFm)

      // Render the layout template through Liquid.
      val template = liquidParser.parse(layoutBody)
      content = template.render(variables)

      // Move up the chain: check if this layout declares its own layout.
      currentFm = layoutFm
      layoutName = extractStringFromDataView(currentFm, layoutKey)
    }

    content
  }

  /** Extracts a string value from a DataView mapping by key. */
  private def extractStringFromDataView(dv: DataView, key: String): Option[String] =
    dv.asMap.toOption.flatMap(_.get(key)).flatMap(_.asString.toOption)

  /** Builds the `page.*` DataView from the front-matter DataView, the relative source path, and the computed page URL.
    *
    * Includes all front-matter keys plus computed `page.path` (source-relative path) and `page.url` (derived from permalink style or explicit override).
    */
  private def buildPageDataView(frontMatter: DataView, relativePath: String, pageUrl: String): DataView = {
    val baseMap: VectorMap[String, DataView] = frontMatter.asMap.toOption.getOrElse(VectorMap.empty)
    // Add computed page.path (the source-relative path).
    val withPath = baseMap.updated("path", DataView.from(relativePath))
    // Add computed page.url (derived from permalink style or explicit override).
    val withUrl = withPath.updated("url", DataView.from(pageUrl))
    DataView.from(withUrl)
  }

  /** Extracts the file extension from a path string (e.g. "index.md" -> ".md"). */
  private def extractExtension(path: String): String = {
    val lastDot = path.lastIndexOf('.')
    if (lastDot >= 0) path.substring(lastDot)
    else ""
  }

  /** Changes the file extension of a path string (e.g. "index.md" -> "index.html"). */
  private def changeExtension(path: String, newExt: String): String = {
    val lastDot = path.lastIndexOf('.')
    if (lastDot >= 0) path.substring(0, lastDot) + newExt
    else path + newExt
  }
}
