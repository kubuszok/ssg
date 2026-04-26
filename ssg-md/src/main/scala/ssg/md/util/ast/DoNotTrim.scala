/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-ast/src/main/java/com/vladsch/flexmark/util/ast/DoNotTrim.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-ast/src/main/java/com/vladsch/flexmark/util/ast/DoNotTrim.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package util
package ast

/** To be implemented by nodes marking their text as not to be trimmed because they represent whitespace or EOL text
  */
trait DoNotTrim {}
