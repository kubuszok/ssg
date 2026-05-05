/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package highlight

import scala.scalajs.js

object QueryLoaderPlatform {

  private val fs      = js.Dynamic.global.require("fs")
  private val pathMod = js.Dynamic.global.require("path")

  private def queriesDir: String = {
    val qDir = js.Dynamic.global.process.env.TREE_SITTER_QUERIES_DIR
    if (!js.isUndefined(qDir)) qDir.asInstanceOf[String]
    else {
      val wDir = js.Dynamic.global.process.env.TREE_SITTER_WASM_DIR
      if (!js.isUndefined(wDir)) pathMod.join(wDir, "queries").asInstanceOf[String]
      else "queries"
    }
  }

  def loadHighlightQuery(queryDir: String): Option[String] = {
    val filePath = pathMod.join(queriesDir, queryDir, "highlights.scm").asInstanceOf[String]
    if (!fs.existsSync(filePath).asInstanceOf[Boolean]) None
    else Some(fs.readFileSync(filePath, "utf-8").asInstanceOf[String])
  }
}
