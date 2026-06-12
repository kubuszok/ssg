/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package sass

import scala.language.implicitConversions

import ssg.sass.importer.MapImporter
import ssg.sass.value.SassNumber
import ssg.sass.visitor.OutputStyle

/** Differential red tests for ISS-990 / ISS-991 / ISS-992 / ISS-993 — the
  * `Compile.compileString` parameters that are accepted but dropped before
  * they reach the component that consumes them ([R0610-P1] api-noop family).
  *
  * Red-commit protocol (remediation-2026-06.md §6): every test tagged "RED"
  * below FAILS at the red-sha and must pass after the fix, with this file
  * unchanged. The "control" tests pin behavior that already works today so
  * the fix cannot regress the adjacent working path.
  *
  * Upstream consumption of each parameter in dart-sass (the port's reference,
  * vendored at original-src/dart-sass):
  *
  *   - `functions` (ISS-990): lib/sass.dart:220 (compileStringToResult param)
  *     -> lib/sass.dart:241 (forwarded) -> lib/src/compile.dart:120/206
  *     (compileString -> _compileStylesheet -> evaluate(functions:)) ->
  *     lib/src/visitor/evaluate.dart:699-709 — every user callable is merged
  *     with the global built-ins and registered:
  *     `_builtInFunctions[function.name.replaceAll("_", "-")] = function;`
  *     (evaluate.dart:707-708). The port's equivalents exist:
  *     Callable.function (Callable.scala:41) and Environment.setFunction
  *     (Environment.scala:709) — they are simply never invoked from
  *     Compile.compileString.
  *
  *   - `importers` / `loadPaths` (ISS-991): lib/sass.dart:217/219 ->
  *     lib/sass.dart:236-239 — both are folded into the ImportCache:
  *     `importCache: ImportCache(importers: importers, ..., loadPaths:
  *     loadPaths)`. lib/src/import_cache.dart:100-103 stores
  *     `_importers = _toImporters(importers, loadPaths, packageConfig)` and
  *     import_cache.dart:128-129 turns each load path into a filesystem
  *     importer: `for (var path in loadPaths) FilesystemImporter(path)`.
  *     (loadPaths is exercised in CompileLoadPathsIss991JvmSuite because
  *     FilesystemImporter is JVM-only in the port.)
  *
  *   - `quietDeps` (ISS-992): lib/sass.dart:224 -> lib/sass.dart:245 ->
  *     lib/src/compile.dart:126/166/208 -> lib/src/visitor/evaluate.dart:375
  *     constructor param, stored at evaluate.dart:381 `_quietDeps =
  *     quietDeps`, consumed at evaluate.dart:4682:
  *     `if (_quietDeps && _inDependency) return;` — i.e. warnings from
  *     stylesheets loaded through importers/loadPaths are silenced
  *     (lib/sass.dart:185-186). The port has the identical check at
  *     EvaluateVisitor.scala:2355 but hardcodes `_quietDeps = false`
  *     (EvaluateVisitor.scala:292).
  *
  *   - `charset` (ISS-993): lib/sass.dart:227 -> lib/sass.dart:248 ->
  *     lib/src/compile.dart:129/168/220 (serialize(charset: charset)) ->
  *     lib/src/visitor/serialize.dart:55 param, consumed at
  *     serialize.dart:70-71: when true and the output has any code unit
  *     > 0x7F the prefix is the U+FEFF BOM (compressed) or `'@charset
  *     "UTF-8";\n'` (other styles); when false there is never a prefix.
  *     The port supports this in SerializeVisitor.serialize
  *     (SerializeVisitor.scala:161-168) but the call site
  *     (Compile.scala:153) omits the argument.
  */
final class CompileWiringIss990Suite extends munit.FunSuite {

  /** A `my-double($x)` user function: returns its numeric argument times 2. */
  private def myDouble: Callable =
    Callable.function(
      "my-double",
      "$x",
      args => SassNumber(args.head.assertNumber().value * 2)
    )

  // ---------------------------------------------------------------------------
  // ISS-990 — `functions =` must register user callables in the global
  // environment (dart-sass evaluate.dart:699-709).
  // ---------------------------------------------------------------------------

  test("ISS-990 RED: custom function passed via functions= is invocable") {
    val result = Compile.compileString(
      "a { b: my-double(4); }",
      functions = Nullable(List(myDouble))
    )
    // dart-sass: a registered Callable is a real function, so `my-double(4)`
    // evaluates to the number 8 (evaluate.dart:707-708 registration; plain-CSS
    // fallback never applies to registered names).
    assertEquals(result.css, "a {\n  b: 8;\n}\n")
  }

  test("ISS-990 control: without functions=, unknown call renders as plain CSS") {
    // Pins the plain-CSS fallback (port: EvaluateVisitor.scala:1366-1399,
    // dart-sass async_evaluate.dart:3638-3656): an unregistered function is
    // emitted verbatim as an unquoted string.
    val result = Compile.compileString("a { b: my-double(4); }")
    assertEquals(result.css, "a {\n  b: my-double(4);\n}\n")
  }

  // ---------------------------------------------------------------------------
  // ISS-991 — `importers =` must participate in load resolution
  // (dart-sass sass.dart:236-239 via ImportCache, import_cache.dart:100-103).
  // ---------------------------------------------------------------------------

  /** In-memory importer resolving `dep` -> `_dep.scss` (a partial). */
  private def depImporter: MapImporter =
    new MapImporter(Map("_dep.scss" -> "c {\n  d: e;\n}\n"))

  test("ISS-991 RED: importers= resolves @use of an in-memory stylesheet") {
    val result = Compile.compileString(
      "@use \"dep\";\na { b: f; }",
      importers = Nullable(List(depImporter))
    )
    assert(
      result.css.contains("c {\n  d: e;\n}"),
      s"@use \"dep\" content missing from output:\n${result.css}"
    )
  }

  test("ISS-991 control: singular importer= resolves the same @use today") {
    val result = Compile.compileString(
      "@use \"dep\";\na { b: f; }",
      importer = Nullable(depImporter)
    )
    assert(
      result.css.contains("c {\n  d: e;\n}"),
      s"@use \"dep\" content missing from output:\n${result.css}"
    )
  }

  // ---------------------------------------------------------------------------
  // ISS-992 — `quietDeps =` must silence warnings originating in dependencies
  // (dart-sass evaluate.dart:4682: `if (_quietDeps && _inDependency) return;`).
  //
  // The dependency is loaded through an ImportCache importer that is NOT the
  // entrypoint importer, so `isDependency` is true (dart-sass
  // async_evaluate.dart:2007 `importer != _importer`; port
  // EvaluateVisitor.scala:1001). Its body triggers the slash-div deprecation
  // (parenthesized `/` division), the same fixture DeprecationSuite pins for
  // the entrypoint case.
  // ---------------------------------------------------------------------------

  /** Dependency whose evaluation emits a slash-div deprecation warning. */
  private def slashDivDepCache: ImportCache =
    ImportCache.only(List(new MapImporter(Map("_dep.scss" -> "q { r: (10 / 2); }"))))

  test("ISS-992 control: dependency deprecation surfaces when quietDeps=false") {
    val result = Compile.compileString(
      "@use \"dep\";",
      importCache = Nullable(slashDivDepCache),
      quietDeps = false
    )
    assert(
      result.warnings.exists(_.contains("[slash-div]")),
      s"Expected [slash-div] deprecation from the dependency, got:\n${result.warnings.mkString("\n")}"
    )
  }

  test("ISS-992 RED: quietDeps=true silences dependency deprecation warnings") {
    val result = Compile.compileString(
      "@use \"dep\";",
      importCache = Nullable(slashDivDepCache),
      quietDeps = true
    )
    // dart-sass sass.dart:185-186: "If [quietDeps] is `true`, this will
    // silence compiler warnings emitted for stylesheets loaded through
    // [importers], [loadPaths], or [packageConfig]." Enforced at
    // evaluate.dart:4682.
    assert(
      !result.warnings.exists(_.contains("[slash-div]")),
      s"quietDeps=true must suppress dependency warnings, got:\n${result.warnings.mkString("\n")}"
    )
  }

  test("ISS-992 control: quietDeps=true keeps entrypoint warnings (not a dependency)") {
    // dart-sass only silences _inDependency warnings; the entrypoint
    // stylesheet still warns (evaluate.dart:4682 requires both flags).
    val result = Compile.compileString(
      "q { r: (10 / 2); }",
      quietDeps = true
    )
    assert(
      result.warnings.exists(_.contains("[slash-div]")),
      s"Entrypoint deprecation must survive quietDeps=true, got:\n${result.warnings.mkString("\n")}"
    )
  }

  // ---------------------------------------------------------------------------
  // ISS-993 — `charset =` must be forwarded to serialization
  // (dart-sass serialize.dart:55, prefix logic at serialize.dart:70-71).
  // ---------------------------------------------------------------------------

  test("ISS-993 control: charset=true (default) prefixes @charset for non-ASCII output") {
    // serialize.dart:70-71: expanded style with a >0x7F code unit gets
    // `@charset "UTF-8";\n`.
    val result = Compile.compileString("a { content: \"é\"; }")
    assert(
      result.css.startsWith("@charset \"UTF-8\";\n"),
      s"Expected @charset prefix for non-ASCII output:\n${result.css}"
    )
  }

  test("ISS-993 RED: charset=false omits the @charset prefix for non-ASCII output") {
    val result = Compile.compileString(
      "a { content: \"é\"; }",
      charset = false
    )
    // serialize.dart:70: the prefix is only emitted `if (charset && ...)` —
    // with charset=false the output starts directly with the first rule.
    assert(
      !result.css.contains("@charset"),
      s"charset=false must not emit @charset:\n${result.css}"
    )
    assert(
      !result.css.startsWith("\uFEFF"),
      s"charset=false must not emit a BOM:\n${result.css}"
    )
  }

  test("ISS-993 RED: charset=false omits the BOM in compressed mode") {
    val result = Compile.compileString(
      "a { content: \"é\"; }",
      style = OutputStyle.Compressed,
      charset = false
    )
    // serialize.dart:71: compressed style uses a U+FEFF BOM instead of
    // `@charset` — charset=false must suppress that too.
    assert(
      !result.css.startsWith("\uFEFF"),
      s"charset=false must not emit a BOM in compressed mode:\n${result.css}"
    )
  }
}
