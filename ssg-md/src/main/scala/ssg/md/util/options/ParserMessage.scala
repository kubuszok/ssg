/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-options/src/main/java/com/vladsch/flexmark/util/options/ParserMessage.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package util
package options

import ssg.md.util.sequence.BasedSequence

class ParserMessage(val source: BasedSequence, val status: ParsedOptionStatus, val message: String)
