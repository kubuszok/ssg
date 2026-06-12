/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Red tests for ISS-1171: tagged templates on a parenthesized callee are broken
 * two ways — pure print drops template content, compress{} throws
 * ClassCastException AstString→AstTemplateString. Probing shows BOTH defects are
 * independent of the parenthesized callee: a plain `tag` prefix (the issue's
 * "likely control") is equally broken in both modes, so the breakage class is
 * "every tagged template whose final raw segment is non-empty" (print) and
 * "every substitution-free tagged template" (compress crash).
 *
 * Root-cause findings (2026-06-12, more-improvements 45f9fc7a + this suite) —
 * NEITHER defect lives in OutputStream.scala, where the issue was filed:
 *
 * 1. PRINT defect — Tokenizer/AstToken, not OutputStream. The print path is a
 *    faithful port: OutputStream.scala:1280 prints `seg.raw` for tagged segments
 *    exactly like output.js DEFPRINT(AST_TemplateString). The raw text is lost
 *    at tokenize/parse time: Tokenizer.scala:83 keys `templateRaws` by AstToken
 *    in a Scala mutable HashMap, and AstToken (AstToken.scala:26) is a final
 *    case class whose `var flags` participates in case-class equals/hashCode.
 *    readTemplateCharacters inserts the template-END token into the map
 *    (Tokenizer.scala:607) and THEN mutates it via `tok.templateEnd_=(true)`
 *    (Tokenizer.scala:608), changing the key's hashCode after insertion — so the
 *    parser's lookups `input.templateRaws.getOrElse(token, "")`
 *    (Parser.scala:1762, 1774) MISS for every template-end token and fall back
 *    to raw = "". Interpolation tokens take `templateEnd_=(false)`
 *    (Tokenizer.scala:583-584), a no-op on flags, so their raws survive — which
 *    is why exactly the FINAL segment vanishes (the only segment, for
 *    substitution-free templates). The cooked `value` lives on the token itself
 *    and survives (pinned by the white-box control below), so untagged templates
 *    (printed from `value`, OutputStream.scala:1280 else-branch) are unaffected.
 *    Terser parse.js:175 is immune: TEMPLATE_RAWS is a JS Map keyed by object
 *    identity (set at parse.js:758/775; the `tok.template_end = true` mutation
 *    at parse.js:776 is harmless there).
 *    Note: the issue says the template segment is dropped; precisely, the port
 *    prints empty backticks — `(function(){})`` + `;` — which is VALID JS but
 *    silently changes semantics (the tag receives "" instead of the raw text;
 *    node v24.12.0 proof 2026-06-12: `(function(s){return s.raw[0]})`tpl`` is
 *    "tpl" vs "" for the misprint; String.raw`\n`.length is 2 vs 0).
 *
 * 2. COMPRESS crash — Compressor guard, not OutputStream. Compressor.scala:4860
 *    optimizeTemplateString ports the compress/index.js:3910-3913 guard
 *    `compressor.parent() instanceof AST_PrefixedTemplateString`, but evaluates
 *    it against the Compressor's OWN TreeWalker stack (`parent(0)`,
 *    AstNode.scala:171-174), which is EMPTY during the compress pass: the live
 *    ancestry is held by the separate TreeTransformer built at
 *    Compressor.scala:295-297 and exposed as `activeWalker`, consulted only by
 *    `inComputedKey` (comment at Compressor.scala:291-293). `parent(0)` on the
 *    empty stack returns null, the guard never fires, a substitution-free
 *    template under a tag is unwrapped to AstString, and the parent's
 *    AstPrefixedTemplateString.transformDescend cast
 *    `.asInstanceOf[AstTemplateString]` (AstExpressions.scala:420) throws. CCE
 *    stack head observed on the first red run:
 *      java.lang.ClassCastException: class ssg.js.ast.AstString cannot be cast
 *        to class ssg.js.ast.AstTemplateString
 *      at ssg.js.ast.AstPrefixedTemplateString.transformDescend(AstExpressions.scala:420)
 *      at ssg.js.ast.AstNode.$anonfun$1(AstNode.scala:85)
 *      at ssg.js.compress.Compressor.$anonfun$1$$anonfun$1(Compressor.scala:296)
 *      at ssg.js.compress.Compressor.before(Compressor.scala:620)
 *    Templates with non-constant substitutions never unwrap, so they do not
 *    crash (they still exhibit defect 1 in the compressed output). The dead
 *    try/catch IndexOutOfBoundsException at Compressor.scala:4863-4865 is moot:
 *    parent() already returns null on an empty stack.
 *
 * Oracle: the vendored original terser at original-src/terser, version 5.46.1
 * (package.json), executed with node v24.12.0 on 2026-06-12:
 *
 *   cd original-src/terser && node --input-type=module -e "
 *   import { minify } from './main.js';
 *   const r = await minify(CODE, OPTS);   // OPTS in {compress:false,mangle:false}
 *   console.log(r.code);"                 //          {compress:{},   mangle:false}
 *
 * Oracle outputs recorded from those runs (2026-06-12) — terser returns every
 * input below UNCHANGED in BOTH modes, except where noted:
 *   (function(){})`tpl`;      tag`tpl`;        tag`a${x}b`;
 *   (a=b)`tpl`;               a.b`tpl`;        tag`a``b`;
 *   String.raw`\n`;           new f`tpl`;      (function(){})`a${x}b`;
 *   var t=(function(){})`x`;  tag`a${x}`;      tag`${x}`;
 *   `tpl`;                    var u=`a${x}b`;
 *   var u=`tpl`;              → compress: var u="tpl";
 *
 * Port outputs observed on the first red run (2026-06-12): pure print drops the
 * final raw segment — (function(){})``;  tag``;  tag`a${x}`;  (a=b)``;  a.b``;
 * tag````;  String.raw``;  new f``;  (function(){})`a${x}`;
 * var t=(function(){})``; — and compress{} throws the CCE above for every
 * substitution-free tagged form; the substitution forms compress to the same
 * segment-dropped strings as print. All controls below matched the oracle
 * byte-for-byte on the same run.
 */
package ssg
package js

import ssg.js.ast.{ AstFunction, AstPrefixedTemplateString, AstSimpleStatement, AstTemplateSegment }

final class TaggedTemplateIss1171Suite extends munit.FunSuite {

  override val munitTimeout = scala.concurrent.duration.Duration(30, "s")

  /** Equivalent of terser's `{compress: false, mangle: false}`. */
  private val printOnly = MinifyOptions(compress = false, mangle = false)

  /** Equivalent of terser's `{compress: {}, mangle: false}`. */
  private val compressOnly =
    MinifyOptions(compress = ssg.js.compress.CompressorOptions(), mangle = false)

  // -- Red: expected output taken verbatim from terser 5.46.1 (header oracle runs) --

  test("red ISS-1171 (a): pure print keeps the template of a parenthesized function tag") {
    val out = Terser.minifyToString("(function(){})`tpl`;", printOnly)
    assertEquals(out, "(function(){})`tpl`;", "output must match terser 5.46.1 — raw segment must not be dropped")
  }

  test("red ISS-1171 (b): compress does not crash on a parenthesized function tag") {
    val out = Terser.minifyToString("(function(){})`tpl`;", compressOnly)
    assertEquals(out, "(function(){})`tpl`;", "output must match terser 5.46.1 (today: ClassCastException)")
  }

  test("red ISS-1171 (c): pure print keeps the template of a plain tag") {
    val out = Terser.minifyToString("tag`tpl`;", printOnly)
    assertEquals(out, "tag`tpl`;", "output must match terser 5.46.1 — raw segment must not be dropped")
  }

  test("red ISS-1171 (d): compress does not crash on a plain tag") {
    val out = Terser.minifyToString("tag`tpl`;", compressOnly)
    assertEquals(out, "tag`tpl`;", "output must match terser 5.46.1 (today: ClassCastException)")
  }

  test("red ISS-1171 (e): pure print keeps the trailing segment after a substitution") {
    val out = Terser.minifyToString("tag`a${x}b`;", printOnly)
    assertEquals(out, "tag`a${x}b`;", "output must match terser 5.46.1 — trailing raw segment must not be dropped")
  }

  test("red ISS-1171 (f): compress keeps the trailing segment after a substitution") {
    val out = Terser.minifyToString("tag`a${x}b`;", compressOnly)
    assertEquals(out, "tag`a${x}b`;", "output must match terser 5.46.1 — trailing raw segment must not be dropped")
  }

  test("red ISS-1171 (g): pure print keeps the template of a parenthesized assignment tag") {
    val out = Terser.minifyToString("(a=b)`tpl`;", printOnly)
    assertEquals(out, "(a=b)`tpl`;", "output must match terser 5.46.1 — raw segment must not be dropped")
  }

  test("red ISS-1171 (h): compress does not crash on a parenthesized assignment tag") {
    val out = Terser.minifyToString("(a=b)`tpl`;", compressOnly)
    assertEquals(out, "(a=b)`tpl`;", "output must match terser 5.46.1 (today: ClassCastException)")
  }

  test("red ISS-1171 (i): pure print keeps the template of a member-expression tag") {
    val out = Terser.minifyToString("a.b`tpl`;", printOnly)
    assertEquals(out, "a.b`tpl`;", "output must match terser 5.46.1 — raw segment must not be dropped")
  }

  test("red ISS-1171 (j): pure print keeps both templates of a chained tagged template") {
    val out = Terser.minifyToString("tag`a``b`;", printOnly)
    assertEquals(out, "tag`a``b`;", "output must match terser 5.46.1 — raw segments must not be dropped")
  }

  test("red ISS-1171 (k): pure print keeps the raw escape of String.raw") {
    val out = Terser.minifyToString("String.raw`\\n`;", printOnly)
    assertEquals(out, "String.raw`\\n`;", "output must match terser 5.46.1 — raw `\\n` must not be dropped")
  }

  test("red ISS-1171 (l): pure print keeps the template in expression position") {
    val out = Terser.minifyToString("var t=(function(){})`x`;", printOnly)
    assertEquals(out, "var t=(function(){})`x`;", "output must match terser 5.46.1 — raw segment must not be dropped")
  }

  test("red ISS-1171 (m): compress does not crash on a tagged template in expression position") {
    val out = Terser.minifyToString("var t=(function(){})`x`;", compressOnly)
    assertEquals(out, "var t=(function(){})`x`;", "output must match terser 5.46.1 (today: ClassCastException)")
  }

  test("red ISS-1171 (n): pure print keeps the template after a new expression tag") {
    val out = Terser.minifyToString("new f`tpl`;", printOnly)
    assertEquals(out, "new f`tpl`;", "output must match terser 5.46.1 — raw segment must not be dropped")
  }

  test("red ISS-1171 (o): pure print keeps trailing segment on a parenthesized function tag with substitution") {
    val out = Terser.minifyToString("(function(){})`a${x}b`;", printOnly)
    assertEquals(out, "(function(){})`a${x}b`;", "output must match terser 5.46.1 — trailing raw segment must not be dropped")
  }

  test("red ISS-1171 (p): compress keeps trailing segment on a parenthesized function tag with substitution") {
    val out = Terser.minifyToString("(function(){})`a${x}b`;", compressOnly)
    assertEquals(out, "(function(){})`a${x}b`;", "output must match terser 5.46.1 — trailing raw segment must not be dropped")
  }

  // -- Controls: untagged templates print from cooked values and are unaffected --
  // Probe result (2026-06-12, this suite's first red run): all controls PASS today.

  test("control ISS-1171 (q): untagged template prints its cooked content") {
    val out = Terser.minifyToString("`tpl`;", printOnly)
    assertEquals(out, "`tpl`;", "output must match terser 5.46.1")
  }

  test("control ISS-1171 (r): compress folds an untagged constant template to a string") {
    val out = Terser.minifyToString("var u=`tpl`;", compressOnly)
    assertEquals(out, "var u=\"tpl\";", "output must match terser 5.46.1")
  }

  test("control ISS-1171 (s): untagged template with substitution prints all segments") {
    val out = Terser.minifyToString("var u=`a${x}b`;", printOnly)
    assertEquals(out, "var u=`a${x}b`;", "output must match terser 5.46.1")
  }

  test("control ISS-1171 (t): compress keeps an untagged template with non-constant substitution") {
    val out = Terser.minifyToString("var u=`a${x}b`;", compressOnly)
    assertEquals(out, "var u=`a${x}b`;", "output must match terser 5.46.1")
  }

  // -- Controls: tagged forms whose template-END raw is empty happen to round-trip --
  // The end token's raw is "" anyway (`...${x}` + backtick), so the broken raw lookup
  // is harmless here, and the non-constant substitution blocks the compress unwrap
  // (hence no CCE). Keep green so a fix does not regress these.

  test("control ISS-1171 (u): tagged template ending in a substitution prints unchanged") {
    val out = Terser.minifyToString("tag`a${x}`;", printOnly)
    assertEquals(out, "tag`a${x}`;", "output must match terser 5.46.1")
  }

  test("control ISS-1171 (v): tagged template ending in a substitution compresses unchanged") {
    val out = Terser.minifyToString("tag`a${x}`;", compressOnly)
    assertEquals(out, "tag`a${x}`;", "output must match terser 5.46.1")
  }

  test("control ISS-1171 (w): substitution-only tagged template compresses unchanged") {
    val out = Terser.minifyToString("tag`${x}`;", compressOnly)
    assertEquals(out, "tag`${x}`;", "output must match terser 5.46.1")
  }

  // -- Control, white-box: the parse representation matches terser's and the cooked
  // value SURVIVES — only the raw is lost (templateRaws lookup miss, see header).
  // Probe result (2026-06-12, this suite's first red run): this test PASSES today
  // (observed segment: value=[tpl] raw=[]). It localizes the print defect to the
  // raw side-table, not the segment parse, and pins the AST shape the issue's
  // parenthesized-callee case actually produces.

  test("control ISS-1171 (white-box): parenthesized-callee tag parses as PrefixedTemplateString with cooked value intact") {
    val ast = Terser.minify("(function(){})`tpl`;", printOnly).ast
    ast.body.head match {
      case stmt: AstSimpleStatement =>
        stmt.body.nn match {
          case pts: AstPrefixedTemplateString =>
            assert(pts.prefix.nn.isInstanceOf[AstFunction], s"prefix must be AstFunction, got: ${pts.prefix.nn.getClass.getName}")
            val segments = pts.templateString.nn.segments
            assertEquals(segments.size, 1, "single template segment expected")
            segments.head match {
              case seg: AstTemplateSegment =>
                assertEquals(seg.value, "tpl", "cooked value must survive the parse (only raw is lost today)")
              case other => fail(s"Expected AstTemplateSegment, got: $other")
            }
          case other => fail(s"Expected AstPrefixedTemplateString (terser: AST_PrefixedTemplateString), got: $other")
        }
      case other => fail(s"Expected AstSimpleStatement, got: $other")
    }
  }
}
