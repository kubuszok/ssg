/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-dependency/src/main/java/com/vladsch/flexmark/util/dependency/DependencyHandler.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package util
package dependency

import ssg.md.util.collection.iteration.ReversibleIndexedIterator

import java.util.BitSet
import scala.collection.mutable.ArrayBuffer
import scala.util.boundary
import scala.util.boundary.break

/** @deprecated
  *   use DependencyResolver instead
  */
@deprecated("use DependencyResolver instead", "")
abstract class DependencyHandler[D <: Dependent, S, R <: ResolvedDependencies[S]] {

  protected def createStage(dependents:            List[D]): S
  protected def getDependentClass(dependent:       D):       Class[?]
  protected def createResolvedDependencies(stages: List[S]): R

  def resolveDependencies(dependentsList: List[D]): R = {
    if (dependentsList.isEmpty) {
      createResolvedDependencies(Nil)
    } else if (dependentsList.size == 1) {
      val dependent = dependentsList.head
      createResolvedDependencies(List(createStage(List(dependent))))
    } else {
      // resolve dependencies and processing lists
      val dependentCount   = dependentsList.size
      val dependentItemMap = new DependentItemMap[D](dependentCount)

      for (dependent <- dependentsList) {
        val dependentClass = getDependentClass(dependent)
        if (dependentItemMap.containsKey(dependentClass)) {
          throw new IllegalStateException(s"Dependent class $dependentClass is duplicated. Only one instance can be present in the list")
        }
        val item = new DependentItem[D](dependentItemMap.size(), dependent, getDependentClass(dependent), dependent.affectsGlobalScope)
        dependentItemMap.put(dependentClass, item)
      }

      val entryIter = dependentItemMap.iterator()
      while (entryIter.hasNext) {
        val entry             = entryIter.next()
        val item              = entry.getValue
        val afterDependencies = item.dependent.afterDependents

        if (afterDependencies.isDefined && afterDependencies.get.nonEmpty) {
          for (depClass <- afterDependencies.get) {
            val dependentItem = dependentItemMap.get(depClass)
            if (dependentItem != null) {
              item.addDependency(dependentItem)
              dependentItem.addDependent(item)
            }
          }
        }

        val beforeDependents = item.dependent.beforeDependents
        if (beforeDependents.isDefined && beforeDependents.get.nonEmpty) {
          for (depClass <- beforeDependents.get) {
            val dependentItem = dependentItemMap.get(depClass)
            if (dependentItem != null) {
              dependentItem.addDependency(item)
              item.addDependent(dependentItem)
            }
          }
        }
      }

      val prioritizedMap = prioritize(dependentItemMap)
      val finalCount     = prioritizedMap.size()

      var newReady = new BitSet(finalCount)
      val iterator: ReversibleIndexedIterator[DependentItem[D]] = prioritizedMap.valueIterator()
      while (iterator.hasNext) {
        val item = iterator.next()
        if (!item.hasDependencies) {
          newReady.set(item.index)
        }
      }

      val dependents = new BitSet(finalCount)
      dependents.set(0, prioritizedMap.size())

      val dependencyStages = ArrayBuffer.empty[S]

      while (newReady.nextSetBit(0) != -1) {
        // process these independents in unspecified order since they do not have dependencies
        val stageDependents = ArrayBuffer.empty[D]
        val nextDependents  = new BitSet()

        // collect block processors ready for processing, any non-globals go into independents
        boundary {
          while (true) {
            val i = newReady.nextSetBit(0)
            if (i < 0) break()

            newReady.clear(i)
            val item = prioritizedMap.getValue(i)
            assert(item != null)

            stageDependents += item.dependent
            dependents.clear(i)

            // removeIndex it from dependent's dependencies
            if (item.hasDependents) {
              boundary {
                while (true) {
                  val j = item.dependents.get.nextSetBit(0)
                  if (j < 0) break()

                  item.dependents.get.clear(j)
                  val dependentItem = prioritizedMap.getValue(j)
                  assert(dependentItem != null)

                  if (!dependentItem.removeDependency(item)) {
                    if (item.isGlobalScope) {
                      nextDependents.set(j)
                    } else {
                      newReady.set(j)
                    }
                  }
                }
              }
            } else if (item.isGlobalScope) {
              // globals go in their own stage
              nextDependents.or(newReady)
              break()
            }
          }
        }

        // can process these in parallel since it will only contain non-globals or globals not dependent on other globals
        newReady = nextDependents
        dependencyStages += createStage(stageDependents.toList)
      }

      if (dependents.nextSetBit(0) != -1) {
        throw new IllegalStateException("have dependents with dependency cycles" + dependents)
      }

      createResolvedDependencies(dependencyStages.toList)
    }
  }

  protected def prioritize(dependentMap: DependentItemMap[D]): DependentItemMap[D] =
    dependentMap
}
