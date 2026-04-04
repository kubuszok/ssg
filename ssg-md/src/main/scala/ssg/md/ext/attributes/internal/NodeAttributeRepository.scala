/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-attributes/src/main/java/com/vladsch/flexmark/ext/attributes/internal/NodeAttributeRepository.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package attributes
package internal

import ssg.md.util.ast.{ KeepType, Node }
import ssg.md.util.data.{ DataHolder, DataKey }

import java.{ util => ju }
import scala.language.implicitConversions

class NodeAttributeRepository(options: DataHolder) extends ju.AbstractMap[Node, ju.ArrayList[AttributesNode]] {

  protected val nodeAttributesHashMap: ju.HashMap[Node, ju.ArrayList[AttributesNode]] = new ju.HashMap()

  def dataKey: DataKey[NodeAttributeRepository] = AttributesExtension.NODE_ATTRIBUTES

  def keepDataKey: DataKey[KeepType] = AttributesExtension.ATTRIBUTES_KEEP

  override def size(): Int = nodeAttributesHashMap.size()

  override def isEmpty: Boolean = nodeAttributesHashMap.isEmpty

  override def containsKey(key: Any): Boolean = nodeAttributesHashMap.containsKey(key)

  override def containsValue(value: Any): Boolean = nodeAttributesHashMap.containsValue(value)

  override def get(key: Any): ju.ArrayList[AttributesNode] = nodeAttributesHashMap.get(key) // @nowarn - returns null from Java Map

  override def put(key: Node, value: ju.ArrayList[AttributesNode]): ju.ArrayList[AttributesNode] =
    nodeAttributesHashMap.put(key, value)

  def put(key: Node, value: AttributesNode): ju.ArrayList[AttributesNode] = {
    var another = nodeAttributesHashMap.get(key)
    if (another == null) { // @nowarn - Java Map.get returns null
      another = new ju.ArrayList[AttributesNode]()
      nodeAttributesHashMap.put(key, another)
    }
    another.add(value)
    another
  }

  override def remove(key: Any): ju.ArrayList[AttributesNode] = nodeAttributesHashMap.remove(key)

  override def putAll(m: ju.Map[? <: Node, ? <: ju.ArrayList[AttributesNode]]): Unit =
    nodeAttributesHashMap.putAll(m)

  override def clear(): Unit = nodeAttributesHashMap.clear()

  override def keySet(): ju.Set[Node] = nodeAttributesHashMap.keySet()

  override def values(): ju.Collection[ju.ArrayList[AttributesNode]] = nodeAttributesHashMap.values()

  override def entrySet(): ju.Set[ju.Map.Entry[Node, ju.ArrayList[AttributesNode]]] = nodeAttributesHashMap.entrySet()
}
