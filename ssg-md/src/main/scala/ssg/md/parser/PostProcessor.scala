/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/parser/PostProcessor.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package parser

import ssg.md.util.ast.{ Document, Node, NodeTracker }

trait PostProcessor {

  /** @param document
    *   the node to post-process
    * @return
    *   the result of post-processing, may be a modified `document` argument
    */
  def processDocument(document: Document): Document

  /** @param state
    *   node tracker used for optimizing node processing
    * @param node
    *   the node to post-process
    */
  def process(state: NodeTracker, node: Node): Unit
}
