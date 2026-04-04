/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-enumerated-reference/src/main/java/com/vladsch/flexmark/ext/enumerated/reference/EnumeratedReferenceVisitor.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package enumerated
package reference

trait EnumeratedReferenceVisitor {
  def visit(node: EnumeratedReferenceText):  Unit
  def visit(node: EnumeratedReferenceLink):  Unit
  def visit(node: EnumeratedReferenceBlock): Unit
}
