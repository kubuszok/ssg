/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-ast/src/main/java/com/vladsch/flexmark/util/ast/DoNotLinkDecorate.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-ast/src/main/java/com/vladsch/flexmark/util/ast/DoNotLinkDecorate.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package util
package ast

/** To be implemented by nodes marking their text as not for conversion to links or other decoration methods by extensions
  */
trait DoNotLinkDecorate {}
