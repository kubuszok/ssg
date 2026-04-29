/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-dependency/src/main/java/com/vladsch/flexmark/util/dependency/FlatDependencyHandler.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-dependency/src/main/java/com/vladsch/flexmark/util/dependency/FlatDependencyHandler.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package util
package dependency

/** @deprecated
  *   use [[DependencyResolver.resolveFlatDependencies]] use null for functions if no need for sort or class extractor
  */
@deprecated("use DependencyResolver.resolveFlatDependencies, use null for functions if no need for sort or class extractor", "")
class FlatDependencyHandler[T <: Dependent] extends DependencyHandler[T, FlatDependencyStage[T], FlatDependencies[T]] {

  def resolvedDependencies(dependentsList: List[T]): List[T] = {
    val dependencies = resolveDependencies(dependentsList)
    dependencies.dependencies
  }

  override protected def createStage(dependents: List[T]): FlatDependencyStage[T] =
    new FlatDependencyStage[T](dependents)

  override protected def getDependentClass(dependent: T): Class[?] =
    dependent.getClass

  override protected def createResolvedDependencies(stages: List[FlatDependencyStage[T]]): FlatDependencies[T] =
    new FlatDependencies[T](stages)
}

object FlatDependencyHandler {
  def computeDependencies[T <: Dependent](dependentsList: List[T]): List[T] = {
    val resolver = new FlatDependencyHandler[T]()
    resolver.resolvedDependencies(dependentsList)
  }
}
