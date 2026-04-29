/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/exceptions/IncompatibleTypeComparisonException.java
 * Original: Copyright (c) 2012 Bart Kiers, 2022 Vasyl Khrystiuk
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: liqp.exceptions → ssg.liquid.exceptions
 *   Idiom: Override getMessage as def
 *
 * Covenant: full-port
 * Covenant-java-reference: src/main/java/liqp/exceptions/IncompatibleTypeComparisonException.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: 1f0c47e87053f937fde5448cab0963f3379ce4a3
 */
package ssg
package liquid
package exceptions

class IncompatibleTypeComparisonException(a: Any, b: Any) extends RuntimeException() {

  override def getMessage: String = {
    val aType = if (a == null) "null" else a.getClass.getName
    val bType = if (b == null) "null" else b.getClass.getName
    s"Cannot compare $a with $b because they are not the same type: $aType vs $bType"
  }
}
