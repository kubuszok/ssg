/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-enumerated-reference/src/main/java/com/vladsch/flexmark/ext/enumerated/reference/EnumeratedReferenceRepository.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package enumerated
package reference

import ssg.md.Nullable
import ssg.md.util.ast.{ KeepType, Node, NodeRepository }
import ssg.md.util.data.{ DataHolder, DataKey }

import scala.language.implicitConversions
import java.util.{ ArrayList, HashSet }

class EnumeratedReferenceRepository(options: DataHolder)
    extends NodeRepository[EnumeratedReferenceBlock](
      EnumeratedReferenceExtension.ENUMERATED_REFERENCES_KEEP.get(options)
    ) {

  private val referencedEnumeratedReferenceBlocks_ = new ArrayList[EnumeratedReferenceBlock]()

  def referencedEnumeratedReferenceBlocks: java.util.List[EnumeratedReferenceBlock] = referencedEnumeratedReferenceBlocks_

  override def dataKey: DataKey[EnumeratedReferenceRepository] = EnumeratedReferenceExtension.ENUMERATED_REFERENCES

  override def keepDataKey: DataKey[KeepType] = EnumeratedReferenceExtension.ENUMERATED_REFERENCES_KEEP

  override def getReferencedElements(parent: Node): java.util.Set[EnumeratedReferenceBlock] = {
    val references = new HashSet[EnumeratedReferenceBlock]()
    visitNodes(
      parent,
      value =>
        value match {
          case ref: EnumeratedReferenceBase =>
            val reference = ref.getReferenceNode(EnumeratedReferenceRepository.this)
            if (reference != null) { // @nowarn - getReferenceNode may return null
              references.add(reference)
            }
          case _ =>
        },
      classOf[EnumeratedReferenceText],
      classOf[EnumeratedReferenceLink]
    )
    references
  }
}

object EnumeratedReferenceRepository {
  def getType(text: String): String = {
    val pos = text.lastIndexOf(':')
    if (pos > 0) {
      text.substring(0, pos)
    } else {
      // use empty type
      EnumeratedReferences.EMPTY_TYPE
    }
  }
}
