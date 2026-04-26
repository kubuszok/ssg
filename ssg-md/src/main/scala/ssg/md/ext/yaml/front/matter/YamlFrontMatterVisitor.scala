/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-yaml-front-matter/src/main/java/com/vladsch/flexmark/ext/yaml/front/matter/YamlFrontMatterVisitor.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-yaml-front-matter/src/main/java/com/vladsch/flexmark/ext/yaml/front/matter/YamlFrontMatterVisitor.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package ext
package yaml
package front
package matter

trait YamlFrontMatterVisitor {
  def visit(node: YamlFrontMatterNode):  Unit
  def visit(node: YamlFrontMatterBlock): Unit
}
