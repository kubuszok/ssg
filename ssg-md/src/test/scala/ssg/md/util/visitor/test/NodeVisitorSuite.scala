/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package md
package util
package visitor
package test

import ssg.md.util.ast.*

final class NodeVisitorSuite extends munit.FunSuite {

  test("test_basic") {
    // Original test is empty — it verifies that the visitor infrastructure compiles.
    // The helper classes below verify the same thing in Scala.

    val tester = new VisitTester()
    tester.test()

    val tester2 = new VisitTester2()
    tester2.test()
  }
}

trait BlockVisitor {
  def visit(node: Block):       Unit
  def visit(node: ContentNode): Unit
}

object BlockVisitorExt {
  def VISIT_HANDLERS[V <: BlockVisitor](visitor: V): Array[VisitHandler[? <: Node]] =
    Array(
      new VisitHandler[Block](classOf[Block], (node: Block) => visitor.visit(node)),
      new VisitHandler[ContentNode](classOf[ContentNode], (node: ContentNode) => visitor.visit(node))
    )
}

class VisitTester extends BlockVisitor {
  def test(): Unit = {
    val self      = this
    val myVisitor = new NodeVisitor(
      new VisitHandler[Document](classOf[Document], (node: Document) => this.visit(node)),
      new VisitHandler[BlankLine](classOf[BlankLine], (node: BlankLine) => this.visit(node))
    )
    myVisitor.addHandlers(BlockVisitorExt.VISIT_HANDLERS(self))
  }

  // generic node handler when specific one is not defined
  def visit(node: Node): Unit = {}

  def visit(node: Block): Unit = {}

  def visit(node: BlankLine): Unit = {}

  def visit(node: ContentNode): Unit = {}

  def visit(node: Document): Unit = {}
}

class VisitTester2 extends BlockVisitor {
  def test(): Unit = {
    val self     = this
    val handlers = BlockVisitorExt.VISIT_HANDLERS(self) ++ BlockVisitorExt.VISIT_HANDLERS(self)
    val _        = new NodeVisitor(handlers*)
  }

  // generic node handler when specific one is not defined
  def visit(node: Node): Unit = {}

  def visit(node: Block): Unit = {}

  def visit(node: BlankLine): Unit = {}

  def visit(node: ContentNode): Unit = {}

  def visit(node: Document): Unit = {}
}
