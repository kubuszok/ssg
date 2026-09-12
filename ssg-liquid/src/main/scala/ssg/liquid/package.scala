/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Liquid template engine — Scala 3 port of liqp.
 *
 * Covenant: full-port
 * Covenant-verified: 2026-04-26
 */
package ssg

package object liquid {

  /** SSG project version (snapshot). */
  val Version = "0.1.0-SNAPSHOT"

  extension (t: Template.type) def parse(input: String): Template = TemplateParser.DEFAULT.parse(input)

  extension (tp: TemplateParser) {
    def parse(input: String, sourcePath: java.nio.file.Path): Template =
      tp.parse(org.antlr.v4.runtime.CharStreams.fromString(input, sourcePath.toString))
    def parse(input: String, sourcePath: ssg.commons.io.FilePath): Template =
      tp.parse(org.antlr.v4.runtime.CharStreams.fromString(input, sourcePath.pathString))
  }

  extension (t: Template) {
    def withJailRoot(root: ssg.commons.io.FilePath): Template = t
    def render(vars: java.util.Map[String, ?]):      String   =
      t.render(unwrapDataViewMap(vars))
    def renderToObject(vars: java.util.Map[String, ?]): Object =
      t.renderToObject(unwrapDataViewMap(vars))
    def renderUnguarded(vars: java.util.Map[String, ?]): String =
      t.renderUnguarded(unwrapDataViewMap(vars))
    def renderToObjectUnguarded(vars: java.util.Map[String, ?]): Object =
      t.renderToObjectUnguarded(unwrapDataViewMap(vars))
  }

  private def unwrapDataViewMap(vars: java.util.Map[String, ?]): scala.collection.mutable.Map[String, Object] = {
    val result = new scala.collection.mutable.HashMap[String, Object]()
    vars.forEach { (k, v) =>
      result.put(k, unwrapValue(v.asInstanceOf[Object]))
    }
    result
  }

  private def unwrapValue(v: Object): Object = v match {
    case dv: ssg.data.DataView =>
      dv.view match {
        case null => null
        case b:   Boolean                             => java.lang.Boolean.valueOf(b)
        case s:   Short                               => java.lang.Short.valueOf(s)
        case i:   Int                                 => java.lang.Integer.valueOf(i)
        case l:   Long                                => java.lang.Long.valueOf(l)
        case f:   Float                               => java.lang.Float.valueOf(f)
        case d:   Double                              => java.lang.Double.valueOf(d)
        case s:   String                              => s
        case bd:  java.math.BigDecimal                => bd
        case ta:  java.time.temporal.TemporalAccessor => ta
        case vec: Vector[?]                           =>
          val list = new java.util.ArrayList[Object](vec.size)
          vec.foreach(elem => list.add(unwrapValue(elem.asInstanceOf[Object])))
          list
        case map: scala.collection.immutable.VectorMap[?, ?] =>
          val jmap = new java.util.LinkedHashMap[String, Object](map.size)
          map.foreach((k, v) => jmap.put(k.asInstanceOf[String], unwrapValue(v.asInstanceOf[Object])))
          jmap
      }
    case _ => v
  }
}
