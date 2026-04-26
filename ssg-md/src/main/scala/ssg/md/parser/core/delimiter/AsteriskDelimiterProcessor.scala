/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/parser/core/delimiter/AsteriskDelimiterProcessor.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/parser/core/delimiter/AsteriskDelimiterProcessor.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package parser
package core
package delimiter

class AsteriskDelimiterProcessor(strongWrapsEmphasis: Boolean) extends EmphasisDelimiterProcessor('*', strongWrapsEmphasis) {}
