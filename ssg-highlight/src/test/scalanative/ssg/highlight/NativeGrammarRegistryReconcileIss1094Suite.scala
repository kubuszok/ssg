/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package highlight

/** Native-platform proof test for ISS-1094 (R0610).
  *
  * Asserts that the set of grammars embedded in the Native binary
  * (TreeSitterPlatformImpl.availableGrammars) exactly equals the set
  * of grammars registered in LanguageRegistry.registeredGrammars.
  *
  * Before the fix, the Native impl had 84 entries (including 11 dead
  * grammars: agda, commonlisp, diff, hcl, hlsl, ql, query, test,
  * verilog, vue, wgsl_bevy) while the registry had 73 — the assertion
  * fails because the sets are not equal. After removal of the 11 dead
  * grammars, both sets have 73 entries and the assertion passes.
  */
final class NativeGrammarRegistryReconcileIss1094Suite extends munit.FunSuite {

  test("ISS-1094: Native availableGrammars equals LanguageRegistry.registeredGrammars") {
    val nativeGrammars   = TreeSitterPlatform.availableGrammars.toSet
    val registryGrammars = LanguageRegistry.registeredGrammars
    val inNativeOnly     = (nativeGrammars -- registryGrammars).toSeq.sorted
    val inRegistryOnly   = (registryGrammars -- nativeGrammars).toSeq.sorted
    assert(
      nativeGrammars == registryGrammars,
      s"Native grammars (${nativeGrammars.size}) != registry grammars (${registryGrammars.size}). " +
        s"In native only: [${inNativeOnly.mkString(", ")}]. " +
        s"In registry only: [${inRegistryOnly.mkString(", ")}]."
    )
  }

  test("ISS-1094: none of the 11 removed grammars appear in Native availableGrammars") {
    val removedGrammars = Set(
      "agda", "commonlisp", "diff", "hcl", "hlsl",
      "ql", "query", "test", "verilog", "vue", "wgsl_bevy"
    )
    val nativeGrammars = TreeSitterPlatform.availableGrammars.toSet
    val stillPresent   = removedGrammars.intersect(nativeGrammars).toSeq.sorted
    assert(
      stillPresent.isEmpty,
      s"${stillPresent.size} dead grammar(s) still present in Native availableGrammars: ${stillPresent.mkString(", ")}"
    )
  }

  test("ISS-1094: Native availableGrammars count is 73") {
    val count = TreeSitterPlatform.availableGrammars.size
    assertEquals(count, 73, s"Expected 73 available grammars on Native, got $count")
  }
}
