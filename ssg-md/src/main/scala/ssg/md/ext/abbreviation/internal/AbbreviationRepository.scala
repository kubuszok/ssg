/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-abbreviation/src/main/java/com/vladsch/flexmark/ext/abbreviation/internal/AbbreviationRepository.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-abbreviation/src/main/java/com/vladsch/flexmark/ext/abbreviation/internal/AbbreviationRepository.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package ext
package abbreviation
package internal

import ssg.md.Nullable
import ssg.md.util.ast.{ KeepType, Node, NodeRepository }
import ssg.md.util.data.{ DataHolder, DataKey }

import java.{ util => ju }
import scala.language.implicitConversions

class AbbreviationRepository(options: Nullable[DataHolder]) extends NodeRepository[AbbreviationBlock](options.map(AbbreviationExtension.ABBREVIATIONS_KEEP.get(_))) {

  override def dataKey: DataKey[AbbreviationRepository] = AbbreviationExtension.ABBREVIATIONS

  override def keepDataKey: DataKey[KeepType] = AbbreviationExtension.ABBREVIATIONS_KEEP

  override def getReferencedElements(parent: Node): ju.Set[AbbreviationBlock] = {
    val references = new ju.HashSet[AbbreviationBlock]()
    visitNodes(
      parent,
      value =>
        value match {
          case abbr: Abbreviation =>
            val reference = abbr.getReferenceNode(AbbreviationRepository.this)
            if (reference != null) { // @nowarn - Java interop: getReferenceNode may return null
              references.add(reference)
            }
          case _ => ()
        },
      classOf[Abbreviation]
    )
    references
  }
}
