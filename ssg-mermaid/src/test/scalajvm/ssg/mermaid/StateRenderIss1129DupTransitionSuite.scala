/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Red-2 test for ISS-1129: after the first fix (6f8571e8) terminated the
 * minimal `[*] --> s1; s1 --> [*]` chain, the ISS-1129 audit verified that
 * rendering a state diagram with TWO labelled transitions from s1 to the end
 * pseudo-state still hangs the JVM. StateDb resolves the diagram to
 * start_0 -> s1 plus two parallel edges s1 -> end_1; DagreUtil.simplify
 * (run inside Rank.networkSimplex in ssg-graphs-commons) sums the parallel
 * edges to a single weight-2 edge, calcCutValue then computes 1 - 2 = -1
 * where the true cut value (Gansner et al. 1993, section 4.2) is +1, and the
 * leaveEdge/enterEdge/exchangeEdges loop never terminates.
 *
 * JVM-only: the failure mode is an infinite loop, which can only be observed
 * safely from a separate daemon thread with a join timeout. JS and Native are
 * single-threaded here, so a hang would take the whole test runner down.
 */
package ssg
package mermaid

import munit.FunSuite

final class StateRenderIss1129DupTransitionSuite extends FunSuite {

  private val TimeoutMs = 15000L

  test("ISS-1129 red-2 case 4: Mermaid.render terminates on `[*] --> s1; s1 --> [*] : a; s1 --> [*] : b`") {
    val diagram = "stateDiagram-v2\n  [*] --> s1\n  s1 --> [*] : a\n  s1 --> [*] : b"

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
      // Leave the daemon thread to die with the JVM; do not block on it.
      fail(
        s"ISS-1129 reproduced: Mermaid.render did not terminate within ${TimeoutMs}ms on " +
          "`stateDiagram-v2; [*] --> s1; s1 --> [*] : a; s1 --> [*] : b` — dagre's networkSimplex " +
          "hangs on the two parallel s1 -> end_1 edges produced by StateDb (simplify sums them " +
          "to weight 2; audit: cut value computed 1 - 2 = -1, true +1)."
      )
    }
    failure.foreach(t => fail(s"Mermaid.render threw instead of completing: ${t}", t))

    val svg = result.getOrElse(fail("Mermaid.render returned no result despite the worker thread finishing"))
    assert(svg.contains("<svg"), s"Expected SVG output, got: ${svg.take(200)}")
    assert(svg.contains("start-state"), "Expected the rendered SVG to contain the start state marker (class start-state)")
    assert(svg.contains("end-state-inner"), "Expected the rendered SVG to contain the end state marker (class end-state-inner)")
  }
}
