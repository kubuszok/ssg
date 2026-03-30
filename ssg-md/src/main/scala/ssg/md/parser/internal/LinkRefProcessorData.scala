/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/parser/internal/LinkRefProcessorData.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package parser
package internal

import ssg.md.parser.LinkRefProcessorFactory

class LinkRefProcessorData(
  val processors:   List[LinkRefProcessorFactory], // sorted least nesting level to greatest nesting level
  val maxNesting:   Int, // maximum desired reference link nesting level
  val nestingIndex: Array[Int] // nesting level to index in ReferenceLinkProcessors for the first processor interested in that nesting level
) {}
