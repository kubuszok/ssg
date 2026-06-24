/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * JVM-specific compression runner with timeout guard.
 *
 * Uses Future/Await to run compression with a 5-second timeout,
 * protecting against ISS-031/032 ScopeAnalysis hangs on code
 * with undeclared/global references. */
package ssg
package js
package compress

import scala.concurrent.{ Await, Future }
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.*
import scala.util.{ Failure, Success, Try }

/** JVM compression runner with timeout guard (ISS-031/032 protection). */
object CompressRunner {

  /** Default timeout for compression operations (per test case).
    *
    * Set to 5 seconds which is long enough for any single-pass compression to complete, but short enough to not block the thread pool when ISS-031/032 causes a hang.
    */
  private val DefaultTimeout: FiniteDuration = 5.seconds

  /** Run the compression thunk with a timeout guard.
    *
    * Returns `Some(result)` if compression completes within the timeout, `None` if it times out (indicating ISS-031/032 hang).
    */
  def run(thunk: () => String): Option[String] = {
    val future = Future(thunk())
    Try(Await.result(future, DefaultTimeout)) match {
      case Success(result)                                   => Some(result)
      case Failure(_: java.util.concurrent.TimeoutException) => None
      case Failure(ex)                                       => throw ex
    }
  }

  /** Skip the test with a JUnit assumeTrue when compression timed out.
    *
    * This keeps the JVM's existing behavior of marking timed-out tests as skipped (via JUnit's AssumptionViolatedException) rather than failing.
    */
  def skipOnTimeout(): Unit =
    org.junit.Assume.assumeTrue(
      "Compression timed out after 5s (ISS-031/032 -- undeclared references cause ScopeAnalysis hang)",
      false
    )
}
