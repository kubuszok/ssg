/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package sass
package util

object TerminalPlatform {
  def supportsColor: Boolean =
    System.getenv("TERM") != null && System.getenv("TERM") != "dumb"
}
