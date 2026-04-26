/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-ast/src/main/java/com/vladsch/flexmark/util/ast/NodeRepository.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-ast/src/main/java/com/vladsch/flexmark/util/ast/NodeRepository.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package util
package ast

import ssg.md.Nullable
import ssg.md.util.data.DataKey

import java.{ util => ju }
import java.util.function.Consumer
import scala.util.boundary
import scala.util.boundary.break

abstract class NodeRepository[T](keepType: Nullable[KeepType]) extends ju.Map[String, T] {

  protected val nodeList:  ju.ArrayList[T]   = new ju.ArrayList[T]()
  protected val nodeMap:   ju.Map[String, T] = new ju.HashMap[String, T]()
  protected val _keepType: KeepType          = keepType.getOrElse(KeepType.LOCKED)

  def dataKey:     DataKey[? <: NodeRepository[T]]
  def keepDataKey: DataKey[KeepType]

  // function implementing extraction of referenced elements by given node or its children
  def getReferencedElements(parent: Node): ju.Set[T]

  final protected def visitNodes(parent: Node, runnable: Consumer[Node], classes: Class[? <: Node]*): Unit = {
    val visitor = new NodeVisitor()
    for (clazz <- classes)
      visitor.addHandler(new VisitHandler(clazz.asInstanceOf[Class[Node]], (node: Node) => runnable.accept(node)))
    visitor.visit(parent)
  }

  def normalizeKey(key: CharSequence): String = key.toString

  def getFromRaw(rawKey: CharSequence): Nullable[T] = Nullable(nodeMap.get(normalizeKey(rawKey)))

  def putRawKey(key: CharSequence, t: T): Nullable[T] = Nullable(put(normalizeKey(key), t))

  def getValues: ju.Collection[T] = nodeMap.values

  override def put(s: String, t: T): T = boundary {
    nodeList.add(t)

    if (_keepType == KeepType.LOCKED) throw new IllegalStateException("Not allowed to modify LOCKED repository")
    if (_keepType != KeepType.LAST) {
      val another = nodeMap.get(s)
      if (another != null) {
        if (_keepType == KeepType.FAIL) throw new IllegalStateException("Duplicate key " + s)
        break(another)
      }
    }
    nodeMap.put(s, t)
  }

  override def putAll(map: ju.Map[? <: String, ? <: T]): Unit = {
    if (_keepType == KeepType.LOCKED) throw new IllegalStateException("Not allowed to modify LOCKED repository")
    if (_keepType != KeepType.LAST) {
      val it = map.keySet.iterator()
      while (it.hasNext) {
        val key = it.next()
        nodeMap.put(key, map.get(key))
      }
    } else {
      nodeMap.putAll(map)
    }
  }

  override def remove(o: AnyRef): T = {
    if (_keepType == KeepType.LOCKED) throw new IllegalStateException("Not allowed to modify LOCKED repository")
    nodeMap.remove(o)
  }

  override def clear(): Unit = {
    if (_keepType == KeepType.LOCKED) throw new IllegalStateException("Not allowed to modify LOCKED repository")
    nodeMap.clear()
  }

  override def size:                     Int                             = nodeMap.size
  override def isEmpty:                  Boolean                         = nodeMap.isEmpty
  override def containsKey(o:   AnyRef): Boolean                         = nodeMap.containsKey(o)
  override def containsValue(o: AnyRef): Boolean                         = nodeMap.containsValue(o)
  override def get(o:           AnyRef): T                               = nodeMap.get(o)
  override def keySet:                   ju.Set[String]                  = nodeMap.keySet
  override def values:                   ju.List[T]                      = nodeList
  override def entrySet:                 ju.Set[ju.Map.Entry[String, T]] = nodeMap.entrySet

  override def equals(o: Any): Boolean = nodeMap.equals(o)
  override def hashCode():     Int     = nodeMap.hashCode()
}

object NodeRepository {
  def transferReferences[T](destination: NodeRepository[T], included: NodeRepository[T], onlyIfUndefined: Boolean, referenceIdMap: Nullable[ju.Map[String, String]]): Boolean = {
    // copy references but only if they are not defined in the original document
    var transferred = false
    val it          = included.entrySet.iterator()
    while (it.hasNext) {
      val entry = it.next()
      var key   = entry.getKey

      // map as requested
      if (referenceIdMap.isDefined) key = referenceIdMap.get.getOrDefault(key, key)

      if (!onlyIfUndefined || !destination.containsKey(key)) {
        destination.put(key, entry.getValue)
        transferred = true
      }
    }
    transferred
  }
}
