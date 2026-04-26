/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/html/renderer/PhasedNodeRenderer.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/html/renderer/PhasedNodeRenderer.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package html
package renderer

import ssg.md.util.ast.Document

/** A renderer for a document node for a specific rendering phase
  */
trait PhasedNodeRenderer extends NodeRenderer {

  def getRenderingPhases: Nullable[Set[RenderingPhase]]

  /** Render the specified node.
    *
    * @param context
    *   node renderer context instance
    * @param html
    *   html writer instance
    * @param document
    *   the document node to render
    * @param phase
    *   rendering phase for which to generate the output. Will be any of [[RenderingPhase]] no rendering should be done if phase is [[RenderingPhase.BODY]] because this phase is used for the
    *   non-phased node rendering. For body phase this method is called before the node renderer calls are made so it is a good place to reset internal structures for start of each phase.
    */
  def renderDocument(context: NodeRendererContext, html: HtmlWriter, document: Document, phase: RenderingPhase): Unit
}
