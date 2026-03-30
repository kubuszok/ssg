/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/parser/block/DocumentPostProcessorFactory.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package parser
package block

import ssg.md.parser.PostProcessorFactory

abstract class DocumentPostProcessorFactory extends PostProcessorFactory {

  /** Node types that this post processor processes.
    *
    * @return
    *   always Nullable.empty for document post processors
    */
  final override def getNodeTypes: Nullable[Map[Class[?], Set[Class[?]]]] = Nullable.empty

  override def afterDependents: Nullable[Set[Class[?]]] = Nullable.empty

  override def beforeDependents: Nullable[Set[Class[?]]] = Nullable.empty

  final override def affectsGlobalScope: Boolean = true
}
