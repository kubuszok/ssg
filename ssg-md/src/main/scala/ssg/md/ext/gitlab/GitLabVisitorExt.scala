/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-gitlab/src/main/java/com/vladsch/flexmark/ext/gitlab/GitLabVisitorExt.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-gitlab/src/main/java/com/vladsch/flexmark/ext/gitlab/GitLabVisitorExt.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package ext
package gitlab

import ssg.md.util.ast.VisitHandler
import scala.language.implicitConversions

object GitLabVisitorExt {
  def VISIT_HANDLERS[V <: GitLabVisitor](visitor: V): Array[VisitHandler[?]] =
    Array(
      new VisitHandler[GitLabIns](classOf[GitLabIns], visitor.visit(_)),
      new VisitHandler[GitLabDel](classOf[GitLabDel], visitor.visit(_)),
      new VisitHandler[GitLabInlineMath](classOf[GitLabInlineMath], visitor.visit(_)),
      new VisitHandler[GitLabBlockQuote](classOf[GitLabBlockQuote], visitor.visit(_))
    )
}
