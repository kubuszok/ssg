/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-dependency/src/main/java/com/vladsch/flexmark/util/dependency/FlatDependencies.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package util
package dependency

/** @deprecated
  *   use [[DependencyResolver.resolveFlatDependencies]]
  */
@deprecated("use DependencyResolver.resolveFlatDependencies", "")
class FlatDependencies[T](dependentStages: List[FlatDependencyStage[T]]) extends ResolvedDependencies[FlatDependencyStage[T]](dependentStages) {

  val dependencies: List[T] = dependentStages.flatMap(_.dependents)
}
