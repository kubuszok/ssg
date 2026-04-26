/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-ast/src/main/java/com/vladsch/flexmark/util/ast/BlankLineBreakNode.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-ast/src/main/java/com/vladsch/flexmark/util/ast/BlankLineBreakNode.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package util
package ast

/** Implemented by nodes after which text collecting visitor should add a line break regardless of whether there is a previous line break
  */
trait BlankLineBreakNode {}
