/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-options/src/main/java/com/vladsch/flexmark/util/options/ParserParams.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-options/src/main/java/com/vladsch/flexmark/util/options/ParserParams.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package util
package options

import ssg.md.Nullable

import scala.collection.mutable.ArrayBuffer

class ParserParams {
  var messages: Nullable[ArrayBuffer[ParserMessage]] = Nullable.empty
  var skip:     Boolean                              = false
  var status:   ParsedOptionStatus                   = ParsedOptionStatus.VALID

  def add(message: ParserMessage): ParserParams = {
    if (messages.isEmpty) {
      messages = Nullable(ArrayBuffer.empty[ParserMessage])
    }
    messages.get += message
    escalate(message.status)
    this
  }

  def escalate(other: ParsedOptionStatus): ParserParams = {
    status = status.escalate(other)
    this
  }
}
