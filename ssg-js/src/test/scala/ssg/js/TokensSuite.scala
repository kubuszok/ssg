/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from terser/test/mocha/tokens.js
 * Original: 2 it() calls
 */
package ssg
package js

import ssg.js.ast.*
import ssg.js.parse.Parser

final class TokensSuite extends munit.FunSuite {

  private def parse(code: String) = new Parser().parse(code)

  // 1. "Should give correct line in the face of DOS line endings"
  test("should give correct line with DOS line endings") {
    def endAtLine2(code: String): Unit = {
      val ast   = parse(code)
      var found = false
      ast.walk(
        new TreeWalker((node, _) => {
          node match {
            case sym: AstSymbol if sym.name == "end" =>
              assertEquals(sym.start.line, 2, s"Expected line 2 for 'end' in: $code")
              found = true
            case _ =>
          }
          false
        })
      )
      assert(found, s"Symbol 'end' not found in: $code")
    }

    endAtLine2("`A\nB`;end")
    endAtLine2("`A\r\nB`;end")
    endAtLine2("`A\\\nB`;end")
    endAtLine2("`A\\\r\nB`;end")
    endAtLine2("'A\\\nB';end")
    endAtLine2("'A\\\r\nB';end")
  }

  // 2. "Should give correct positions for accessors"
  // Note: the object-literal getter / class-method parse gap was fixed by ISS-1174
  // (createAccessor now parses the parameter list); this test passes, so the expected-failure pin
  // was retired.
  test("should give correct positions for accessors") {
    // location             0         1         2         3         4
    //                      01234567890123456789012345678901234567890123456789
    val ast   = parse("var obj = { get latest() { return undefined; } }")
    var found = false
    ast.walk(
      new TreeWalker((node, _) => {
        node match {
          case prop: AstObjectGetter =>
            found = true
            assertEquals(prop.start.pos, 12)

            prop.key match {
              case sym: AstSymbolMethod =>
                assertEquals(sym.start.pos, 12)
              case other =>
                fail(s"Expected AstSymbolMethod key, got: ${other.getClass.getSimpleName}")
            }
            assert(prop.value.isInstanceOf[AstAccessor], "Expected AstAccessor value")
            assertEquals(prop.value.asInstanceOf[AstAccessor].start.pos, 22)
          case _ =>
        }
        false
      })
    )
    assert(found, "AST_ObjectProperty not found")
  }
}
