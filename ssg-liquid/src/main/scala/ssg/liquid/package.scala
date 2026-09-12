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

  extension (t: Template) {
    def render(vars: java.util.Map[String, ?]): String = {
      import scala.jdk.CollectionConverters.*
      t.render(vars.asScala.asInstanceOf[scala.collection.mutable.Map[String, Object]])
    }
    def renderToObject(vars: java.util.Map[String, ?]): Object = {
      import scala.jdk.CollectionConverters.*
      t.renderToObject(vars.asScala.asInstanceOf[scala.collection.mutable.Map[String, Object]])
    }
    def renderUnguarded(vars: java.util.Map[String, ?]): String = {
      import scala.jdk.CollectionConverters.*
      t.renderUnguarded(vars.asScala.asInstanceOf[scala.collection.mutable.Map[String, Object]])
    }
    def renderToObjectUnguarded(vars: java.util.Map[String, ?]): Object = {
      import scala.jdk.CollectionConverters.*
      t.renderToObjectUnguarded(vars.asScala.asInstanceOf[scala.collection.mutable.Map[String, Object]])
    }
  }
}
