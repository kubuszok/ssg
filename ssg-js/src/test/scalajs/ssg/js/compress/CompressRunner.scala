/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Scala.js compression runner -- direct execution without timeout.
 *
 * JS is single-threaded so Future/Await timeout is not available.
 * Suites relocated to the shared test directory are vetted to be
 * hang-free (no undeclared references triggering ISS-031/032). */
package ssg
package js
package compress

/** JS compression runner -- direct execution, no timeout guard. */
object CompressRunner {

  /** Run the compression thunk directly.
   *
   * Always returns `Some(result)` since there is no timeout mechanism
   * on the single-threaded JS runtime.
   */
  def run(thunk: () => String): Option[String] =
    Some(thunk())

  /** No-op on JS -- shared suites are vetted hang-free.
   *
   * This method exists for API compatibility with the JVM CompressRunner
   * but should never be reached for properly vetted shared suites.
   */
  def skipOnTimeout(): Unit =
    throw new AssertionError(
      "CompressRunner.skipOnTimeout called on JS -- this should not happen for vetted hang-free suites"
    )
}
