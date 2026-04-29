/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Cross-platform test compatibility helpers. */
package ssg
package liquid

/** Platform detection for conditional test execution. */
object PlatformCompat {

  /** True on JVM where reflection is available. */
  def supportsReflection: Boolean = PlatformCompatImpl.supportsReflection
}
