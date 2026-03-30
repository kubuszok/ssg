/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/formatter/NodeFormatterFactory.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package formatter

import ssg.md.util.data.DataHolder
import ssg.md.util.dependency.Dependent

/** Factory for instantiating new node renderers when rendering is done.
  */
trait NodeFormatterFactory extends Dependent {

  /** Create a new node renderer for the specified rendering context.
    *
    * @param options
    *   the context for rendering (normally passed on to the node renderer)
    * @return
    *   a node renderer
    */
  def create(options: DataHolder): NodeFormatter

  override def afterDependents: Nullable[Set[Class[?]]] = Nullable.empty

  override def beforeDependents: Nullable[Set[Class[?]]] = Nullable.empty

  override def affectsGlobalScope: Boolean = false
}
