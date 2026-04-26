/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-options/src/main/java/com/vladsch/flexmark/util/options/OptionParser.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-options/src/main/java/com/vladsch/flexmark/util/options/OptionParser.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package util
package options

import ssg.md.Nullable
import ssg.md.util.misc.Pair
import ssg.md.util.sequence.BasedSequence

trait OptionParser[T] {
  def optionName:                                                                              String
  def parseOption(optionText: BasedSequence, options: T, provider: Nullable[MessageProvider]): Pair[T, java.util.List[ParsedOption[T]]]
  def getOptionText(options:  T, defaultOptions:      Nullable[T]):                            String
}
