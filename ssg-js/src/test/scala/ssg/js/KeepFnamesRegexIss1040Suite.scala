/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Differential test for ISS-1040: keep_fnames / keep_classnames regex form
 * was unreachable from the API because CompressorOptions typed them as Boolean.
 * After widening to Any (consistent with ManglerOptions), a Regex flows through
 * CompressorOptions.get("keep_fnames") -> DropUnused.keepName's Regex arm.
 *
 * Oracle: original-src/terser (vendored at upstream-commit 6080510)
 *   terser lib/utils/index.js:219-222  keep_name():
 *     return keep_setting === true || (keep_setting instanceof RegExp && keep_setting.test(name));
 *
 *   keep_fnames regex /^keep/:
 *     regex: "var a=function keepable(){return 1},b=function(){return 2};console.log(a(),b());"
 *     true:  "var a=function keepable(){return 1},b=function droppable(){return 2};console.log(a(),b());"
 *     false: "var a=function(){return 1},b=function(){return 2};console.log(a(),b());"
 *
 *   keep_classnames regex /^Keepable/:
 *     regex: "var A=class KeepableClass{constructor(){}},B=class{constructor(){}};new A,new B;"
 *     true:  "var A=class KeepableClass{constructor(){}},B=class DroppableClass{constructor(){}};new A,new B;"
 *     false: "var A=class{constructor(){}},B=class{constructor(){}};new A,new B;"
 */
package ssg
package js

import ssg.js.compress.CompressorOptions

final class KeepFnamesRegexIss1040Suite extends munit.FunSuite {

  override val munitTimeout = scala.concurrent.duration.Duration(30, "s")

  // -- keep_fnames with Regex --
  // Oracle: terser `{compress:{keep_fnames:/^keep/, unused:true}, mangle:false}`
  // on `var a = function keepable() { return 1; }; var b = function droppable() { return 2; }; console.log(a(), b());`
  // => "var a=function keepable(){return 1},b=function(){return 2};console.log(a(),b());"
  // i.e. `keepable` name retained (matches /^keep/), `droppable` name stripped (does not match).
  test("ISS-1040: keep_fnames regex retains only matching function names") {
    val src =
      "var a = function keepable() { return 1; }; var b = function droppable() { return 2; }; console.log(a(), b());"

    val withRegex =
      Terser.minifyToString(src, MinifyOptions(compress = CompressorOptions(keepFnames = "^keep".r, unused = true), mangle = false))
    val withTrue =
      Terser.minifyToString(src, MinifyOptions(compress = CompressorOptions(keepFnames = true, unused = true), mangle = false))
    val withFalse =
      Terser.minifyToString(src, MinifyOptions(compress = CompressorOptions(keepFnames = false, unused = true), mangle = false))

    // Oracle: regex keeps 'keepable', strips 'droppable'
    assert(
      withRegex.contains("function keepable()"),
      s"regex /^keep/ must retain 'keepable' name, got: $withRegex"
    )
    assert(
      !withRegex.contains("droppable"),
      s"regex /^keep/ must strip 'droppable' name, got: $withRegex"
    )

    // true keeps both names
    assert(
      withTrue.contains("function keepable()") && withTrue.contains("function droppable()"),
      s"keep_fnames=true must retain both names, got: $withTrue"
    )

    // false strips both names
    assert(
      !withFalse.contains("keepable") && !withFalse.contains("droppable"),
      s"keep_fnames=false must strip both names, got: $withFalse"
    )

    // Differential: regex output differs from both true and false
    assertNotEquals(withRegex, withTrue, "regex must differ from keep_fnames=true (which keeps both)")
    assertNotEquals(withRegex, withFalse, "regex must differ from keep_fnames=false (which strips both)")
  }

  // -- keep_classnames with Regex --
  // Oracle: terser `{compress:{keep_classnames:/^Keepable/, unused:true}, mangle:false}`
  // on `var A = class KeepableClass { constructor() {} }; var B = class DroppableClass { constructor() {} }; new A(); new B();`
  // => "var A=class KeepableClass{constructor(){}},B=class{constructor(){}};new A,new B;"
  // i.e. `KeepableClass` name retained (matches /^Keepable/), `DroppableClass` name stripped.
  test("ISS-1040: keep_classnames regex retains only matching class names") {
    val src =
      "var A = class KeepableClass { constructor() {} }; var B = class DroppableClass { constructor() {} }; new A(); new B();"

    val withRegex =
      Terser.minifyToString(src, MinifyOptions(compress = CompressorOptions(keepClassnames = "^Keepable".r, unused = true), mangle = false))
    val withTrue =
      Terser.minifyToString(src, MinifyOptions(compress = CompressorOptions(keepClassnames = true, unused = true), mangle = false))
    val withFalse =
      Terser.minifyToString(src, MinifyOptions(compress = CompressorOptions(keepClassnames = false, unused = true), mangle = false))

    // Oracle: regex keeps 'KeepableClass', strips 'DroppableClass'
    assert(
      withRegex.contains("class KeepableClass"),
      s"regex /^Keepable/ must retain 'KeepableClass' name, got: $withRegex"
    )
    assert(
      !withRegex.contains("DroppableClass"),
      s"regex /^Keepable/ must strip 'DroppableClass' name, got: $withRegex"
    )

    // true keeps both names
    assert(
      withTrue.contains("class KeepableClass") && withTrue.contains("class DroppableClass"),
      s"keep_classnames=true must retain both names, got: $withTrue"
    )

    // false strips both names
    assert(
      !withFalse.contains("KeepableClass") && !withFalse.contains("DroppableClass"),
      s"keep_classnames=false must strip both names, got: $withFalse"
    )

    // Differential: regex output differs from both true and false
    assertNotEquals(withRegex, withTrue, "regex must differ from keep_classnames=true (which keeps both)")
    assertNotEquals(withRegex, withFalse, "regex must differ from keep_classnames=false (which strips both)")
  }

  // Verify the Regex arm in DropUnused.keepName is actually reachable via compressor.option()
  test("ISS-1040: CompressorOptions.get returns Regex (not Boolean) when set") {
    val opts = CompressorOptions(keepFnames = "^keep".r, keepClassnames = "^Keep".r)
    val fnamesOpt = opts.get("keep_fnames")
    val classnamesOpt = opts.get("keep_classnames")

    assert(fnamesOpt.isInstanceOf[scala.util.matching.Regex], s"get(keep_fnames) must return Regex, got: ${fnamesOpt.getClass}")
    assert(classnamesOpt.isInstanceOf[scala.util.matching.Regex], s"get(keep_classnames) must return Regex, got: ${classnamesOpt.getClass}")
  }
}
