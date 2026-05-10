/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/dagre-wrapper/shapes/util.ts (shape dispatch)
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces switch-based shape dispatch with immutable registry map
 *   Idiom: Function type aliases; HashMap-based dispatch; no reflection
 *   Renames: shapes/util.ts shape dispatch → ShapeRegistry
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package render
package shapes

import ssg.commons.Nullable
import ssg.mermaid.svg.SvgBuilder

/** Registry that maps shape names to their render functions.
  *
  * Mermaid uses string-based shape identifiers (e.g. "rect", "circle", "diamond") from the parser output to select the appropriate rendering function. This registry provides a central dispatch point
  * for all known shapes.
  *
  * New shapes can be registered at runtime using [[register]]. The built-in shapes are pre-registered when the registry is initialized.
  */
object ShapeRegistry {

  /** Type alias for shape render functions.
    *
    * Each shape function takes a parent SVG builder and a shape configuration, and returns a [[ShapeResult]] containing the rendered group and an intersection function.
    */
  type ShapeRenderFn = (SvgBuilder, ShapeConfig) => ShapeResult

  /** Mutable registry of shape names to render functions. */
  private val _shapes = scala.collection.mutable.HashMap.empty[String, ShapeRenderFn]

  // Register built-in shapes
  register("rect", RectShape.render)
  register("rectangle", RectShape.render)
  register("roundedRect", RoundedRectShape.render)
  register("rounded_rect", RoundedRectShape.render)
  register("circle", CircleShape.render)
  register("ellipse", EllipseShape.render)
  register("diamond", DiamondShape.render)
  register("question", DiamondShape.render)
  register("rhombus", DiamondShape.render)
  register("hexagon", HexagonShape.render)
  register("stadium", StadiumShape.render)
  register("pill", StadiumShape.render)
  register("cylinder", CylinderShape.render)
  register("database", CylinderShape.render)
  register("doublecircle", DoubleCircleShape.render)
  register("double_circle", DoubleCircleShape.render)
  register("note", NoteShape.render)
  register("subroutine", SubroutineShape.render)
  register("trapezoid", TrapezoidShape.render)

  /** Registers a shape render function under the given name.
    *
    * If a shape with this name already exists, it is replaced.
    *
    * @param name
    *   the shape identifier (used in diagram definitions)
    * @param renderFn
    *   the function that renders this shape
    */
  def register(name: String, renderFn: ShapeRenderFn): Unit =
    _shapes(name) = renderFn

  /** Looks up the render function for a shape name.
    *
    * @param name
    *   the shape identifier
    * @return
    *   the render function, or empty if not registered
    */
  def get(name: String): Nullable[ShapeRenderFn] =
    _shapes.get(name) match {
      case Some(fn) => Nullable(fn)
      case None     => Nullable.empty
    }

  /** Renders a shape by name.
    *
    * Looks up the shape in the registry and invokes its render function. If the shape is not found, falls back to rendering a rectangle.
    *
    * @param parent
    *   the parent SVG builder to append to
    * @param shapeName
    *   the shape identifier
    * @param config
    *   shape configuration with position, size, and style
    * @return
    *   shape result with the rendered group and an intersection function
    */
  def render(parent: SvgBuilder, shapeName: String, config: ShapeConfig): ShapeResult = {
    val renderFn = get(shapeName).getOrElse(RectShape.render)
    renderFn(parent, config)
  }

  /** Returns the set of all registered shape names. */
  def registeredNames: Set[String] = _shapes.keySet.toSet

  /** Returns true if a shape with the given name is registered. */
  def isRegistered(name: String): Boolean = _shapes.contains(name)
}
