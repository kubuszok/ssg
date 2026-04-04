/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-toc/src/main/java/com/vladsch/flexmark/ext/toc/SimTocVisitorExt.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package toc

import ssg.md.util.ast.VisitHandler
import scala.language.implicitConversions

object SimTocVisitorExt {
  def VISIT_HANDLERS[V <: SimTocVisitor](visitor: V): Array[VisitHandler[?]] =
    Array(
      new VisitHandler[SimTocBlock](classOf[SimTocBlock], visitor.visit(_)),
      new VisitHandler[SimTocOptionList](classOf[SimTocOptionList], visitor.visit(_)),
      new VisitHandler[SimTocOption](classOf[SimTocOption], visitor.visit(_)),
      new VisitHandler[SimTocContent](classOf[SimTocContent], visitor.visit(_))
    )
}
