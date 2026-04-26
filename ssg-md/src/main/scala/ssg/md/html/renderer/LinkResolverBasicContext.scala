/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/html/renderer/LinkResolverBasicContext.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/html/renderer/LinkResolverBasicContext.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package html
package renderer

import ssg.md.util.ast.Document
import ssg.md.util.data.DataHolder

trait LinkResolverBasicContext {

  /** Get the current rendering context DataHolder. These are the options passed or set on the HtmlRenderer.builder() or passed to HtmlRenderer.builder(DataHolder). To get the document options you
    * should use getDocument() as the data holder.
    *
    * @return
    *   the current renderer options DataHolder
    */
  def getOptions: DataHolder

  /** @return
    *   the Document node of the current context
    */
  def getDocument: Document
}
