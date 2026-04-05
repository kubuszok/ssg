/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package js

import ssg.js.ast.*
import scala.collection.mutable.ArrayBuffer

final class AstSuite extends munit.FunSuite {

  test("AstToken creation") {
    val tok = AstToken("name", "foo", 1, 0, 0)
    assertEquals(tok.tokenType, "name")
    assertEquals(tok.value, "foo")
    assertEquals(tok.line, 1)
    assert(!tok.nlb)
  }

  test("AstToken flag manipulation") {
    val tok = AstToken("string", "hello", 1, 0, 0)
    tok.nlb = true
    assert(tok.nlb)
    tok.quote = "'"
    assertEquals(tok.quote, "'")
    tok.quote = "\""
    assertEquals(tok.quote, "\"")
    tok.templateEnd = true
    assert(tok.templateEnd)
  }

  test("simple AST construction — var x = 1") {
    // var x = 1;
    val sym = new AstSymbolVar
    sym.name = "x"

    val num = new AstNumber
    num.value = 1.0

    val varDef = new AstVarDef
    varDef.name = sym
    varDef.value = num

    val varStmt = new AstVar
    varStmt.definitions.addOne(varDef)

    val toplevel = new AstToplevel
    toplevel.body.addOne(varStmt)

    assertEquals(toplevel.nodeType, "Toplevel")
    assertEquals(varStmt.nodeType, "Var")
    assertEquals(sym.name, "x")
    assertEquals(num.value, 1.0)
  }

  test("AST construction — function foo(a) { return a; }") {
    val paramSym = new AstSymbolFunarg
    paramSym.name = "a"

    val ref = new AstSymbolRef
    ref.name = "a"

    val ret = new AstReturn
    ret.value = ref

    val nameSym = new AstSymbolDefun
    nameSym.name = "foo"

    val fn = new AstDefun
    fn.name = nameSym
    fn.argnames.addOne(paramSym)
    fn.body.addOne(ret)

    assertEquals(fn.nodeType, "Defun")
    assertEquals(fn.name.nn.asInstanceOf[AstSymbolDefun].name, "foo")
    assertEquals(fn.argnames.size, 1)
    assertEquals(fn.body.size, 1)
  }

  test("AST construction — binary expression: a + b") {
    val left = new AstSymbolRef
    left.name = "a"
    val right = new AstSymbolRef
    right.name = "b"

    val binary = new AstBinary
    binary.operator = "+"
    binary.left = left
    binary.right = right

    assertEquals(binary.nodeType, "Binary")
    assertEquals(binary.operator, "+")
  }

  test("TreeWalker visits all nodes") {
    val num = new AstNumber
    num.value = 42.0

    val simpleStmt = new AstSimpleStatement
    simpleStmt.body = num

    val toplevel = new AstToplevel
    toplevel.body.addOne(simpleStmt)

    val visited = ArrayBuffer[String]()
    val walker  = new TreeWalker((node, _) => {
      visited.addOne(node.nodeType)
      ()
    })
    toplevel.walk(walker)

    assert(visited.contains("Toplevel"), s"Expected Toplevel in $visited")
    assert(visited.contains("SimpleStatement"), s"Expected SimpleStatement in $visited")
    assert(visited.contains("Number"), s"Expected Number in $visited")
    assertEquals(visited.size, 3)
  }

  test("TreeWalker parent tracking") {
    val num = new AstNumber
    num.value = 1.0

    val simpleStmt = new AstSimpleStatement
    simpleStmt.body = num

    val toplevel = new AstToplevel
    toplevel.body.addOne(simpleStmt)

    var parentOfNum: AstNode | Null    = null
    var w:           TreeWalker | Null = null
    w = new TreeWalker((node, _) =>
      if (node.isInstanceOf[AstNumber]) {
        parentOfNum = w.nn.parent()
      }
    )
    toplevel.walk(w.nn)

    assert(parentOfNum != null, "Expected parent of number")
    assert(parentOfNum.nn.isInstanceOf[AstSimpleStatement], s"Expected SimpleStatement parent, got ${parentOfNum.nn.nodeType}")
  }

  test("walk free function visits all nodes depth-first") {
    val left = new AstNumber
    left.value = 1.0
    val right = new AstNumber
    right.value = 2.0
    val binary = new AstBinary
    binary.operator = "+"
    binary.left = left
    binary.right = right

    val visited = ArrayBuffer[String]()
    ast.walk(binary,
             (node, _) => {
               visited.addOne(node.nodeType)
               ()
             }
    )

    assertEquals(visited.toList, List("Binary", "Number", "Number"))
  }

  test("childrenBackwards produces correct order") {
    val left = new AstSymbolRef
    left.name = "a"
    val right = new AstSymbolRef
    right.name = "b"
    val binary = new AstBinary
    binary.operator = "+"
    binary.left = left
    binary.right = right

    val children = ArrayBuffer[AstNode]()
    binary.childrenBackwards(children.addOne)

    assertEquals(children.size, 2)
    // Backwards: right first, then left
    assertEquals(children(0).asInstanceOf[AstSymbolRef].name, "b")
    assertEquals(children(1).asInstanceOf[AstSymbolRef].name, "a")
  }

  test("constant nodes") {
    val t = new AstTrue
    assertEquals(t.nodeType, "True")

    val f = new AstFalse
    assertEquals(f.nodeType, "False")

    val n = new AstNull
    assertEquals(n.nodeType, "Null")

    val u = new AstUndefined
    assertEquals(u.nodeType, "Undefined")

    val str = new AstString
    str.value = "hello"
    assertEquals(str.nodeType, "String")
    assertEquals(str.value, "hello")
  }

  test("all node types have unique nodeType strings") {
    val nodes: List[AstNode] = List(
      new AstDebugger,
      new AstSimpleStatement,
      new AstBlockStatement,
      new AstEmptyStatement,
      new AstDo,
      new AstWhile,
      new AstFor,
      new AstForIn,
      new AstForOf,
      new AstWith,
      new AstIf,
      new AstSwitch,
      new AstDefault,
      new AstCase,
      new AstTry,
      new AstTryBlock,
      new AstCatch,
      new AstFinally,
      new AstReturn,
      new AstThrow,
      new AstBreak,
      new AstContinue,
      new AstToplevel,
      new AstAccessor,
      new AstFunction,
      new AstArrow,
      new AstDefun,
      new AstDestructuring,
      new AstExpansion,
      new AstCall,
      new AstNew,
      new AstSequence,
      new AstDot,
      new AstDotHash,
      new AstSub,
      new AstChain,
      new AstUnaryPrefix,
      new AstUnaryPostfix,
      new AstBinary,
      new AstAssign,
      new AstDefaultAssign,
      new AstConditional,
      new AstArray,
      new AstObject,
      new AstAwait,
      new AstYield,
      new AstTemplateString,
      new AstTemplateSegment,
      new AstPrefixedTemplateString,
      new AstVar,
      new AstLet,
      new AstConst,
      new AstUsing,
      new AstVarDef,
      new AstUsingDef,
      new AstNameMapping,
      new AstImport,
      new AstImportMeta,
      new AstExport,
      new AstSymbolVar,
      new AstSymbolFunarg,
      new AstSymbolRef,
      new AstSymbolConst,
      new AstSymbolLet,
      new AstSymbolDefun,
      new AstSymbolLambda,
      new AstSymbolCatch,
      new AstSymbolDefClass,
      new AstSymbolClass,
      new AstLabel,
      new AstLabelRef,
      new AstThis,
      new AstSuper,
      new AstNewTarget,
      new AstString,
      new AstNumber,
      new AstBigInt,
      new AstRegExp,
      new AstNull,
      new AstNaN,
      new AstUndefined,
      new AstInfinity,
      new AstHole,
      new AstTrue,
      new AstFalse,
      new AstObjectKeyVal,
      new AstObjectGetter,
      new AstObjectSetter,
      new AstConciseMethod,
      new AstPrivateMethod,
      new AstClassProperty,
      new AstClassPrivateProperty,
      new AstClassStaticBlock,
      new AstPrivateIn,
      new AstDefClass,
      new AstClassExpression,
      new AstLabeledStatement,
      new AstDirective
    )

    val types = nodes.map(_.nodeType)
    val dups  = types.groupBy(identity).filter(_._2.size > 1).keys.toList
    assert(dups.isEmpty, s"Duplicate nodeType values: $dups")
    // Verify we have a reasonable count
    assert(types.size >= 85, s"Expected at least 85 node types, got ${types.size}")
  }
}
