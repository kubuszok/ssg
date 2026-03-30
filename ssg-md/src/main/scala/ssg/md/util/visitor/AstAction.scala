/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-visitor/src/main/java/com/vladsch/flexmark/util/visitor/AstAction.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package util
package visitor

/** Interface for subclassing by specific Node actions: visit, format, render, etc
  *
  * @tparam N
  *   node type
  */
trait AstAction[N] {}
