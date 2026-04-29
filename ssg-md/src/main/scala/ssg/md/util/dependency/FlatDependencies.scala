/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-dependency/src/main/java/com/vladsch/flexmark/util/dependency/FlatDependencies.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-dependency/src/main/java/com/vladsch/flexmark/util/dependency/FlatDependencies.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
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
