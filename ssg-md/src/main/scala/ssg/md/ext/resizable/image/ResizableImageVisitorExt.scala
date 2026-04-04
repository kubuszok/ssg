/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-resizable-image/src/main/java/com/vladsch/flexmark/ext/resizable/image/ResizableImageVisitorExt.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
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
