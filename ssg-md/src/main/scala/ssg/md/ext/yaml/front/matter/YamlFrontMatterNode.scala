/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-yaml-front-matter/src/main/java/com/vladsch/flexmark/ext/yaml/front/matter/YamlFrontMatterNode.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-yaml-front-matter/src/main/java/com/vladsch/flexmark/ext/yaml/front/matter/YamlFrontMatterNode.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package ext
package yaml
package front
package matter

import ssg.md.util.ast.Node
import ssg.md.util.sequence.BasedSequence

import java.util.{ ArrayList, List as JList }

class YamlFrontMatterNode(private var _key: BasedSequence, values: JList[BasedSequence]) extends Node {

  // private List<BasedSequence> values;
  values.forEach { value =>
    appendChild(new YamlFrontMatterValue(value))
  }

  override def segments: Array[BasedSequence] = Array(_key)

  def key: String = _key.toString

  def keySequence: BasedSequence = _key

  def key_=(key: BasedSequence): Unit =
    _key = key

  def getValues: JList[String] = {
    val list  = new ArrayList[String]()
    var child = firstChild
    while (child.isDefined) {
      list.add(child.get.chars.toString)
      child = child.get.next
    }
    list
  }

  def valuesSequences: JList[BasedSequence] = {
    val list  = new ArrayList[BasedSequence]()
    var child = firstChild
    while (child.isDefined) {
      list.add(child.get.chars)
      child = child.get.next
    }
    list
  }
}
