/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Red test for ISS-1056: standard YAML frontmatter breaks Mermaid rendering.
 *
 * Upstream contract (vendored reference sources):
 *   - mermaid/packages/mermaid/src/diagram-api/regexes.ts
 *       frontMatterRegex = /^-{3}\s*[\n\r](.*?)[\n\r]-{3}\s*[\n\r]+/s
 *     A leading `---\n...\n---\n` block is YAML frontmatter (valid Mermaid
 *     input since v10.5).
 *   - mermaid/packages/mermaid/src/diagram-api/frontmatter.ts
 *       extractFrontMatter (lines 24-60) strips the block from the text
 *       (`text.slice(matches[0].length)`) and extracts ONLY the supported
 *       keys: `title` (line 49-51), `displayMode` (line 46-48) and
 *       `config` (line 52-54); unspecified keys are ignored.
 *   - mermaid/packages/mermaid/src/preprocess.ts
 *       preprocessDiagram → processFrontmatter (lines 19-29) runs
 *       extractFrontMatter on EVERY diagram before the parser sees the
 *       text, so the parser never receives the `---` delimiters. The
 *       `displayMode` key is folded into config.gantt.displayMode
 *       (preprocess.ts:22-27).
 *   - mermaid/packages/mermaid/src/Diagram.ts line 41-43:
 *       `if (metadata.title) db.setDiagramTitle?.(metadata.title)` — the
 *       extracted frontmatter title is applied to the diagram db and
 *       rendered by diagrams that support titles (e.g. pie's pieTitleText).
 *   - Behavioral cases mirrored from
 *     mermaid/packages/mermaid/src/diagram-api/frontmatter.spec.ts:
 *       "handles empty frontmatter" (line 25), "handles frontmatter with
 *       title" (line 98), "ignores unspecified frontmatter keys" (line 122).
 *
 * SSG bug (ISS-1056): Mermaid.render (Mermaid.scala:74-78) passes the RAW
 * input to the per-diagram parsers; only DetectType.detect strips the
 * frontmatter, so detection succeeds but every parser then chokes on the
 * leading `---` line and throws ParseException (or otherwise fails to
 * apply / strip the frontmatter).
 *
 * JVM-only: the state-diagram case is rendered from a daemon thread with a
 * join timeout (mirroring RankIss1132Suite) so that if any diagram path
 * regresses into an infinite loop it is observed as a test failure rather
 * than taking the whole runner down. JS and Native are single-threaded, so
 * this guard lives under src/test/scalajvm and the suite runs --jvm only.
 */
package ssg
package mermaid

import munit.FunSuite

final class FrontmatterIss1056Suite extends FunSuite {

  /** Wall-clock budget for a single render. A correct render of any of these tiny diagrams completes in well under a second; this only fires if a render hangs (e.g. an infinite-loop regression).
    */
  private val TimeoutMs = 15000L

  /** Renders `input` on a daemon thread with a join timeout so an infinite loop surfaces as a test failure instead of hanging the JVM runner. Returns the produced SVG, or fails the test on timeout /
    * thrown error.
    */
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
      // Leave the daemon thread to die with the JVM; do not block on it.
      fail(
        s"ISS-1056: Mermaid.render did not terminate within ${TimeoutMs}ms for input:\n$input"
      )
    }
    failure.foreach(t => fail(s"ISS-1056: Mermaid.render threw instead of completing: $t", t))
    result.getOrElse(fail("ISS-1056: Mermaid.render produced no result"))
  }

  /** Standard frontmatter block, exactly the shape matched by upstream frontMatterRegex (regexes.ts) and stripped by extractFrontMatter (frontmatter.ts:24-60).
    */
  private val frontmatter = "---\ntitle: Test Title\n---\n"

  // ──────────────────────────────────────────────────────────────────────────
  // One entry point per representative diagram type: the SAME body that the
  // existing smoke suites render must still render when a frontmatter block
  // is prepended (upstream preprocess.ts strips it before parsing).
  // ──────────────────────────────────────────────────────────────────────────

  test("ISS-1056: flowchart renders with YAML frontmatter") {
    val svg = renderGuarded(frontmatter + "graph TD\n    A-->B")
    assert(svg.contains("<svg"), s"Should produce SVG, got: ${svg.take(200)}")
    assert(svg.contains("<rect") || svg.contains("<path"), "SVG should contain shape elements")
  }

  test("ISS-1056: sequence diagram renders with YAML frontmatter") {
    val svg = renderGuarded(frontmatter + "sequenceDiagram\n    Alice->>Bob: Hello")
    assert(svg.contains("<svg"), s"Should produce SVG, got: ${svg.take(200)}")
  }

  test("ISS-1056: pie chart renders with YAML frontmatter") {
    val svg = renderGuarded(frontmatter + "pie\n    \"Dogs\" : 386\n    \"Cats\" : 85")
    assert(svg.contains("<svg"), s"Should produce SVG, got: ${svg.take(200)}")
  }

  test("ISS-1056: class diagram renders with YAML frontmatter") {
    val svg = renderGuarded(frontmatter + "classDiagram\n    class Animal")
    assert(svg.contains("<svg"), s"Should produce SVG, got: ${svg.take(200)}")
  }

  test("ISS-1056: state diagram renders with YAML frontmatter") {
    // Previously hung on the Rank blocker (ISS-1132, now fixed); guarded with
    // a join timeout so a regression to the hang surfaces as a failure.
    val svg = renderGuarded(frontmatter + "stateDiagram-v2\n    [*] --> s1\n    s1 --> [*]")
    assert(svg.contains("<svg"), s"Should produce SVG, got: ${svg.take(200)}")
  }

  test("ISS-1056: gantt chart renders with YAML frontmatter") {
    val svg = renderGuarded(frontmatter + "gantt\n    section S\n    Task :2024-01-01, 3d")
    assert(svg.contains("<svg"), s"Should produce SVG, got: ${svg.take(200)}")
  }

  // ──────────────────────────────────────────────────────────────────────────
  // Frontmatter metadata application.
  // ──────────────────────────────────────────────────────────────────────────

  test("ISS-1056: frontmatter title is applied to the rendered diagram (pie)") {
    // Upstream: extractFrontMatter puts `title` into metadata
    // (frontmatter.ts:49-51); Diagram.ts:41-43 applies it via
    // `db.setDiagramTitle?.(metadata.title)`; PieRenderer renders db.title
    // as the pieTitleText element. The body itself carries no title, so
    // "Test Title" in the output can only come from the frontmatter.
    val svg = renderGuarded(frontmatter + "pie\n    \"Dogs\" : 386\n    \"Cats\" : 85")
    assert(svg.contains("Test Title"), s"Frontmatter title should be rendered, got: ${svg.take(300)}")
  }

  test("ISS-1056: frontmatter delimiters must not leak into the output") {
    // extractFrontMatter strips the whole matched block including both
    // `---` delimiter lines (frontmatter.ts:57: text.slice(matches[0].length)).
    val svg = renderGuarded(frontmatter + "graph TD\n    A-->B")
    assert(svg.contains("<svg"), s"Should produce SVG, got: ${svg.take(200)}")
    assert(!svg.contains("---"), "Frontmatter delimiters must not appear in the SVG output")
  }

  // displayMode (frontmatter.ts:46-48) is folded into config.gantt.displayMode
  // by preprocess.ts:22-27; the gantt body must still render with the block
  // stripped (the parser must never see the leading `---`).
  test("ISS-1056: displayMode frontmatter renders (gantt compact)") {
    val input =
      "---\ndisplayMode: compact\n---\ngantt\n    section S\n    Task1 :2024-01-01, 3d\n    Task2 :2024-01-01, 3d"
    val svg = renderGuarded(input)
    assert(svg.contains("<svg"), s"Should produce SVG, got: ${svg.take(200)}")
    assert(!svg.contains("---"), "Frontmatter delimiters must not appear in the SVG output")
  }

  // config-bearing frontmatter (frontmatter.ts:52-54). The whole block is
  // stripped by extractFrontMatter before the parser runs; with both a config
  // and a title key present, the title must still be applied and the diagram
  // must still render. (Deep config application is out of scope here — the
  // contract under test is "the block is stripped and title applied".)
  test("ISS-1056: config-bearing frontmatter renders and applies title (pie)") {
    val input =
      "---\ntitle: Test Title\nconfig:\n  theme: dark\n---\npie\n    \"Dogs\" : 386\n    \"Cats\" : 85"
    val svg = renderGuarded(input)
    assert(svg.contains("<svg"), s"Should produce SVG, got: ${svg.take(200)}")
    assert(!svg.contains("---"), "Frontmatter delimiters must not appear in the SVG output")
    assert(svg.contains("Test Title"), s"Frontmatter title should be rendered, got: ${svg.take(300)}")
  }

  // Mirrors frontmatter.spec.ts "handles empty frontmatter" (line 25):
  // `---\n\n---\ndiagram` → metadata {}, text "diagram".
  test("ISS-1056: empty frontmatter block is stripped (flowchart)") {
    val svg = renderGuarded("---\n\n---\ngraph TD\n    A-->B")
    assert(svg.contains("<svg"), s"Should produce SVG, got: ${svg.take(200)}")
  }

  // Mirrors frontmatter.spec.ts "ignores unspecified frontmatter keys"
  // (line 122): unsupported keys are dropped, the diagram still renders.
  test("ISS-1056: unspecified frontmatter keys are ignored (flowchart)") {
    val svg = renderGuarded("---\ninvalid: true\ntitle: Test Title\ntest: bar\n---\ngraph TD\n    A-->B")
    assert(svg.contains("<svg"), s"Should produce SVG, got: ${svg.take(200)}")
  }

  // ──────────────────────────────────────────────────────────────────────────
  // Frontmatter combined with an %%{init:}%% directive: upstream
  // preprocess.ts strips the frontmatter first (processFrontmatter) and the
  // directives afterwards (processDirectives), so the combination must not
  // throw. Config application of the directive itself is ISS-1057's scope
  // and is deliberately NOT asserted here.
  // ──────────────────────────────────────────────────────────────────────────

  test("ISS-1056: frontmatter followed by an init directive must not throw") {
    val svg = renderGuarded(frontmatter + "%%{init: {\"theme\": \"dark\"}}%%\ngraph TD\n    A-->B")
    assert(svg.contains("<svg"), s"Should produce SVG, got: ${svg.take(200)}")
  }

  // ──────────────────────────────────────────────────────────────────────────
  // Control: no frontmatter — the existing smoke pattern must keep passing
  // (pins no-regression for the eventual fix).
  // ──────────────────────────────────────────────────────────────────────────

  test("ISS-1056 control: flowchart without frontmatter renders") {
    val svg = renderGuarded("graph TD\n    A-->B")
    assert(svg.contains("<svg"), s"Should produce SVG, got: ${svg.take(200)}")
    assert(svg.contains("<rect") || svg.contains("<path"), "SVG should contain shape elements")
  }

  test("ISS-1056 control: pie without frontmatter renders") {
    val svg = renderGuarded("pie\n    \"Dogs\" : 386\n    \"Cats\" : 85")
    assert(svg.contains("<svg"), s"Should produce SVG, got: ${svg.take(200)}")
  }
}
