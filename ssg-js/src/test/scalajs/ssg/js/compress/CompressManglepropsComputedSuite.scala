/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Property mangling tests for computed properties with keep_quoted.
 * Ported from: terser/test/compress/mangleprops-computed.js (24 test cases)
 *
 * All 24 tests use mangle.properties with computed_props + keep_quoted options
 * and verify output via terser's expect_stdout (minify -> execute in Node ->
 * assert stdout == "bar").  PropMangler IS integrated into Terser.minify()
 * (Terser.scala:492) and produces runtime-correct output.
 *
 * JS-only: executing the minified JS requires a JS runtime.  On Scala.js the
 * test runtime IS Node (build.sbt NodeJSEnv), so StdoutAssert uses
 * js.Dynamic/Function eval.  JVM and Native have no JS engine.
 *
 * Auto-ported by hand since gen-compress-tests.js does not support mangle format. */
package ssg
package js
package compress

import ssg.js.MinifyOptions
import ssg.js.scope.{ ManglerOptions, PropManglerOptions }

final class CompressManglepropsComputedSuite extends munit.FunSuite {

  override val munitTimeout = scala.concurrent.duration.Duration(30, "s")

  // -- Shared option builders -------------------------------------------------
  // terser fixture format: options = { defaults:false, reduce_vars:true,
  //   unused:true, toplevel:true, computed_props:<bool> }
  // mangle = { properties: { keep_quoted: true } }

  private def computedOpts(computedProps: Boolean): MinifyOptions =
    MinifyOptions(
      compress = CompressorOptions.NoDefaults.copy(
        reduceVars = true,
        unused = true,
        toplevel = ToplevelConfig(funcs = true, vars = true),
        computedProps = computedProps
      ),
      mangle = ManglerOptions(properties = PropManglerOptions(keepQuoted = true))
    )

  private val withComputed    = computedOpts(computedProps = true)
  private val withoutComputed = computedOpts(computedProps = false)

  // -- computed_props: true, keep_quoted: true (cases 1-10) -------------------
  // terser/test/compress/mangleprops-computed.js:1-22

  test("computed_props_keep_quoted_inlined_sub_1") {
    StdoutAssert.assertStdout(
      input = """|let prop = '_foo';
                 |let o = {
                 |    [prop]: 'bar'
                 |};
                 |console.log(o[id('_foo')]);""".stripMargin,
      options = withComputed,
      expected = "bar"
    )
  }

  // terser/test/compress/mangleprops-computed.js:24-45
  test("computed_props_keep_quoted_inlined_sub_2") {
    StdoutAssert.assertStdout(
      input = """|let prop = '_foo';
                 |let o = {
                 |    [prop]() { return 'bar' }
                 |};
                 |console.log(o[id('_foo')]());""".stripMargin,
      options = withComputed,
      expected = "bar"
    )
  }

  // terser/test/compress/mangleprops-computed.js:47-68
  test("computed_props_keep_quoted_inlined_sub_3") {
    StdoutAssert.assertStdout(
      input = """|let prop = '_foo';
                 |let o = {
                 |    get [prop]() { return 'bar' }
                 |};
                 |console.log(o[id('_foo')]);""".stripMargin,
      options = withComputed,
      expected = "bar"
    )
  }

  // terser/test/compress/mangleprops-computed.js:70-91
  test("computed_props_keep_quoted_inlined_sub_4") {
    StdoutAssert.assertStdout(
      input = """|let prop = '_foo';
                 |let o = class {
                 |    static [prop] = 'bar'
                 |};
                 |console.log(o[id('_foo')]);""".stripMargin,
      options = withComputed,
      expected = "bar"
    )
  }

  // terser/test/compress/mangleprops-computed.js:93-114
  test("computed_props_keep_quoted_inlined_sub_5") {
    StdoutAssert.assertStdout(
      input = """|let prop = '_foo';
                 |let o = class {
                 |    static [prop]() { return 'bar' }
                 |};
                 |console.log(o[id('_foo')]());""".stripMargin,
      options = withComputed,
      expected = "bar"
    )
  }

  // terser/test/compress/mangleprops-computed.js:116-137
  test("computed_props_keep_quoted_inlined_sub_6") {
    StdoutAssert.assertStdout(
      input = """|let prop = '_foo';
                 |let o = class {
                 |    static get [prop]() { return 'bar' }
                 |};
                 |console.log(o[id('_foo')]);""".stripMargin,
      options = withComputed,
      expected = "bar"
    )
  }

  // terser/test/compress/mangleprops-computed.js:139-160
  test("computed_props_keep_quoted_inlined_sub_7") {
    StdoutAssert.assertStdout(
      input = """|let prop = '_foo';
                 |let o = new class {
                 |    [prop] = 'bar'
                 |};
                 |console.log(o[id('_foo')]);""".stripMargin,
      options = withComputed,
      expected = "bar"
    )
  }

  // terser/test/compress/mangleprops-computed.js:162-183
  test("computed_props_keep_quoted_inlined_sub_8") {
    StdoutAssert.assertStdout(
      input = """|let prop = '_foo';
                 |let o = new class {
                 |    [prop]() { return 'bar' }
                 |};
                 |console.log(o[id('_foo')]());""".stripMargin,
      options = withComputed,
      expected = "bar"
    )
  }

  // terser/test/compress/mangleprops-computed.js:185-206
  test("computed_props_keep_quoted_inlined_sub_9") {
    StdoutAssert.assertStdout(
      input = """|let prop = '_foo';
                 |let o = new class {
                 |    get [prop]() { return 'bar' }
                 |};
                 |console.log(o[id('_foo')]);""".stripMargin,
      options = withComputed,
      expected = "bar"
    )
  }

  // terser/test/compress/mangleprops-computed.js:208-229
  test("computed_props_keep_quoted_inlined_sub_10") {
    StdoutAssert.assertStdout(
      input = """|let prop = '_foo';
                 |let {
                 |    [prop]: val
                 |} = { [id('_foo')]: 'bar' };
                 |console.log(val);""".stripMargin,
      options = withComputed,
      expected = "bar"
    )
  }

  // -- computed_props: false, keep_quoted: true (cases 11-20) -----------------
  // terser/test/compress/mangleprops-computed.js:231-252

  test("no_computed_props_keep_quoted_inlined_sub_1") {
    StdoutAssert.assertStdout(
      input = """|let prop = '_foo';
                 |let o = {
                 |    [prop]: 'bar'
                 |};
                 |console.log(o[id('_foo')]);""".stripMargin,
      options = withoutComputed,
      expected = "bar"
    )
  }

  // terser/test/compress/mangleprops-computed.js:254-275
  test("no_computed_props_keep_quoted_inlined_sub_2") {
    StdoutAssert.assertStdout(
      input = """|let prop = '_foo';
                 |let o = {
                 |    [prop]() { return 'bar' }
                 |};
                 |console.log(o[id('_foo')]());""".stripMargin,
      options = withoutComputed,
      expected = "bar"
    )
  }

  // terser/test/compress/mangleprops-computed.js:277-298
  test("no_computed_props_keep_quoted_inlined_sub_3") {
    StdoutAssert.assertStdout(
      input = """|let prop = '_foo';
                 |let o = {
                 |    get [prop]() { return 'bar' }
                 |};
                 |console.log(o[id('_foo')]);""".stripMargin,
      options = withoutComputed,
      expected = "bar"
    )
  }

  // terser/test/compress/mangleprops-computed.js:300-321
  test("no_computed_props_keep_quoted_inlined_sub_4") {
    StdoutAssert.assertStdout(
      input = """|let prop = '_foo';
                 |let o = class {
                 |    static [prop] = 'bar'
                 |};
                 |console.log(o[id('_foo')]);""".stripMargin,
      options = withoutComputed,
      expected = "bar"
    )
  }

  // terser/test/compress/mangleprops-computed.js:323-344
  test("no_computed_props_keep_quoted_inlined_sub_5") {
    StdoutAssert.assertStdout(
      input = """|let prop = '_foo';
                 |let o = class {
                 |    static [prop]() { return 'bar' }
                 |};
                 |console.log(o[id('_foo')]());""".stripMargin,
      options = withoutComputed,
      expected = "bar"
    )
  }

  // terser/test/compress/mangleprops-computed.js:346-367
  test("no_computed_props_keep_quoted_inlined_sub_6") {
    StdoutAssert.assertStdout(
      input = """|let prop = '_foo';
                 |let o = class {
                 |    static get [prop]() { return 'bar' }
                 |};
                 |console.log(o[id('_foo')]);""".stripMargin,
      options = withoutComputed,
      expected = "bar"
    )
  }

  // terser/test/compress/mangleprops-computed.js:369-390
  test("no_computed_props_keep_quoted_inlined_sub_7") {
    StdoutAssert.assertStdout(
      input = """|let prop = '_foo';
                 |let o = new class {
                 |    [prop] = 'bar'
                 |};
                 |console.log(o[id('_foo')]);""".stripMargin,
      options = withoutComputed,
      expected = "bar"
    )
  }

  // terser/test/compress/mangleprops-computed.js:392-413
  test("no_computed_props_keep_quoted_inlined_sub_8") {
    StdoutAssert.assertStdout(
      input = """|let prop = '_foo';
                 |let o = new class {
                 |    [prop]() { return 'bar' }
                 |};
                 |console.log(o[id('_foo')]());""".stripMargin,
      options = withoutComputed,
      expected = "bar"
    )
  }

  // terser/test/compress/mangleprops-computed.js:415-436
  test("no_computed_props_keep_quoted_inlined_sub_9") {
    StdoutAssert.assertStdout(
      input = """|let prop = '_foo';
                 |let o = new class {
                 |    get [prop]() { return 'bar' }
                 |};
                 |console.log(o[id('_foo')]);""".stripMargin,
      options = withoutComputed,
      expected = "bar"
    )
  }

  // terser/test/compress/mangleprops-computed.js:438-459
  test("no_computed_props_keep_quoted_inlined_sub_10") {
    StdoutAssert.assertStdout(
      input = """|let prop = '_foo';
                 |let {
                 |    [prop]: val
                 |} = { [id('_foo')]: 'bar' };
                 |console.log(val);""".stripMargin,
      options = withoutComputed,
      expected = "bar"
    )
  }

  // -- property access sub variants (cases 21-24) -----------------------------
  // These use a different input shape: the OBJECT literal has [id('foo_')]
  // and the ACCESS uses the variable `prop`.

  // terser/test/compress/mangleprops-computed.js:461-482
  test("no_computed_props_keep_quoted_property_access_sub") {
    StdoutAssert.assertStdout(
      input = """|let prop = 'foo_';
                 |let o = {
                 |    [id('foo_')]: 'bar'
                 |};
                 |console.log(o[prop]);""".stripMargin,
      options = withoutComputed,
      expected = "bar"
    )
  }

  // terser/test/compress/mangleprops-computed.js:484-505
  test("computed_props_keep_quoted_property_access_sub") {
    StdoutAssert.assertStdout(
      input = """|let prop = 'foo_';
                 |let o = {
                 |    [id('foo_')]: 'bar'
                 |};
                 |console.log(o[prop]);""".stripMargin,
      options = withComputed,
      expected = "bar"
    )
  }

  // terser/test/compress/mangleprops-computed.js:507-528
  test("no_computed_props_keep_quoted_optional_property_access_sub") {
    StdoutAssert.assertStdout(
      input = """|let prop = 'foo_';
                 |let o = {
                 |    [id('foo_')]: 'bar'
                 |};
                 |console.log(o?.[prop]);""".stripMargin,
      options = withoutComputed,
      expected = "bar"
    )
  }

  // terser/test/compress/mangleprops-computed.js:530-551
  test("computed_props_keep_quoted_optional_property_access_sub") {
    StdoutAssert.assertStdout(
      input = """|let prop = 'foo_';
                 |let o = {
                 |    [id('foo_')]: 'bar'
                 |};
                 |console.log(o?.[prop]);""".stripMargin,
      options = withComputed,
      expected = "bar"
    )
  }
}
