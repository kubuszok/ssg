/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-gfm-issues/src/main/java/com/vladsch/flexmark/ext/gfm/issues/GfmIssuesVisitorExt.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-gfm-issues/src/main/java/com/vladsch/flexmark/ext/gfm/issues/GfmIssuesVisitorExt.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package ext
package gfm
package issues

import ssg.md.util.ast.VisitHandler
import scala.language.implicitConversions

object GfmIssuesVisitorExt {
  def VISIT_HANDLERS[V <: GfmIssuesVisitor](visitor: V): Array[VisitHandler[?]] =
    Array(new VisitHandler[GfmIssue](classOf[GfmIssue], visitor.visit(_)))
}
