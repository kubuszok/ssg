/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from terser/test/mocha/annotations.js
 * Original: 3 it() calls
 */
package ssg
package js

import ssg.js.ast.{ Annotations, AstCall, AstSimpleStatement }
import ssg.js.parse.Parser

final class AnnotationsSuite extends munit.FunSuite {

  private def parse(code: String) = new Parser().parse(code)

  private def hasAnnotation(node: ssg.js.ast.AstNode, flag: Int): Boolean =
    (node.flags & flag) != 0

  // 1. "#__PURE__ — Should add a 'pure' annotation to the AST node"
  test("__PURE__ annotation on call expression") {
    val ast = parse("/*@__PURE__*/foo.bar.baz();impure()")
    val call1 = ast.body(0).asInstanceOf[AstSimpleStatement].body.asInstanceOf[AstCall]
    val call2 = ast.body(1).asInstanceOf[AstSimpleStatement].body.asInstanceOf[AstCall]
    assert(hasAnnotation(call1, Annotations.Pure), "Expected PURE annotation on first call")
    assert(!hasAnnotation(call2, Annotations.Pure), "Expected no PURE annotation on second call")
  }

  // 2. "#__INLINE__ — Adds an annotation"
  test("__INLINE__ annotation on call expression") {
    val ast  = parse("/*@__INLINE__*/foo();")
    val call = ast.body(0).asInstanceOf[AstSimpleStatement].body.asInstanceOf[AstCall]
    assert(hasAnnotation(call, Annotations.Inline), "Expected INLINE annotation")
  }

  // 3. "#__NOINLINE__ — Adds an annotation"
  test("__NOINLINE__ annotation on call expression") {
    val ast  = parse("/*@__NOINLINE__*/foo();")
    val call = ast.body(0).asInstanceOf[AstSimpleStatement].body.asInstanceOf[AstCall]
    assert(hasAnnotation(call, Annotations.NoInline), "Expected NOINLINE annotation")
  }
}
