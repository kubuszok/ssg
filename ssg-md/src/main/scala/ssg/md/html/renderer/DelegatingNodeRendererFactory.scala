/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/html/renderer/DelegatingNodeRendererFactory.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package html
package renderer

/** Factory for instantiating new node renderers when rendering is done.
  */
trait DelegatingNodeRendererFactory extends NodeRendererFactory {

  /** List of renderer factories to which this factory's renderer may delegate rendering
    *
    * @return
    *   list of renderer factories
    */
  def getDelegates: Nullable[Set[Class[?]]]
}
