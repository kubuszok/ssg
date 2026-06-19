/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Site pipeline orchestrator — single-page render, no layout (ISS-1208).
 *
 * This is an SSG-native module (not a port of an external library).
 * See docs/architecture/site-pipeline-design.md section 3 for design.
 *
 * Scope: SourceScan -> FrontMatter -> Liquid -> Markdown -> OutputWriter
 * for .md/.markdown files.
 * Layout chain + includes + permalinks: ISS-1209.
 * Sass conversion + minify + diagnostics: ISS-1210.
 * Root-jail + hardening: ISS-1211.
 */
package ssg
package site

import ssg.commons.io.FileOps
import ssg.commons.io.FilePath
import ssg.data.DataView
import ssg.liquid.TemplateParser
import ssg.md.html.HtmlRenderer
import ssg.md.parser.Parser

import java.util.{ HashMap => JHashMap }

import scala.collection.immutable.VectorMap

/** Orchestrates the site build pipeline.
  *
  * Composes SourceScan, Liquid, and Markdown rendering into an end-to-end page pipeline. Renders `.md`/`.markdown` files through Liquid (with `site.*` + `page.*` variables) then Markdown, and writes
  * the result to the destination directory.
  */
object Site {

  /** The set of file extensions that route through the Markdown pipeline.
    *
    * Per design section 2: `.md` and `.markdown` are the two common Markdown extensions. Other Jekyll `markdown_ext` values (mkdown, mkdn, mkd) are a named follow-up.
    */
  private val markdownExtensions: Set[String] = Set(".md", ".markdown")

  /** Builds the site from the given configuration.
    *
    * Scans the source directory, renders processed `.md`/`.markdown` files through the Liquid + Markdown pipeline, and writes the result to the destination. Static files are copied verbatim. Files
    * with other extensions that have front matter are processed through Liquid only (no Markdown step), with their extension preserved.
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

    // Create the Liquid template parser with Jekyll flavor.
    val liquidParser = TemplateParser.Builder().withFlavor(config.flavor.liquidFlavor).build()

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
      val ext       = extractExtension(relativePath)
      val isMarkdown = markdownExtensions.contains(ext.toLowerCase)

      // Build the page-level DataView for Liquid `page.*` variables.
      val pageDataView = buildPageDataView(frontMatter, relativePath)

      // Build the Liquid render variable map: site.* + page.*
      val variables = new JHashMap[String, DataView]()
      variables.put("site", siteDataView)
      variables.put("page", pageDataView)

      // Step 1: Render through Liquid.
      val template    = liquidParser.parse(body)
      val afterLiquid = template.render(variables)

      // Step 2: If markdown file, render through Markdown.
      val rendered = if (isMarkdown) {
        val document = mdParser.parse(afterLiquid)
        mdRenderer.render(document)
      } else {
        afterLiquid
      }

      // Compute the output relative path (change extension for markdown files).
      val outputRelativePath = if (isMarkdown) {
        changeExtension(relativePath, ".html")
      } else {
        relativePath
      }

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

  /** Builds the `page.*` DataView from the front-matter DataView and the relative source path.
    *
    * Includes all front-matter keys plus computed `page.path` (source-relative path). Additional computed keys (`page.url`, `page.content`) belong to ISS-1209.
    */
  private def buildPageDataView(frontMatter: DataView, relativePath: String): DataView = {
    val baseMap: VectorMap[String, DataView] = frontMatter.asMap.toOption.getOrElse(VectorMap.empty)
    // Add computed page.path (the source-relative path).
    val withPath = baseMap.updated("path", DataView.from(relativePath))
    DataView.from(withPath)
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
