/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package commons
package io

import scala.scalajs.js

/** Platform capability probe for the shared FileOps suite: creates a symbolic link at `link` pointing at `target`, returning true if the platform allowed it. Scala.js under Node uses fs.symlinkSync.
  * The probe is detection-only — its boolean result lets the shared suite decide whether to run the does-not-follow-symlinks assertion; it never masks a failure of the operation actually under test.
  */
object SymlinkTestSupport {

  private lazy val fs:   js.Dynamic = js.Dynamic.global.require("fs")
  private lazy val path: js.Dynamic = js.Dynamic.global.require("path")

  def tryCreateSymlink(link: FilePath, target: FilePath): Boolean =
    try {
      // The target is resolved to an absolute path before being recorded in the link. A relative target string would
      // otherwise be resolved by the OS against the link's own parent directory (not the cwd), producing a dangling
      // link that a follow-semantics deletion would never traverse — which would let the does-not-follow assertion
      // pass vacuously. An absolute target points at the real directory regardless of where the link sits.
      val absoluteTarget = path.resolve(target.pathString).asInstanceOf[String]
      fs.symlinkSync(absoluteTarget, link.pathString): Unit
      true
    } catch {
      // Detection only: some Node hosts (e.g. Windows without privilege) disallow symlink creation, surfacing as a
      // js.JavaScriptException. A false result means "this Node host cannot make a symlink in-test", not that the
      // FileOps behavior is broken.
      case _: js.JavaScriptException => false
    }

  /** Probes whether the platform honors no-follow-link semantics for an existing directory symlink. `link` must already exist and point at a directory target. Returns true when lstatSync reports the
    * entry is NOT a directory (i.e. it sees the symlink, not its target). On Node all platforms honor lstat correctly, so this always returns true. Exists for API parity with the JVM/Native
    * SymlinkTestSupport (ISS-1347).
    */
  def nofollowLinksHonored(link: FilePath): Boolean =
    !fs.lstatSync(link.pathString).isDirectory().asInstanceOf[Boolean]

  /** Combined capability probe: returns true only when this platform can BOTH create directory symlinks AND honors
    * no-follow-link semantics during walks/deletes. Uses an isolated probe symlink under `parentDir` pointing at
    * `targetDir` that is created, tested, and removed entirely within this method — no lingering symlink is left
    * behind regardless of the result. Call BEFORE creating the test's own symlink so the test can decide whether to
    * create one at all.
    *
    * On Node all platforms honor lstat correctly, so this returns true whenever symlink creation succeeds. Exists for
    * API parity with the JVM/Native SymlinkTestSupport (ISS-1347).
    */
  def symlinkSafelyTestable(parentDir: FilePath, targetDir: FilePath): Boolean = {
    val probeDir  = parentDir.resolve("_symlink_probe_" + System.nanoTime().toString)
    val probeLink = probeDir.resolve("probe-link")
    try {
      fs.mkdirSync(probeDir.pathString, js.Dynamic.literal(recursive = true))
      val absoluteTarget = path.resolve(targetDir.pathString).asInstanceOf[String]
      fs.symlinkSync(absoluteTarget, probeLink.pathString): Unit
      val honored = !fs.lstatSync(probeLink.pathString).isDirectory().asInstanceOf[Boolean]
      // Clean up the probe link directly (unlinkSync removes the symlink entry itself, not its target).
      fs.unlinkSync(probeLink.pathString)
      fs.rmdirSync(probeDir.pathString)
      honored
    } catch {
      // Symlink creation failed — this Node host cannot create symlinks.
      case _: js.JavaScriptException => false
    }
  }
}
