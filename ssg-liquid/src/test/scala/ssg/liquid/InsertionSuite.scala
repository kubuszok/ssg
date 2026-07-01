/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package liquid

import lowlevel.Nullable
import ssg.data.DataView

/** Tests ported from liqp's InsertionTest.java — 11 tests + ISS-1021 regression tests. */
final class InsertionSuite extends munit.FunSuite {

  test("ISS-1021: Insertions.get returns Nullable[Insertion] — missing key is empty") {
    val insertions = Insertions.STANDARD_INSERTIONS
    val result: Nullable[Insertion] = insertions.get("nonexistent_tag_xyz")
    assert(result.isEmpty, "get for non-existent name must return Nullable.empty, not raw null")
  }

  test("ISS-1021: Insertions.get returns Nullable[Insertion] — existing key is defined") {
    val insertions = Insertions.STANDARD_INSERTIONS
    val result: Nullable[Insertion] = insertions.get("assign")
    assert(result.isDefined, "get for existing name 'assign' must return a defined Nullable")
    assertEquals(result.get.name, "assign")
  }

  test("ISS-1021: Insertions.get works with Nullable API (fold/getOrElse)") {
    val insertions = Insertions.STANDARD_INSERTIONS
    // missing key — fold takes the empty branch
    val missing    = insertions.get("no_such_tag")
    val foldResult = missing.fold("EMPTY")(_.name)
    assertEquals(foldResult, "EMPTY")

    // existing key — fold takes the non-empty branch
    val present     = insertions.get("if")
    val foldResult2 = present.fold("EMPTY")(_.name)
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

  test("insertion: custom tag") {
    assume(PlatformCompat.isJVM, "Double.toString formatting differs on JS/Native")
    val parser = new TemplateParser.Builder()
      .withTag(
        new tags.Tag("twice") {
          override def render(context: TemplateContext, ns: Array[nodes.LNode]): DataView = {
            val number = super.asNumber(ns(0).render(context)).doubleValue()
            DataView.from(number * 2)
          }
        }
      )
      .build()

    assertEquals(parser.parse("{% twice 10 %}").render(), "20.0")
  }

  test("insertion: custom tag parameters") {
    assume(PlatformCompat.isJVM, "Double.toString formatting differs on JS/Native")
    val parser = new TemplateParser.Builder()
      .withTag(
        new tags.Tag("multiply") {
          override def render(context: TemplateContext, ns: Array[nodes.LNode]): DataView = {
            val n1 = super.asNumber(ns(0).render(context)).doubleValue()
            val n2 = super.asNumber(ns(1).render(context)).doubleValue()
            DataView.from(n1 * n2)
          }
        }
      )
      .build()

    assertEquals(parser.parse("{% multiply 2 4 %}").render(), "8.0")
  }

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
