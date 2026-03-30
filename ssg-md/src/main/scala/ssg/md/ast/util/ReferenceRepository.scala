/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/ast/util/ReferenceRepository.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ast
package util

import ssg.md.Nullable
import ssg.md.parser.Parser
import ssg.md.util.ast.KeepType
import ssg.md.util.ast.Node
import ssg.md.util.ast.NodeRepository
import ssg.md.util.data.DataHolder
import ssg.md.util.data.DataKey
import ssg.md.util.sequence.Escaping

import java.{ util => ju }

import scala.language.implicitConversions

class ReferenceRepository(options: DataHolder) extends NodeRepository[Reference](Parser.REFERENCES_KEEP.get(options)) {

  override def dataKey: DataKey[ReferenceRepository] = Parser.REFERENCES

  override def keepDataKey: DataKey[KeepType] = Parser.REFERENCES_KEEP

  override def normalizeKey(key: CharSequence): String =
    Escaping.normalizeReference(key, true)

  override def getReferencedElements(parent: Node): ju.Set[Reference] = {
    val references = new ju.HashSet[Reference]()
    visitNodes(
      parent,
      value =>
        value match {
          case refNode: RefNode =>
            val reference: Nullable[Reference] = refNode.getReferenceNode(ReferenceRepository.this)
            if (reference.isDefined) {
              references.add(reference.get)
            }
          case _ => ()
        },
      classOf[LinkRef],
      classOf[ImageRef]
    )
    references
  }
}
