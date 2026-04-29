/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/parser/block/NodePostProcessor.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/parser/block/NodePostProcessor.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package parser
package block

import ssg.md.parser.PostProcessor
import ssg.md.util.ast.Document

abstract class NodePostProcessor extends PostProcessor {

  /** @param document
    *   the node to post-process
    * @return
    *   the result of post-processing, may be a modified `document` argument
    */
  final override def processDocument(document: Document): Document = document
}
