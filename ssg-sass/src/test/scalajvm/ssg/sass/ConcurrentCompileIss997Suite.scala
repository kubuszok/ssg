/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * JVM-only — this is a CONCURRENCY reproducer and needs real OS threads
 * (java.util.concurrent.Executors), which only exist on the JVM. It therefore
 * lives under src/test/scalajvm so it never builds for ssg-js / ssg-native.
 *
 * Migration notes:
 *   Convention: RED reproducer for ISS-997 — no source counterpart.
 */
package ssg
package sass

import java.util.concurrent.{ Callable => JCallable, Executors, TimeUnit }
import java.util.concurrent.atomic.AtomicReference

import scala.concurrent.duration._

/** Concurrency RED test for ISS-997 ([R0610-P1] bug, high).
  *
  * `EvaluationContext` (object, `private var _stack: List[EvaluationContext]`,
  * EvaluationContext.scala:59) and `CurrentEnvironment` (object,
  * `private var _env`, EvaluationContext.scala:118) — together with the
  * `_invoker` vars in `CurrentCallableInvoker`/`CurrentMixinInvoker` (:143/:165)
  * — are SHARED MUTABLE module-level statics, deliberately "single-threaded"
  * per the in-source NOTE comments (e.g. "this is a single shared `var` rather
  * than a `ThreadLocal`/`DynamicVariable`").
  *
  * The obvious SSG build pattern is to compile many Sass files CONCURRENTLY
  * (one `Compile.compileString` call per source file). Because the evaluation
  * `_stack`/`_env` are process-global, two concurrent evaluations interleave
  * their push/pop on the SAME shared mutable state. That corrupts each other's
  * context: a task can observe another task's environment, pop the wrong frame
  * off `_stack`, or hit a `NoSuchElementException` from `_stack.tail` on an
  * already-emptied list.
  *
  * Reproduction strategy: each task compiles a DISTINCT source whose correct
  * output is known and depends on evaluating variables + an `@if` branch
  * through the environment/context (so the shared state is actually exercised
  * during evaluation, not just at parse time). If a task ever sees the wrong
  * `$x` / branch — i.e. another task's context — its output is detectably
  * wrong. We run K tasks across a real fixed thread pool, repeated for M
  * iterations, to make the race fire reliably. A SINGLE wrong output, a SINGLE
  * cross-contaminated result, or a SINGLE thrown exception across all
  * iterations means RED.
  *
  * NOTE: this suite intentionally has NO `.fail`/`assume` — it must genuinely
  * pass once each compilation owns its own evaluation context (the ISS-997
  * fix), and genuinely fail (wrong output / exception) on the shared statics.
  */
final class ConcurrentCompileIss997Suite extends munit.FunSuite {

  // The compile loop runs K * M compilations; give it plenty of headroom.
  override val munitTimeout: FiniteDuration = 5.minutes

  // Number of concurrent tasks per iteration and number of iterations. Tuned
  // high enough that the push/pop interleaving on the shared `_stack`/`_env`
  // reliably collides at least once across the run.
  private val K = 12
  private val M = 150

  /** A source for task `i` that exercises the evaluation context: it binds a
    * variable, then selects a branch via `@if` on that variable, and finally
    * emits the variable's value. The "correct" output for task `i` is fully
    * determined by `i` alone, so any contamination by another task's `$x` is
    * detectable.
    */
  private def sourceFor(i: Int): String =
    s"""$$x: $i;
       |.a {
       |  @if $$x == $i {
       |    width: $$x * 2;
       |  } @else {
       |    width: -1;
       |  }
       |}
       |""".stripMargin

  /** The oracle output for task `i`, computed by compiling it ALONE (no
    * concurrency), so the expectation is exactly what a correct, isolated
    * evaluation produces.
    */
  private def expectedFor(i: Int): String =
    Compile.compileString(sourceFor(i)).css

  test("ISS-997 RED: concurrent compileString calls must not corrupt each other") {
    // Precompute the known-good outputs single-threaded.
    val inputs   = (0 until K).map(sourceFor).toVector
    val expected = (0 until K).map(expectedFor).toVector

    // A place to record the first failure we observe, so the assertion message
    // can show the actual corrupted output.
    val firstFailure = new AtomicReference[String](null)
    var wrongCount    = 0
    var iterations    = 0

    val pool = Executors.newFixedThreadPool(K)
    try {
      var iter = 0
      while (iter < M) {
        iter += 1
        iterations = iter

        val tasks = new java.util.ArrayList[JCallable[(Int, Either[Throwable, String])]]()
        var i     = 0
        while (i < K) {
          val idx = i
          tasks.add(new JCallable[(Int, Either[Throwable, String])] {
            def call(): (Int, Either[Throwable, String]) =
              try (idx, Right(Compile.compileString(inputs(idx)).css))
              catch { case t: Throwable => (idx, Left(t)) }
          })
          i += 1
        }

        val futures = pool.invokeAll(tasks)
        var f       = 0
        while (f < futures.size()) {
          val (idx, result) = futures.get(f).get()
          result match {
            case Left(t) =>
              wrongCount += 1
              firstFailure.compareAndSet(
                null,
                s"task #$idx (iteration $iter) THREW ${t.getClass.getName}: ${t.getMessage}"
              )
            case Right(css) =>
              if (css != expected(idx)) {
                wrongCount += 1
                firstFailure.compareAndSet(
                  null,
                  s"task #$idx (iteration $iter) produced the WRONG css.\n" +
                    s"  expected (its own):\n${indent(expected(idx))}\n" +
                    s"  got (cross-contaminated):\n${indent(css)}"
                )
              }
          }
          f += 1
        }
      }
    } finally {
      pool.shutdown()
      pool.awaitTermination(1, TimeUnit.MINUTES)
    }

    val sample = firstFailure.get()
    assert(
      sample == null,
      s"ISS-997: $wrongCount of ${K * iterations} concurrent compilations were corrupted by the " +
        s"shared module-level statics (EvaluationContext._stack / CurrentEnvironment._env / _invoker). " +
        s"First failure:\n$sample"
    )
  }

  private def indent(s: String): String =
    s.linesIterator.map("    " + _).mkString("\n")
}
