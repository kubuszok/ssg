/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-yaml-front-matter/src/main/java/com/vladsch/flexmark/ext/yaml/front/matter/YamlFrontMatterVisitorExt.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-yaml-front-matter/src/main/java/com/vladsch/flexmark/ext/yaml/front/matter/YamlFrontMatterVisitorExt.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package ext
package yaml
package front
package matter

import ssg.md.util.ast.VisitHandler
import scala.language.implicitConversions

object YamlFrontMatterVisitorExt {

  def VISIT_HANDLERS[V <: YamlFrontMatterVisitor](visitor: V): Array[VisitHandler[?]] =
    Array(
      new VisitHandler[YamlFrontMatterNode](classOf[YamlFrontMatterNode], visitor.visit(_)),
      new VisitHandler[YamlFrontMatterBlock](classOf[YamlFrontMatterBlock], visitor.visit(_))
    )
}
