/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/exceptions/ExceededMaxIterationsException.java
 * Original: Copyright (c) 2012 Bart Kiers, 2022 Vasyl Khrystiuk
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: liqp.exceptions → ssg.liquid.exceptions
 *
 * Covenant: full-port
 * Covenant-java-reference: src/main/java/liqp/exceptions/ExceededMaxIterationsException.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: 1f0c47e87053f937fde5448cab0963f3379ce4a3
 */
package ssg
package liquid
package exceptions

class ExceededMaxIterationsException(maxIterations: Int) extends RuntimeException("exceeded maxIterations: " + maxIterations)
