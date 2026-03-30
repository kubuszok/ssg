/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/parser/internal/BlockContinueImpl.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package parser
package internal

import ssg.md.parser.block.BlockContinue

class BlockContinueImpl(
  val newIndex:   Int,
  val newColumn:  Int,
  val isFinalize: Boolean
) extends BlockContinue {}
