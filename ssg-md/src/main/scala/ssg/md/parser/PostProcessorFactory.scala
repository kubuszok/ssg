/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/parser/PostProcessorFactory.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package parser

import ssg.md.util.ast.Document
import ssg.md.util.dependency.Dependent

/** Factory for creating [[PostProcessor]] instances for a given document.
  */
trait PostProcessorFactory extends (Document => PostProcessor), Dependent {

  /** A map of nodes of interest as keys and values a set of classes, if implemented by an ancestors then the node should be excluded from processing by this processor.
    *
    * @return
    *   a map of desired node types mapped to a set of ancestors under which the post processor does not process the block
    */
  def getNodeTypes: Nullable[Map[Class[?], Set[Class[?]]]]

  /** @param document
    *   for which to create the post processor
    * @return
    *   post processor for the document
    */
  override def apply(document: Document): PostProcessor
}
