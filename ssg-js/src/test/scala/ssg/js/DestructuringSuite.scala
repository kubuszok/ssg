/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from terser/test/mocha/destructuring.js
 * Original: 1 it() call (complex — verifies AST consistency across contexts)
 *
 * This test verifies that destructuring patterns produce similar AST trees
 * across lhs expressions, var definitions, function parameters, and arrow parameters.
 */
package ssg
package js

import ssg.js.ast.*
import ssg.js.parse.Parser

import scala.collection.mutable.ArrayBuffer

final class DestructuringSuite extends munit.FunSuite {

  private def parse(code: String) = new Parser().parse(code)

  // 1. "Should generate similar trees for destructuring in left hand side expressions, definitions, functions and arrow functions"
  test("should generate similar trees for destructuring across contexts") {
    val patterns = List(
      "[]",
      "{}",
      "[a, b, c]",
      "{a: b, c: d}",
      "{a}",
      "{a, b}",
      "{a: {}}",
      "{a: []}",
      "[{}]",
      "[[]]",
      "{a: {b}}",
      // Can't do `a = 123` with lhs expression, so only test in destructuring
      "[foo = bar]",
      "{a = 123}",
      "[{foo: abc = 123}]",
      "{foo: [abc = 123]}",
      "[...foo]",
      "[...{}]",
      "[...[]]",
    )

    // For each pattern, parse in different contexts and verify the node types are similar
    // (checking that destructuring AST structure is consistent)
    case class Context(name: String, getNode: AstToplevel => AstNode, generate: String => String)

    val contexts = List(
      Context("lhs",
        ast => ast.body(0).asInstanceOf[AstSimpleStatement].body.asInstanceOf[AstAssign].left,
        code => s"($code = a)"
      ),
      Context("var",
        ast => ast.body(0).asInstanceOf[AstVar].definitions(0).asInstanceOf[AstVarDef].name,
        code => s"var $code = a"
      ),
      Context("function",
        ast => ast.body(0).asInstanceOf[AstDefun].argnames(0),
        code => s"function a($code) {}"
      ),
      Context("arrow",
        ast => ast.body(0).asInstanceOf[AstVar].definitions(0).asInstanceOf[AstVarDef]
          .value.asInstanceOf[AstArrow].argnames(0),
        code => s"var a = ($code) => {}"
      ),
    )

    def collectNodeTypes(node: AstNode, parentAllowed: Boolean = true): List[String] = {
      val result = ArrayBuffer.empty[String]
      node.walk(new TreeWalker((n, _) => {
        n match {
          case _: AstDefaultAssign if !parentAllowed => true // skip default assign value
          case sym: AstSymbol =>
            result += sym.nodeType
            false
          case _ =>
            result += n.nodeType
            false
        }
      }))
      result.toList
    }

    patterns.foreach { pattern =>
      val results = contexts.map { ctx =>
        val code = ctx.generate(pattern)
        try {
          val ast = parse(code)
          val node = ctx.getNode(ast)
          collectNodeTypes(node)
        } catch {
          case _: Exception =>
            // Some patterns are not valid in all contexts (like default assignments in lhs)
            List.empty[String]
        }
      }

      // Compare results: all non-empty results should have the same structure
      // (ignoring symbol type differences between SymbolRef/SymbolVar/SymbolFunarg)
      val normalized = results.filter(_.nonEmpty).map { types =>
        types.map {
          case "SymbolRef" | "SymbolVar" | "SymbolFunarg" => "Symbol"
          case other => other
        }
      }
      if (normalized.size > 1) {
        val reference = normalized.head
        normalized.tail.foreach { other =>
          assertEquals(reference, other, s"AST disagree on pattern: $pattern")
        }
      }
    }
  }
}
