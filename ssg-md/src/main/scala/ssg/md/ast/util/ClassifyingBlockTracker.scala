/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/ast/util/ClassifyingBlockTracker.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ast
package util

import ssg.md.Nullable
import ssg.md.parser.block.BlockParser
import ssg.md.parser.block.BlockParserTracker
import ssg.md.util.ast.Block
import ssg.md.util.ast.BlockTracker
import ssg.md.util.ast.Node
import ssg.md.util.ast.NodeClassifier
import ssg.md.util.collection.ClassificationBag
import ssg.md.util.collection.CollectionHost
import ssg.md.util.collection.OrderedMultiMap
import ssg.md.util.collection.OrderedSet
import ssg.md.util.collection.iteration.ReversiblePeekingIterable
import ssg.md.util.misc.Paired

import scala.language.implicitConversions

class ClassifyingBlockTracker extends BlockTracker, BlockParserTracker {

  protected val nodeClassifier: ClassificationBag[Class[?], Node] = new ClassificationBag[Class[?], Node](NodeClassifier.INSTANCE)

  protected val allBlockParsersMap: OrderedMultiMap[BlockParser, Block] = new OrderedMultiMap[BlockParser, Block](
    new CollectionHost[Paired[Nullable[BlockParser], Nullable[Block]]] {
      override def adding(index: Int, paired: Nullable[Paired[Nullable[BlockParser], Nullable[Block]]], v: Nullable[Object]): Unit =
        paired.foreach { p =>
          val block = p.second
          if (block.isDefined) nodeClassifier.add(block.get)
        }

      override def removing(index: Int, paired: Nullable[Paired[Nullable[BlockParser], Nullable[Block]]]): Nullable[Object] = {
        paired.foreach { p =>
          val block = p.second
          if (block.isDefined) nodeClassifier.remove(block.get)
        }
        paired.asInstanceOf[Nullable[Object]]
      }

      override def clearing(): Unit =
        nodeClassifier.clear()

      override def addingNulls(index: Int): Unit = {
        // ignore
      }

      override def skipHostUpdate(): Boolean = false

      override def getIteratorModificationCount: Int = allBlockParsersMap.getModificationCount
    }
  )

  def allBlockParsers: OrderedSet[BlockParser] = allBlockParsersMap.keySet()

  def allBlocks: OrderedSet[Block] = allBlockParsersMap.valueSet()

  def getValue(parser: BlockParser): Block = allBlockParsersMap.getKeyValue(parser)

  def getKey(parser: Block): BlockParser = allBlockParsersMap.getValueKey(parser)

  def containsKey(parser: BlockParser): Boolean = allBlockParsersMap.containsKey(parser)

  def containsValue(parser: Block): Boolean = allBlockParsersMap.containsValue(parser)

  def getNodeClassifier: ClassificationBag[Class[?], Node] = nodeClassifier

  override def blockParserAdded(blockParser: BlockParser): Unit =
    allBlockParsersMap.putKeyValue(blockParser, blockParser.getBlock)

  override def blockParserRemoved(blockParser: BlockParser): Unit =
    allBlockParsersMap.removeKey(blockParser)

  private def validateLinked(node: Node): Unit =
    if (node.next.isEmpty && node.parent.isEmpty) {
      throw new IllegalStateException("Added block " + node + " is not linked into the AST")
    }

  override def blockAdded(node: Block): Unit = {
    validateLinked(node)
    allBlockParsersMap.putValueKey(node, null)
  }

  override def blockAddedWithChildren(node: Block): Unit = {
    validateLinked(node)
    allBlockParsersMap.putValueKey(node, null)
    addBlocks(node.children)
  }

  override def blockAddedWithDescendants(node: Block): Unit = {
    validateLinked(node)
    allBlockParsersMap.putValueKey(node, null)
    addBlocks(node.descendants)
  }

  private def addBlocks(nodes: ReversiblePeekingIterable[Node]): Unit = {
    val iter = nodes.iterator()
    while (iter.hasNext)
      iter.next() match {
        case block: Block => allBlockParsersMap.putValueKey(block, null)
        case _ => ()
      }
  }

  private def validateUnlinked(node: Node): Unit =
    if (!(node.next.isEmpty && node.parent.isEmpty)) {
      throw new IllegalStateException("Removed block " + node + " is still linked in the AST")
    }

  override def blockRemoved(node: Block): Unit = {
    validateUnlinked(node)
    allBlockParsersMap.removeValue(node)
  }

  override def blockRemovedWithChildren(node: Block): Unit = {
    validateUnlinked(node)
    allBlockParsersMap.removeValue(node)
    removeBlocks(node.children)
  }

  override def blockRemovedWithDescendants(node: Block): Unit = {
    validateUnlinked(node)
    allBlockParsersMap.removeValue(node)
    removeBlocks(node.descendants)
  }

  private def removeBlocks(nodes: ReversiblePeekingIterable[Node]): Unit = {
    val iter = nodes.iterator()
    while (iter.hasNext)
      iter.next() match {
        case block: Block => allBlockParsersMap.removeValue(block)
        case _ => ()
      }
  }
}
