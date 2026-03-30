/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/ReplacedBasedSequence.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package util
package sequence

/** Interface implemented by sequences which do not contain contiguous base characters from startOffset to endOffset
  */
trait ReplacedBasedSequence extends BasedSequence {}
