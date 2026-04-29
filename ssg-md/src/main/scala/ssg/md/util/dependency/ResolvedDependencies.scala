/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-dependency/src/main/java/com/vladsch/flexmark/util/dependency/ResolvedDependencies.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-dependency/src/main/java/com/vladsch/flexmark/util/dependency/ResolvedDependencies.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package util
package dependency

/** @deprecated
  *   use [[DependencyResolver.resolveDependencies]]
  */
@deprecated("use DependencyResolver.resolveDependencies", "")
class ResolvedDependencies[T](val dependentStages: List[T]) {
  def isEmpty: Boolean = dependentStages.isEmpty
}
