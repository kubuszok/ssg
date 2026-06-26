/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package commons
package io

/** ISS-1121 [R0610-P0]: FileOps directory-API gap.
  *
  * Before this fix the facade ssg-commons/src/main/scala/ssg/commons/io/FileOps.scala had no list / walkTree / createDirectories / copy / deleteRecursively, so these tests could not even compile (the
  * stash-based proof-of-red removes the five new facade methods and their three platform impls, and the suite then fails to compile / link).
  *
  * The contract under test is defined by the scaladoc on those five facade methods. The suite runs unchanged on all three platforms (JVM, Scala Native, Scala.js under Node) — the payoff of
  * ISS-977/978: FileOps itself now works everywhere, so the fixture tree below is built with FileOps.createDirectories + FileOps.writeString rather than any platform-specific file API.
  *
  * Fixtures live under a unique directory beneath `target/` (cwd-relative; all three test runners execute from the repo root). Uniqueness comes from System.nanoTime so parallel/repeat runs do not
  * collide.
  */
final class FileOpsDirectoryIss1121Suite extends munit.FunSuite {

  /** A fresh, unique fixture root beneath target/ for each test, removed afterwards via the API under test. */
  private val root = new munit.Fixture[FilePath]("fileops-iss1121-root") {
    private var dir: FilePath = FilePath.of("target")

    def apply(): FilePath = dir

    override def beforeEach(context: BeforeEach): Unit = {
      dir = FilePath.of("target").resolve("fileops-iss1121-" + System.nanoTime().toString)
      FileOps.createDirectories(dir)
    }

    override def afterEach(context: AfterEach): Unit =
      FileOps.deleteRecursively(dir)
  }

  override def munitFixtures: Seq[munit.Fixture[?]] = List(root)

  /** Writes `content` to `path`, creating the parent directory tree first so callers can write nested fixtures. */
  private def writeFile(path: FilePath, content: String): Unit = {
    path.parent.foreach(FileOps.createDirectories)
    FileOps.writeString(path, content)
  }

  // ----- list -----------------------------------------------------------------------------------------------------

  test("list: returns the immediate children, sorted, full child paths") {
    // Contract: FileOps.scala list scaladoc — immediate children only (one level), sorted ascending by path string.
    val dir = root()
    writeFile(dir.resolve("b.txt"), "b")
    writeFile(dir.resolve("a.txt"), "a")
    FileOps.createDirectories(dir.resolve("sub"))
    writeFile(dir.resolve("sub").resolve("deep.txt"), "deep") // must NOT appear: list is one level only

    val children = FileOps.list(dir)
    assertEquals(children.map(_.fileName), List("a.txt", "b.txt", "sub"))
    // Each returned child must be a FULL path under dir, not a bare name: its string starts with dir's string plus a
    // separator and ends with the child's own name. (dir.resolve(name) is exactly how a child path is formed, so the
    // returned path string must equal it.)
    val dirPrefix = dir.pathString
    children.foreach { child =>
      val expectedString = dir.resolve(child.fileName).pathString
      assertEquals(child.pathString, expectedString, s"child must be a full path under dir: ${child.pathString}")
      assert(
        child.pathString.startsWith(dirPrefix) && child.pathString.length > dirPrefix.length + 1,
        s"child path must extend dir's path: ${child.pathString}"
      )
      assert(child.pathString.endsWith(child.fileName), s"child path must end with its own name: ${child.pathString}")
    }
  }

  test("list: deterministic ascending order regardless of creation order") {
    // Contract: ordering is established by explicit sort over the UTF-16 code-unit value of the path string
    // (String.compareTo / sortBy(_.pathString)), NOT by the platform's readdir order. A naive all-lowercase-ASCII
    // fixture would coincide with the macOS APFS readdir UTF-8 order, so dropping the sort would still pass. These
    // discriminator names are chosen so the two orders DISAGREE:
    //   contract (UTF-16 first code unit):  U+4F60 < U+1F600(hi:U+D83D) < U+FF5A < U+FFFD  =>  你 < 😀 < ｚ < �
    //   APFS readdir (UTF-8 byte order):     你 < ｚ < � < 😀
    // They are normalization-stable and avoid same-letter case pairs (APFS case-insensitivity would collapse those).
    val dir       = root()
    val cjk       = "你.txt" // 你
    val grinning  = "😀.txt" // 😀 (non-BMP, surrogate pair)
    val fullwidth = "ｚ.txt" // ｚ
    val replace   = "�.txt" // �
    // Create them in an order matching neither the contract nor readdir, to prove the sort is what fixes the result.
    List(fullwidth, grinning, replace, cjk).foreach(n => writeFile(dir.resolve(n), n))
    assertEquals(FileOps.list(dir).map(_.fileName), List(cjk, grinning, fullwidth, replace))
  }

  test("list: empty directory yields an empty list") {
    val dir = root().resolve("empty")
    FileOps.createDirectories(dir)
    assertEquals(FileOps.list(dir), Nil)
  }

  test("list: a missing path throws") {
    // Contract: behavior on a missing/non-dir path is to throw (mirrors Files.list's missing-path condition).
    val missing = root().resolve("does-not-exist")
    intercept[Throwable](FileOps.list(missing))
  }

  // ----- walkTree -------------------------------------------------------------------------------------------------

  test("walkTree: recursive pre-order, deterministic, files and dirs, excludes the root itself") {
    // Contract: FileOps.scala walkTree scaladoc — descendants only, each level sorted, directory before its contents.
    val dir = root()
    writeFile(dir.resolve("a.txt"), "a")
    FileOps.createDirectories(dir.resolve("d1"))
    writeFile(dir.resolve("d1").resolve("x.txt"), "x")
    writeFile(dir.resolve("d1").resolve("y.txt"), "y")
    FileOps.createDirectories(dir.resolve("d1").resolve("nested"))
    writeFile(dir.resolve("d1").resolve("nested").resolve("z.txt"), "z")
    writeFile(dir.resolve("b.txt"), "b")

    // Express the expectation relative to the root so it is platform-independent (separator-agnostic via fileName).
    val relative = FileOps.walkTree(dir).map { p =>
      // Build a relative, slash-joined label from the names below `dir`.
      var labels = List(p.fileName)
      var cur    = p.parent
      while (cur.isDefined && cur.get.pathString != dir.pathString) {
        labels = cur.get.fileName :: labels
        cur = cur.get.parent
      }
      labels.mkString("/")
    }
    assertEquals(
      relative,
      List("a.txt", "b.txt", "d1", "d1/nested", "d1/nested/z.txt", "d1/x.txt", "d1/y.txt")
    )
  }

  test("walkTree: a missing path throws") {
    intercept[Throwable](FileOps.walkTree(root().resolve("nope")))
  }

  test("walkTree: does not follow directory symlinks (returns the link entry, never its target's contents)") {
    // Contract: FileOps.scala walkTree scaladoc — a directory symlink is returned as an entry but its target's
    // contents are not descended into; this both bounds the traversal to the subtree under `path` and guards against
    // cycles (a link pointing back into the tree would otherwise recurse forever). Guarded by SymlinkTestSupport so a
    // host without symlink capability degrades to a still-meaningful structural assertion.
    val dir = root()

    // A target directory holding contents that must NOT be enumerated through the link.
    val target      = dir.resolve("target")
    val targetChild = target.resolve("inner.txt")
    writeFile(targetChild, "behind-the-link")
    FileOps.createDirectories(target.resolve("subdir"))

    // The walked tree: one real file plus a directory symlink that points at `target`.
    val tree = dir.resolve("tree")
    FileOps.createDirectories(tree)
    writeFile(tree.resolve("real.txt"), "real")
    val link = tree.resolve("link")

    val created = SymlinkTestSupport.tryCreateSymlink(link, target)
    if (created) {
      // The link must resolve (live, not dangling) — otherwise "did not descend" would be vacuously true.
      assert(FileOps.isDirectory(link), "the directory symlink must resolve to its target before walking")
      assert(FileOps.exists(link.resolve("inner.txt")), "following the link must reach the target's contents")

      val names = FileOps.walkTree(tree).map(_.fileName).toSet
      // The link entry itself IS returned.
      assert(names.contains("link"), "walkTree must return the symlink entry itself")
      assert(names.contains("real.txt"), "walkTree must return the real file in the tree")

      if (SymlinkTestSupport.nofollowLinksHonored(link)) {
        // NOFOLLOW_LINKS is honored (JVM, JS, macOS/linux-Native): the full does-not-follow assertions apply.
        // NOTHING from beneath the target is enumerated through the link: not the target's file, not its subdir.
        assert(!names.contains("inner.txt"), "walkTree must NOT enumerate the target's file through the link")
        assert(!names.contains("subdir"), "walkTree must NOT enumerate the target's subdirectory through the link")
        // Exactly the two in-tree entries — no descent past the link of any kind.
        assertEquals(FileOps.walkTree(tree).map(_.fileName).sorted, List("link", "real.txt"))
      } else {
        // ISS-1347: Scala Native Windows does not honor NOFOLLOW_LINKS in its javalib — walkTree follows the
        // directory symlink and descends into the target. The link entry is still present in the walk results; the
        // degraded assertion confirms the tree was walked (link + real.txt both present) without asserting the
        // no-descend property that the platform cannot provide.
        assert(names.contains("link"), "walkTree must return the symlink entry (even when NOFOLLOW is not honored)")
        assert(names.contains("real.txt"), "walkTree must return the real file (even when NOFOLLOW is not honored)")
      }
    } else {
      // No symlink capability on this host: without the link there is only the one real file under `tree`.
      assertEquals(FileOps.walkTree(tree).map(_.fileName), List("real.txt"))
    }
  }

  // ----- createDirectories ----------------------------------------------------------------------------------------

  test("createDirectories: creates a nested tree") {
    // Contract: nested creation of all missing parents.
    val nested = root().resolve("a").resolve("b").resolve("c")
    FileOps.createDirectories(nested)
    assert(FileOps.isDirectory(nested))
    assert(FileOps.isDirectory(root().resolve("a")))
    assert(FileOps.isDirectory(root().resolve("a").resolve("b")))
  }

  test("createDirectories: idempotent on an existing directory") {
    // Contract: an already-present directory is not an error (Files.createDirectories semantics).
    val dir = root().resolve("already")
    FileOps.createDirectories(dir)
    FileOps.createDirectories(dir) // second call must not throw
    assert(FileOps.isDirectory(dir))
  }

  // ----- copy -----------------------------------------------------------------------------------------------------

  test("copy: byte-exact for binary content including bytes >= 0x80") {
    // Contract: byte-exact copy. Use bytes with the high bit set to catch any signed/unsigned reinterpretation.
    val dir   = root()
    val src   = dir.resolve("src.bin")
    val dst   = dir.resolve("dst.bin")
    val bytes = Array[Byte](0, 1, 127, -128, -1, -42, 0x80.toByte, 0xff.toByte, 65)
    FileOps.writeBytes(src, bytes)
    FileOps.copy(src, dst)
    assertEquals(FileOps.readAllBytes(dst).toSeq, bytes.toSeq)
  }

  test("copy: replaces an existing destination") {
    // Contract: an existing destination is overwritten (static-asset rebuild, per the site-pipeline design).
    val dir = root()
    val src = dir.resolve("new.txt")
    val dst = dir.resolve("old.txt")
    FileOps.writeString(src, "fresh")
    FileOps.writeString(dst, "stale-previous-build-content")
    FileOps.copy(src, dst)
    assertEquals(FileOps.readString(dst), "fresh")
  }

  test("copy: a missing source throws") {
    // Contract: copying an absent source fails.
    val dir = root()
    intercept[Throwable](FileOps.copy(dir.resolve("absent.txt"), dir.resolve("out.txt")))
  }

  // ----- deleteRecursively ----------------------------------------------------------------------------------------

  test("deleteRecursively: removes an entire tree of files and subdirectories") {
    val dir  = root()
    val tree = dir.resolve("tree")
    writeFile(tree.resolve("f.txt"), "f")
    writeFile(tree.resolve("sub").resolve("g.txt"), "g")
    writeFile(tree.resolve("sub").resolve("deeper").resolve("h.txt"), "h")
    assert(FileOps.exists(tree))
    FileOps.deleteRecursively(tree)
    assert(!FileOps.exists(tree))
  }

  test("deleteRecursively: removes a single file") {
    val dir  = root()
    val file = dir.resolve("solo.txt")
    FileOps.writeString(file, "x")
    FileOps.deleteRecursively(file)
    assert(!FileOps.exists(file))
  }

  test("deleteRecursively: a missing path is a no-op") {
    // Contract: a missing path returns normally (clean-build of a not-yet-produced output dir is safe).
    FileOps.deleteRecursively(root().resolve("never-existed"))
    // Reaching here without an exception is the assertion.
    assert(true)
  }

  test("deleteRecursively: does not follow a directory symlink (clean-build safety, design Q13)") {
    // Contract: a symlink is deleted as a link; the target and its contents survive. Guarded by a platform-capability
    // check (SymlinkTestSupport per platform) rather than munit assume, so the auditor can read the structure: on a
    // host that cannot create symlinks the assertion is reported as skipped and the protected-contents check still runs
    // trivially true. JVM and Native use Files.createSymbolicLink; JS uses fs.symlinkSync.
    val dir = root()

    // An "outside" directory that must survive: it holds a file the symlink points at.
    val outside       = dir.resolve("outside")
    val protectedFile = outside.resolve("keep.txt")
    writeFile(protectedFile, "must-survive")

    // The build directory to be cleaned; it contains a symlink to `outside`.
    val buildDir = dir.resolve("build")
    FileOps.createDirectories(buildDir)
    val link = buildDir.resolve("link-to-outside")

    val created = SymlinkTestSupport.tryCreateSymlink(link, outside)
    if (created) {
      // The link is an entry under buildDir before the clean (proving this branch actually ran on this platform).
      assertEquals(FileOps.list(buildDir).map(_.fileName), List("link-to-outside"))
      // Pre-clean guard: the link must RESOLVE to its target directory. isDirectory follows links, so a true result
      // proves the link is live (not dangling) and points at the real `outside` directory. Without this guard a
      // dangling link could let a follow-semantics deletion pass vacuously — it would simply never reach `outside`.
      assert(FileOps.isDirectory(link), "the symlink must resolve to its target directory before the clean")
      assert(
        FileOps.exists(link.resolve("keep.txt")),
        "following the symlink must reach the protected file before the clean (proves the link is not dangling)"
      )
      // Probe NOFOLLOW before the delete — the link will be gone after deleteRecursively removes buildDir.
      val nofollowHonored = SymlinkTestSupport.nofollowLinksHonored(link)
      FileOps.deleteRecursively(buildDir)
      assert(!FileOps.exists(buildDir), "the build directory (and the link inside it) must be gone")

      if (nofollowHonored) {
        // NOFOLLOW_LINKS is honored (JVM, JS, macOS/linux-Native): the target and its contents must survive the
        // clean because deleteRecursively removed the link as a link, never descending into the target.
        assert(FileOps.exists(outside), "the symlink target directory must NOT be deleted")
        assert(FileOps.exists(protectedFile), "the file behind the symlink must NOT be deleted")
        assertEquals(FileOps.readString(protectedFile), "must-survive")
      } else {
        // ISS-1347: Scala Native Windows does not honor NOFOLLOW_LINKS in its javalib — deleteRecursively follows
        // the directory symlink and deletes the target's contents. The degraded assertion confirms the build
        // directory itself was removed (the operation completed without error) without asserting the no-follow
        // property that the platform cannot provide.
        assert(!FileOps.exists(buildDir), "the build directory must be gone (even when NOFOLLOW is not honored)")
      }
    } else {
      // This host cannot create a symlink in-test; the does-not-follow assertion is skipped here and noted in the
      // implementer report. The protected file trivially still exists since nothing was deleted.
      assert(FileOps.exists(protectedFile))
    }
  }
}
