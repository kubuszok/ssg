/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package liquid

import ssg.commons.io.{ FileOps, FilePath }
import ssg.liquid.antlr.LocalFSNameResolver
import ssg.liquid.tags.IncludeRelative

/** ISS-1020: tests for the optional root-jail on `LocalFSNameResolver`.
  *
  * Tests (a)/(b): absolute and relative traversal attempts with a jail-root set must throw `JailViolationException` BEFORE any filesystem I/O.
  *
  * Tests (c)/(e): legitimate include resolution using a real temp directory.
  *
  * Test (d): the separator-boundary predicate rejects sibling-prefix paths — pure string comparison, no filesystem I/O.
  */
final class LocalFSNameResolverJailIss1020Suite extends munit.FunSuite {

  // ---------- (a) absolute-path traversal rejected ----------

  test("Iss1020-a: absolute path outside jail root throws JailViolationException") {
    val jailRoot = FilePath.of("/tmp/safe-root")
    val resolver = new LocalFSNameResolver("/tmp/safe-root", jailRoot = Some(jailRoot))

    val ex = intercept[IncludeRelative.JailViolationException] {
      resolver.resolve("/etc/passwd")
    }
    // The exception must carry the resolved path and the jail root
    assertEquals(ex.jailRoot.pathString, jailRoot.pathString)
    assert(ex.resolvedPath.pathString.contains("/etc/passwd"), "resolved path should reference /etc/passwd")
  }

  // ---------- (b) relative ../ traversal rejected ----------

  test("Iss1020-b: relative path escaping jail root via ../ throws JailViolationException") {
    val jailRoot = FilePath.of("/tmp/safe-root")
    val resolver = new LocalFSNameResolver("/tmp/safe-root/includes", jailRoot = Some(jailRoot))

    val ex = intercept[IncludeRelative.JailViolationException] {
      resolver.resolve("../../../../etc/passwd")
    }
    assertEquals(ex.jailRoot.pathString, jailRoot.pathString)
  }

  // ---------- (c) legitimate include accepted ----------

  test("Iss1020-c: legitimate include under jail root resolves normally") {
    val tmpBase = FilePath.of(System.getProperty("java.io.tmpdir", "/tmp")).resolve("iss1020-test-c-" + System.currentTimeMillis()).toAbsolute.normalize
    FileOps.createDirectories(tmpBase)
    try {
      val includeFile = tmpBase.resolve("header.liquid")
      FileOps.writeString(includeFile, "Hello from header")

      val resolver = new LocalFSNameResolver(tmpBase.pathString, jailRoot = Some(tmpBase))
      val result   = resolver.resolve("header")

      assertEquals(result.content, "Hello from header")
      assert(result.sourceName.contains("header.liquid"), "source name should contain header.liquid")
    } finally
      FileOps.deleteRecursively(tmpBase)
  }

  // ---------- (d) separator-boundary REJECTION (mandatory — recurring C8 gap) ----------

  test("Iss1020-d: isUnderRoot rejects sibling-prefix path (separator-boundary predicate)") {
    val root    = FilePath.of("/tmp/src")
    val sibling = FilePath.of("/tmp/srcfoo/evil.txt")
    val child   = FilePath.of("/tmp/src/ok.txt")

    // Sibling-prefix path MUST be rejected. Under the weakened predicate
    // `startsWith(rootStr)` (dropping `+ Separator`), "/tmp/srcfoo/evil.txt"
    // .startsWith("/tmp/src") is TRUE, so this assertion would FAIL.
    assert(
      !IncludeRelative.isUnderRoot(sibling, root),
      s"Sibling-prefix path '${sibling.pathString}' must NOT be accepted under root '${root.pathString}'"
    )

    // Genuine child MUST be accepted.
    assert(
      IncludeRelative.isUnderRoot(child, root),
      s"Genuine child path '${child.pathString}' must be accepted under root '${root.pathString}'"
    )
  }

  // ---------- (e) inert when no jail-root ----------

  test("Iss1020-e: resolver without jail-root resolves normally (faithful port)") {
    val tmpBase = FilePath.of(System.getProperty("java.io.tmpdir", "/tmp")).resolve("iss1020-test-e-" + System.currentTimeMillis()).toAbsolute.normalize
    FileOps.createDirectories(tmpBase)
    try {
      val includeFile = tmpBase.resolve("footer.liquid")
      FileOps.writeString(includeFile, "Footer content")

      // No jail-root set — default constructor behavior
      val resolver = new LocalFSNameResolver(tmpBase.pathString)
      val result   = resolver.resolve("footer")

      assertEquals(result.content, "Footer content")
      assert(result.sourceName.contains("footer.liquid"), "source name should contain footer.liquid")
    } finally
      FileOps.deleteRecursively(tmpBase)
  }
}
