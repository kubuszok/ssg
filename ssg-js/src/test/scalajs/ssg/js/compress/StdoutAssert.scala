/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * JS-only expect_stdout test runner: minify code via Terser, execute the
 * minified output in a sandboxed JS Function with a capturing console.log,
 * and assert stdout matches the expected value.
 *
 * Mirrors terser/test/sandbox.js:44-80 — provides `id`, `leak`, `pass`,
 * `fail` globals plus a capturing `console` object.
 *
 * JS-only because executing minified JS requires a JS runtime.  On Scala.js
 * the test runtime IS Node (build.sbt NodeJSEnv), so js.Dynamic / Function
 * eval works with no new dependency.  JVM and Native have no JS engine. */
package ssg
package js
package compress

import scala.scalajs.js

import ssg.js.{ MinifyOptions, Terser }

object StdoutAssert {

  /** Execute `code` in a sandbox providing terser's id/leak helpers plus a capturing console.log; return captured stdout lines joined by newline.
    *
    * The sandbox mirrors terser/test/sandbox.js globals:
    *   - id(x) = identity (blocks constant folding)
    *   - leak() = no-op (prevents dead-code elimination)
    *   - console.log(args) = captures toString of each arg
    */
  def runStdout(code: String): String = {
    val sb = new StringBuilder
    val logFn: js.Function1[js.Any, Unit] = (arg: js.Any) => {
      sb.append(arg.toString).append("\n")
      ()
    }
    val consoleDyn = js.Dynamic.literal(log = logFn)
    val idFn:   js.Function1[js.Any, js.Any] = (x: js.Any) => x
    val leakFn: js.Function0[Unit]           = () => ()
    // new Function("id", "leak", "console", code)(idFn, leakFn, consoleDyn)
    val fn = js.Dynamic.newInstance(js.Dynamic.global.Function)(
      "id",
      "leak",
      "console",
      code
    )
    fn(idFn.asInstanceOf[js.Any], leakFn.asInstanceOf[js.Any], consoleDyn)
    sb.toString.trim
  }

  /** Minify `input` with `options`, execute the minified code in the sandbox, and assert stdout equals `expected`.
    */
  def assertStdout(
    input:    String,
    options:  MinifyOptions,
    expected: String
  )(using munit.Location): Unit = {
    val minified = Terser.minifyToString(input, options)
    val out      = runStdout(minified)
    munit.Assertions.assertEquals(out, expected)
  }
}
