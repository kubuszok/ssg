/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-attributes/src/main/java/com/vladsch/flexmark/ext/attributes/AttributeImplicitName.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-attributes/src/main/java/com/vladsch/flexmark/ext/attributes/AttributeImplicitName.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package ext
package attributes

enum AttributeImplicitName extends java.lang.Enum[AttributeImplicitName] {
  case AS_IS, IMPLICIT_PREFERRED, EXPLICIT_PREFERRED

  def isNoChange: Boolean = this == AS_IS
  def isImplicit: Boolean = this == IMPLICIT_PREFERRED
  def isExplicit: Boolean = this == EXPLICIT_PREFERRED
}
