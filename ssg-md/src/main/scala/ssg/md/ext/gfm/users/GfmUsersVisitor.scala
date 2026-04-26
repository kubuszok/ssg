/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-gfm-users/src/main/java/com/vladsch/flexmark/ext/gfm/users/GfmUsersVisitor.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-gfm-users/src/main/java/com/vladsch/flexmark/ext/gfm/users/GfmUsersVisitor.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package ext
package gfm
package users

trait GfmUsersVisitor {
  def visit(node: GfmUser): Unit
}
