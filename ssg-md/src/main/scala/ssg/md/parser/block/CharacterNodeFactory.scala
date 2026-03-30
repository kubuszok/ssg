/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/parser/block/CharacterNodeFactory.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package parser
package block

import ssg.md.util.ast.Node

trait CharacterNodeFactory extends (() => Node) {

  def skipNext(c: Char): Boolean

  def skipPrev(c: Char): Boolean

  def wantSkippedWhitespace: Boolean
}
