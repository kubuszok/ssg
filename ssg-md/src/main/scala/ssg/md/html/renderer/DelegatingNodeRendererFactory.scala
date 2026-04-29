/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/html/renderer/DelegatingNodeRendererFactory.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/html/renderer/DelegatingNodeRendererFactory.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
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
