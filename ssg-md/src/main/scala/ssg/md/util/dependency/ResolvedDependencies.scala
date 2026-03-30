/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-dependency/src/main/java/com/vladsch/flexmark/util/dependency/ResolvedDependencies.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
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
