/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Audit follow-up for ISS-1056 (ISS-1200): frontmatter-vs-inline title precedence.
 *
 * Upstream contract (mermaid/packages/mermaid/src/Diagram.ts:41-44):
 *     if (metadata.title) {
 *       db.setDiagramTitle?.(metadata.title);   // frontmatter title set FIRST
 *     }
 *     await parser.parse(text);                 // parse may set an INLINE title, overriding it
 *
 * So when a diagram carries BOTH a frontmatter `title:` AND an inline `title`
 * directive in its body, the INLINE title WINS: the frontmatter title is
 * pre-set on the db, then `parser.parse` overwrites it iff an inline title is
 * present (the parsers set db.title only conditionally — see PieParser
 * tryParseTitle / parsePieHeader and GanttParser tryParseTitle).
 *
 * The earlier port applied the frontmatter title AFTER parse, which inverted
 * the precedence (frontmatter wrongly clobbered the inline title). This suite
 * pins the corrected ordering for the inline-title-bearing diagrams.
 *
 * JVM-only daemon-thread join timeout mirrors FrontmatterIss1056Suite so a
 * render hang surfaces as a failure rather than wedging the runner.
 */
package ssg
package mermaid

import munit.FunSuite

final class FrontmatterInlineTitleIss1056Suite extends FunSuite {

  /** Wall-clock budget for a single render (see FrontmatterIss1056Suite). */
  private val TimeoutMs = 15000L

  /** Renders `input` on a daemon thread with a join timeout so an infinite
    * loop surfaces as a test failure instead of hanging the JVM runner.
    */
  private def renderGuarded(input: String): String = {
    @volatile var result: Option[String]     = None
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
      fail(s"ISS-1200: Mermaid.render did not terminate within ${TimeoutMs}ms for input:\n$input")
    }
    failure.foreach(t => fail(s"ISS-1200: Mermaid.render threw instead of completing: $t", t))
    result.getOrElse(fail("ISS-1200: Mermaid.render produced no result"))
  }

  // ──────────────────────────────────────────────────────────────────────────
  // Inline title MUST win over the frontmatter title (Diagram.ts:41-44).
  // ──────────────────────────────────────────────────────────────────────────

  test("ISS-1200: inline pie title overrides frontmatter title") {
    // Frontmatter pre-sets "FrontTitle"; the inline `title InlineTitle` line
    // (parsed by PieParser tryParseTitle) overwrites it. The rendered
    // pieTitleText must therefore show the inline title, not the frontmatter one.
    val svg = renderGuarded(
      "---\ntitle: FrontTitle\n---\npie\n    title InlineTitle\n    \"Dogs\" : 386\n    \"Cats\" : 85"
    )
    assert(svg.contains("<svg"), s"Should produce SVG, got: ${svg.take(200)}")
    assert(svg.contains("InlineTitle"), s"Inline title should win, got: ${svg.take(400)}")
    assert(!svg.contains("FrontTitle"), s"Frontmatter title must NOT override inline, got: ${svg.take(400)}")
  }

  test("ISS-1200: inline gantt title overrides frontmatter title") {
    // GanttRenderer emits db.title as a titleText element when non-empty.
    // The inline `title InlineTitle` (GanttParser tryParseTitle) must win.
    val svg = renderGuarded(
      "---\ntitle: FrontTitle\n---\ngantt\n    title InlineTitle\n    section S\n    Task :2024-01-01, 3d"
    )
    assert(svg.contains("<svg"), s"Should produce SVG, got: ${svg.take(200)}")
    assert(svg.contains("InlineTitle"), s"Inline title should win, got: ${svg.take(400)}")
    assert(!svg.contains("FrontTitle"), s"Frontmatter title must NOT override inline, got: ${svg.take(400)}")
  }

  // ──────────────────────────────────────────────────────────────────────────
  // Control: with NO inline title, the frontmatter title is still applied
  // (the parser leaves the pre-set title untouched — pins that the reorder
  // did not regress the plain frontmatter-title path).
  // ──────────────────────────────────────────────────────────────────────────

  test("ISS-1200 control: frontmatter title survives when no inline title (pie)") {
    val svg = renderGuarded("---\ntitle: FrontTitle\n---\npie\n    \"Dogs\" : 386\n    \"Cats\" : 85")
    assert(svg.contains("<svg"), s"Should produce SVG, got: ${svg.take(200)}")
    assert(svg.contains("FrontTitle"), s"Frontmatter title should be rendered when no inline, got: ${svg.take(400)}")
  }

  test("ISS-1200 control: frontmatter title survives when no inline title (gantt)") {
    val svg = renderGuarded("---\ntitle: FrontTitle\n---\ngantt\n    section S\n    Task :2024-01-01, 3d")
    assert(svg.contains("<svg"), s"Should produce SVG, got: ${svg.take(200)}")
    assert(svg.contains("FrontTitle"), s"Frontmatter title should be rendered when no inline, got: ${svg.take(400)}")
  }
}
