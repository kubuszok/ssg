/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * End-to-end render proof for ISS-1064: the `--` concurrency divider inside a
 * composite state must survive the full Mermaid.render pipeline (parse, dagre
 * layout, render) and appear in the SVG as the dashed `line.divider` from
 * shapes.js drawDivider().
 *
 * JVM-only: uses a daemon-thread timeout guard (same pattern as ISS-1195/1129
 * suites) to avoid hanging the test runner if dagre layout regresses.
 *
 * Faithful to:
 *   - diagrams/state/shapes.js drawDivider() — the rendered `line` carries
 *     class `divider` and `stroke-dasharray: 3` (grey dashed separator).
 *   - diagrams/state/stateDb.js getDividerId() — `divider-id-<n>` nodes
 *     survive docTranslator into concurrent regions.
 */
package ssg
package mermaid

import munit.FunSuite

final class StateDividerRenderIss1064Suite extends FunSuite {

  private val TimeoutMs = 15000L

  test("ISS-1064: Mermaid.render produces a divider line in SVG for `--` inside a composite state") {
    val diagram =
      """stateDiagram-v2
        |  state Active {
        |    state A
        |    --
        |    state B
        |  }""".stripMargin

    @volatile var result:  Option[String]    = None
    @volatile var failure: Option[Throwable] = None

    val worker = new Thread(() =>
      try
        result = Some(Mermaid.render(diagram))
      catch {
        case t: Throwable => failure = Some(t)
      }
    )
    worker.setDaemon(true)
    worker.start()
    worker.join(TimeoutMs)

    if (worker.isAlive) {
      fail(
        s"Composite-state render with divider did not terminate within ${TimeoutMs}ms — " +
          "dagre layout may be hanging on the compound graph."
      )
    }
    failure.foreach(t => fail(s"Mermaid.render threw instead of completing: ${t}", t))

    val svg = result.getOrElse(fail("Mermaid.render returned no result despite the worker thread finishing"))
    assert(svg.contains("<svg"), s"Expected SVG output, got: ${svg.take(200)}")

    // The divider line must appear in the SVG — this is the drawDivider() visual from shapes.js.
    assert(
      svg.contains("class=\"divider\""),
      s"Expected the rendered SVG to contain the divider line (class=\"divider\"), got:\n${svg.take(1000)}"
    )
    assert(
      svg.contains("stroke-dasharray"),
      s"Expected the divider to have stroke-dasharray (dashed line), got:\n${svg.take(1000)}"
    )
  }
}
