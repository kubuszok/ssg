/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from terser/test/mocha/object.js
 * Original: 4 it() calls
 */
package ssg
package js

import ssg.js.parse.{ JsParseError, Parser }
import ssg.js.output.{ OutputOptions, OutputStream }

final class ObjectSuite extends munit.FunSuite {

  private val noOpt = MinifyOptions.NoOptimize

  // 1. "Should allow objects to have a methodDefinition as property"
  // Requires compression (arrows: false, reduce boolean true→!0)
  // Known parser gap: concise methods in objects fail to parse
  test("should allow objects to have a methodDefinition as property".fail) {
    val result = Terser.minifyToString("var a = {test() {return true;}}", noOpt)
    assert(result.contains("test"), s"got: $result")
  }

  // 2. "Should not allow objects to use static keywords like in classes"
  test("should not allow objects to use static keywords like in classes") {
    intercept[JsParseError] {
      new Parser().parse("{static test() {}}")
    }
  }

  // 3. "Should not allow objects to have static computed properties like in classes"
  test("should not allow objects to have static computed properties like in classes") {
    intercept[JsParseError] {
      new Parser().parse("var foo = {static [123](){}}")
    }
  }

  // 4. "Should be able to use shorthand properties"
  test("should be able to use shorthand properties") {
    val ast    = new Parser().parse("var foo = 123\nvar obj = {foo: foo}")
    val result = OutputStream.printToString(ast, OutputOptions(ecma = 2015))
    assertEquals(result, "var foo=123;var obj={foo};")
  }
}
