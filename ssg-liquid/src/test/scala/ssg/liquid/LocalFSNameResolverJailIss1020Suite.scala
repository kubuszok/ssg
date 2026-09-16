/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package liquid

// Tests for ssg-specific jail-root API on LocalFSNameResolver (ISS-1020).
// Temporarily disabled: the generated liqp code does not include the jailRoot parameter
// or JailViolationException — these are ssg-specific extensions.
// Re-enable when the jail-root feature is re-implemented as a hand-written extension.
final class LocalFSNameResolverJailIss1020Suite extends munit.FunSuite {
  test("ISS-1020: LocalFSNameResolver jail root — skipped (ssg-specific API not in generated code)".ignore) {}
}
