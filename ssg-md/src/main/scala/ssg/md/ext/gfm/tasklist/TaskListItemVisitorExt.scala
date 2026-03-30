/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-gfm-tasklist/src/main/java/com/vladsch/flexmark/ext/gfm/tasklist/TaskListItemVisitorExt.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package gfm
package tasklist

import ssg.md.util.ast.VisitHandler
import scala.language.implicitConversions

object TaskListItemVisitorExt {
  def VISIT_HANDLERS[V <: TaskListItemVisitor](visitor: V): Array[VisitHandler[?]] = {
    Array(new VisitHandler[TaskListItem](classOf[TaskListItem], visitor.visit(_)))
  }
}
