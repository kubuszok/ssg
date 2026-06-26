/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package commons
package io

import java.nio.file.{ Files, LinkOption, Paths }

/** Platform capability probe for the shared FileOps suite: creates a symbolic link at `link` pointing at `target`, returning true if the platform allowed it. Scala Native routes through
  * java.nio.file.Files.createSymbolicLink (the same API as JVM); if the Native javalib does not implement it the probe returns false and the shared suite skips the does-not-follow-symlinks assertion
  * on this platform. The probe is detection-only — its boolean result never masks a failure of the operation actually under test.
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
      // Detection only: a false result means "this Native host/javalib cannot make a symlink in-test", not that the
      // FileOps behavior is broken. Both the I/O-level refusal and a file-system-capability refusal land here.
      case _: java.io.IOException => false
      case _: RuntimeException    => false
    }

  /** Probes whether the platform honors LinkOption.NOFOLLOW_LINKS for an existing directory symlink. `link` must already exist and point at a directory target. Returns true when
    * Files.isDirectory(link, NOFOLLOW_LINKS) correctly reports false (i.e. the link itself is not a directory — NOFOLLOW is honored). Returns false when the platform's javalib follows the link
    * despite NOFOLLOW_LINKS (ISS-1347: Scala Native Windows).
    */
  def nofollowLinksHonored(link: FilePath): Boolean =
    !Files.isDirectory(Paths.get(link.pathString), LinkOption.NOFOLLOW_LINKS)

  /** Combined capability probe: returns true only when this platform can BOTH create directory symlinks AND honors
    * NOFOLLOW_LINKS during walks/deletes. Uses an isolated probe symlink under `parentDir` pointing at `targetDir`
    * that is created, tested, and removed entirely within this method — no lingering symlink is left behind regardless
    * of the result. Call BEFORE creating the test's own symlink so the test can decide whether to create one at all.
    *
    * On platforms where this returns false (Native-Windows: symlinks work but NOFOLLOW is not honored), the caller
    * must NOT create a directory symlink — otherwise the fixture teardown's deleteRecursively would recurse through it
    * and throw (ISS-1347).
    */
  def symlinkSafelyTestable(parentDir: FilePath, targetDir: FilePath): Boolean = {
    val probeDir  = parentDir.resolve("_symlink_probe_" + System.nanoTime().toString)
    val probeLink = probeDir.resolve("probe-link")
    try {
      Files.createDirectories(Paths.get(probeDir.pathString))
      val absoluteTarget = Paths.get(targetDir.pathString).toAbsolutePath
      Files.createSymbolicLink(Paths.get(probeLink.pathString), absoluteTarget)
      val honored = !Files.isDirectory(Paths.get(probeLink.pathString), LinkOption.NOFOLLOW_LINKS)
      // Clean up the probe link directly (Files.delete on the link entry removes the symlink itself, not its target —
      // this works even when NOFOLLOW_LINKS is not honored during walks, because delete targets the path entry).
      Files.delete(Paths.get(probeLink.pathString))
      Files.delete(Paths.get(probeDir.pathString))
      honored
    } catch {
      // Symlink creation failed — this platform cannot create symlinks at all.
      case _: java.io.IOException => false
      case _: RuntimeException    => false
    }
  }
}
