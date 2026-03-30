/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-dependency/src/main/java/com/vladsch/flexmark/util/dependency/DependentItem.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package util
package dependency

import ssg.md.Nullable

import java.util.BitSet

class DependentItem[D](
  val index:          Int,
  val dependent:      D,
  val dependentClass: Class[?],
  val isGlobalScope:  Boolean
) {
  var dependencies: Nullable[BitSet] = Nullable.empty
  var dependents:   Nullable[BitSet] = Nullable.empty

  def addDependency(dependency: DependentItem[D]): Unit = {
    if (dependencies.isEmpty) dependencies = Nullable(new BitSet())
    dependencies.get.set(dependency.index)
  }

  def addDependency(deps: BitSet): Unit = {
    if (dependencies.isEmpty) dependencies = Nullable(new BitSet())
    dependencies.get.or(deps)
  }

  def removeDependency(dependency: DependentItem[D]): Boolean = {
    if (dependencies.isDefined) {
      dependencies.get.clear(dependency.index)
    }
    hasDependencies
  }

  def removeDependency(deps: BitSet): Boolean = {
    if (dependencies.isDefined) {
      dependencies.get.andNot(deps)
    }
    hasDependencies
  }

  def addDependent(dep: DependentItem[D]): Unit = {
    if (dependents.isEmpty) dependents = Nullable(new BitSet())
    dependents.get.set(dep.index)
  }

  def addDependent(deps: BitSet): Unit = {
    if (dependents.isEmpty) dependents = Nullable(new BitSet())
    dependents.get.or(deps)
  }

  def removeDependent(dep: DependentItem[D]): Unit =
    if (dependents.isDefined) {
      dependents.get.clear(dep.index)
    }

  def removeDependent(deps: BitSet): Unit =
    if (dependents.isDefined) {
      dependents.get.andNot(deps)
    }

  def hasDependencies: Boolean =
    dependencies.isDefined && dependencies.get.nextSetBit(0) != -1

  def hasDependents: Boolean =
    dependents.isDefined && dependents.get.nextSetBit(0) != -1
}
