/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package liquid

/** Tests ported from liqp's InsertionTest.java — 11 tests. */
final class InsertionSuite extends munit.FunSuite {

  test("insertion: nested custom tags and blocks") {
    val parser = new TemplateParser.Builder()
      .withBlock(
        new blocks.Block("block") {
          override def render(context: TemplateContext, ns: Array[nodes.LNode]): Any = {
            val data = if (ns.length >= 2) ns(1).render(context) else ns(0).render(context)
            "blk[" + data + "]"
          }
        }
      )
      .withTag(new tags.Tag("simple") {
        override def render(context: TemplateContext, ns: Array[nodes.LNode]): Any =
          "(sim)"
      })
      .build()

    val templateString = "{% block %}a{% simple %}b{% block %}c{% endblock %}d{% endblock %}"
    assertEquals(parser.parse(templateString).render(), "blk[a(sim)bblk[c]d]")
  }

  test("insertion: nested custom tags and blocks as one collection") {
    val parser = new TemplateParser.Builder()
      .withBlock(
        new blocks.Block("block") {
          override def render(context: TemplateContext, ns: Array[nodes.LNode]): Any = {
            val data = if (ns.length >= 2) ns(1).render(context) else ns(0).render(context)
            "blk[" + data + "]"
          }
        }
      )
      .withTag(new tags.Tag("simple") {
        override def render(context: TemplateContext, ns: Array[nodes.LNode]): Any =
          "(sim)"
      })
      .build()

    val templateString = "{% block %}a{% simple %}b{% block %}c{% endblock %}d{% endblock %}"
    assertEquals(parser.parse(templateString).render(), "blk[a(sim)bblk[c]d]")
  }

  test("insertion: custom tag") {
    assume(PlatformCompat.supportsReflection, "Double.toString formatting differs on JS/Native")
    val parser = new TemplateParser.Builder()
      .withTag(
        new tags.Tag("twice") {
          override def render(context: TemplateContext, ns: Array[nodes.LNode]): Any = {
            val number = super.asNumber(ns(0).render(context)).doubleValue()
            java.lang.Double.valueOf(number * 2)
          }
        }
      )
      .build()

    assertEquals(parser.parse("{% twice 10 %}").render(), "20.0")
  }

  test("insertion: custom tag parameters") {
    assume(PlatformCompat.supportsReflection, "Double.toString formatting differs on JS/Native")
    val parser = new TemplateParser.Builder()
      .withTag(
        new tags.Tag("multiply") {
          override def render(context: TemplateContext, ns: Array[nodes.LNode]): Any = {
            val n1 = super.asNumber(ns(0).render(context)).doubleValue()
            val n2 = super.asNumber(ns(1).render(context)).doubleValue()
            java.lang.Double.valueOf(n1 * n2)
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
          override def render(context: TemplateContext, ns: Array[nodes.LNode]): Any = {
            val blockNode  = ns(ns.length - 1)
            val blockValue = super.asString(blockNode.render(context), context)
            blockValue + " " + blockValue
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
      .withTag(new tags.Tag("custom_tag") {
        override def render(context: TemplateContext, ns: Array[nodes.LNode]): Any =
          "xxx"
      })
      .build()
    assertEquals(parser.parse("{% custom_tag %}").render(), "xxx")
  }
}
