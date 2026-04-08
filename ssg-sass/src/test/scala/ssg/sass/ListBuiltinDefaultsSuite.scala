/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Regression for sass-spec libsass-closed-issues/issue_577 and
 * sibling cases (issue_1171/1240/1269/1604): calling list built-ins
 * with fewer positional args than declared (relying on trailing
 * defaults like `$separator: auto`) used to raise
 * IndexOutOfBoundsException because BuiltInCallable dispatch passes
 * positional args verbatim without filling in declared defaults.
 * list.append and list.join now guard trailing default accesses.
 */
package ssg
package sass

final class ListBuiltinDefaultsSuite extends munit.FunSuite {

  test("list.append on empty list inherits default separator (issue_577)") {
    val src =
      """@use "sass:list";
        |@function map-each($map) {
        |  $values: ();
        |  @each $key, $value in $map {
        |    $values: list.append($values, $value);
        |  }
        |  @return $values;
        |}
        |$map: (foo: bar);
        |.test { -map-test: map-each($map); }
        |""".stripMargin
    val r = Compile.compileString(src)
    assertEquals(
      r.css.trim,
      """.test {
        |  -map-test: bar;
        |}""".stripMargin
    )
  }

  test("list.join without explicit separator/bracketed does not IOOBE") {
    val src =
      """@use "sass:list";
        |.t { a: list.join((1 2), (3 4)); }
        |""".stripMargin
    val r = Compile.compileString(src)
    assertEquals(r.css.trim, ".t {\n  a: 1 2 3 4;\n}")
  }
}
