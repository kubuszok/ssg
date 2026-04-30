/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-definition/.../DefinitionParserTest.java Original: Copyright (c) 2016-2023 Vladimir Schneider Original license: BSD-2-Clause */
package ssg
package md
package ext
package definition
package test

import ssg.md.Nullable
import ssg.md.ext.definition.DefinitionExtension
import ssg.md.parser.Parser
import ssg.md.util.data.MutableDataSet
import ssg.md.util.sequence.BasedSequence

import java.util.Collections
import scala.language.implicitConversions
import scala.util.boundary
import scala.util.boundary.break

final class DefinitionParserTest extends munit.FunSuite {

  private def escape(input: String, parser: Parser): String = {
    val baseSeq  = BasedSequence.of(input)
    val handlers = Parser.SPECIAL_LEAD_IN_HANDLERS.get(parser.options.get)
    val sb       = new StringBuilder()

    boundary {
      for (handler <- handlers)
        if (handler.escape(baseSeq, Nullable.empty, (cs: CharSequence) => sb.append(cs))) break(sb.toString())
      input
    }
  }

  private def unEscape(input: String, parser: Parser): String = {
    val baseSeq  = BasedSequence.of(input)
    val handlers = Parser.SPECIAL_LEAD_IN_HANDLERS.get(parser.options.get)
    val sb       = new StringBuilder()

    boundary {
      for (handler <- handlers)
        if (handler.unEscape(baseSeq, Nullable.empty, (cs: CharSequence) => sb.append(cs))) break(sb.toString())
      input
    }
  }

  test("test_escape") {
    val parser = Parser.builder().extensions(Collections.singleton(DefinitionExtension.create())).build()

    assertEquals(escape("abc", parser), "abc")

    assertEquals(escape(":", parser), "\\:")
    assertEquals(escape(":abc", parser), ":abc")

    assertEquals(escape("~", parser), "\\~")
    assertEquals(escape("~abc", parser), "~abc")

    assertEquals(unEscape("\\:", parser), ":")
    assertEquals(unEscape("\\:abc", parser), "\\:abc")

    assertEquals(unEscape("\\~", parser), "~")
    assertEquals(unEscape("\\~abc", parser), "\\~abc")
  }

  test("test_escapeNoColon") {
    val parser = Parser.builder(new MutableDataSet().set(DefinitionExtension.COLON_MARKER, false)).extensions(Collections.singleton(DefinitionExtension.create())).build()

    assertEquals(escape("abc", parser), "abc")

    assertEquals(escape(":", parser), ":")
    assertEquals(escape(":abc", parser), ":abc")

    assertEquals(escape("~", parser), "\\~")
    assertEquals(escape("~abc", parser), "~abc")

    assertEquals(unEscape("\\:", parser), "\\:")
    assertEquals(unEscape("\\:abc", parser), "\\:abc")

    assertEquals(unEscape("\\~", parser), "~")
    assertEquals(unEscape("\\~abc", parser), "\\~abc")
  }

  test("test_escapeNoTilde") {
    val parser = Parser.builder(new MutableDataSet().set(DefinitionExtension.TILDE_MARKER, false)).extensions(Collections.singleton(DefinitionExtension.create())).build()

    assertEquals(escape("abc", parser), "abc")

    assertEquals(escape(":", parser), "\\:")
    assertEquals(escape(":abc", parser), ":abc")

    assertEquals(escape("~", parser), "~")
    assertEquals(escape("~abc", parser), "~abc")

    assertEquals(unEscape("\\:", parser), ":")
    assertEquals(unEscape("\\:abc", parser), "\\:abc")

    assertEquals(unEscape("\\~", parser), "\\~")
    assertEquals(unEscape("\\~abc", parser), "\\~abc")
  }
}
