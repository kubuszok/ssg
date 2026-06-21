/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Red test for ISS-1194 (R0610-P1, incomplete-port): SSG's Unicode identifier
 * predicates diverge from terser's.
 *
 * Token.isIdentifierStartCodePoint / isIdentifierCharCodePoint (Token.scala:304,326)
 * classify code points via Java `Character.getType`, i.e. the JDK's MODERN Unicode
 * tables (whatever the running JDK ships). terser instead tests two VENDORED regexes
 * UNICODE.ID_Start / UNICODE.ID_Continue (original-src/terser/lib/parse.js:266-268),
 * generated from an OLDER Unicode snapshot. Letters added to Unicode AFTER terser's
 * snapshot are therefore ACCEPTED by SSG but REJECTED by terser.
 *
 * terser oracle — membership is byte-truth, re-derived by evaluating terser's OWN
 * regexes (run from original-src/terser/):
 *
 *   node --input-type=module -e "import('fs').then(fs=>{const src=fs.readFileSync('./lib/parse.js','utf8');const idStart=eval(src.match(/ID_Start:\s*(\/[\s\S]*?\/),/)[1]);const idCont=eval(src.match(/ID_Continue:\s*(\/[\s\S]*?\/),/)[1]);const S=new RegExp('^(?:'+idStart.source+')$'),C=new RegExp('^(?:'+idCont.source+')$');for(const cp of [0x41,0xE9,0x560,0x870,0xA7C2,0x1E900,0xEB3,0x200C,0x30]){const ch=String.fromCodePoint(cp);console.log('U+'+cp.toString(16),'Start='+S.test(ch),'Continue='+C.test(ch));}})"
 *
 * Confirmed oracle table:
 *   U+0041 'A'      Start=true   Continue=true    (control — ASCII letter)
 *   U+00E9 'é'      Start=true   Continue=true    (control — accepted in both)
 *   U+0560 (Armenian, Unicode 11)   Start=false  Continue=false   DIVERGENT
 *   U+0870 (Arabic ext-B, Unicode 14) Start=false Continue=false  DIVERGENT
 *   U+A7C2 (Latin ext, Unicode 12)  Start=false  Continue=false   DIVERGENT
 *   U+1E900 (Adlam, astral)         Start=false  Continue=false   DIVERGENT
 *   U+0EB3 (Lao)    Start=true   Continue=true    (terser ACCEPTS — NOT divergent;
 *                                                  the issue's original U+0EB3 example
 *                                                  was wrong, kept here as a control)
 *   U+200C (ZWNJ)   Start=false  Continue=true    (continue-only control)
 *   U+0030 '0'      Start=false  Continue=true    (digit control)
 *
 * The four DIVERGENT code points (U+0560/U+0870/U+A7C2/U+1E900) are letters under the
 * JDK's Character.getType (so SSG returns true) but are absent from terser's vendored
 * tables (oracle false). Those assertions are RED today. The fix (implementer's job)
 * must make SSG's predicates match terser's vendored ID_Start/ID_Continue tables; do
 * NOT modify product code in this suite.
 */
package ssg
package js
package parse

final class UnicodeIdentifierTableIss1194Suite extends munit.FunSuite {

  // (code point, expected isIdentifierStart, expected isIdentifierChar) per the
  // terser oracle table above.
  private val oracle: Seq[(Int, Boolean, Boolean)] = Seq(
    (0x0041, true, true), // 'A'  control
    (0x00e9, true, true), // 'é'  control
    (0x0560, false, false), // Armenian (Unicode 11) — DIVERGENT
    (0x0870, false, false), // Arabic ext-B (Unicode 14) — DIVERGENT
    (0xa7c2, false, false), // Latin ext (Unicode 12) — DIVERGENT
    (0x1e900, false, false), // Adlam astral — DIVERGENT
    (0x0eb3, true, true), // Lao — terser accepts (control; issue example was wrong)
    (0x200c, false, true), // ZWNJ — continue-only control
    (0x0030, false, true) // '0' — digit control
  )

  for ((cp, expStart, _) <- oracle)
    test(f"ISS-1194: isIdentifierStartCodePoint(U+$cp%04X) matches terser oracle ($expStart)") {
      assertEquals(
        Token.isIdentifierStartCodePoint(cp),
        expStart,
        f"U+$cp%04X: SSG isIdentifierStartCodePoint disagrees with terser ID_Start"
      )
    }

  for ((cp, _, expCont) <- oracle)
    test(f"ISS-1194: isIdentifierCharCodePoint(U+$cp%04X) matches terser oracle ($expCont)") {
      assertEquals(
        Token.isIdentifierCharCodePoint(cp),
        expCont,
        f"U+$cp%04X: SSG isIdentifierCharCodePoint disagrees with terser ID_Continue"
      )
    }

  // Parse-level consequence: U+0560 (Armenian) is NOT a valid identifier start in
  // terser, so `var ՠ=1;ՠ;` must be REJECTED. terser throws "Unexpected character";
  // SSG must likewise reject it (here: a JsParseError). Today SSG ACCEPTS it (red),
  // because Character.getType treats U+0560 as a letter.
  test("ISS-1194: parsing `var <U+0560>=1;` is rejected (matches terser)") {
    val src = "var " + new String(Character.toChars(0x0560)) + "=1;"
    intercept[JsParseError] {
      new Parser().parse(src)
    }
  }
}
