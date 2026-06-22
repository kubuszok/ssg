/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Differential tests for ISS-1242: SymbolDef.mangle must dispatch to
 * Mangler.nextMangledFunction when the symbol's scope is an AstFunction
 * (a function expression). Without the dispatch, the Safari strict-mode
 * tricky-def guard (terser #179, #326) is bypassed, allowing the mangled
 * output to produce `function X(X){...}` — a syntax error in Safari strict
 * mode because a function expression's argument cannot shadow the function
 * expression's own name.
 *
 * Oracle: terser lib/scope.js:745-759 (AST_Function.DEFMETHOD("next_mangled",
 * ...)) is a polymorphic override that, for every candidate mangled name,
 * checks whether it collides with the function expression's own (mangled or
 * original) name when the symbol being mangled is a funarg. If it collides,
 * the candidate is skipped. The SSG helper Mangler.nextMangledFunction
 * (Mangler.scala:257-288) faithfully implements this logic; the bug was that
 * SymbolDef.mangle never dispatched to it.
 *
 * Proof-of-red strategy: reverting the `case fn: AstFunction =>
 * Mangler.nextMangledFunction(fn, options, this)` dispatch in SymbolDef.mangle
 * (SymbolDef.scala:171-172) causes the ie8 tricky-def test to fail, because
 * nextMangled (the base path) does not skip candidate names matching the
 * function expression's name. With ie8=true, the AstSymbolLambda (function
 * expression name) is mangled at the PARENT scope (different cname counter),
 * while the AstSymbolFunarg (parameter) is mangled at the function scope
 * (its own cname counter starting fresh). A SequentialIdentifier is used to
 * make the base54 sequence deterministic (a, b, c, ...), guaranteeing the
 * collision: the function expression name gets "a" from the parent scope,
 * and the parameter's first candidate from the function scope is also "a".
 * With the dispatch, nextMangledFunction detects this and skips to "b".
 */
package ssg
package js

import ssg.js.output.OutputStream
import ssg.js.parse.Parser
import ssg.js.scope.{ Mangler, ManglerOptions, NthIdentifier, ScopeAnalysis, ScopeOptions }

final class NextMangledFunctionDispatchIss1242Suite extends munit.FunSuite {

  override val munitTimeout = scala.concurrent.duration.Duration(30, "s")

  // ---------------------------------------------------------------------------
  // A deterministic NthIdentifier that returns a, b, c, ..., z, aa, ab, ...
  // This bypasses the frequency-based Base54 sorting, making the mangled name
  // sequence predictable for testing.
  // ---------------------------------------------------------------------------

  private class SequentialIdentifier extends NthIdentifier {
    override def get(n: Int): String = {
      val sb  = new StringBuilder
      var num = n + 1
      while (num > 0) {
        num -= 1
        sb.append(('a' + (num % 26)).toChar)
        num = num / 26
      }
      sb.toString()
    }
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  /** Parse, run scope analysis, mangle with ie8 and sequential identifiers,
    * and return the printed output.
    */
  private def mangleIe8(input: String): String = {
    val ast = new Parser().parse(input)
    ScopeAnalysis.figureOutScope(ast, ScopeOptions(ie8 = true))
    val seqId = new SequentialIdentifier()
    Mangler.mangleNames(ast, ManglerOptions(ie8 = true, nthIdentifier = seqId))
    OutputStream.printToString(ast)
  }

  /** Parse, run scope analysis, mangle WITHOUT ie8 and with sequential
    * identifiers, and return the printed output.
    */
  private def mangleNormal(input: String): String = {
    val ast = new Parser().parse(input)
    ScopeAnalysis.figureOutScope(ast)
    val seqId = new SequentialIdentifier()
    Mangler.mangleNames(ast, ManglerOptions(nthIdentifier = seqId))
    OutputStream.printToString(ast)
  }

  /** Regex matching `function <name>(<params>)` and capturing the function
    * name and parameter list.
    */
  private val funcExprPattern =
    """function\s+([a-zA-Z_$][a-zA-Z0-9_$]*)\s*\(([^)]*)\)""".r

  /** Assert that no function expression in the output has a parameter with the
    * same name as the function expression itself.
    */
  private def assertNoFunctionNameParamCollision(output: String, clue: String): Unit = {
    funcExprPattern.findAllMatchIn(output).foreach { m =>
      val fname  = m.group(1)
      val params = m.group(2).split(",").map(_.trim).filter(_.nonEmpty)
      params.foreach { p =>
        assertNotEquals(
          p, fname,
          s"Safari tricky-def collision: function expression name '$fname' " +
            s"equals parameter '$p' in output: $output ($clue)"
        )
      }
    }
  }

  // ---------------------------------------------------------------------------
  // Test 1: ie8 tricky-def — THE differential test
  // ---------------------------------------------------------------------------
  //
  // With ie8=true and a SequentialIdentifier (a, b, c, ...):
  //
  //   Input: var f = function fname(arg) { return arg; };
  //
  //   Scope structure:
  //     Toplevel: f (global, unmangleable since toplevel=false)
  //     Function fname: fname (AstSymbolLambda), arg (AstSymbolFunarg)
  //
  //   Mangling (ie8=true):
  //     fname (AstSymbolLambda): ie8 redirects to parent scope (toplevel).
  //       toplevel.cname: -1 → 0 → "a". Enclosed check: f is unmangleable
  //       with name "f", "a" != "f" → OK. fname.mangledName = "a".
  //     arg (AstSymbolFunarg): scope = function.
  //       function.cname: -1 → 0 → "a". Enclosed check: only "arguments"
  //       (unmangleable name "arguments") and arg itself (mangledName null,
  //       mangleable → not checked). fname is NOT in enclosed (not
  //       referenced in the body). So "a" passes the enclosed check.
  //       WITHOUT the fix: arg.mangledName = "a" → function a(a){...} ← BUG
  //       WITH the fix: nextMangledFunction checks tricky_def.
  //         trickyName = fname.mangledName = "a". "a" == "a" → skip.
  //         function.cname: 0 → 1 → "b". arg.mangledName = "b".
  //         → function a(b){...} ← CORRECT
  //
  //   Without the fix the output contains "function a(a)" (Safari syntax error).
  //   With the fix the output contains "function a(b)" (parameter skips the
  //   colliding name).

  test("ISS-1242 ie8 tricky-def: named function expression param must not shadow its name") {
    val output = mangleIe8("var f = function fname(arg) { return arg; };")

    // Structural assertion: no function X(X) collision
    assertNoFunctionNameParamCollision(output, "ie8 tricky-def")

    // Specific assertion: the function expression must be "function a(b)"
    // (fname mangled to "a" at parent scope, arg skips "a" and gets "b")
    assert(
      output.contains("function a(b)"),
      s"expected function expression name 'a' with parameter 'b', got: $output"
    )
  }

  // ---------------------------------------------------------------------------
  // Test 2: ie8 tricky-def with multiple params
  // ---------------------------------------------------------------------------

  test("ISS-1242 ie8 tricky-def: multiple params skip the function expression name") {
    val output = mangleIe8("var f = function fname(x, y) { return x + y; };")

    assertNoFunctionNameParamCollision(output, "ie8 tricky-def multiple params")

    // fname → "a" (parent scope), x → skips "a" → "b", y → "c"
    assert(
      output.contains("function a(b,c)"),
      s"expected function a(b,c), got: $output"
    )
  }

  // ---------------------------------------------------------------------------
  // Test 3: control — function declaration (AstDefun) is unaffected
  // ---------------------------------------------------------------------------
  //
  // AstDefun is NOT a subtype of AstFunction (they are sibling classes both
  // extending AstNode with AstLambda), so the new dispatch case does not match
  // function declarations. This is correct per terser: AST_Function's
  // next_mangled override applies only to function expressions.

  test("ISS-1242 control: function declaration mangling is unaffected") {
    val output = mangleNormal("function f(longName) { return longName; }")

    // f is a function DECLARATION (AstDefun), its scope is the toplevel.
    // longName is mangled at the function scope. With sequential ids:
    // longName → function.cname 0 → "a". No tricky_def needed.
    assert(
      output.contains("function f(a)"),
      s"expected function f(a), got: $output"
    )
  }

  // ---------------------------------------------------------------------------
  // Test 4: control — anonymous function expression is unaffected
  // ---------------------------------------------------------------------------

  test("ISS-1242 control: anonymous function expression mangling is unaffected") {
    val output = mangleNormal("var f = function(longArg) { return longArg; };")

    // No function expression name → tricky_def cannot trigger
    // longArg → function.cname 0 → "a"
    assert(
      !output.contains("longArg"),
      s"parameter must be mangled: $output"
    )
  }

  // ---------------------------------------------------------------------------
  // Test 5: control — non-ie8 named function expression
  // ---------------------------------------------------------------------------
  //
  // Without ie8, the function expression name and parameter share the same
  // scope's cname counter, so they naturally get different names. The
  // tricky_def check is still wired (via the AstFunction dispatch) but does
  // not need to skip because the cname counter already prevents collision.

  test("ISS-1242 control: non-ie8 named function expression mangling works") {
    val output = mangleNormal("var f = function fname(arg) { return arg; };")

    assertNoFunctionNameParamCollision(output, "non-ie8 named function expression")

    // fname and arg share the function scope's cname counter:
    // fname → cname 0 → "a", arg → cname 1 → "b". No collision.
    assert(
      output.contains("function a(b)"),
      s"expected function a(b), got: $output"
    )
  }
}
