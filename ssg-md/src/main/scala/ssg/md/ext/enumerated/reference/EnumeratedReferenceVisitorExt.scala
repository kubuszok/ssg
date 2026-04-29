/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-enumerated-reference/src/main/java/com/vladsch/flexmark/ext/enumerated/reference/EnumeratedReferenceVisitorExt.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-enumerated-reference/src/main/java/com/vladsch/flexmark/ext/enumerated/reference/EnumeratedReferenceVisitorExt.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package ext
package enumerated
package reference

import ssg.md.util.ast.VisitHandler
import scala.language.implicitConversions

object EnumeratedReferenceVisitorExt {
  def VISIT_HANDLERS[V <: EnumeratedReferenceVisitor](visitor: V): Array[VisitHandler[?]] =
    Array(
      new VisitHandler[EnumeratedReferenceText](classOf[EnumeratedReferenceText], visitor.visit(_)),
      new VisitHandler[EnumeratedReferenceLink](classOf[EnumeratedReferenceLink], visitor.visit(_)),
      new VisitHandler[EnumeratedReferenceBlock](classOf[EnumeratedReferenceBlock], visitor.visit(_))
    )
}
