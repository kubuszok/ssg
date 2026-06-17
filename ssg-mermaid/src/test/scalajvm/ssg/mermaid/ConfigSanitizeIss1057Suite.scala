/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Differential test for the ISS-1057 audit failure: the upstream
 * `config.ts sanitize()` stage (config.ts:146-181) was not ported. On the live
 * render path (mermaidAPI.ts:55-57) `configApi.addDirective(processed.config)`
 * runs `sanitize()` over the WHOLE merged (frontmatter + init-directive) config,
 * which:
 *   - drops every key in `['secure', ...siteConfig.secure]` (config.ts:151-158)
 *     so author markup cannot override secure keys (the default secure array in
 *     config.schema.yaml:197-205 is
 *     ['secure','securityLevel','startOnLoad','maxTextSize','suppressErrorRendering','maxEdges']);
 *   - drops `__`-prefixed keys (proto-pollution, config.ts:161-165);
 *   - deletes any string value containing `<`, `>`, or `url(data:`, recursing
 *     into nested objects/arrays (XSS guard, config.ts:168-180).
 *
 * These tests prove the sanitize stage runs on the MERGED overlay (so frontmatter
 * `config:` blocks are sanitized too, FINDING-3), the secure-key drop (FINDING-1),
 * the XSS string filter (FINDING-2), and that a non-secure / non-XSS key (theme)
 * still flows through (regression guard against over-filtering).
 */
package ssg
package mermaid

import munit.FunSuite

import ssg.data.DataView

import scala.collection.immutable.VectorMap

final class ConfigSanitizeIss1057Suite extends FunSuite {

  private def map(pairs: (String, DataView)*): DataView =
    DataView.from(VectorMap.from(pairs))

  // --- FINDING-1: secure-array key drop ---------------------------------

  test("sanitizeConfig drops a secure key (securityLevel) from the overlay") {
    val overlay = map(
      "securityLevel" -> DataView.from("loose"),
      "theme"         -> DataView.from("forest")
    )
    val sanitised = Directives.sanitizeConfig(overlay)
    val keys      = sanitised.asMap.fold[Set[String]](Set.empty)(_.keySet)
    assert(!keys.contains("securityLevel"), s"securityLevel must be dropped (secure key); got keys=$keys")
    assert(keys.contains("theme"), s"theme must survive (non-secure); got keys=$keys")
  }

  test("sanitizeConfig drops every default secure key") {
    val overlay = map(
      "secure"                 -> DataView.from(Vector.empty[DataView]),
      "securityLevel"          -> DataView.from("loose"),
      "startOnLoad"            -> DataView.from(true),
      "maxTextSize"            -> DataView.from(999999),
      "suppressErrorRendering" -> DataView.from(true),
      "maxEdges"               -> DataView.from(99999),
      "theme"                  -> DataView.from("forest")
    )
    val sanitised = Directives.sanitizeConfig(overlay)
    val keys      = sanitised.asMap.fold[Set[String]](Set.empty)(_.keySet)
    Directives.SecureKeys.foreach(k => assert(!keys.contains(k), s"secure key '$k' must be dropped; got keys=$keys"))
    assertEquals(keys, Set("theme"), "only the non-secure theme key may remain")
  }

  // --- FINDING-2: XSS string filter -------------------------------------

  test("sanitizeConfig drops a top-level string value containing '<'") {
    val overlay = map(
      "fontFamily" -> DataView.from("trebuchet<script>"),
      "theme"      -> DataView.from("forest")
    )
    val sanitised = Directives.sanitizeConfig(overlay)
    val keys      = sanitised.asMap.fold[Set[String]](Set.empty)(_.keySet)
    assert(!keys.contains("fontFamily"), s"fontFamily with '<' must be dropped (XSS); got keys=$keys")
    assert(keys.contains("theme"), s"theme must survive; got keys=$keys")
  }

  test("sanitizeConfig drops a string value containing 'url(data:'") {
    val overlay = map("themeCSS" -> DataView.from("background: url(data:image/svg+xml;base64,xxx)"))
    val sanitised = Directives.sanitizeConfig(overlay)
    val keys      = sanitised.asMap.fold[Set[String]](Set.empty)(_.keySet)
    assert(!keys.contains("themeCSS"), s"themeCSS with url(data: must be dropped (XSS); got keys=$keys")
  }

  test("sanitizeConfig recurses into nested objects to drop XSS strings") {
    val overlay = map(
      "flowchart" -> map(
        "evil" -> DataView.from("a>b"),
        "good" -> DataView.from("ok")
      )
    )
    val sanitised = Directives.sanitizeConfig(overlay)
    val nestedKeys: Set[String] =
      sanitised.asMap.fold(Set.empty[String]) { topMap =>
        topMap.get("flowchart").fold(Set.empty[String])(fc => fc.asMap.fold(Set.empty[String])(_.keySet))
      }
    assert(!nestedKeys.contains("evil"), s"nested 'a>b' string must be dropped (XSS recursion); got keys=$nestedKeys")
    assert(nestedKeys.contains("good"), s"nested clean string must survive; got keys=$nestedKeys")
  }

  test("sanitizeConfig drops a __-prefixed key (proto-pollution)") {
    val overlay = map(
      "__proto__" -> DataView.from("x"),
      "theme"     -> DataView.from("forest")
    )
    val sanitised = Directives.sanitizeConfig(overlay)
    val keys      = sanitised.asMap.fold[Set[String]](Set.empty)(_.keySet)
    assert(!keys.contains("__proto__"), s"__proto__ must be dropped; got keys=$keys")
    assert(keys.contains("theme"), s"theme must survive; got keys=$keys")
  }

  // --- FINDING-3: the frontmatter config is sanitized too ---------------
  // cleanAndMerge merges the frontmatter config and the directive; sanitizeConfig
  // then runs over the MERGED overlay, so a secure/XSS key coming from EITHER
  // source is dropped.

  test("sanitizeConfig drops a secure key that arrived via the merged overlay (frontmatter analogue)") {
    // cleanAndMerge(frontmatter, directive): frontmatter carries securityLevel,
    // directive carries theme. The merged overlay is then sanitized once.
    val frontmatter = map("securityLevel" -> DataView.from("loose"))
    val directive   = map("theme" -> DataView.from("forest"))
    val merged      = Directives.cleanAndMerge(lowlevel.Nullable(frontmatter), lowlevel.Nullable(directive))
    val sanitised   = Directives.sanitizeConfig(merged)
    val keys        = sanitised.asMap.fold[Set[String]](Set.empty)(_.keySet)
    assert(!keys.contains("securityLevel"), s"frontmatter securityLevel must be dropped; got keys=$keys")
    assert(keys.contains("theme"), s"directive theme must survive; got keys=$keys")
  }

  // --- Render-level guards (theme still flows; XSS via author markup gone) ---

  private val TimeoutMs = 15000L

  private def renderGuarded(input: String): String = {
    @volatile var result:  Option[String]    = None
    @volatile var failure: Option[Throwable] = None
    val worker = new Thread(() =>
      try result = Some(Mermaid.render(input))
      catch {
        case t: Throwable => failure = Some(t)
      }
    )
    worker.setDaemon(true)
    worker.start()
    worker.join(TimeoutMs)
    if (worker.isAlive) {
      fail(s"Mermaid.render did not terminate within ${TimeoutMs}ms for input:\n$input")
    }
    failure.foreach(t => fail(s"Mermaid.render threw instead of completing: $t", t))
    result.getOrElse(fail("Mermaid.render produced no result"))
  }

  private val flowchartBody = "graph TD\n    A-->B"
  private lazy val defaultSvg = renderGuarded(flowchartBody)

  test("regression: theme:forest still flows through sanitize (directive)") {
    val themed = renderGuarded("%%{init: {'theme':'forest'}}%%\n" + flowchartBody)
    assertNotEquals(themed, defaultSvg, "non-secure theme key must survive sanitize and change the render")
  }

  test("regression: theme:forest still flows through sanitize (frontmatter)") {
    val themed = renderGuarded("---\nconfig:\n  theme: forest\n---\n" + flowchartBody)
    assertNotEquals(themed, defaultSvg, "non-secure frontmatter theme key must survive sanitize and change the render")
  }

  test("XSS themeCSS via directive does not leak a script tag into the SVG") {
    val out = renderGuarded("%%{init: {'themeCSS':'a<script>alert(1)</script>'}}%%\n" + flowchartBody)
    assert(!out.contains("<script>alert(1)"), s"XSS themeCSS must not leak into SVG; got: ${out.take(300)}")
  }

  test("XSS themeCSS via frontmatter does not leak a script tag into the SVG") {
    val out = renderGuarded("---\nconfig:\n  themeCSS: \"a<script>alert(1)</script>\"\n---\n" + flowchartBody)
    assert(!out.contains("<script>alert(1)"), s"XSS frontmatter themeCSS must not leak into SVG; got: ${out.take(300)}")
  }
}
