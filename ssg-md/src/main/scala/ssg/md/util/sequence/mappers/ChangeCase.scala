/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/mappers/ChangeCase.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/mappers/ChangeCase.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package util
package sequence
package mappers

object ChangeCase {

  val toUpperCase: CharMapper = new CharMapper {
    def map(c: Char): Char =
      if (Character.isLowerCase(c)) Character.toUpperCase(c) else c
  }

  val toLowerCase: CharMapper = new CharMapper {
    def map(c: Char): Char =
      if (Character.isUpperCase(c)) Character.toLowerCase(c) else c
  }
}
