/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/html/DelegatingNodeRendererFactoryWrapper.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package html

import ssg.md.html.renderer.{ DelegatingNodeRendererFactory, NodeRenderer, NodeRendererFactory }
import ssg.md.util.data.DataHolder
import ssg.md.util.dependency.Dependent

import scala.collection.mutable
import scala.language.implicitConversions

/** Factory for instantiating new node renderers with dependencies
  */
private[html] class DelegatingNodeRendererFactoryWrapper(
  private var nodeRenderers:       Nullable[List[DelegatingNodeRendererFactoryWrapper]],
  private val nodeRendererFactory: NodeRendererFactory
) extends (DataHolder => NodeRenderer)
    with Dependent
    with DelegatingNodeRendererFactory {

  private var myDelegates: Nullable[Set[Class[?]]] = Nullable.empty

  override def apply(options: DataHolder): NodeRenderer =
    nodeRendererFactory.apply(options)

  def factory: NodeRendererFactory = nodeRendererFactory

  override def getDelegates: Nullable[Set[Class[?]]] =
    nodeRendererFactory match {
      case dnrf: DelegatingNodeRendererFactory => dnrf.getDelegates
      case _ => Nullable.empty
    }

  override def afterDependents: Nullable[Set[Class[?]]] = Nullable.empty

  override def beforeDependents: Nullable[Set[Class[?]]] = {
    if (myDelegates.isEmpty && nodeRenderers.isDefined) {
      val delegates = getDelegates
      delegates.foreach { delegateSet =>
        val result = mutable.HashSet[Class[?]]()
        nodeRenderers.foreach { renderers =>
          for (factory <- renderers)
            if (delegateSet.contains(factory.factory.getClass)) {
              result.add(factory.factory.getClass)
            }
        }
        myDelegates = Nullable(result.toSet)
      }
      // release reference
      nodeRenderers = Nullable.empty
    }
    myDelegates
  }

  override def affectsGlobalScope: Boolean = false
}
