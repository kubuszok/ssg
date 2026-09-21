/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package liquid

import ssg.data.DataView

/** Tests ported from liqp's InsertionTest.java — 11 tests + ISS-1021 regression tests. */
final class InsertionSuite extends munit.FunSuite {

  test("ISS-1021: Insertions.get returns null for missing key") {
    val insertions = Insertions.STANDARD_INSERTIONS
    val result     = insertions.get("nonexistent_tag_xyz")
    assert(result == null, "get for non-existent name must return null")
  }

  test("ISS-1021: Insertions.get returns Insertion for existing key") {
    val insertions = Insertions.STANDARD_INSERTIONS
    val result     = insertions.get("assign")
    assert(result != null, "get for existing name 'assign' must return a non-null Insertion")
    assertEquals(result.name, "assign")
  }

  test("ISS-1021: Insertions.get null check pattern") {
    val insertions = Insertions.STANDARD_INSERTIONS
    val missing    = insertions.get("no_such_tag")
    val foldResult = if (missing == null) "EMPTY" else missing.name
    assertEquals(foldResult, "EMPTY")

    val present     = insertions.get("if")
    val foldResult2 = if (present == null) "EMPTY" else present.name
    assertEquals(foldResult2, "if")
  }

  test("insertion: nested custom tags and blocks") {
    val parser = new TemplateParser.Builder()
      .withBlock(
        new blocks.Block("block") {
          override def render(context: TemplateContext, ns: Array[nodes.LNode]): DataView = {
            val data = if (ns.length >= 2) ns(1).render(context) else ns(0).render(context)
            DataView.from("blk[" + super.asString(data, context) + "]")
          }
        }
      )
      .withTag(
        new tags.Tag("simple") {
          override def render(context: TemplateContext, ns: Array[nodes.LNode]): DataView =
            DataView.from("(sim)")
        }
      )
      .build()

    val templateString = "{% block %}a{% simple %}b{% block %}c{% endblock %}d{% endblock %}"
    assertEquals(parser.parse(templateString).render(), "blk[a(sim)bblk[c]d]")
  }

  test("insertion: nested custom tags and blocks as one collection") {
    val parser = new TemplateParser.Builder()
      .withBlock(
        new blocks.Block("block") {
          override def render(context: TemplateContext, ns: Array[nodes.LNode]): DataView = {
            val data = if (ns.length >= 2) ns(1).render(context) else ns(0).render(context)
            DataView.from("blk[" + super.asString(data, context) + "]")
          }
        }
      )
      .withTag(
        new tags.Tag("simple") {
          override def render(context: TemplateContext, ns: Array[nodes.LNode]): DataView =
            DataView.from("(sim)")
        }
      )
      .build()

    val templateString = "{% block %}a{% simple %}b{% block %}c{% endblock %}d{% endblock %}"
    assertEquals(parser.parse(templateString).render(), "blk[a(sim)bblk[c]d]")
  }

  // "insertion: custom tag" and "insertion: custom tag parameters" are in src/test/scala-jvm-native,
  // InsertionJvmNativeSuite: on Scala.js a whole-number Double cannot be told from an integer.

  test("insertion: custom tag block") {
    val parser = new TemplateParser.Builder()
      .withBlock(
        new blocks.Block("twice") {
          override def render(context: TemplateContext, ns: Array[nodes.LNode]): DataView = {
            val blockNode  = ns(ns.length - 1)
            val blockValue = super.asString(blockNode.render(context), context)
            DataView.from(blockValue + " " + blockValue)
          }
        }
      )
      .build()

    assertEquals(parser.parse("{% twice %}abc{% endtwice %}").render(), "abc abc")
  }

  test("insertion: break") {
    val vars = TestHelper.mapOf(
      "array" -> TestHelper.listOf(
        java.lang.Integer.valueOf(11),
        java.lang.Integer.valueOf(22),
        java.lang.Integer.valueOf(33),
        java.lang.Integer.valueOf(44),
        java.lang.Integer.valueOf(55)
      )
    )
    val markup = "{% for item in array %}{% if item > 35 %}{% break %}{% endif %}{{ item }}{% endfor %}"
    assertEquals(Template.parse(markup).render(vars), "112233")
  }

  test("insertion: break with no block") {
    assertEquals(Template.parse("{% break %}").render(), "")
  }

  test("insertion: continue") {
    val vars = TestHelper.mapOf(
      "array" -> TestHelper.listOf(
        java.lang.Integer.valueOf(11),
        java.lang.Integer.valueOf(22),
        java.lang.Integer.valueOf(33),
        java.lang.Integer.valueOf(44),
        java.lang.Integer.valueOf(55)
      )
    )
    val markup = "{% for item in array %}{% if item < 35 %}{% continue %}{% endif %}{{ item }}{% endfor %}"
    assertEquals(Template.parse(markup).render(vars), "4455")
  }

  test("insertion: continue with no block") {
    assertEquals(Template.parse("{% continue %}").render(), "")
  }

  test("insertion: no transform") {
    assertEquals(
      Template.parse("this text should come out of the template without change...").render(),
      "this text should come out of the template without change..."
    )
    assertEquals(Template.parse("blah").render(), "blah")
    assertEquals(Template.parse("<blah>").render(), "<blah>")
    assertEquals(Template.parse("|,.:").render(), "|,.:")
    assertEquals(Template.parse("").render(), "")
    val text = "this shouldnt see any transformation either but has multiple lines\n as you can clearly see here ..."
    assertEquals(Template.parse(text).render(), text)
  }

  test("insertion: custom tag registration") {
    val parser = new TemplateParser.Builder()
      .withTag(
        new tags.Tag("custom_tag") {
          override def render(context: TemplateContext, ns: Array[nodes.LNode]): DataView =
            DataView.from("xxx")
        }
      )
      .build()
    assertEquals(parser.parse("{% custom_tag %}").render(), "xxx")
  }
}
