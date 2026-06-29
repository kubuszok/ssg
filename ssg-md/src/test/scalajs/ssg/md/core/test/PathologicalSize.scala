/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Scala.js pathological test size — reduced to 5000 (ISS-1351).
 *
 * At x=100000 each pathological suite takes ~4min on Node.js (no JIT + GC
 * pressure on 100k-char buffers), totalling ~7min for the two suites and
 * pushing the ci-js-3 job past GitHub's 6h timeout. JVM and Native keep
 * x=100000 for the full algorithmic-blowup stress; JS verifies correctness
 * at this smaller but still meaningful size. */
package ssg
package md
package core
package test

private[test] object PathologicalSize {

  val x: Int = 5000
}
