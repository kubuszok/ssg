/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package liquid

import ssg.commons.io.FilePath
import ssg.liquid.tags.IncludeRelative

/** ISS-1214 / C8: mutation-killing test for the separator-boundary predicate in `IncludeRelative.isUnderRoot`.
  *
  * Same predicate shape as `RootJail.isUnderRoot` in ssg-site. The predicate uses `startsWith(rootStr + Separator)` (not bare `startsWith(rootStr)`) to prevent sibling-prefix false positives (e.g.
  * root="/tmp/src", resolved="/tmp/srcfoo/evil.txt").
  *
  * All three assertions use `FilePath.of(...)` with absolute synthetic paths. The predicate is pure string-prefix comparison on `pathString`, so no filesystem I/O is required.
  */
final class IncludeRelativeJailIss1214Suite extends munit.FunSuite {

  test("Iss1214: isUnderRoot rejects sibling-prefix path that shares root prefix but is not a child") {
    val root    = FilePath.of("/tmp/src")
    val sibling = FilePath.of("/tmp/srcfoo/evil.txt")
    val child   = FilePath.of("/tmp/src/ok.txt")

    // (a) Sibling-prefix path MUST be rejected (the mutation-killing assertion).
    // Under the weakened predicate `startsWith(rootStr)` (dropping `+ Separator`),
    // "/tmp/srcfoo/evil.txt".startsWith("/tmp/src") is TRUE, so this assertion
    // would FAIL -- killing the mutation.
    assert(
      !IncludeRelative.isUnderRoot(sibling, root),
      s"Sibling-prefix path '${sibling.pathString}' must NOT be accepted under root '${root.pathString}'"
    )

    // (b) Genuine child MUST be accepted (sanity -- the predicate is not over-restrictive).
    assert(
      IncludeRelative.isUnderRoot(child, root),
      s"Genuine child path '${child.pathString}' must be accepted under root '${root.pathString}'"
    )

    // (c) Root itself MUST be accepted (exact-match branch of the predicate).
    assert(
      IncludeRelative.isUnderRoot(root, root),
      s"Root path itself '${root.pathString}' must be accepted as under root"
    )
  }
}
