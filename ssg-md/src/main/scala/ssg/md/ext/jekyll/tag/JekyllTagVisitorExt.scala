/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-jekyll-tag/src/main/java/com/vladsch/flexmark/ext/jekyll/tag/JekyllTagVisitorExt.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-jekyll-tag/src/main/java/com/vladsch/flexmark/ext/jekyll/tag/JekyllTagVisitorExt.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package ext
package jekyll
package tag

import ssg.md.util.ast.VisitHandler
import scala.language.implicitConversions

object JekyllTagVisitorExt {

  def VISIT_HANDLERS[V <: JekyllTagVisitor](visitor: V): Array[VisitHandler[?]] =
    Array(
      new VisitHandler[JekyllTag](classOf[JekyllTag], visitor.visit(_)),
      new VisitHandler[JekyllTagBlock](classOf[JekyllTagBlock], visitor.visit(_))
    )
}
