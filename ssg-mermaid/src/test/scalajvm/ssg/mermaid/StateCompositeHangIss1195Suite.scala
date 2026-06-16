/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Red test for ISS-1195: rendering ANY composite state diagram hangs the JVM.
 * `DagreLayout.layout` runs `Rank.rank` (DagreLayout.scala:38) BEFORE
 * `Nesting.run` (DagreLayout.scala:60), inverted relative to upstream dagre's
 * runLayout (nestingGraph.run before rank). On a composite state's disconnected
 * COMPOUND graph there is no nesting root connecting the components, so
 * `Rank.feasibleTree` (Rank.scala:138) never finds a tight tree edge and spins
 * forever — the render never returns (100% CPU).
 *
 * JVM-only: the failure mode is an infinite loop, which can only be observed
 * safely from a separate daemon thread with a join timeout. JS and Native are
 * single-threaded here, so a hang would take the whole test runner down.
 */
package ssg
package mermaid

import munit.FunSuite

final class StateCompositeHangIss1195Suite extends FunSuite {

  private val TimeoutMs = 15000L

  test("ISS-1195: Mermaid.render terminates on a composite state `state Active { a --> b }`") {
    // A composite state `Active` containing a transition. This produces a
    // disconnected COMPOUND dagre graph (the inner a/b component is not joined
    // to anything by a nesting root, because Nesting.run is invoked too late).
    val diagram = "stateDiagram-v2\n  state Active {\n    a --> b\n  }"

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
        s"ISS-1195 reproduced: composite-state render did not terminate within ${TimeoutMs}ms — " +
          "dagre Rank.feasibleTree hangs on the disconnected compound graph " +
          "(DagreLayout runs Rank before Nesting)."
      )
    }
    failure.foreach(t => fail(s"Mermaid.render threw instead of completing: ${t}", t))

    val svg = result.getOrElse(fail("Mermaid.render returned no result despite the worker thread finishing"))
    assert(svg.contains("<svg"), s"Expected SVG output, got: ${svg.take(200)}")
  }
}
