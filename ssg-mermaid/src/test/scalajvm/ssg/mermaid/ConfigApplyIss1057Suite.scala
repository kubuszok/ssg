/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Differential test for ISS-1057: `%%{init: {...}}%%` directives and the
 * frontmatter `config` are extracted but never applied, so an author's
 * theme/themeVariables/per-diagram config is silently ignored.
 *
 * Upstream contract (vendored reference sources):
 *   - mermaid/packages/mermaid/src/utils.ts
 *       detectInit (99-131): collects init/initialize directives via the
 *       directive regex, merges them with assignWithDepth, and remaps a
 *       `config` key under the detected diagram type (flowchart-v2 ->
 *       flowchart).
 *       detectDirective (160-199): before parsing a body, replaces `'` with
 *       `"` (utils.ts:169) and JSON-parses the body (group 4).
 *       cleanAndMerge (858-860): merge({}, defaultData, data) — a deep merge
 *       where `data` (the init directive) wins over `defaultData` (the
 *       frontmatter config).
 *   - mermaid/packages/mermaid/src/preprocess.ts
 *       processFrontmatter (19-30) extracts the frontmatter config;
 *       processDirectives (32-44) detects the init/wrap directives;
 *       preprocessDiagram (52-63) calls cleanAndMerge(frontmatterConfig,
 *       directive). The resulting config is then applied to the render
 *       (the caller config plays the `siteConfig` role, so author markup
 *       overrides it).
 *   - mermaid/packages/mermaid/src/diagram-api/regexes.ts
 *       directiveRegex (8-9).
 *
 * The `theme` config key is chosen because it ALREADY has a render effect
 * today via the themed renderers (a different theme produces different
 * theme-variable-derived colours in the SVG). This avoids depending on a
 * currently-dead config field (that is ISS-1058's scope).
 *
 * JVM-only: rendered from a daemon thread with a join timeout (mirroring
 * FrontmatterIss1056Suite) so a render hang surfaces as a failure rather than
 * hanging the runner.
 */
package ssg
package mermaid

import munit.FunSuite

final class ConfigApplyIss1057Suite extends FunSuite {

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
      fail(s"ISS-1057: Mermaid.render did not terminate within ${TimeoutMs}ms for input:\n$input")
    }
    failure.foreach(t => fail(s"ISS-1057: Mermaid.render threw instead of completing: $t", t))
    result.getOrElse(fail("ISS-1057: Mermaid.render produced no result"))
  }

  private val flowchartBody = "graph TD\n    A-->B"

  // The default render is the baseline against which a themed render must differ.
  private lazy val defaultSvg = renderGuarded(flowchartBody)

  test("ISS-1057: init directive theme:forest differs from the default-theme render") {
    val themed = renderGuarded("%%{init: {'theme':'forest'}}%%\n" + flowchartBody)
    assert(themed.contains("<svg"), s"Should produce SVG, got: ${themed.take(200)}")
    assertNotEquals(
      themed,
      defaultSvg,
      "init-directive theme:forest must change the rendered SVG (theme applied via directive)"
    )
  }

  test("ISS-1057: frontmatter config theme:forest differs from the default-theme render") {
    val themed = renderGuarded("---\nconfig:\n  theme: forest\n---\n" + flowchartBody)
    assert(themed.contains("<svg"), s"Should produce SVG, got: ${themed.take(200)}")
    assertNotEquals(
      themed,
      defaultSvg,
      "frontmatter config theme:forest must change the rendered SVG (theme applied via frontmatter)"
    )
  }

  test("ISS-1057: init directive wins over frontmatter config (cleanAndMerge precedence)") {
    // Frontmatter sets theme=dark; the init directive sets theme=forest. Per
    // cleanAndMerge (merge({}, frontmatterConfig, directive)) the directive
    // wins, so the result must match a forest-only render and differ from a
    // dark-only render.
    val both =
      renderGuarded("---\nconfig:\n  theme: dark\n---\n%%{init: {'theme':'forest'}}%%\n" + flowchartBody)
    val forestOnly = renderGuarded("%%{init: {'theme':'forest'}}%%\n" + flowchartBody)
    val darkOnly   = renderGuarded("---\nconfig:\n  theme: dark\n---\n" + flowchartBody)

    assert(both.contains("<svg"), s"Should produce SVG, got: ${both.take(200)}")
    assertEquals(both, forestOnly, "init directive (forest) must win over frontmatter (dark)")
    assertNotEquals(both, darkOnly, "the frontmatter dark theme must be overridden by the init directive")
  }
}
