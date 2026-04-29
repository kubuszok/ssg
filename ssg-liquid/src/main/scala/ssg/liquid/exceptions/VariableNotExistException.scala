/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/exceptions/VariableNotExistException.java
 * Original: Copyright (c) 2012 Bart Kiers, 2022 Vasyl Khrystiuk
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: liqp.exceptions → ssg.liquid.exceptions
 *   Idiom: val parameter instead of getter
 *
 * Covenant: full-port
 * Covenant-java-reference: src/main/java/liqp/exceptions/VariableNotExistException.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: 1f0c47e87053f937fde5448cab0963f3379ce4a3
 */
package ssg
package liquid
package exceptions

class VariableNotExistException(val variableName: String) extends RuntimeException(s"Variable '$variableName' does not exist")
