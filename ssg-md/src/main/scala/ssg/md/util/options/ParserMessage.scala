/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-options/src/main/java/com/vladsch/flexmark/util/options/ParserMessage.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-options/src/main/java/com/vladsch/flexmark/util/options/ParserMessage.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package util
package options

import ssg.md.util.sequence.BasedSequence

class ParserMessage(val source: BasedSequence, val status: ParsedOptionStatus, val message: String)
