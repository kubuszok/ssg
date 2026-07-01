/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package highlight

import scala.scalajs.js

object QueryLoaderPlatform {

  private val fs      = js.Dynamic.global.require("fs")
  private val pathMod = js.Dynamic.global.require("path")

  // ISS-1118: at-most-once guard for the env-unset diagnostic
  private[highlight] var warnedMissingEnv: Boolean = false

  /** Checks whether either `TREE_SITTER_QUERIES_DIR` or `TREE_SITTER_WASM_DIR` is set in the process environment.
    */
  private def isEnvConfigured: Boolean = {
    val qDir = js.Dynamic.global.process.env.TREE_SITTER_QUERIES_DIR
    val wDir = js.Dynamic.global.process.env.TREE_SITTER_WASM_DIR
    !js.isUndefined(qDir) || !js.isUndefined(wDir)
  }

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
    val dir      = queriesDir
    val filePath = pathMod.join(dir, queryDir, "highlights.scm").asInstanceOf[String]
    if (!fs.existsSync(filePath).asInstanceOf[Boolean]) {
      // ISS-1118: emit a diagnostic when neither TREE_SITTER_QUERIES_DIR nor
      // TREE_SITTER_WASM_DIR is set and the query file is missing, so the
      // misconfiguration is visible instead of silently degrading to
      // no-highlighting. Logged at-most-once to avoid per-grammar spam.
      if (!isEnvConfigured && !warnedMissingEnv) {
        warnedMissingEnv = true
        js.Dynamic.global.console.error(
          "[ssg-highlight] Neither TREE_SITTER_QUERIES_DIR nor TREE_SITTER_WASM_DIR is set. " +
            s"Falling back to cwd-relative directory '$dir' but query file not found at: $filePath. " +
            "Set one of these environment variables to the directory containing your tree-sitter query files."
        )
      }
      None
    } else Some(fs.readFileSync(filePath, "utf-8").asInstanceOf[String])
  }
}
