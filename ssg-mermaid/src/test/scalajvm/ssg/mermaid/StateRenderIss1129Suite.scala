/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Red test for ISS-1129: rendering the minimal state diagram
 * `stateDiagram-v2; [*] --> s1; s1 --> [*]` hangs the JVM because
 * Rank.networkSimplex in ssg-graphs-commons never terminates on the
 * 3-node DAG (start_0 -> s1 -> end_1) that StateDb resolves it to.
 *
 * JVM-only: the failure mode is an infinite loop, which can only be observed
 * safely from a separate daemon thread with a join timeout. JS and Native are
 * single-threaded here, so a hang would take the whole test runner down.
 */
package ssg
package mermaid

import munit.FunSuite

final class StateRenderIss1129Suite extends FunSuite {

  private val TimeoutMs = 15000L

  test("ISS-1129: Mermaid.render terminates on `[*] --> s1; s1 --> [*]` and emits state markers") {
    val diagram = "stateDiagram-v2\n  [*] --> s1\n  s1 --> [*]"

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
          "`stateDiagram-v2; [*] --> s1; s1 --> [*]` — dagre's networkSimplex hangs on the " +
          "start_0 -> s1 -> end_1 graph produced by StateDb."
      )
    }
    failure.foreach(t => fail(s"Mermaid.render threw instead of completing: ${t}", t))

    val svg = result.getOrElse(fail("Mermaid.render returned no result despite the worker thread finishing"))
    assert(svg.contains("<svg"), s"Expected SVG output, got: ${svg.take(200)}")
    assert(svg.contains("start-state"), "Expected the rendered SVG to contain the start state marker (class start-state)")
    assert(svg.contains("end-state-inner"), "Expected the rendered SVG to contain the end state marker (class end-state-inner)")
  }
}
