/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package liquid

object PlatformCompat {

  def isJVM: Boolean = PlatformCompatImpl.isJVM
}
