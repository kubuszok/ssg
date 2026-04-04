/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-footnotes/src/main/java/com/vladsch/flexmark/ext/footnotes/FootnoteVisitorExt.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package footnotes

import ssg.md.util.ast.VisitHandler
import scala.language.implicitConversions

object FootnoteVisitorExt {
  def VISIT_HANDLERS[V <: FootnoteVisitor](visitor: V): Array[VisitHandler[?]] =
    Array(
      new VisitHandler[FootnoteBlock](classOf[FootnoteBlock], visitor.visit(_)),
      new VisitHandler[Footnote](classOf[Footnote], visitor.visit(_))
    )
}
