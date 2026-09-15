/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Statement AST nodes: blocks, loops, conditionals, switch, try/catch, jumps.
 *
 * Original source: terser lib/ast.js
 * Original author: Mihai Bazon
 * Original license: BSD-2-Clause
 *
 * Migration notes:
 *   Renames: AST_* -> Ast*
 *   Convention: Mutable var fields, T | Null for nullable
 *   Idiom: ArrayBuffer for body arrays
 *
 * Covenant: full-port
 * Covenant-js-reference: lib/ast.js
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: 88493d7ca0d708389f5f78f541c4fb48e71d9fe2
 */
package ssg
package js
package ast

import scala.collection.mutable.ArrayBuffer

// ---------------------------------------------------------------------------
// Base statement
// ---------------------------------------------------------------------------

/** Base class of all statements. */
trait AstStatement extends AstNode

/** Represents a `debugger` statement. */
class AstDebugger extends AstNode with AstStatement {
  def nodeType: String = "Debugger"
}

/** Represents a directive, like "use strict". */
class AstDirective extends AstNode with AstStatement {
  var value: String = ""
  var quote: String = ""

  def nodeType: String = "Directive"
}

/** A statement consisting of an expression, i.e. `a = 1 + 2`. */
class AstSimpleStatement extends AstNode with AstStatement {
  var body: AstNode | Null = null

  def nodeType: String = "SimpleStatement"

  override protected def walkChildren(visitor: TreeWalker): Unit =
    if (body != null) body.nn.walk(visitor)

  override def childrenBackwards(push: AstNode => Unit): Unit =
    if (body != null) push(body.nn)

  override protected def transformDescend(tw: TreeTransformer): Unit =
    if (body != null) body = body.nn.transform(tw)
}

// ---------------------------------------------------------------------------
// Blocks
// ---------------------------------------------------------------------------

/** A body of statements (usually braced). */
trait AstBlock extends AstStatement {
  var body:       ArrayBuffer[AstNode] = ArrayBuffer.empty
  var blockScope: AstScope | Null      = null
}

/** A block statement. Concrete node for `{ ... }`. */
class AstBlockStatement extends AstNode with AstBlock {
  def nodeType: String = "BlockStatement"

  override protected def walkChildren(visitor: TreeWalker): Unit =
    walkBody(body, visitor)

  override def childrenBackwards(push: AstNode => Unit): Unit = {
    var i = body.size
    while ({ i -= 1; i >= 0 }) push(body(i))
  }

  override protected def transformDescend(tw: TreeTransformer): Unit =
    body = transformList(body, tw)
}

/** The empty statement (empty block or simply a semicolon). */
class AstEmptyStatement extends AstNode with AstStatement {
  def nodeType: String = "EmptyStatement"
}

// ---------------------------------------------------------------------------
// Statements with body
// ---------------------------------------------------------------------------

/** Base class for all statements that contain one nested body. */
trait AstStatementWithBody extends AstStatement {
  var body: AstNode | Null = null
}

/** Statement with a label. */
class AstLabeledStatement extends AstNode with AstStatementWithBody {
  var label: AstLabel | Null = null

  def nodeType: String = "LabeledStatement"

  override protected def walkChildren(visitor: TreeWalker): Unit = {
    if (label != null) label.nn.walk(visitor)
    if (body != null) body.nn.walk(visitor)
  }

  override def childrenBackwards(push: AstNode => Unit): Unit = {
    if (body != null) push(body.nn)
    if (label != null) push(label.nn)
  }

  override protected def transformDescend(tw: TreeTransformer): Unit = {
    if (label != null) label = label.nn.transform(tw).asInstanceOf[AstLabel]
    if (body != null) body = body.nn.transform(tw)
  }
}

// ---------------------------------------------------------------------------
// Iteration statements
// ---------------------------------------------------------------------------

/** Internal class. All loops inherit from it. */
trait AstIterationStatement extends AstStatementWithBody {
  var blockScope: AstScope | Null = null
}

/** Base class for do/while statements. */
trait AstDWLoop extends AstIterationStatement {
  var condition: AstNode | Null = null
}

/** A `do` statement. */
class AstDo extends AstNode with AstDWLoop {
  def nodeType: String = "Do"

  override protected def walkChildren(visitor: TreeWalker): Unit = {
    if (body != null) body.nn.walk(visitor)
    if (condition != null) condition.nn.walk(visitor)
  }

  override def childrenBackwards(push: AstNode => Unit): Unit = {
    if (condition != null) push(condition.nn)
    if (body != null) push(body.nn)
  }

  override protected def transformDescend(tw: TreeTransformer): Unit = {
    if (body != null) body = body.nn.transform(tw)
    if (condition != null) condition = condition.nn.transform(tw)
  }
}

/** A `while` statement. */
class AstWhile extends AstNode with AstDWLoop {
  def nodeType: String = "While"

  override protected def walkChildren(visitor: TreeWalker): Unit = {
    if (condition != null) condition.nn.walk(visitor)
    if (body != null) body.nn.walk(visitor)
  }

  override def childrenBackwards(push: AstNode => Unit): Unit = {
    if (body != null) push(body.nn)
    if (condition != null) push(condition.nn)
  }

  override protected def transformDescend(tw: TreeTransformer): Unit = {
    if (condition != null) condition = condition.nn.transform(tw)
    if (body != null) body = body.nn.transform(tw)
  }
}

/** A `for` statement. */
class AstFor extends AstNode with AstIterationStatement {
  var init:      AstNode | Null = null
  var condition: AstNode | Null = null
  var step:      AstNode | Null = null

  def nodeType: String = "For"

  override protected def walkChildren(visitor: TreeWalker): Unit = {
    if (init != null) init.nn.walk(visitor)
    if (condition != null) condition.nn.walk(visitor)
    if (step != null) step.nn.walk(visitor)
    if (body != null) body.nn.walk(visitor)
  }

  override def childrenBackwards(push: AstNode => Unit): Unit = {
    if (body != null) push(body.nn)
    if (step != null) push(step.nn)
    if (condition != null) push(condition.nn)
    if (init != null) push(init.nn)
  }

  override protected def transformDescend(tw: TreeTransformer): Unit = {
    if (init != null) init = init.nn.transform(tw)
    if (condition != null) condition = condition.nn.transform(tw)
    if (step != null) step = step.nn.transform(tw)
    if (body != null) body = body.nn.transform(tw)
  }
}

/** A `for ... in` statement. */
class AstForIn extends AstNode with AstIterationStatement {
  var init: AstNode | Null = null
  var obj:  AstNode | Null = null

  def nodeType: String = "ForIn"

  override protected def walkChildren(visitor: TreeWalker): Unit = {
    if (init != null) init.nn.walk(visitor)
    if (obj != null) obj.nn.walk(visitor)
    if (body != null) body.nn.walk(visitor)
  }

  override def childrenBackwards(push: AstNode => Unit): Unit = {
    if (body != null) push(body.nn)
    if (obj != null) push(obj.nn)
    if (init != null) push(init.nn)
  }

  override protected def transformDescend(tw: TreeTransformer): Unit = {
    init = init.nn.transform(tw)
    obj = obj.nn.transform(tw)
    if (body != null) body = body.nn.transform(tw)
  }
}

/** A `for ... of` statement. */
class AstForOf extends AstForIn {
  var isAwait: Boolean = false

  override def nodeType: String = "ForOf"
}

/** A `with` statement. */
class AstWith extends AstNode with AstStatementWithBody {
  var expression: AstNode | Null = null

  def nodeType: String = "With"

  override protected def walkChildren(visitor: TreeWalker): Unit = {
    if (expression != null) expression.nn.walk(visitor)
    if (body != null) body.nn.walk(visitor)
  }

  override def childrenBackwards(push: AstNode => Unit): Unit = {
    if (body != null) push(body.nn)
    if (expression != null) push(expression.nn)
  }

  override protected def transformDescend(tw: TreeTransformer): Unit = {
    if (expression != null) expression = expression.nn.transform(tw)
    if (body != null) body = body.nn.transform(tw)
  }
}

// ---------------------------------------------------------------------------
// If
// ---------------------------------------------------------------------------

/** A `if` statement. */
class AstIf extends AstNode with AstStatementWithBody {
  var condition:   AstNode | Null = null
  var alternative: AstNode | Null = null

  def nodeType: String = "If"

  override protected def walkChildren(visitor: TreeWalker): Unit = {
    if (condition != null) condition.nn.walk(visitor)
    if (body != null) body.nn.walk(visitor)
    if (alternative != null) alternative.nn.walk(visitor)
  }

  override def childrenBackwards(push: AstNode => Unit): Unit = {
    if (alternative != null) push(alternative.nn)
    if (body != null) push(body.nn)
    if (condition != null) push(condition.nn)
  }

  override protected def transformDescend(tw: TreeTransformer): Unit = {
    if (condition != null) condition = condition.nn.transform(tw)
    if (body != null) body = body.nn.transform(tw)
    if (alternative != null) alternative = alternative.nn.transform(tw)
  }
}

// ---------------------------------------------------------------------------
// Switch
// ---------------------------------------------------------------------------

/** A `switch` statement. */
class AstSwitch extends AstNode with AstBlock {
  var expression: AstNode | Null = null

  def nodeType: String = "Switch"

  override protected def walkChildren(visitor: TreeWalker): Unit = {
    if (expression != null) expression.nn.walk(visitor)
    walkBody(body, visitor)
  }

  override def childrenBackwards(push: AstNode => Unit): Unit = {
    var i = body.size
    while ({ i -= 1; i >= 0 }) push(body(i))
    if (expression != null) push(expression.nn)
  }

  override protected def transformDescend(tw: TreeTransformer): Unit = {
    if (expression != null) expression = expression.nn.transform(tw)
    body = transformList(body, tw)
  }
}

/** Base class for `switch` branches. */
trait AstSwitchBranch extends AstBlock

/** A `default` switch branch. */
class AstDefault extends AstNode with AstSwitchBranch {
  def nodeType: String = "Default"

  override protected def walkChildren(visitor: TreeWalker): Unit =
    walkBody(body, visitor)

  override def childrenBackwards(push: AstNode => Unit): Unit = {
    var i = body.size
    while ({ i -= 1; i >= 0 }) push(body(i))
  }

  override protected def transformDescend(tw: TreeTransformer): Unit =
    body = transformList(body, tw)
}

/** A `case` switch branch. */
class AstCase extends AstNode with AstSwitchBranch {
  var expression: AstNode | Null = null

  def nodeType: String = "Case"

  override protected def walkChildren(visitor: TreeWalker): Unit = {
    if (expression != null) expression.nn.walk(visitor)
    walkBody(body, visitor)
  }

  override def childrenBackwards(push: AstNode => Unit): Unit = {
    var i = body.size
    while ({ i -= 1; i >= 0 }) push(body(i))
    if (expression != null) push(expression.nn)
  }

  override protected def transformDescend(tw: TreeTransformer): Unit = {
    if (expression != null) expression = expression.nn.transform(tw)
    body = transformList(body, tw)
  }
}

// ---------------------------------------------------------------------------
// Exceptions
// ---------------------------------------------------------------------------

/** A `try` statement. */
class AstTry extends AstNode with AstStatement {
  var body:     AstTryBlock | Null = null
  var bcatch:   AstCatch | Null    = null
  var bfinally: AstFinally | Null  = null

  def nodeType: String = "Try"

  override protected def walkChildren(visitor: TreeWalker): Unit = {
    if (body != null) body.nn.walk(visitor)
    if (bcatch != null) bcatch.nn.walk(visitor)
    if (bfinally != null) bfinally.nn.walk(visitor)
  }

  override def childrenBackwards(push: AstNode => Unit): Unit = {
    if (bfinally != null) push(bfinally.nn)
    if (bcatch != null) push(bcatch.nn)
    if (body != null) push(body.nn)
  }

  override protected def transformDescend(tw: TreeTransformer): Unit = {
    if (body != null) body = body.nn.transform(tw).asInstanceOf[AstTryBlock]
    if (bcatch != null) bcatch = bcatch.nn.transform(tw).asInstanceOf[AstCatch]
    if (bfinally != null) bfinally = bfinally.nn.transform(tw).asInstanceOf[AstFinally]
  }
}

/** The `try` block of a try statement. */
class AstTryBlock extends AstNode with AstBlock {
  def nodeType: String = "TryBlock"

  override protected def walkChildren(visitor: TreeWalker): Unit =
    walkBody(body, visitor)

  override def childrenBackwards(push: AstNode => Unit): Unit = {
    var i = body.size
    while ({ i -= 1; i >= 0 }) push(body(i))
  }

  override protected def transformDescend(tw: TreeTransformer): Unit =
    body = transformList(body, tw)
}

/** A `catch` node; only makes sense as part of a `try` statement. */
class AstCatch extends AstNode with AstBlock {
  var argname: AstNode | Null = null

  def nodeType: String = "Catch"

  override protected def walkChildren(visitor: TreeWalker): Unit = {
    if (argname != null) argname.nn.walk(visitor)
    walkBody(body, visitor)
  }

  override def childrenBackwards(push: AstNode => Unit): Unit = {
    var i = body.size
    while ({ i -= 1; i >= 0 }) push(body(i))
    if (argname != null) push(argname.nn)
  }

  override protected def transformDescend(tw: TreeTransformer): Unit = {
    if (argname != null) argname = argname.nn.transform(tw)
    body = transformList(body, tw)
  }
}

/** A `finally` node; only makes sense as part of a `try` statement. */
class AstFinally extends AstNode with AstBlock {
  def nodeType: String = "Finally"

  override protected def walkChildren(visitor: TreeWalker): Unit =
    walkBody(body, visitor)

  override def childrenBackwards(push: AstNode => Unit): Unit = {
    var i = body.size
    while ({ i -= 1; i >= 0 }) push(body(i))
  }

  override protected def transformDescend(tw: TreeTransformer): Unit =
    body = transformList(body, tw)
}

// ---------------------------------------------------------------------------
// Jumps
// ---------------------------------------------------------------------------

/** Base class for "jumps" (return, throw, break, continue). */
trait AstJump extends AstStatement

/** Base class for "exits" (return and throw). */
trait AstExit extends AstJump {
  var value: AstNode | Null = null
}

/** A `return` statement. */
class AstReturn extends AstNode with AstExit {
  def nodeType: String = "Return"

  override protected def walkChildren(visitor: TreeWalker): Unit =
    if (value != null) value.nn.walk(visitor)

  override def childrenBackwards(push: AstNode => Unit): Unit =
    if (value != null) push(value.nn)

  override protected def transformDescend(tw: TreeTransformer): Unit =
    if (value != null) value = value.nn.transform(tw)
}

/** A `throw` statement. */
class AstThrow extends AstNode with AstExit {
  def nodeType: String = "Throw"

  override protected def walkChildren(visitor: TreeWalker): Unit =
    if (value != null) value.nn.walk(visitor)

  override def childrenBackwards(push: AstNode => Unit): Unit =
    if (value != null) push(value.nn)

  override protected def transformDescend(tw: TreeTransformer): Unit =
    if (value != null) value = value.nn.transform(tw)
}

/** Base class for loop control statements (break and continue). */
trait AstLoopControl extends AstJump {
  var label: AstLabelRef | Null = null
}

/** A `break` statement. */
class AstBreak extends AstNode with AstLoopControl {
  def nodeType: String = "Break"

  override protected def walkChildren(visitor: TreeWalker): Unit =
    if (label != null) label.nn.walk(visitor)

  override def childrenBackwards(push: AstNode => Unit): Unit =
    if (label != null) push(label.nn)

  override protected def transformDescend(tw: TreeTransformer): Unit =
    if (label != null) label = label.nn.transform(tw).asInstanceOf[AstLabelRef]
}

/** A `continue` statement. */
class AstContinue extends AstNode with AstLoopControl {
  def nodeType: String = "Continue"

  override protected def walkChildren(visitor: TreeWalker): Unit =
    if (label != null) label.nn.walk(visitor)

  override def childrenBackwards(push: AstNode => Unit): Unit =
    if (label != null) push(label.nn)

  override protected def transformDescend(tw: TreeTransformer): Unit =
    if (label != null) label = label.nn.transform(tw).asInstanceOf[AstLabelRef]
}
