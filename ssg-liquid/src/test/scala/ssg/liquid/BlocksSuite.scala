/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package liquid

import java.util.HashMap
import java.util.ArrayList

final class BlocksSuite extends munit.FunSuite {

  // ===== if =====

  test("if: false condition produces no output") {
    assertEquals(
      Template.parse(" {% if false %} this text should not go into the output {% endif %} ").render(),
      "  "
    )
  }

  test("if: true condition produces output") {
    assertEquals(
      Template.parse(" {% if true %} this text should go into the output {% endif %} ").render(),
      "  this text should go into the output  "
    )
  }

  test("if: multiple if blocks") {
    assertEquals(
      Template.parse("{% if false %} you suck {% endif %} {% if true %} you rock {% endif %}?").render(),
      "  you rock ?"
    )
  }

  test("if/else: false goes to else") {
    assertEquals(
      Template.parse("{% if false %} NO {% else %} YES {% endif %}").render(),
      " YES "
    )
  }

  test("if/else: true goes to if") {
    assertEquals(
      Template.parse("{% if true %} YES {% else %} NO {% endif %}").render(),
      " YES "
    )
  }

  test("if/else: truthy string goes to if") {
    assertEquals(
      Template.parse("{% if \"foo\" %} YES {% else %} NO {% endif %}").render(),
      " YES "
    )
  }

  test("if: boolean variable") {
    val vars = new HashMap[String, Any]()
    vars.put("var", java.lang.Boolean.TRUE)
    assertEquals(
      Template.parse("{% if var %} YES {% endif %}").render(vars),
      " YES "
    )
  }

  test("if: elsif matching") {
    val vars = new HashMap[String, Any]()
    val user = new HashMap[String, Any]()
    user.put("name", "Tobi")
    vars.put("user", user)
    assertEquals(
      Template.parse("{% if user.name == 'tobi' %}A{% elsif user.name == 'Tobi' %}B{% endif %}").render(vars),
      "B"
    )
  }

  test("if: elsif with else fallthrough") {
    val vars = new HashMap[String, Any]()
    val user = new HashMap[String, Any]()
    user.put("name", "Tobi")
    vars.put("user", user)
    assertEquals(
      Template.parse("{% if user.name == 'tobi' %}A{% elsif user.name == 'TOBI' %}B{% else %}C{% endif %}").render(vars),
      "C"
    )
  }

  test("if: nil variable is falsy") {
    assertEquals(
      Template.parse("{% if user %} Hello {{ user.name }} {% endif %}").render(),
      ""
    )
  }

  test("if: nested if blocks") {
    val vars = new HashMap[String, Any]()
    vars.put("a", java.lang.Boolean.TRUE)
    vars.put("b", java.lang.Boolean.TRUE)
    assertEquals(
      Template.parse("{% if a %}{% if b %}AB{% endif %}{% endif %}").render(vars),
      "AB"
    )
  }

  // ===== unless =====

  test("unless: true condition produces no output") {
    assertEquals(
      Template.parse(" {% unless true %} this text should not go into the output {% endunless %} ").render(),
      "  "
    )
  }

  test("unless: false condition produces output") {
    assertEquals(
      Template.parse(" {% unless false %} this text should go into the output {% endunless %} ").render(),
      "  this text should go into the output  "
    )
  }

  // TODO: pre-existing bug — unless/else not yet implemented
  test("unless: with else") {
    assertEquals(
      Template.parse("{% unless true %} NO {% else %} YES {% endunless %}").render(),
      " YES "
    )
  }

  // TODO: pre-existing bug — forloop.index not resolved inside unless
  test("unless: in loop with nil and false") {
    val vars    = new HashMap[String, Any]()
    val choices = new ArrayList[Any]()
    choices.add(java.lang.Integer.valueOf(1))
    choices.add(null)
    choices.add(java.lang.Boolean.FALSE)
    vars.put("choices", choices)
    assertEquals(
      Template.parse("{% for i in choices %}{% unless i %}{{ forloop.index }}{% endunless %}{% endfor %}").render(vars),
      "23"
    )
  }

  // ===== case/when =====

  test("case: basic matching") {
    val vars = new HashMap[String, Any]()
    vars.put("condition", java.lang.Integer.valueOf(2))
    assertEquals(
      Template.parse("{% case condition %}{% when 1 %} its 1 {% when 2 %} its 2 {% endcase %}").render(vars),
      " its 2 "
    )
  }

  test("case: no match produces empty") {
    val vars = new HashMap[String, Any]()
    vars.put("condition", java.lang.Integer.valueOf(3))
    assertEquals(
      Template.parse("{% case condition %}{% when 1 %} its 1 {% when 2 %} its 2 {% endcase %}").render(vars),
      ""
    )
  }

  test("case: with else") {
    val vars = new HashMap[String, Any]()
    vars.put("condition", java.lang.Integer.valueOf(6))
    assertEquals(
      Template.parse("{% case condition %}{% when 5 %} hit {% else %} else {% endcase %}").render(vars),
      " else "
    )
  }

  test("case: string matching") {
    val vars = new HashMap[String, Any]()
    vars.put("condition", "string here")
    assertEquals(
      Template.parse("{% case condition %}{% when \"string here\" %} hit {% endcase %}").render(vars),
      " hit "
    )
  }

  test("case: or in when") {
    val vars = new HashMap[String, Any]()
    vars.put("condition", java.lang.Integer.valueOf(2))
    assertEquals(
      Template.parse("{% case condition %}{% when 1 or 2 %} hit {% endcase %}").render(vars),
      " hit "
    )
  }

  test("case: comma in when") {
    val vars = new HashMap[String, Any]()
    vars.put("condition", java.lang.Integer.valueOf(2))
    assertEquals(
      Template.parse("{% case condition %}{% when 1, 2 %} hit {% endcase %}").render(vars),
      " hit "
    )
  }

  // ===== for =====

  test("for: basic array iteration") {
    val vars  = new HashMap[String, Any]()
    val array = new ArrayList[Any]()
    array.add("a")
    array.add("b")
    array.add("c")
    vars.put("array", array)
    assertEquals(
      Template.parse("{% for item in array %}{{item}}{% endfor %}").render(vars),
      "abc"
    )
  }

  test("for: range (1..5)") {
    assertEquals(
      Template.parse("{% for i in (1..5) %}{{i}}{% endfor %}").render(),
      "12345"
    )
  }

  test("for: range (1..3) with spaces") {
    assertEquals(
      Template.parse("{%for item in (1..3) %} {{item}} {%endfor%}").render(),
      " 1  2  3 "
    )
  }

  test("for: limit parameter") {
    val vars  = new HashMap[String, Any]()
    val array = new ArrayList[Any]()
    array.add(java.lang.Integer.valueOf(1))
    array.add(java.lang.Integer.valueOf(2))
    array.add(java.lang.Integer.valueOf(3))
    array.add(java.lang.Integer.valueOf(4))
    array.add(java.lang.Integer.valueOf(5))
    vars.put("array", array)
    assertEquals(
      Template.parse("{% for item in array limit:2 %}{{item}}{% endfor %}").render(vars),
      "12"
    )
  }

  test("for: offset parameter") {
    val vars  = new HashMap[String, Any]()
    val array = new ArrayList[Any]()
    array.add(java.lang.Integer.valueOf(1))
    array.add(java.lang.Integer.valueOf(2))
    array.add(java.lang.Integer.valueOf(3))
    array.add(java.lang.Integer.valueOf(4))
    array.add(java.lang.Integer.valueOf(5))
    vars.put("array", array)
    assertEquals(
      Template.parse("{% for item in array offset:2 %}{{item}}{% endfor %}").render(vars),
      "345"
    )
  }

  test("for: limit and offset combined") {
    val vars  = new HashMap[String, Any]()
    val array = new ArrayList[Any]()
    for (i <- 1 to 10)
      array.add(java.lang.Integer.valueOf(i))
    vars.put("array", array)
    assertEquals(
      Template.parse("{%for i in array limit:4 offset:2 %}{{ i }}{%endfor%}").render(vars),
      "3456"
    )
  }

  test("for: forloop.index") {
    val vars  = new HashMap[String, Any]()
    val array = new ArrayList[Any]()
    array.add(java.lang.Integer.valueOf(1))
    array.add(java.lang.Integer.valueOf(2))
    array.add(java.lang.Integer.valueOf(3))
    vars.put("array", array)
    assertEquals(
      Template.parse("{% for item in array %}{{ forloop.index }}{% endfor %}").render(vars),
      "123"
    )
  }

  test("for: forloop.index0") {
    val vars  = new HashMap[String, Any]()
    val array = new ArrayList[Any]()
    array.add(java.lang.Integer.valueOf(1))
    array.add(java.lang.Integer.valueOf(2))
    array.add(java.lang.Integer.valueOf(3))
    vars.put("array", array)
    assertEquals(
      Template.parse("{%for item in array%} {{forloop.index0}} {%endfor%}").render(vars),
      " 0  1  2 "
    )
  }

  test("for: forloop.first") {
    val vars  = new HashMap[String, Any]()
    val array = new ArrayList[Any]()
    array.add(java.lang.Integer.valueOf(1))
    array.add(java.lang.Integer.valueOf(2))
    array.add(java.lang.Integer.valueOf(3))
    vars.put("array", array)
    assertEquals(
      Template.parse("{% for item in array %}{% if forloop.first %}First{% endif %}{% endfor %}").render(vars),
      "First"
    )
  }

  test("for: forloop.last") {
    val vars  = new HashMap[String, Any]()
    val array = new ArrayList[Any]()
    array.add(java.lang.Integer.valueOf(1))
    array.add(java.lang.Integer.valueOf(2))
    array.add(java.lang.Integer.valueOf(3))
    vars.put("array", array)
    assertEquals(
      Template.parse("{%for item in array%} {{forloop.last}} {%endfor%}").render(vars),
      " false  false  true "
    )
  }

  test("for: forloop.length") {
    val vars  = new HashMap[String, Any]()
    val array = new ArrayList[Any]()
    array.add(java.lang.Integer.valueOf(1))
    array.add(java.lang.Integer.valueOf(2))
    array.add(java.lang.Integer.valueOf(3))
    vars.put("array", array)
    assertEquals(
      Template.parse("{%for item in array%} {{forloop.index}}/{{forloop.length}} {%endfor%}").render(vars),
      " 1/3  2/3  3/3 "
    )
  }

  test("for: forloop.rindex") {
    val vars  = new HashMap[String, Any]()
    val array = new ArrayList[Any]()
    array.add(java.lang.Integer.valueOf(1))
    array.add(java.lang.Integer.valueOf(2))
    array.add(java.lang.Integer.valueOf(3))
    vars.put("array", array)
    assertEquals(
      Template.parse("{%for item in array%} {{forloop.rindex}} {%endfor%}").render(vars),
      " 3  2  1 "
    )
  }

  test("for: reversed") {
    val vars  = new HashMap[String, Any]()
    val array = new ArrayList[Any]()
    array.add(java.lang.Integer.valueOf(1))
    array.add(java.lang.Integer.valueOf(2))
    array.add(java.lang.Integer.valueOf(3))
    vars.put("array", array)
    assertEquals(
      Template.parse("{%for item in array reversed %}{{item}}{%endfor%}").render(vars),
      "321"
    )
  }

  test("for: break exits loop early") {
    val vars  = new HashMap[String, Any]()
    val array = new ArrayList[Any]()
    array.add(java.lang.Integer.valueOf(1))
    array.add(java.lang.Integer.valueOf(2))
    array.add(java.lang.Integer.valueOf(3))
    array.add(java.lang.Integer.valueOf(4))
    array.add(java.lang.Integer.valueOf(5))
    vars.put("array", array)
    assertEquals(
      Template.parse("{% for item in array %}{% if item == 4 %}{% break %}{% endif %}{{item}}{% endfor %}").render(vars),
      "123"
    )
  }

  test("for: continue skips iteration") {
    val vars  = new HashMap[String, Any]()
    val array = new ArrayList[Any]()
    array.add(java.lang.Integer.valueOf(1))
    array.add(java.lang.Integer.valueOf(2))
    array.add(java.lang.Integer.valueOf(3))
    array.add(java.lang.Integer.valueOf(4))
    array.add(java.lang.Integer.valueOf(5))
    vars.put("array", array)
    assertEquals(
      Template.parse("{% for item in array %}{% if item == 3 %}{% continue %}{% endif %}{{item}}{% endfor %}").render(vars),
      "1245"
    )
  }

  test("for/else: renders else when array is empty") {
    val vars  = new HashMap[String, Any]()
    val array = new ArrayList[Any]()
    vars.put("array", array)
    assertEquals(
      Template.parse("{%for item in array%}+{%else%}-{%endfor%}").render(vars),
      "-"
    )
  }

  test("for/else: renders body when array has items") {
    val vars  = new HashMap[String, Any]()
    val array = new ArrayList[Any]()
    array.add(java.lang.Integer.valueOf(1))
    array.add(java.lang.Integer.valueOf(2))
    array.add(java.lang.Integer.valueOf(3))
    vars.put("array", array)
    assertEquals(
      Template.parse("{%for item in array%}+{%else%}-{%endfor%}").render(vars),
      "+++"
    )
  }

  test("for: nested for loops") {
    val vars   = new HashMap[String, Any]()
    val outer  = new ArrayList[Any]()
    val inner1 = new ArrayList[Any]()
    inner1.add(java.lang.Integer.valueOf(1))
    inner1.add(java.lang.Integer.valueOf(2))
    val inner2 = new ArrayList[Any]()
    inner2.add(java.lang.Integer.valueOf(3))
    inner2.add(java.lang.Integer.valueOf(4))
    val inner3 = new ArrayList[Any]()
    inner3.add(java.lang.Integer.valueOf(5))
    inner3.add(java.lang.Integer.valueOf(6))
    outer.add(inner1)
    outer.add(inner2)
    outer.add(inner3)
    vars.put("array", outer)
    assertEquals(
      Template.parse("{%for item in array%}{%for i in item%}{{ i }}{%endfor%}{%endfor%}").render(vars),
      "123456"
    )
  }

  test("for: forloop.first with if") {
    val vars  = new HashMap[String, Any]()
    val array = new ArrayList[Any]()
    array.add(java.lang.Integer.valueOf(1))
    array.add(java.lang.Integer.valueOf(2))
    array.add(java.lang.Integer.valueOf(3))
    vars.put("array", array)
    assertEquals(
      Template.parse("{%for item in array%}{% if forloop.first %}+{% else %}-{% endif %}{%endfor%}").render(vars),
      "+--"
    )
  }

  // ===== capture =====

  test("capture: basic capture") {
    assertEquals(
      Template.parse("{% capture foo %}Abc{% endcapture %}{{foo}}").render(),
      "Abc"
    )
  }

  test("capture: empty capture") {
    assertEquals(
      Template.parse("{% capture foo %}{% endcapture %}{{foo}}").render(),
      ""
    )
  }

  test("capture: global scope - capture in if block") {
    assertEquals(
      Template.parse("{% capture 'var' %}test string{% endcapture %}{{var}}").render(),
      "test string"
    )
  }

  // ===== cycle =====

  test("cycle: basic cycling") {
    assertEquals(
      Template.parse("{%cycle 'one', 'two'%} {%cycle 'one', 'two'%}").render(),
      "one two"
    )
  }

  test("cycle: named cycles") {
    assertEquals(
      Template
        .parse(
          "{% cycle 33: 'one', 'two', 'three' %}\n" +
            "{% cycle 33: 'one', 'two', 'three' %}"
        )
        .render(),
      "one\ntwo"
    )
  }

  test("cycle: multiple independent cycles") {
    assertEquals(
      Template
        .parse(
          "{% cycle 'one', 'two', 'three' %}\n" +
            "{% cycle 'one', 'two', 'three' %}\n" +
            "{% cycle 'one', 'two', 'three' %}"
        )
        .render(),
      "one\ntwo\nthree"
    )
  }

  // ===== comment =====

  test("comment: basic comment is hidden") {
    assertEquals(
      Template.parse("a{% comment %}hidden{% endcomment %}b").render(),
      "ab"
    )
  }

  test("comment: tags inside comments are ignored") {
    assertEquals(
      Template.parse("{% comment %}{% if true %}yes{% endif %}{% endcomment %}done").render(),
      "done"
    )
  }

  // ===== raw =====

  test("raw: basic raw outputs literal text") {
    assertEquals(
      Template.parse("{% raw %}{{a|b}}{% endraw %}").render(),
      "{{a|b}}"
    )
  }

  test("raw: tags inside raw are not processed") {
    assertEquals(
      Template.parse("{% raw %}{% comment %} test {% endcomment %}{% endraw %}").render(),
      "{% comment %} test {% endcomment %}"
    )
  }

  test("raw: output tags inside raw are not processed") {
    assertEquals(
      Template.parse("{% raw %}{{ test }}{% endraw %}").render(),
      "{{ test }}"
    )
  }

  // ===== tablerow =====

  test("tablerow: basic table row generation") {
    val vars  = new HashMap[String, Any]()
    val array = new ArrayList[Any]()
    array.add(java.lang.Integer.valueOf(1))
    array.add(java.lang.Integer.valueOf(2))
    array.add(java.lang.Integer.valueOf(3))
    vars.put("array", array)
    val rendered = Template.parse("{% tablerow item in array %}{{item}}{% endtablerow %}").render(vars)
    assert(rendered.contains("<tr"))
    assert(rendered.contains("<td"))
    assert(rendered.contains("1"))
    assert(rendered.contains("2"))
    assert(rendered.contains("3"))
  }

  test("tablerow: cols parameter") {
    val vars  = new HashMap[String, Any]()
    val array = new ArrayList[Any]()
    array.add(java.lang.Integer.valueOf(1))
    array.add(java.lang.Integer.valueOf(2))
    array.add(java.lang.Integer.valueOf(3))
    array.add(java.lang.Integer.valueOf(4))
    vars.put("array", array)
    val rendered = Template.parse("{% tablerow item in array cols:2 %}{{item}}{% endtablerow %}").render(vars)
    assert(rendered.contains("<tr class=\"row1\">"))
    assert(rendered.contains("<tr class=\"row2\">"))
  }

  // ===== ifchanged =====

  test("ifchanged: only outputs when value changes") {
    val vars  = new HashMap[String, Any]()
    val array = new ArrayList[Any]()
    array.add(java.lang.Integer.valueOf(1))
    array.add(java.lang.Integer.valueOf(1))
    array.add(java.lang.Integer.valueOf(2))
    array.add(java.lang.Integer.valueOf(2))
    array.add(java.lang.Integer.valueOf(3))
    array.add(java.lang.Integer.valueOf(3))
    vars.put("array", array)
    assertEquals(
      Template.parse("{%for item in array%}{%ifchanged%}{{item}}{% endifchanged %}{%endfor%}").render(vars),
      "123"
    )
  }

  test("ifchanged: all same values") {
    val vars  = new HashMap[String, Any]()
    val array = new ArrayList[Any]()
    array.add(java.lang.Integer.valueOf(1))
    array.add(java.lang.Integer.valueOf(1))
    array.add(java.lang.Integer.valueOf(1))
    array.add(java.lang.Integer.valueOf(1))
    vars.put("array", array)
    assertEquals(
      Template.parse("{%for item in array%}{%ifchanged%}{{item}}{% endifchanged %}{%endfor%}").render(vars),
      "1"
    )
  }

  // ===== assign =====

  test("assign: basic string assignment") {
    assertEquals(
      Template.parse("{% assign name = 'freestyle' %}{{ name }}").render(),
      "freestyle"
    )
  }

  test("assign: number assignment") {
    assertEquals(
      Template.parse("{% assign age = 42 %}{{ age }}").render(),
      "42"
    )
  }

  test("assign: global scope across blocks") {
    assertEquals(
      Template.parse("{% if true %}{% assign x = 'inner' %}{% endif %}{{ x }}").render(),
      "inner"
    )
  }

  test("assign: with filter") {
    assertEquals(
      Template.parse("{% assign v = 1 | minus: 10 | plus: 5 %}{{v}}").render(),
      "-4"
    )
  }

  // ===== increment =====

  test("increment: basic counter") {
    assertEquals(
      Template.parse("{% increment x %}{% increment x %}{% increment x %}").render(),
      "012"
    )
  }

  // TODO: pre-existing bug — independent increment counters share state
  test("increment: independent counters") {
    assertEquals(
      Template.parse("{%increment port %} {%increment starboard%} {%increment port %} {%increment port%} {%increment starboard %}").render(),
      "0 0 1 2 1"
    )
  }

  test("increment: does not affect assigned variables") {
    assertEquals(
      Template.parse("{% assign x = 42 %}{{x}} {%increment x %} {%increment x %} {{x}}").render(),
      "42 0 1 42"
    )
  }

  // ===== decrement =====

  test("decrement: basic counter") {
    assertEquals(
      Template.parse("{%decrement port %}").render(),
      "-1"
    )
  }

  test("decrement: successive decrements") {
    assertEquals(
      Template.parse("{%decrement port %} {%decrement port%}").render(),
      "-1 -2"
    )
  }

  test("decrement: does not affect assigned variables") {
    assertEquals(
      Template.parse("{% assign x = 42 %}{{x}} {%decrement x %} {%decrement x %} {{x}}").render(),
      "42 -1 -2 42"
    )
  }

  // ===== break/continue in for loops =====

  test("break: stops for loop") {
    val vars  = new HashMap[String, Any]()
    val array = new ArrayList[Any]()
    for (i <- 1 to 5)
      array.add(java.lang.Integer.valueOf(i))
    vars.put("array", array)
    assertEquals(
      Template.parse("{% for item in array %}{% if item == 3 %}{% break %}{% endif %}{{ item }}{% endfor %}").render(vars),
      "12"
    )
  }

  test("continue: skips current iteration in for loop") {
    val vars  = new HashMap[String, Any]()
    val array = new ArrayList[Any]()
    for (i <- 1 to 5)
      array.add(java.lang.Integer.valueOf(i))
    vars.put("array", array)
    assertEquals(
      Template.parse("{% for item in array %}{% if item == 2 %}{% continue %}{% endif %}{{ item }}{% endfor %}").render(vars),
      "1345"
    )
  }

  // ===== whitespace control =====

  test("whitespace control: lhs strip with {%-") {
    val rendered = Template.parse("a  \n  {%- assign letter = 'b' %}  \n{{ letter }}\n  c").render()
    assertEquals(rendered.replace(' ', '.'), "a..\nb\n..c")
  }

  test("whitespace control: rhs strip with -%}") {
    val rendered = Template.parse("a  \n  {% assign letter = 'b' -%}  \n{{ letter }}\n  c").render()
    assertEquals(rendered.replace(' ', '.'), "a..\n..b\n..c")
  }

  test("whitespace control: both sides strip") {
    val rendered = Template.parse("a  \n  {%- assign letter = 'b' -%}  \n{{ letter }}\n  c").render()
    assertEquals(rendered.replace(' ', '.'), "ab\n..c")
  }

  test("whitespace control: all tags stripped") {
    val rendered = Template.parse("a  \n  {%- assign letter = 'b' -%}  \n{{- letter -}}\n  c").render()
    assertEquals(rendered.replace(' ', '.'), "abc")
  }
}
