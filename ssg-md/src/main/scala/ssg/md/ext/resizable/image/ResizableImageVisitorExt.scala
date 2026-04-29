/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-resizable-image/src/main/java/com/vladsch/flexmark/ext/resizable/image/ResizableImageVisitorExt.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-resizable-image/src/main/java/com/vladsch/flexmark/ext/resizable/image/ResizableImageVisitorExt.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package ext
package resizable
package image

import ssg.md.util.ast.VisitHandler
import scala.language.implicitConversions

object ResizableImageVisitorExt {
  def VISIT_HANDLERS[V <: ResizableImageVisitor](visitor: V): Array[VisitHandler[?]] =
    Array(new VisitHandler[ResizableImage](classOf[ResizableImage], visitor.visit(_)))
}
