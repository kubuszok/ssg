/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package md
package util
package html
package test

import ssg.md.html.HtmlWriter
import ssg.md.util.misc.Utils
import ssg.md.util.sequence.LineAppendable

final class HtmlAppendableBaseSuite extends munit.FunSuite {

  private def r(fn: => Unit): Runnable = () => fn

  test("test_basic") {
    val fa = new HtmlWriter(2, LineAppendable.F_FORMAT_ALL)

    fa.tagIndent("ul", r(fa.withCondIndent().tagLine("li", r(fa.text("item1")))))
    assertEquals(fa.toString(0, 0), "<ul>\n  <li>item1</li>\n</ul>\n")
  }

  test("test_basic1") {
    val fa1 = new HtmlWriter(2, LineAppendable.F_FORMAT_ALL)

    fa1.tagIndent(
      "ul",
      r {
        fa1
          .withCondIndent()
          .tagLine("li",
                   r {
                     fa1.text("item1")
                     fa1.tagIndent("ul", r(fa1.withCondIndent().tagLine("li", r(fa1.text("item1")))))
                   }
          )
      }
    )

    assertEquals(fa1.toString(0, 0), "<ul>\n  <li>item1\n    <ul>\n      <li>item1</li>\n    </ul>\n  </li>\n</ul>\n")
  }

  test("test_basic2") {
    val fa2 = new HtmlWriter(2, LineAppendable.F_FORMAT_ALL)

    fa2.withCondLineOnChildText().withCondIndent().tag("tbody", r {})

    assertEquals(fa2.toString(0, 0), "<tbody></tbody>\n")
  }

  test("test_basic3") {
    val fa = new HtmlWriter(2, LineAppendable.F_FORMAT_ALL)

    fa.tagIndent("ul", r(fa.withCondIndent().tagLine("li", r(fa.text("item1\ntwo line text")))))
    assertEquals(fa.toString(0, 0),
                 "" +
                   "<ul>\n" +
                   "  <li>item1\n" +
                   "    two line text</li>\n" +
                   "</ul>\n" +
                   ""
    )
  }

  test("test_basic4") {
    val fa = new HtmlWriter(2, LineAppendable.F_FORMAT_ALL)

    fa.tagIndent(
      "ul",
      r {
        fa.withCondIndent().tagLine("li", r(fa.text("item1\ntwo line text")))
        fa.withCondIndent().tagLine("li", r(fa.text("item1")))
      }
    )

    assertEquals(fa.toString(0, 0),
                 "" +
                   "<ul>\n" +
                   "  <li>item1\n" +
                   "    two line text</li>\n" +
                   "  <li>item1</li>\n" +
                   "</ul>\n" +
                   ""
    )
  }

  // test tag tracking
  test("test_tagList") {
    val fa = new HtmlWriter(2, LineAppendable.F_FORMAT_ALL)

    fa.tag("span", false)
    fa.tagIndent(
      "ul",
      r {
        fa.withCondIndent()
          .tagLine(
            "li",
            r {
              val tagsAfterLast = fa.getOpenTagsAfterLast("span")
              val tags          = Utils.splice(tagsAfterLast.toArray, ", ")
              assertEquals(tags, "ul, li")
              fa.text("item1")
            }
          )
      }
    )

    fa.closeTag("span")

    assertEquals(fa.toString(0, 0), "<span>\n<ul>\n  <li>item1</li>\n</ul>\n</span>\n")

    val fa1 = new HtmlWriter(2, LineAppendable.F_FORMAT_ALL)

    fa1.tagIndent(
      "ul",
      r {
        fa1
          .withCondIndent()
          .tagLine(
            "li",
            r {
              val tagsAfterLast = fa.getOpenTagsAfterLast("span")
              val tags          = Utils.splice(tagsAfterLast.toArray, ", ")
              assertEquals(tags, "")
              fa1.text("item1")
              fa1.tagIndent("ul", r(fa1.withCondIndent().tagLine("li", r(fa1.text("item1")))))
            }
          )
      }
    )

    assertEquals(fa1.toString(0, 0), "<ul>\n  <li>item1\n    <ul>\n      <li>item1</li>\n    </ul>\n  </li>\n</ul>\n")
  }
}
