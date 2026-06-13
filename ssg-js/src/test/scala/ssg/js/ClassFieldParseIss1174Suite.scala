/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Red test for ISS-1174: a class field declaration FOLLOWED BY another member fails
 * to PARSE. The three shapes named by the issue all throw
 * `SyntaxError: Unexpected token punc ( expected punc {` at the method's paren —
 * `Parser.objectOrClassProperty` reaches `createAccessor` for the trailing method and
 * `block` then `expect`s a `{` but sees the `(` that begins the parameter list. terser
 * 5.46.1 parses and prints all three. This blocks any class-field-then-member code.
 * (Discovered by the ISS-1170 audit; pre-existing.)
 *
 * NOTE on scope (recorded by the reproducer, 2026-06-13): in THIS codebase the parser
 * cannot parse a class concise METHOD at all — `class C{m(){}}` and `class C{x;m(){}}`
 * (public field then method) throw the identical SyntaxError at the method paren, and
 * the existing `MinifySuite` "minify class with non-keyword methods" and `DirectivesSuite`
 * "should detect implicit usages of strict mode" tests are already marked `.fail` for
 * this same "class method parsing gap". So the issue's framing ("the port already
 * parses `class C{m(){}}`") does not hold here — a method-bearing class is NOT a valid
 * passing control. The defect the red shapes pin is real and reproduces with the exact
 * stated symptom; the controls below therefore use FIELD-ONLY classes (no trailing
 * member) to prove that class fields themselves parse + print correctly today, so the
 * suite distinguishes "fields work" from "a member after a field breaks the parse".
 *
 * The private-name (`#x`) print path has a SEPARATE defect (the port prints `class C{#x}`
 * as `class C{##x}`, doubling the `#`); that is NOT ISS-1174 and is deliberately not
 * pinned here, so the field-only controls use public / static-public fields whose
 * print output matches terser byte-for-byte.
 *
 * Oracle (C11): the vendored original terser at original-src/terser, version 5.46.1
 * (package.json:7), executed with node v24.12.0 on 2026-06-13:
 *
 *   cd original-src/terser && node --input-type=module -e "
 *   import { minify } from './main.js';
 *   const r = await minify(CODE, {compress: false, mangle: false});
 *   console.log(JSON.stringify(r.code));"
 *
 * Expected outputs recorded verbatim from that run (2026-06-13):
 *   shape1)   class C{#x;m(){}}         → "class C{#x;m(){}}"
 *   shape2)   class C{static y;m(){}}   → "class C{static y;m(){}}"
 *   shape3)   class C{static #x=1;m(){}}→ "class C{static#x=1;m(){}}"   (note: no space before #x)
 *   control1) class C{x;}               → "class C{x}"          (single public field; trailing `;` dropped)
 *   control2) class C{static x;}        → "class C{static x}"   (static public field)
 *   control3) class C{x;y;}             → "class C{x;y}"        (field-then-field: field ordering is fine)
 *
 * Port outputs observed on the reproducer's first red run (2026-06-13, this branch):
 *   shape1)   THROW SyntaxError: Unexpected token punc «(», expected punc «{» (:1:11)
 *   shape2)   THROW SyntaxError: Unexpected token punc «(», expected punc «{» (:1:18)
 *   shape3)   THROW SyntaxError: Unexpected token punc «(», expected punc «{» (:1:21)
 *   control1) class C{x}     (matches terser — PASS)
 *   control2) class C{static x}  (matches terser — PASS)
 *   control3) class C{x;y}   (matches terser — PASS)
 *
 * The three red shapes are exactly the shapes named in ISS-1174 and each fails with the
 * issue's stated symptom. The three controls match terser byte-for-byte, proving the
 * break is specifically "a member following a class field", not field declarations
 * themselves.
 */
package ssg
package js

final class ClassFieldParseIss1174Suite extends munit.FunSuite {

  override val munitTimeout = scala.concurrent.duration.Duration(30, "s")

  /** Equivalent of terser's `{compress: false, mangle: false}` (pure parse + print). */
  private val noOpt = MinifyOptions(compress = false, mangle = false)

  // -- Red: a class field followed by another member must parse + print (terser 5.46.1) --

  test("red ISS-1174 shape1: private field followed by method parses and prints") {
    val out = Terser.minifyToString("class C{#x;m(){}}", noOpt)
    assertEquals(out, "class C{#x;m(){}}", "output must match terser 5.46.1")
  }

  test("red ISS-1174 shape2: static field followed by method parses and prints") {
    val out = Terser.minifyToString("class C{static y;m(){}}", noOpt)
    assertEquals(out, "class C{static y;m(){}}", "output must match terser 5.46.1")
  }

  test("red ISS-1174 shape3: static private field with initializer followed by method parses and prints") {
    val out = Terser.minifyToString("class C{static #x=1;m(){}}", noOpt)
    assertEquals(out, "class C{static#x=1;m(){}}", "output must match terser 5.46.1")
  }

  // -- Controls: field-only classes must PASS today (isolate the defect to a member after a field) --

  test("control ISS-1174 control1: a single public field parses and prints") {
    val out = Terser.minifyToString("class C{x;}", noOpt)
    assertEquals(out, "class C{x}", "output must match terser 5.46.1")
  }

  test("control ISS-1174 control2: a static public field parses and prints") {
    val out = Terser.minifyToString("class C{static x;}", noOpt)
    assertEquals(out, "class C{static x}", "output must match terser 5.46.1")
  }

  test("control ISS-1174 control3: field-then-field (no method) parses and prints") {
    val out = Terser.minifyToString("class C{x;y;}", noOpt)
    assertEquals(out, "class C{x;y}", "output must match terser 5.46.1")
  }
}
