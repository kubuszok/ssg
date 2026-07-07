/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Site pipeline orchestrator — layout chain, includes, permalinks (ISS-1209),
 * sass conversion, minify integration, diagnostics channel (ISS-1210).
 *
 * This is an SSG-native module (not a port of an external library).
 * See docs/architecture/site-pipeline-design.md sections 2, 3, 6, 7, 9 for design.
 *
 * Scope: SourceScan -> FrontMatter -> Liquid -> Markdown -> LayoutChain
 *        -> SassCompile -> Minify -> OutputWriter, with includes resolved
 *        under _includes/ and permalink-driven output paths.
 * Root-jail + hardening: ISS-1211.
 */
package ssg
package site

import lowlevel.Nullable

import ssg.commons.io.FileOps
import ssg.commons.io.FilePath
import ssg.data.DataView
import ssg.liquid.TemplateParser
import ssg.liquid.antlr.LocalFSNameResolver
import ssg.liquid.tags.IncludeRelative
import ssg.md.html.HtmlRenderer
import ssg.md.parser.Parser
import ssg.minify.Minifier
import ssg.sass.Compile
import ssg.sass.Syntax

import java.util.{ HashMap => JHashMap }

import scala.collection.immutable.VectorMap
import scala.util.boundary
import scala.util.boundary.break

/** Orchestrates the site build pipeline.
  *
  * Composes SourceScan, Liquid, Markdown rendering, sass compilation, and minification into an end-to-end page pipeline. Renders pages through Liquid (with `site.*` + `page.*` variables), then
  * Markdown (for `.md`/`.markdown` files), then wraps in the layout chain (Jekyll nested layouts). `.scss`/`.sass` files with front matter route to the sass converter instead of the Markdown/Liquid
  * path. When `config.minify` is true, produced HTML is run through `Minifier.minifyFile`. Includes are resolved under `_includes/` via `LocalFSNameResolver`. Output paths are derived from the
  * permalink style or an explicit per-page `permalink:` front-matter override.
  *
  * Each stage adapts its engine's native error into a [[BuildDiagnostic]] (design section 7). The build does not crash on a single-page error — it records the diagnostic and continues with remaining
  * pages.
  */
object Site {

  /** The set of file extensions that route through the Markdown pipeline.
    *
    * Per design section 2: `.md` and `.markdown` are the two common Markdown extensions. Other Jekyll `markdown_ext` values (mkdown, mkdn, mkd) are a named follow-up.
    */
  private val markdownExtensions: Set[String] = Set(".md", ".markdown")

  /** The set of file extensions that route through the sass converter.
    *
    * Per design section 2: `.scss` and `.sass` files with front matter (even the empty `---\n---` marker) route to `Compile.compileString`, and the output extension becomes `.css`.
    */
  private val sassExtensions: Set[String] = Set(".scss", ".sass")

  /** Thrown when a layout cycle is detected during layout chain resolution.
    *
    * Propagates to the caller (cycle-detection contract from ISS-1209).
    */
  final class LayoutCycleException(val chain: Vector[String]) extends RuntimeException(s"Layout cycle detected: ${chain.mkString(" -> ")}")

  /** Thrown when a layout file referenced by `layout:` in front matter cannot be found.
    *
    * Caught by the diagnostics channel and adapted into a `BuildDiagnostic(stage = Layout, severity = Error)`. Uses an existence check (`FileOps.exists`) instead of catching platform-specific
    * file-not-found exceptions (JVM/Native throw `NoSuchFileException`; JS throws `JavaScriptException`).
    */
  final class MissingLayoutException(val layoutName: String, val layoutPath: FilePath) extends RuntimeException(s"Layout not found: '$layoutName' (looked for ${layoutPath.pathString})")

  /** Builds the site from the given configuration.
    *
    * Scans the source directory, renders processed files through the Liquid + Markdown + layout-chain + sass + minify pipeline, and writes the result to the destination. Static files are copied
    * verbatim. Includes are resolved under `<source>/<includesDir>/`. Output paths are derived from `page.url` (permalink style or explicit per-page `permalink:` override).
    *
    * Returns a [[BuildResult]] with written file paths and any diagnostics (errors/warnings) collected during the build. Per design section 7 (Q8): the build does not throw on single-page errors — it
    * records them as diagnostics. A `failOnError` param can promote any Error diagnostic to a thrown exception.
    *
    * @param config
    *   the site configuration (source, destination, raw config for site.* vars)
    * @param failOnError
    *   if true, throw a RuntimeException when any Error diagnostic is recorded (default false)
    * @return
    *   the build result with written files and diagnostics
    */
  def build(config: SiteConfig, failOnError: Boolean = false): BuildResult = {
    val sourceAbs = config.source.toAbsolute.normalize
    val destAbs   = config.destination.toAbsolute.normalize

    // Ensure the destination directory exists.
    FileOps.createDirectories(destAbs)

    // Scan the source tree.
    val scanResult = SourceScan.scan(config)

    val writtenBuilder     = Vector.newBuilder[FilePath]
    val diagnosticsBuilder = Vector.newBuilder[BuildDiagnostic]

    // Build the site-level DataView for Liquid `site.*` variables.
    val siteDataView = config.raw

    // Configure the Liquid template parser with Jekyll flavor and an includes
    // resolver rooted at <source>/<includesDir>/ (design section 6).
    // The resolver is wrapped with a RootJail.JailedNameResolver (ISS-1211,
    // ISS-1020) that verifies every resolved include path stays under the
    // source root. The jail pre-checks the path BEFORE the delegate reads
    // the file, preventing traversal reads.
    // include_relative (ISS-1214) uses a different base dir (the page's
    // parent directory, set via sourceLocation on parse) and its own jail
    // check inside IncludeRelative.detectSource (via Template.withJailRoot).
    val includesRootAbs = sourceAbs.resolve(config.includesDir).toAbsolute.normalize
    val includesRoot    = includesRootAbs.pathString
    val baseResolver    = new LocalFSNameResolver(includesRoot)
    val jailedResolver  = new RootJail.JailedNameResolver(baseResolver, includesRootAbs, sourceAbs)
    val liquidParser    = TemplateParser.Builder().withFlavor(config.flavor.liquidFlavor).withNameResolver(jailedResolver).build()

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
      val extLower   = ext.toLowerCase
      val isMarkdown = markdownExtensions.contains(extLower)
      val isSass     = sassExtensions.contains(extLower)

      // Compute the output extension for this file (used by permalink resolution).
      // Per design section 2: .md/.markdown -> .html; .scss/.sass -> .css; others keep extension.
      val outputExt =
        if (isMarkdown) ".html"
        else if (isSass) ".css"
        else ext

      // Compute page.url from the permalink style or explicit per-page override
      // (design section 6 Permalinks Q3 DECIDED).
      val pageUrl = computePageUrl(frontMatter, relativePath, outputExt, config)

      // Derive the output file path from page.url.
      val outputRelativePath = urlToOutputPath(pageUrl)

      // Root-jail check on the output path (ISS-1211, ISS-1020, design section 6).
      // The output path must stay under the destination root. A traversal
      // permalink like /../../escape.txt would escape; reject via §7 diagnostics.
      val outputAbsPath = destAbs.resolve(outputRelativePath).toAbsolute.normalize
      if (!RootJail.isUnderRoot(outputAbsPath, destAbs)) {
        diagnosticsBuilder += BuildDiagnostic(
          file = filePath,
          stage = BuildStage.Write,
          severity = Severity.Error,
          message = s"Output path '${outputAbsPath.pathString}' is outside the destination root '${destAbs.pathString}' (from permalink '${pageUrl}')"
        )
      } else {
        // Build the page-level DataView for Liquid `page.*` variables,
        // including computed page.url and page.path.
        val pageDataView = buildPageDataView(frontMatter, relativePath, pageUrl)

        if (isSass) {
          // Sass route: compile the body through the ssg-sass DiagResult facade
          // Compile.compileStringResult (design section 2 — .scss/.sass with front
          // matter -> sass converter -> .css). The body (after front matter
          // stripping) is the raw SCSS/Sass source. NO markdown step, NO liquid
          // step for sass files.
          //
          // ISS-1382 (error-contracts.md section 2.10 step 3): consume the shared
          // envelope instead of catching SassException here. The facade catches the
          // specific SassException hierarchy (SassFormatException for parse errors,
          // SassRuntimeException for evaluation errors) and turns it into one
          // Severity.Error diagnostic carrying the mapped FileSpan source position
          // and the machine-readable code; success carries CompileResult.warnings as
          // Severity.Warning diagnostics. Each diagnostic is embedded verbatim into a
          // BuildDiagnostic, so positions and codes flow through for free. Failure
          // and success both keep the "record diagnostic, continue" contract; a
          // non-SassException (genuine bug) still propagates from the facade.
          val sassSyntax = if (extLower == ".sass") Syntax.Sass else Syntax.Scss
          Compile
            .compileStringResult(body, syntax = sassSyntax)
            .fold(
              diags =>
                diags.foreach { diag =>
                  diagnosticsBuilder += BuildDiagnostic(file = filePath, stage = BuildStage.Sass, diagnostic = diag)
                },
              (result, diags) => {
                // Record any sass compilation warnings (design section 7:
                // CompileResult.warnings -> Warning) — surfaced by the facade.
                diags.foreach { diag =>
                  diagnosticsBuilder += BuildDiagnostic(file = filePath, stage = BuildStage.Sass, diagnostic = diag)
                }

                // Write the compiled CSS output.
                OutputWriter.write(destAbs, outputRelativePath, result.css)
                writtenBuilder += destAbs.resolve(outputRelativePath)
              }
            )
        } else {
          // Page route: Liquid -> Markdown (if .md/.markdown) -> Layout chain.
          try {
            // Build the Liquid render variable map: site.* + page.*
            val variables = new JHashMap[String, DataView]()
            variables.put("site", siteDataView)
            variables.put("page", pageDataView)

            // Step 1: Render through Liquid.
            // Parse with the page's file path as sourceLocation so that
            // include_relative resolves relative to the page's parent dir
            // (page-local base, Jekyll semantics — ISS-1214). The jail root
            // is set to sourceAbs (config.source) so that include_relative
            // paths cannot escape the source root (design §6).
            val template    = liquidParser.parse(body, filePath).withJailRoot(sourceAbs)
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
            val afterLayout = applyLayoutChain(
              afterMarkdown,
              frontMatter,
              pageDataView,
              siteDataView,
              sourceAbs,
              config,
              liquidParser
            )

            // Step 4: Minify — if config.minify is true, run produced HTML
            // through Minifier.minifyFile (design sections 3, 7, 10).
            // Minify is off by default (Q10 DECIDED).
            val rendered = if (config.minify) {
              minifyContent(afterLayout, outputRelativePath, filePath, diagnosticsBuilder)
            } else {
              afterLayout
            }

            // Write the rendered output.
            OutputWriter.write(destAbs, outputRelativePath, rendered)
            writtenBuilder += destAbs.resolve(outputRelativePath)
          } catch {
            // Missing layout file — caught via the cross-platform
            // MissingLayoutException thrown by applyLayoutChain when
            // FileOps.exists returns false.
            // Per design section 7: Layout/Error diagnostic, build continues.
            // LayoutCycleException is NOT caught here — it still propagates
            // (the cycle-detection contract from ISS-1209 is preserved).
            case e: MissingLayoutException =>
              diagnosticsBuilder += BuildDiagnostic(
                file = filePath,
                stage = BuildStage.Layout,
                severity = Severity.Error,
                message = e.getMessage,
                cause = Nullable(e)
              )
            // Root-jail violation from layout resolution (ISS-1211, ISS-1020).
            // checkLayoutPath throws RootJailViolationException when a layout
            // path escapes the source root.
            case e: RootJail.RootJailViolationException =>
              diagnosticsBuilder += BuildDiagnostic(
                file = filePath,
                stage = BuildStage.Layout,
                severity = Severity.Error,
                message = e.getMessage,
                cause = Nullable(e)
              )
            // Root-jail violation from include resolution (ISS-1211, ISS-1020).
            // The JailedNameResolver throws RootJailViolationException when an
            // include path escapes the source root. The Liquid Include tag wraps
            // all exceptions in a RuntimeException("problem with evaluating
            // include", cause) when showExceptionsFromInclude is true (the
            // default). Check the cause chain for the jail violation.
            case e: RuntimeException if findJailViolation(e).isDefined =>
              val jailEx = findJailViolation(e).get
              diagnosticsBuilder += BuildDiagnostic(
                file = filePath,
                stage = BuildStage.Liquid,
                severity = Severity.Error,
                message = jailEx.getMessage,
                cause = Nullable(jailEx)
              )
            // Root-jail violation from include_relative resolution (ISS-1214).
            // IncludeRelative.detectSource throws JailViolationException when an
            // include_relative path escapes the source root. Like the include
            // jail, the Include tag wraps exceptions in RuntimeException when
            // showExceptionsFromInclude is true. Check the cause chain.
            case e: RuntimeException if findIncludeRelativeJailViolation(e).isDefined =>
              val jailEx = findIncludeRelativeJailViolation(e).get
              diagnosticsBuilder += BuildDiagnostic(
                file = filePath,
                stage = BuildStage.Liquid,
                severity = Severity.Error,
                message = jailEx.getMessage,
                cause = Nullable(jailEx)
              )
          }
        }
      }
    }

    // Copy static files verbatim.
    scanResult.static.foreach { filePath =>
      val relativePath = SourceScan.relativize(sourceAbs, filePath)
      OutputWriter.copyStatic(destAbs, relativePath, filePath)
      writtenBuilder += destAbs.resolve(relativePath)
    }

    val result = BuildResult(
      written = writtenBuilder.result(),
      diagnostics = diagnosticsBuilder.result()
    )

    // failOnError: promote any Error diagnostic to a thrown exception (Q8 default = return).
    if (failOnError) {
      result.diagnostics.find(_.severity == Severity.Error).foreach { diag =>
        throw new RuntimeException(s"Build error in ${diag.file.pathString} at stage ${diag.stage}: ${diag.message}")
      }
    }

    result
  }

  /** Runs the minify step on rendered content, recording a Warning diagnostic if it fails.
    *
    * Per design section 7: no silent swallow — if minify fails or is skipped, a Warning diagnostic is recorded so a build never silently ships unminified assets without a trace.
    */
  private def minifyContent(
    content:            String,
    outputRelativePath: String,
    sourceFile:         FilePath,
    diagnosticsBuilder: scala.collection.mutable.Builder[BuildDiagnostic, Vector[BuildDiagnostic]]
  ): String =
    try
      Minifier.minifyFile(content, outputRelativePath)
    catch {
      // Catch the specific exception types that the minify engines throw.
      // HtmlMinifier, CssMinifier, JsMinifier, JsonMinifier can throw
      // various parsing exceptions. Record a Warning diagnostic and
      // yield the original content (no silent swallow — the warning
      // is the trace).
      case e: java.lang.IllegalArgumentException =>
        diagnosticsBuilder += BuildDiagnostic(
          file = sourceFile,
          stage = BuildStage.Minify,
          severity = Severity.Warning,
          message = s"Minification failed for $outputRelativePath: ${e.getMessage}",
          cause = Nullable(e)
        )
        content
      case e: java.lang.IllegalStateException =>
        diagnosticsBuilder += BuildDiagnostic(
          file = sourceFile,
          stage = BuildStage.Minify,
          severity = Severity.Warning,
          message = s"Minification failed for $outputRelativePath: ${e.getMessage}",
          cause = Nullable(e)
        )
        content
      case e: java.io.IOException =>
        diagnosticsBuilder += BuildDiagnostic(
          file = sourceFile,
          stage = BuildStage.Minify,
          severity = Severity.Warning,
          message = s"Minification failed for $outputRelativePath: ${e.getMessage}",
          cause = Nullable(e)
        )
        content
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
      // Root-jail check on the layout path (ISS-1211, ISS-1020, design section 6).
      // The layout path must stay under the source root. A layout name like
      // "../../../../etc/passwd" would escape; reject via RootJailViolationException.
      RootJail.checkLayoutPath(layoutPath, sourceAbs)
      // Check existence before reading — cross-platform safe (JVM/Native throw
      // NoSuchFileException, JS throws JavaScriptException; FileOps.exists works
      // uniformly on all three platforms).
      if (!FileOps.exists(layoutPath)) {
        throw new MissingLayoutException(name, layoutPath)
      }
      val layoutRaw = FileOps.readString(layoutPath)

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
      // Parse with layoutPath as sourceLocation so that any
      // include_relative in a layout resolves relative to the
      // layout file's parent dir (ISS-1214). Jail root = sourceAbs.
      val template = liquidParser.parse(layoutBody, layoutPath).withJailRoot(sourceAbs)
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

  /** Walks the cause chain of a throwable to find a [[RootJail.RootJailViolationException]].
    *
    * The Liquid Include tag wraps all exceptions in `RuntimeException("problem with evaluating include", cause)` when `showExceptionsFromInclude` is true. This helper unwraps that wrapping to extract
    * the jail violation exception from the cause chain.
    *
    * @return
    *   `Some(jailException)` if found, `scala.None` otherwise
    */
  private def findJailViolation(e: Throwable): Option[RootJail.RootJailViolationException] =
    boundary {
      var current: Option[Throwable] = Option(e)
      // Walk at most 10 levels to avoid infinite loops in pathological cause chains.
      var depth = 0
      while (current.isDefined && depth < 10)
        current.get match {
          case jv: RootJail.RootJailViolationException => break(Some(jv))
          case other =>
            current = Option(other.getCause)
            depth += 1
        }
      scala.None
    }

  /** Walks the cause chain of a throwable to find an [[IncludeRelative.JailViolationException]].
    *
    * Same cause-chain unwrapping as [[findJailViolation]], but for the `include_relative` jail (ISS-1214). The Include tag wraps exceptions identically regardless of whether the underlying tag is
    * `include` or `include_relative`.
    *
    * @return
    *   `Some(jailException)` if found, `scala.None` otherwise
    */
  private def findIncludeRelativeJailViolation(e: Throwable): Option[IncludeRelative.JailViolationException] =
    boundary {
      var current: Option[Throwable] = Option(e)
      // Walk at most 10 levels to avoid infinite loops in pathological cause chains.
      var depth = 0
      while (current.isDefined && depth < 10)
        current.get match {
          case jv: IncludeRelative.JailViolationException => break(Some(jv))
          case other =>
            current = Option(other.getCause)
            depth += 1
        }
      scala.None
    }
}
