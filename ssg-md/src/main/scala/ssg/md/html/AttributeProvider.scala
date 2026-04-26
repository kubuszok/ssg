/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/html/AttributeProvider.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/html/AttributeProvider.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package html

import ssg.md.html.renderer.AttributablePart
import ssg.md.util.ast.Node
import ssg.md.util.html.MutableAttributes

/** Extension point for adding/changing attributes on the primary HTML tag for a node.
  */
trait AttributeProvider {

  /** Set the attributes for the node by modifying the provided map.
    *
    * This allows to change or even removeIndex default attributes. With great power comes great responsibility.
    *
    * The attribute key and values will be escaped (preserving character entities), so don't escape them here, otherwise they will be double-escaped.
    *
    * Also used to get the id attribute for the node. Specifically for heading nodes. When the part parameter is AttributablePart.ID only need to check and provide an id attribute.
    *
    * When part is AttributablePart.LINK then attributes are being requested for a Link or Image link, link status after link resolution will be found under the Attribute.LINK_STATUS. Core defines
    * LinkStatus.UNKNOWN,LinkStatus.VALID,LinkStatus.NOT_FOUND. Extensions can define more.
    *
    * AttributablePart.NODE is a generic placeholder when the node did not provide a specific part for attribution.
    *
    * @param node
    *   the node to set attributes for
    * @param part
    *   attributes for the specific part of the node being generated, Core defines AttributablePart.LINK, AttributablePart.ID and generic AttributablePart.NODE, extensions are free to define more
    * @param attributes
    *   the attributes, with any default attributes already set in the map
    */
  def setAttributes(node: Node, part: AttributablePart, attributes: MutableAttributes): Unit
}
