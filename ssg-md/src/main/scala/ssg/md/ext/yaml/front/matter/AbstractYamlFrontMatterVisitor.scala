/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-yaml-front-matter/src/main/java/com/vladsch/flexmark/ext/yaml/front/matter/AbstractYamlFrontMatterVisitor.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-yaml-front-matter/src/main/java/com/vladsch/flexmark/ext/yaml/front/matter/AbstractYamlFrontMatterVisitor.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package ext
package yaml
package front
package matter

import ssg.md.util.ast.{ Node, NodeVisitor }

import java.util.{ LinkedHashMap, List as JList, Map as JMap }

class AbstractYamlFrontMatterVisitor extends YamlFrontMatterVisitor {

  private val data:      JMap[String, JList[String]] = new LinkedHashMap[String, JList[String]]()
  private val myVisitor: NodeVisitor                 = new NodeVisitor(YamlFrontMatterVisitorExt.VISIT_HANDLERS(this)*)

  def visit(node: Node): Unit =
    myVisitor.visit(node)

  override def visit(node: YamlFrontMatterNode): Unit =
    data.put(node.key, node.getValues)

  override def visit(node: YamlFrontMatterBlock): Unit =
    myVisitor.visitChildren(node)

  def getData: JMap[String, JList[String]] = data
}
