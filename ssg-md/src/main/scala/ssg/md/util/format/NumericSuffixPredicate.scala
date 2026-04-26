/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-format/src/main/java/com/vladsch/flexmark/util/format/NumericSuffixPredicate.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-format/src/main/java/com/vladsch/flexmark/util/format/NumericSuffixPredicate.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package util
package format

trait NumericSuffixPredicate extends (String => Boolean) {

  def sortSuffix(suffix: String): Boolean =
    true
}
