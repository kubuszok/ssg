/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-gitlab/src/main/java/com/vladsch/flexmark/ext/gitlab/GitLabVisitor.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-gitlab/src/main/java/com/vladsch/flexmark/ext/gitlab/GitLabVisitor.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package ext
package gitlab

trait GitLabVisitor {
  def visit(node: GitLabIns):        Unit
  def visit(node: GitLabDel):        Unit
  def visit(node: GitLabInlineMath): Unit
  def visit(node: GitLabBlockQuote): Unit
}
