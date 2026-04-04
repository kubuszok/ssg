/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/nodes/LNode.java
 * Original: Copyright (c) 2012 Bart Kiers, 2022 Vasyl Khrystiuk
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: liqp.nodes → ssg.liquid.nodes
 *   Convention: Java interface → Scala trait
 */
package ssg
package liquid
package nodes

/** Denotes a node in the AST the parser creates from the input source. */
trait LNode {

  /** Renders this AST node.
    *
    * @param context
    *   the context (variables) with which this node should be rendered.
    * @return
    *   an object denoting the rendered AST.
    */
  def render(context: TemplateContext): Any
}
