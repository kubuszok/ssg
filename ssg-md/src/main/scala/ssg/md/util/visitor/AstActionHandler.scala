/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-visitor/src/main/java/com/vladsch/flexmark/util/visitor/AstActionHandler.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-visitor/src/main/java/com/vladsch/flexmark/util/visitor/AstActionHandler.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package util
package visitor

import ssg.md.Nullable

import scala.collection.mutable

/** Intended to be completed by subclasses for specific node types and node actions
  *
  * @tparam C
  *   subclass of this class to have functions returning this to have the correct type
  * @tparam N
  *   base node type, this class does not care but in specific handlers it should be a common supertype for all nodes
  * @tparam A
  *   action type, subclasses of [[AstAction]] and [[AstHandler]] provide actual functionality
  * @tparam H
  *   handler to invoke the functionality during AST traversal for specific node
  */
abstract class AstActionHandler[C <: AstActionHandler[C, N, A, H], N, A <: AstAction[N], H <: AstHandler[N, A]](
  private val astAdapter: AstNode[N]
) {

  private val customHandlersMap: mutable.Map[Class[? <: N], H] = mutable.HashMap()

  final protected def addActionHandlers(handlers: Array[H]*): C = {
    for (moreHandlers <- handlers)
      for (handler <- moreHandlers)
        customHandlersMap.put(handler.nodeType, handler)
    this.asInstanceOf[C]
  }

  protected def addActionHandler(handler: H): C = {
    customHandlersMap.put(handler.nodeType, handler)
    this.asInstanceOf[C]
  }

  private def getActionFromHandler(handler: Nullable[H]): Nullable[A] =
    handler.map(_.adapter)

  def getAction(node: N): Nullable[A] =
    getActionFromHandler(Nullable.fromOption(customHandlersMap.get(node.getClass.asInstanceOf[Class[? <: N]])))

  def getAction(nodeClass: Class[?]): Nullable[A] =
    getActionFromHandler(Nullable.fromOption(customHandlersMap.get(nodeClass.asInstanceOf[Class[? <: N]])))

  protected def getHandler(node: N): Nullable[H] =
    Nullable.fromOption(customHandlersMap.get(node.getClass.asInstanceOf[Class[? <: N]]))

  protected def getHandler(nodeClass: Class[?]): Nullable[H] =
    Nullable.fromOption(customHandlersMap.get(nodeClass.asInstanceOf[Class[? <: N]]))

  def nodeClasses: Set[Class[? <: N]] = customHandlersMap.keySet.toSet

  /** Node processing called for every node being processed
    *
    * Override this to add customizations to standard processing callback.
    *
    * @param node
    *   node being processed
    * @param withChildren
    *   whether to process child nodes if there is no handler for the node type
    * @param processor
    *   processor to invoke to perform the processing
    */
  protected def processNode(node: N, withChildren: Boolean, processor: (N, A) => Unit): Unit = {
    val action = getAction(node)
    if (action.isDefined) {
      processor(node, action.get)
    } else if (withChildren) {
      processChildren(node, processor)
    }
  }

  final protected def processChildren(node: N, processor: (N, A) => Unit): Unit = {
    // A subclass of this visitor might modify the node, resulting in getNext returning a different node or no
    // node after visiting it. So get the next node before visiting.
    var child = astAdapter.firstChild(node)
    while (child.isDefined) {
      val next = astAdapter.next(child.get)
      processNode(child.get, true, processor)
      child = next
    }
  }

  /** Process the node and return value from the processor
    *
    * @param node
    *   node to process
    * @param defaultValue
    *   default value if no handler is defined for the node
    * @param processor
    *   processor to pass the node and handler for processing
    * @tparam R
    *   type of result returned by processor
    * @return
    *   result or defaultValue
    */
  final protected def processNodeOnly[R](node: N, defaultValue: R, processor: (N, A) => R): R = {
    var value: R = defaultValue
    processNode(node, false, (n, h) => value = processor(n, h))
    value
  }
}
