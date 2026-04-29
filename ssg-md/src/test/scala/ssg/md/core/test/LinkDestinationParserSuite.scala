/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-core-test/.../parser/LinkDestinationParserTest.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause */
package ssg
package md
package core
package test

import ssg.md.parser.internal.LinkDestinationParser
import ssg.md.util.sequence.CharSubSequence

import scala.language.implicitConversions

final class LinkDestinationParserSuite extends munit.FunSuite {

  private var noParenParser:      LinkDestinationParser = scala.compiletime.uninitialized
  private var normalParser:       LinkDestinationParser = scala.compiletime.uninitialized
  private var spacesParser:       LinkDestinationParser = scala.compiletime.uninitialized
  private var jekyllParser:       LinkDestinationParser = scala.compiletime.uninitialized
  private var jekyllSpacesParser: LinkDestinationParser = scala.compiletime.uninitialized

  override def beforeEach(context: BeforeEach): Unit = {
    normalParser = new LinkDestinationParser(true, false, false, false)
    noParenParser = new LinkDestinationParser(false, false, false, false)
    spacesParser = new LinkDestinationParser(true, true, false, false)
    jekyllParser = new LinkDestinationParser(true, false, true, false)
    jekyllSpacesParser = new LinkDestinationParser(true, true, true, false)
  }

  // Normal tests
  test("test_Normal1")(assertEquals(normalParser.parseLinkDestination(CharSubSequence.of(""), 0).toString, ""))
  test("test_Normal2")(assertEquals(normalParser.parseLinkDestination(CharSubSequence.of("abc"), 0).toString, "abc"))
  test("test_Normal3")(assertEquals(normalParser.parseLinkDestination(CharSubSequence.of("abc"), 1).toString, "bc"))
  test("test_Normal4")(assertEquals(normalParser.parseLinkDestination(CharSubSequence.of("abc \""), 1).toString, "bc"))
  test("test_Normal5")(assertEquals(normalParser.parseLinkDestination(CharSubSequence.of("abc '"), 1).toString, "bc"))
  test("test_Normal6")(assertEquals(normalParser.parseLinkDestination(CharSubSequence.of("abc)"), 0).toString, "abc"))
  test("test_Normal7")(assertEquals(normalParser.parseLinkDestination(CharSubSequence.of("(abc)"), 0).toString, "(abc)"))
  test("test_Normal8")(assertEquals(normalParser.parseLinkDestination(CharSubSequence.of("\\(abc)"), 0).toString, "\\(abc"))
  test("test_Normal9")(assertEquals(normalParser.parseLinkDestination(CharSubSequence.of("abc\\ "), 0).toString, "abc\\"))
  test("test_Normal10")(assertEquals(normalParser.parseLinkDestination(CharSubSequence.of("((abc))"), 0).toString, "((abc))"))

  // NoParen tests
  test("test_NoParen1")(assertEquals(noParenParser.parseLinkDestination(CharSubSequence.of(""), 0).toString, ""))
  test("test_NoParen2")(assertEquals(noParenParser.parseLinkDestination(CharSubSequence.of("abc"), 0).toString, "abc"))
  test("test_NoParen3")(assertEquals(noParenParser.parseLinkDestination(CharSubSequence.of("abc"), 1).toString, "bc"))
  test("test_NoParen4")(assertEquals(noParenParser.parseLinkDestination(CharSubSequence.of("abc \""), 1).toString, "bc"))
  test("test_NoParen5")(assertEquals(noParenParser.parseLinkDestination(CharSubSequence.of("abc '"), 1).toString, "bc"))
  test("test_NoParen6")(assertEquals(noParenParser.parseLinkDestination(CharSubSequence.of("abc)"), 0).toString, "abc"))
  test("test_NoParen7")(assertEquals(noParenParser.parseLinkDestination(CharSubSequence.of("(abc)"), 0).toString, "(abc)"))
  test("test_NoParen8")(assertEquals(noParenParser.parseLinkDestination(CharSubSequence.of("\\(abc)"), 0).toString, "\\(abc"))
  test("test_NoParen9")(assertEquals(noParenParser.parseLinkDestination(CharSubSequence.of("abc\\ "), 0).toString, "abc\\"))
  test("test_NoParen10")(assertEquals(noParenParser.parseLinkDestination(CharSubSequence.of("((abc))"), 0).toString, ""))
  test("test_NoParen11")(assertEquals(noParenParser.parseLinkDestination(CharSubSequence.of("(abc()"), 0).toString, ""))
  test("test_NoParen12")(assertEquals(noParenParser.parseLinkDestination(CharSubSequence.of("(abc(())"), 0).toString, ""))

  // Spaces tests
  test("test_Spaces1")(assertEquals(spacesParser.parseLinkDestination(CharSubSequence.of("ab c"), 0).toString, "ab c"))
  test("test_Spaces2")(assertEquals(spacesParser.parseLinkDestination(CharSubSequence.of("ab c"), 1).toString, "b c"))
  test("test_Spaces3")(assertEquals(spacesParser.parseLinkDestination(CharSubSequence.of("ab c "), 1).toString, "b c"))
  test("test_Spaces4")(assertEquals(spacesParser.parseLinkDestination(CharSubSequence.of("ab c  "), 1).toString, "b c"))
  test("test_Spaces5")(assertEquals(spacesParser.parseLinkDestination(CharSubSequence.of("ab c \""), 1).toString, "b c"))
  test("test_Spaces6")(assertEquals(spacesParser.parseLinkDestination(CharSubSequence.of("ab c '"), 1).toString, "b c"))
  test("test_Spaces7")(assertEquals(spacesParser.parseLinkDestination(CharSubSequence.of("ab c  \""), 1).toString, "b c"))
  test("test_Spaces8")(assertEquals(spacesParser.parseLinkDestination(CharSubSequence.of("ab c  '"), 1).toString, "b c"))
  test("test_Spaces9")(assertEquals(spacesParser.parseLinkDestination(CharSubSequence.of("ab c)"), 0).toString, "ab c"))
  test("test_Spaces10")(assertEquals(spacesParser.parseLinkDestination(CharSubSequence.of("ab c) \""), 0).toString, "ab c"))
  test("test_Spaces11")(assertEquals(spacesParser.parseLinkDestination(CharSubSequence.of("ab c) '"), 0).toString, "ab c"))
  test("test_Spaces12")(assertEquals(spacesParser.parseLinkDestination(CharSubSequence.of("(ab c)"), 0).toString, "(ab c)"))
  test("test_Spaces13")(assertEquals(spacesParser.parseLinkDestination(CharSubSequence.of("\\(ab c)"), 0).toString, "\\(ab c"))
  test("test_Spaces14")(assertEquals(spacesParser.parseLinkDestination(CharSubSequence.of("(ab c) \""), 0).toString, "(ab c)"))
  test("test_Spaces15")(assertEquals(spacesParser.parseLinkDestination(CharSubSequence.of("\\(ab c) '"), 0).toString, "\\(ab c"))

  // Jekyll tests
  test("test_Jekyll1")(assertEquals(jekyllParser.parseLinkDestination(CharSubSequence.of("{{macro}}abc"), 0).toString, "{{macro}}abc"))
  test("test_Jekyll2")(assertEquals(jekyllParser.parseLinkDestination(CharSubSequence.of("{{ macro }}abc"), 0).toString, "{{ macro }}abc"))
  test("test_Jekyll3")(assertEquals(jekyllParser.parseLinkDestination(CharSubSequence.of("{{macro1}}ab{{macro2}}c"), 0).toString, "{{macro1}}ab{{macro2}}c"))
  test("test_Jekyll4") {
    assertEquals(
      jekyllParser.parseLinkDestination(CharSubSequence.of("{{ macro1 }}ab{{ macro2 }}c"), 0).toString,
      "{{ macro1 }}ab{{ macro2 }}c"
    )
  }
  test("test_Jekyll5")(assertEquals(jekyllParser.parseLinkDestination(CharSubSequence.of("\\{{macro}}abc"), 0).toString, "\\{{macro}}abc"))
  test("test_Jekyll6")(assertEquals(jekyllParser.parseLinkDestination(CharSubSequence.of("\\{{ macro }}abc"), 0).toString, "\\{{"))
  test("test_Jekyll7")(assertEquals(jekyllParser.parseLinkDestination(CharSubSequence.of("{{ma(cro}}abc)"), 0).toString, "{{ma(cro}}abc"))
  test("test_Jekyll8")(assertEquals(jekyllParser.parseLinkDestination(CharSubSequence.of("{{ ma(cro }}abc)"), 0).toString, "{{ ma(cro }}abc"))
  test("test_Jekyll9")(assertEquals(jekyllParser.parseLinkDestination(CharSubSequence.of("{{ma(cro)}}abc)"), 0).toString, "{{ma(cro)}}abc"))
  test("test_Jekyll10")(assertEquals(jekyllParser.parseLinkDestination(CharSubSequence.of("{{ ma(cro) }}abc)"), 0).toString, "{{ ma(cro) }}abc"))
  test("test_Jekyll11")(assertEquals(jekyllParser.parseLinkDestination(CharSubSequence.of("{{ma(croabc)"), 0).toString, "{{ma(croabc)"))
  test("test_Jekyll12")(assertEquals(jekyllParser.parseLinkDestination(CharSubSequence.of("{{ma(croabc))"), 0).toString, "{{ma(croabc)"))
  test("test_Jekyll13")(assertEquals(jekyllParser.parseLinkDestination(CharSubSequence.of("{{ma(cro)abc)"), 0).toString, "{{ma(cro)abc"))
  test("test_Jekyll14")(assertEquals(jekyllParser.parseLinkDestination(CharSubSequence.of("{{ ma(cro) abc)"), 0).toString, "{{"))
  test("test_Jekyll15")(assertEquals(jekyllParser.parseLinkDestination(CharSubSequence.of("({{ma(cro)abc)"), 0).toString, "({{ma(cro)abc)"))
  test("test_Jekyll16")(assertEquals(jekyllParser.parseLinkDestination(CharSubSequence.of("({{ma(cro) abc)"), 0).toString, "({{ma(cro)"))
  test("test_Jekyll17")(assertEquals(jekyllParser.parseLinkDestination(CharSubSequence.of("({{ma(cro)abc))"), 0).toString, "({{ma(cro)abc)"))
  test("test_Jekyll18")(assertEquals(jekyllParser.parseLinkDestination(CharSubSequence.of("({{ma(cro) abc))"), 0).toString, "({{ma(cro)"))
  test("test_Jekyll19")(assertEquals(jekyllParser.parseLinkDestination(CharSubSequence.of("({{ma(cro)abc))"), 0).toString, "({{ma(cro)abc)"))
  test("test_Jekyll20")(assertEquals(jekyllParser.parseLinkDestination(CharSubSequence.of("({{ma(cro) abc))"), 0).toString, "({{ma(cro)"))
  test("test_Jekyll21")(assertEquals(jekyllParser.parseLinkDestination(CharSubSequence.of("(({{ma(cro) abc))"), 0).toString, "(({{ma(cro)"))

  // JekyllSpaces tests
  test("test_JekyllSpaces1")(assertEquals(jekyllSpacesParser.parseLinkDestination(CharSubSequence.of("{{macro}}abc"), 0).toString, "{{macro}}abc"))
  test("test_JekyllSpaces2")(assertEquals(jekyllSpacesParser.parseLinkDestination(CharSubSequence.of("{{ macro }}abc"), 0).toString, "{{ macro }}abc"))
  test("test_JekyllSpaces3") {
    assertEquals(
      jekyllSpacesParser.parseLinkDestination(CharSubSequence.of("{{macro1}}ab{{macro2}}c"), 0).toString,
      "{{macro1}}ab{{macro2}}c"
    )
  }
  test("test_JekyllSpaces4") {
    assertEquals(
      jekyllSpacesParser.parseLinkDestination(CharSubSequence.of("{{ macro1 }}ab{{ macro2 }}c"), 0).toString,
      "{{ macro1 }}ab{{ macro2 }}c"
    )
  }
  test("test_JekyllSpaces5")(assertEquals(jekyllSpacesParser.parseLinkDestination(CharSubSequence.of("\\{{macro}}abc"), 0).toString, "\\{{macro}}abc"))
  test("test_JekyllSpaces6")(assertEquals(jekyllSpacesParser.parseLinkDestination(CharSubSequence.of("\\{{ macro }}abc"), 0).toString, "\\{{ macro }}abc"))
  test("test_JekyllSpaces7")(assertEquals(jekyllSpacesParser.parseLinkDestination(CharSubSequence.of("{{ma(cro}}abc)"), 0).toString, "{{ma(cro}}abc"))
  test("test_JekyllSpaces8")(assertEquals(jekyllSpacesParser.parseLinkDestination(CharSubSequence.of("{{ ma(cro }}abc)"), 0).toString, "{{ ma(cro }}abc"))
  test("test_JekyllSpaces9")(assertEquals(jekyllSpacesParser.parseLinkDestination(CharSubSequence.of("{{ma(cro)}}abc)"), 0).toString, "{{ma(cro)}}abc"))
  test("test_JekyllSpaces10")(assertEquals(jekyllSpacesParser.parseLinkDestination(CharSubSequence.of("{{ ma(cro) }}abc)"), 0).toString, "{{ ma(cro) }}abc"))
  test("test_JekyllSpaces11")(assertEquals(jekyllSpacesParser.parseLinkDestination(CharSubSequence.of("{{ma(croabc)"), 0).toString, "{{ma(croabc)"))
  test("test_JekyllSpaces12")(assertEquals(jekyllSpacesParser.parseLinkDestination(CharSubSequence.of("{{ma(croabc))"), 0).toString, "{{ma(croabc)"))
  test("test_JekyllSpaces13")(assertEquals(jekyllSpacesParser.parseLinkDestination(CharSubSequence.of("{{ma(cro)abc)"), 0).toString, "{{ma(cro)abc"))
  test("test_JekyllSpaces14")(assertEquals(jekyllSpacesParser.parseLinkDestination(CharSubSequence.of("{{ ma(cro) abc)"), 0).toString, "{{ ma(cro) abc"))
  test("test_JekyllSpaces15")(assertEquals(jekyllSpacesParser.parseLinkDestination(CharSubSequence.of("({{ma(cro)abc)"), 0).toString, "({{ma(cro)abc)"))
  test("test_JekyllSpaces16a")(assertEquals(jekyllSpacesParser.parseLinkDestination(CharSubSequence.of("({{ma(cro) abc)"), 0).toString, "({{ma(cro) abc)"))
  test("test_JekyllSpaces16b")(assertEquals(jekyllSpacesParser.parseLinkDestination(CharSubSequence.of("({{ ma(cro) abc)"), 0).toString, "({{ ma(cro) abc)"))
  test("test_JekyllSpaces17")(assertEquals(jekyllSpacesParser.parseLinkDestination(CharSubSequence.of("({{ma(cro)abc))"), 0).toString, "({{ma(cro)abc)"))
  test("test_JekyllSpaces18")(assertEquals(jekyllSpacesParser.parseLinkDestination(CharSubSequence.of("({{ma(cro) abc))"), 0).toString, "({{ma(cro) abc)"))
  test("test_JekyllSpaces19")(assertEquals(jekyllSpacesParser.parseLinkDestination(CharSubSequence.of("({{ma(cro)abc))"), 0).toString, "({{ma(cro)abc)"))
  test("test_JekyllSpaces20")(assertEquals(jekyllSpacesParser.parseLinkDestination(CharSubSequence.of("({{ma(cro) abc))"), 0).toString, "({{ma(cro) abc)"))
  test("test_JekyllSpaces21")(assertEquals(jekyllSpacesParser.parseLinkDestination(CharSubSequence.of("(({{ma(cro) abc))"), 0).toString, "(({{ma(cro) abc))"))
}
