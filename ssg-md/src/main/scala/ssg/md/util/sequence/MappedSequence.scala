/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/MappedSequence.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package util
package sequence

import ssg.md.util.sequence.mappers.CharMapper

/** A CharSequence that maps characters according to CharMapper
  */
trait MappedSequence[T <: CharSequence] extends CharSequence {
  def charMapper:   CharMapper
  def charSequence: T
}
