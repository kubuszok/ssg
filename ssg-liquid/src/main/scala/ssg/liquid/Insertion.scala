/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/main/java/liqp/Insertion.java
 * Original: Copyright (c) 2012 Bart Kiers, 2022 Vasyl Khrystiuk
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: liqp → ssg.liquid
 *   Convention: Abstract class extending LValue
 */
package ssg
package liquid

import ssg.liquid.nodes.LNode

import java.util.Locale

/** Base class for Tags and Blocks — the "insertions" in a Liquid template.
  *
  * Each insertion has a name (e.g., "if", "for", "assign") and renders itself given a TemplateContext and its parsed child nodes.
  */
abstract class Insertion(_name: String) extends LValue {

  /** Constructor that derives the name from the class's simple name, lowercased. */
  def this() = {
    this(null)
  }

  /** The name of this insertion. */
  val name: String = if (_name != null) _name else getClass.getSimpleName.toLowerCase(Locale.ENGLISH)

  /** Renders this insertion with the given context and child nodes. */
  def render(context: TemplateContext, nodes: Array[LNode]): Any
}
