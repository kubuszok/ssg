/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package commons
package io

import java.nio.file.{ Files, Paths }

/** Platform capability probe for the shared FileOps suite: creates a symbolic link at `link` pointing at `target`, returning true if the platform allowed it. JVM uses
  * java.nio.file.Files.createSymbolicLink. The probe is detection-only — its boolean result lets the shared suite decide whether to run the does-not-follow-symlinks assertion; it never masks a
  * failure of the operation actually under test.
  */
object SymlinkTestSupport {

  def tryCreateSymlink(link: FilePath, target: FilePath): Boolean =
    try {
      // The target is resolved to an absolute path before being recorded in the link. A relative target string would
      // otherwise be resolved by the OS against the link's own parent directory (not the cwd), producing a dangling
      // link that a follow-semantics deletion would never traverse — which would let the does-not-follow assertion
      // pass vacuously. An absolute target points at the real directory regardless of where the link sits.
      val absoluteTarget = Paths.get(target.pathString).toAbsolutePath
      Files.createSymbolicLink(Paths.get(link.pathString), absoluteTarget): Unit
      true
    } catch {
      // Detection only: some JVM hosts (e.g. Windows without privilege, or a file system that lacks symlink support)
      // refuse symlink creation. A false result means "this platform/host cannot make a symlink in-test", not that the
      // FileOps behavior is broken. Both the I/O-level refusal and the file-system-capability refusal land here.
      case _: java.io.IOException => false
      case _: RuntimeException    => false
    }
}
