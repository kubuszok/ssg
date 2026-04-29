/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/mappers/NullEncoder.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/mappers/NullEncoder.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package util
package sequence
package mappers

object NullEncoder {

  /** Encoded NUL character (Unicode replacement character). */
  private val ENC_NUL: Char = '\uFFFD'

  /** NUL character. */
  private val NUL: Char = '\u0000'

  val encodeNull: CharMapper = new CharMapper {
    def map(c: Char): Char =
      if (c == NUL) ENC_NUL else c
  }

  val decodeNull: CharMapper = new CharMapper {
    def map(c: Char): Char =
      if (c == ENC_NUL) NUL else c
  }
}
