/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/parser/internal/PostProcessorManager.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/parser/internal/PostProcessorManager.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package parser
package internal

import ssg.md.parser.PostProcessorFactory
import ssg.md.util.ast.{ ClassifyingNodeTracker, Document, Node, NodeClassifierVisitor }
import ssg.md.util.data.DataHolder
import ssg.md.util.dependency.{ DependencyResolver, DependentItemMap }

import scala.collection.mutable
import scala.jdk.CollectionConverters.*
import java.util.BitSet

import scala.language.implicitConversions

class PostProcessorManager(
  val postProcessorDependencies: List[PostProcessorManager.PostProcessorDependencyStage]
) {

  // allPostProcessNodes: reserved for future use in post-processing pipeline

  def postProcess(document: Document): Document = {
    // first initialize node tracker
    var doc = document
    var classifyingNodeTracker: Nullable[ClassifyingNodeTracker] = Nullable.empty

    for (stage <- postProcessorDependencies) {
      // idiosyncrasy of post processors the last dependency can be global, in which case it processes
      // the whole document and no ancestry info is provided
      var hadGlobal = false
      for (dependent <- stage.dependents)
        if (dependent.affectsGlobalScope) {
          doc = dependent.apply(doc).processDocument(doc)
          hadGlobal = true
          // assume it no longer reflects reality
          classifyingNodeTracker = Nullable.empty
        } else {
          assert(!hadGlobal)

          if (classifyingNodeTracker.isEmpty) {
            // build the node type information by traversing the document tree
            classifyingNodeTracker = Nullable(NodeClassifierVisitor(stage.myNodeMap.map((k, v) => k -> v.asJava).asJava).classify(doc))
          }

          val tracker            = classifyingNodeTracker.get
          val dependentNodeTypes = dependent.getNodeTypes
          val postProcessor      = dependent.apply(doc)
          val exclusionSet       = new BitSet()

          if (dependentNodeTypes.isDefined) {
            val nodeTypes = dependentNodeTypes.get
            for (excluded <- nodeTypes.values) {
              val mapped = tracker.exclusionSet.indexBitSet(excluded.asJava)
              exclusionSet.or(mapped)
            }

            val nodes = tracker.getCategoryItems(classOf[Node], nodeTypes.keySet.asJava)
            for (node <- nodes.asScala)
              if (node.parent.isDefined) { // was not already removed
                // now we need to get the bitset for the excluded ancestors of the node,
                // then intersect it with the actual ancestors of this factory
                val excluded = nodeTypes.get(node.getClass)
                excluded.foreach { excl =>
                  val index = tracker.items.indexOf(node)
                  if (index != -1) {
                    val nodeAncestors: Nullable[BitSet] = Nullable(tracker.nodeAncestryMap.get(index))
                    nodeAncestors.foreach { ancestors =>
                      val nodeExclusions = tracker.exclusionSet.indexBitSet(excl.asJava)
                      nodeExclusions.and(ancestors)
                      if (nodeExclusions.isEmpty) {
                        postProcessor.process(tracker, node)
                      }
                      // else: has excluded ancestor, skip
                    }
                    if (nodeAncestors.isEmpty) {
                      postProcessor.process(tracker, node)
                    }
                  }
                }
                if (excluded.isEmpty) {
                  postProcessor.process(tracker, node)
                }
              }
          }
        }
    }

    doc
  }
}

object PostProcessorManager {

  def calculatePostProcessors(
    options:                DataHolder,
    postProcessorFactories: List[PostProcessorFactory]
  ): List[PostProcessorDependencyStage] = {
    val resolveDependencies = DependencyResolver.resolveDependencies(
      postProcessorFactories,
      prioritizePostProcessors,
      Nullable.empty
    )
    resolveDependencies.map(dependencies => PostProcessorDependencyStage(dependencies))
  }

  def processDocument(document: Document, processorDependencies: List[PostProcessorDependencyStage]): Document =
    if (processorDependencies.nonEmpty) {
      val manager = PostProcessorManager(processorDependencies)
      manager.postProcess(document)
    } else {
      document
    }

  private def prioritizePostProcessors(
    dependentMap: DependentItemMap[PostProcessorFactory]
  ): DependentItemMap[PostProcessorFactory] = {
    // put globals last
    val prioritized = dependentMap.entries()
    prioritized.sort { (e1, e2) =>
      val g1 = if (e1.getValue.isGlobalScope) 1 else 0
      val g2 = if (e2.getValue.isGlobalScope) 1 else 0
      g1 - g2
    }

    val dependentMapSet = dependentMap.keySet().keyDifferenceBitSet(prioritized)
    if (dependentMapSet.isEmpty) {
      dependentMap
    } else {
      val prioritizedMap = DependentItemMap[PostProcessorFactory](prioritized.size)
      prioritizedMap.addAll(prioritized)
      prioritizedMap
    }
  }

  class PostProcessorDependencyStage(val dependents: List[PostProcessorFactory]) {

    val myNodeMap: Map[Class[? <: Node], Set[Class[?]]] = {
      val nodeMap = mutable.HashMap[Class[? <: Node], Set[Class[?]]]()

      for (dependent <- dependents) {
        val types = dependent.getNodeTypes
        if ((types.isEmpty || types.exists(_.isEmpty)) && !dependent.affectsGlobalScope) {
          throw IllegalStateException(
            s"PostProcessorFactory $dependent is not document post processor and has empty node map, does nothing, should not be registered."
          )
        }

        types.foreach { typeMap =>
          for ((key, value) <- typeMap)
            if (classOf[Node].isAssignableFrom(key)) {
              val nodeKey = key.asInstanceOf[Class[? <: Node]]
              nodeMap.get(nodeKey) match {
                case Some(existing) =>
                  nodeMap.put(nodeKey, existing ++ value)
                case scala.None =>
                  // copy so it is not modified by additional dependencies injecting other exclusions by mistake
                  nodeMap.put(nodeKey, Set.from(value))
              }
            }
        }
      }

      nodeMap.toMap
    }
  }
}
