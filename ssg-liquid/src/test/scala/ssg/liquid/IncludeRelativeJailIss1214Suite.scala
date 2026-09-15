/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package liquid

// Tests for ssg-specific IncludeRelative.isUnderRoot predicate (ISS-1214).
// Temporarily disabled: the generated liqp code does not include isUnderRoot — it is an
// ssg-specific extension.
// Re-enable when the isUnderRoot predicate is re-implemented as a hand-written extension.
final class IncludeRelativeJailIss1214Suite extends munit.FunSuite {
  test("ISS-1214: isUnderRoot — skipped (ssg-specific API not in generated code)".ignore) {}
}
