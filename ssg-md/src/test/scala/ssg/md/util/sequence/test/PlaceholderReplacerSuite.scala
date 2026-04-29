/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package md
package util
package sequence
package test

import ssg.md.Nullable

final class PlaceholderReplacerSuite extends munit.FunSuite {

  private def spansOfList(spans: String*): java.util.List[Array[String]] = {
    val params = new java.util.ArrayList[Array[String]](spans.length)
    for (span <- spans)
      params.add(Array(span))
    params
  }

  private def spansOfResult(spans: java.util.List[Array[String]]): Array[String] = {
    val params = new Array[String](spans.size())
    var i      = 0
    val iter   = spans.iterator()
    while (iter.hasNext) {
      val span = iter.next()
      params(i) = span(0)
      i += 1
    }
    params
  }

  private val ourGetter: Array[String] => String         = span => span(0)
  private val ourSetter: (Array[String], String) => Unit = (span, text) => span(0) = text

  test("simple") {
    val map = new java.util.HashMap[String, String]()
    map.put("NAME", "Joe Smith")
    map.put("USER", "<NAME>")

    val params = spansOfList("<NAME>", "<USER>")
    PlaceholderReplacer.replaceAll(params, k => Nullable(map.get(k)), '<', '>', ourGetter, ourSetter)

    assertEquals(spansOfResult(params).toSeq, Seq("Joe Smith", "<NAME>"))
  }

  test("spanSimple2") {
    val map = new java.util.HashMap[String, String]()
    map.put("NAME", "Joe Smith")
    map.put("USER", "<NAME>")

    val params = spansOfList("<NA", "ME>")
    PlaceholderReplacer.replaceAll(params, k => Nullable(map.get(k)), '<', '>', ourGetter, ourSetter)

    assertEquals(spansOfResult(params).toSeq, Seq("", "Joe Smith"))
  }

  test("spanSimple3") {
    val map = new java.util.HashMap[String, String]()
    map.put("NAME", "Joe Smith")
    map.put("USER", "<NAME>")

    val params = spansOfList("<", "USER", ">")
    PlaceholderReplacer.replaceAll(params, k => Nullable(map.get(k)), '<', '>', ourGetter, ourSetter)

    assertEquals(spansOfResult(params).toSeq, Seq("", "", "<NAME>"))
  }

  test("spanComplex") {
    val map = new java.util.HashMap[String, String]()
    map.put("NAME", "Joe Smith")
    map.put("USER", "<NAME>")

    val params = spansOfList("<NA", "ME> <", "USER", ">")
    PlaceholderReplacer.replaceAll(params, k => Nullable(map.get(k)), '<', '>', ourGetter, ourSetter)

    assertEquals(spansOfResult(params).toSeq, Seq("", "Joe Smith ", "", "<NAME>"))
  }

  test("spanComplex2") {
    val map = new java.util.HashMap[String, String]()
    map.put("NAME", "Joe Smith")
    map.put("USER", "<NAME>")

    val params = spansOfList("<NA", "ME> <U", "SER", ">")
    PlaceholderReplacer.replaceAll(params, k => Nullable(map.get(k)), '<', '>', ourGetter, ourSetter)

    assertEquals(spansOfResult(params).toSeq, Seq("", "Joe Smith ", "", "<NAME>"))
  }

  test("spanUndefined") {
    val map = new java.util.HashMap[String, String]()
    map.put("NAME", "Joe Smith")
    map.put("USER", "<NAME>")

    val params = spansOfList("<NA", "ME> <U", "SER", ">")
    PlaceholderReplacer.replaceAll(params, k => Nullable(map.get(k)), '<', '>', ourGetter, ourSetter)

    assertEquals(spansOfResult(params).toSeq, Seq("", "Joe Smith ", "", "<NAME>"))
  }
}
