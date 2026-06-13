/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Differential test for ISS-1044: property mangling must be reachable from the
 * public minify API. Before the fix, the ~700 LOC PropMangler was implemented
 * but dead — `ManglerOptions` had no `false | true | {object}` `properties`
 * field wired to `PropMangler.mangleProperties`, and `Terser.minify` never
 * invoked it.
 *
 * Upstream semantics (lib/minify.js):
 *   - minify.js:170 defaults `mangle.properties` to `false`.
 *   - minify.js:175-178 normalizes a truthy non-object value to `{}` (here: the
 *     Boolean `true` resolves to a default PropManglerOptions via
 *     ManglerOptions.resolveProperties).
 *   - minify.js:278-280 — `if (options.mangle && options.mangle.properties)
 *     toplevel = mangle_properties(toplevel, options.mangle.properties, ...)`,
 *     run AFTER mangle_names (minify.js:274).
 *
 * Oracle (C11): the vendored original terser at original-src/terser, version
 * 5.46.1 (package.json:7), executed with node:
 *
 *   cd original-src/terser && node --input-type=module -e "
 *   import { minify } from './main.js';
 *   const r = await minify(CODE, {compress:false, mangle:{properties:true}});
 *   console.log(JSON.stringify(r.code));"
 *
 * Expected outputs recorded from that run (2026-06-13), with `compress:false`
 * so the only transform is mangling. A single non-builtin property keeps the
 * byte-match independent of base54 frequency tie-breaking. Top-level vars are
 * not mangled by default, so `obj` is preserved; terser's default property
 * mangler renames the lone non-builtin property `secretProp` to `o`:
 *
 *   CODE = "var obj={secretProp:42};console.log(obj.secretProp);"
 *
 *   a) {compress:false, mangle:{properties:true}}
 *        → "var obj={o:42};console.log(obj.o);"              (property renamed)
 *   b) {compress:false, mangle:true}            (properties off — terser default)
 *        → "var obj={secretProp:42};console.log(obj.secretProp);"  (preserved)
 */
package ssg
package js

import ssg.js.scope.{ ManglerOptions, PropManglerOptions }

final class ManglePropertiesIss1044Suite extends munit.FunSuite {

  override val munitTimeout = scala.concurrent.duration.Duration(30, "s")

  private val code = "var obj={secretProp:42};console.log(obj.secretProp);"

  test("ISS-1044 (a): mangle.properties=true renames the property (oracle byte-match)") {
    val opts = MinifyOptions(compress = false, mangle = ManglerOptions(properties = true))
    val out  = Terser.minifyToString(code, opts)
    assertEquals(out, "var obj={o:42};console.log(obj.o);", "must match terser 5.46.1 mangle:{properties:true}")
  }

  test("ISS-1044 (a'): explicit PropManglerOptions also renames the property (oracle byte-match)") {
    val opts = MinifyOptions(compress = false, mangle = ManglerOptions(properties = PropManglerOptions()))
    val out  = Terser.minifyToString(code, opts)
    assertEquals(out, "var obj={o:42};console.log(obj.o);", "explicit PropManglerOptions must mangle the property")
  }

  test("ISS-1044 (b): default mangle (properties off) preserves the property name (oracle byte-match)") {
    val opts = MinifyOptions(compress = false, mangle = ManglerOptions())
    val out  = Terser.minifyToString(code, opts)
    assertEquals(
      out,
      "var obj={secretProp:42};console.log(obj.secretProp);",
      "default mangle must NOT rename properties (terser properties:false default)"
    )
  }
}
