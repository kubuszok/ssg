/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-definition/src/main/java/com/vladsch/flexmark/ext/definition/DefinitionItem.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-definition/src/main/java/com/vladsch/flexmark/ext/definition/DefinitionItem.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package ext
package definition

import ssg.md.ast.ListItem

/** A Definition item block node, starts with : followed by any content like a list item
  */
class DefinitionItem() extends ListItem {}
