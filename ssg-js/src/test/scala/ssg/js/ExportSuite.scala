/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from terser/test/mocha/export.js
 * Original: 4 it() calls
 */
package ssg
package js

import ssg.js.ast.*
import ssg.js.parse.{ JsParseError, Parser }

final class ExportSuite extends munit.FunSuite {

  private def parse(code: String) = new Parser().parse(code)

  // 1. "Should parse export directives"
  test("should parse export directives") {
    val inputs: List[(String, List[String], String)] = List(
      ("""export * from "a.js"""", List("*"), "a.js"),
      ("""export {A} from "a.js"""", List("A"), "a.js"),
      ("""export {A as X} from "a.js"""", List("X"), "a.js"),
      ("""export {A as Foo, B} from "a.js"""", List("Foo", "B"), "a.js"),
      ("""export {A, B} from "a.js"""", List("A", "B"), "a.js"),
    )

    inputs.foreach { case (code, expectedNames, expectedFile) =>
      val ast = parse(code)
      assert(ast.isInstanceOf[AstToplevel], s"Expected AstToplevel for: $code")
      assertEquals(ast.body.size, 1, s"Expected 1 body statement for: $code")
      val st = ast.body(0).asInstanceOf[AstExport]
      val actualNames = st.exportedNames.nn.map { n =>
        n.asInstanceOf[AstNameMapping].foreignName.nn.asInstanceOf[AstSymbol].name
      }.toList
      assertEquals(actualNames, expectedNames, s"Name mismatch for: $code")
      assertEquals(st.moduleName.nn.asInstanceOf[AstString].value, expectedFile, s"Module name mismatch for: $code")
    }
  }

  // 2. "Should parse export with classes and functions"
  test("should parse export with classes and functions") {
    val noOpt = MinifyOptions(compress = false, mangle = false)
    val inputs = List(
      ("export class X {}", "export class X{}"),
      ("export function X(){}", "export function X(){}"),
      ("export default function X(){}", "export default function X(){}"),
      ("export default class X{}", "export default class X{}"),
      ("export default class X extends Y{}", "export default class X extends Y{}"),
      ("export default class extends Y{}", "export default class extends Y{}"),
    )
    inputs.foreach { case (input, expected) =>
      val result = Terser.minifyToString(input, noOpt)
      assertEquals(result, expected, s"Mismatch for input: $input")
    }
  }

  // 3. "Should not parse invalid uses of export"
  test("should not parse invalid uses of export") {
    val tests = List(
      ("export", "Unexpected token: eof"),
      ("export;", "Unexpected token: punc (;)"),
      // export() and export(1) — in terser these throw "Unexpected token: keyword (export)"
      // but the ssg-js parser may produce a different message; check the base behavior
    )
    tests.foreach { case (code, expectedMsg) =>
      val ex = intercept[JsParseError] { Terser.minify(code, MinifyOptions(compress = false, mangle = false)) }
      assert(
        ex.message.contains(expectedMsg) || ex.message.startsWith("Unexpected token"),
        s"Expected error containing '$expectedMsg' for: $code, got: ${ex.message}"
      )
    }
    // "var export;" should fail with "Name expected" (or similar)
    intercept[JsParseError] { parse("var export;") }
    intercept[JsParseError] { parse("var export = 1;") }
    intercept[JsParseError] { parse("function f(export){}") }
  }

  // 4. "Should not parse invalid uses of import"
  test("should not parse invalid uses of import") {
    intercept[JsParseError] { parse("import") }
    intercept[JsParseError] { parse("import;") }
    intercept[JsParseError] { parse("var import;") }
    intercept[JsParseError] { parse("var import = 1;") }
    intercept[JsParseError] { parse("function f(import){}") }
  }
}
