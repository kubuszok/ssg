/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-misc/src/main/java/com/vladsch/flexmark/util/misc/Extension.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-misc/src/main/java/com/vladsch/flexmark/util/misc/Extension.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package util
package misc

/** Base interface for a parser/renderer extension.
  *
  * Doesn't have any methods itself, but has specific sub interfaces to configure parser/renderer. This base interface is for convenience, so that a list of extensions can be built and then used for
  * configuring both the parser and renderer in the same way.
  *
  * By convention, classes that implement this also have a static create() method that returns an instance of the extension.
  */
trait Extension {}

object Extension {
  val EMPTY_LIST: List[Extension] = List.empty
}
