/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/formatter/MergeContext.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package formatter

import ssg.md.util.ast.Document

trait MergeContext {
  def getDocument(context:               TranslationContext):                                 Document
  def forEachPrecedingDocument(document: Nullable[Document], consumer: MergeContextConsumer): Unit
}
