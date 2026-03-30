/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-tables/src/main/java/com/vladsch/flexmark/ext/tables/TableVisitorExt.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package tables

import ssg.md.util.ast.VisitHandler
import scala.language.implicitConversions

object TableVisitorExt {

  def VISIT_HANDLERS[V <: TableVisitor](visitor: V): Array[VisitHandler[?]] = {
    Array(
      new VisitHandler[TableBlock](classOf[TableBlock], visitor.visit(_)),
      new VisitHandler[TableHead](classOf[TableHead], visitor.visit(_)),
      new VisitHandler[TableSeparator](classOf[TableSeparator], visitor.visit(_)),
      new VisitHandler[TableBody](classOf[TableBody], visitor.visit(_)),
      new VisitHandler[TableRow](classOf[TableRow], visitor.visit(_)),
      new VisitHandler[TableCell](classOf[TableCell], visitor.visit(_)),
      new VisitHandler[TableCaption](classOf[TableCaption], visitor.visit(_)),
    )
  }
}
