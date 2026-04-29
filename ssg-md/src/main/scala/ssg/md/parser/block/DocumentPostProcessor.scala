/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/parser/block/DocumentPostProcessor.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/parser/block/DocumentPostProcessor.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package parser
package block

import ssg.md.parser.PostProcessor
import ssg.md.util.ast.{ Node, NodeTracker }

abstract class DocumentPostProcessor extends PostProcessor {

  /** @param state
    *   node tracker used for optimizing node processing
    * @param node
    *   the node to post-process
    */
  final override def process(state: NodeTracker, node: Node): Unit = {}
}
