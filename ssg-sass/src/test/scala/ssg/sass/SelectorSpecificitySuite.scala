/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package sass

import ssg.sass.ast.selector.{ ComplexSelector, PseudoSelector, SelectorList }
import ssg.sass.parse.SelectorParser

/** Tests for per-pseudo-name specificity specialization.
  *
  * Specificity is stored as a single integer in base-1000, matching
  * dart-sass. For assertion clarity we decode it back into an
  * `(id, class, tpe)` triple using the same base-1000 convention:
  *   id    = total / 1_000_000
  *   class = (total / 1_000) % 1_000
  *   type  = total % 1_000
  */
final class SelectorSpecificitySuite extends munit.FunSuite {

  private def parse(s: String): SelectorList = new SelectorParser(s).parse()

  private def complex(s: String): ComplexSelector = {
    val list = parse(s)
    assert(list.components.size == 1, s"expected single complex in '$s'")
    list.components.head
  }

  /** Decode a base-1000 specificity int into an (id, class, tpe) triple. */
  private def triple(spec: Int): (Int, Int, Int) =
    (spec / 1_000_000, (spec / 1_000) % 1_000, spec % 1_000)

  private def specOf(s: String): (Int, Int, Int) = triple(complex(s).specificity)

  test(":where(...) has zero specificity") {
    assertEquals(specOf(":where(.a, .b)"), (0, 0, 0))
    assertEquals(specOf(":where(#big.complicated[thing])"), (0, 0, 0))
  }

  test(":is(...) uses the max of its arguments") {
    // :is(.a, #b): max(class, id) -> id wins
    assertEquals(specOf(":is(.a, #b)"), (1, 0, 0))
    assertEquals(specOf(":is(.a, .b, .c)"), (0, 1, 0))
  }

  test(":not(...) uses the max of its arguments") {
    assertEquals(specOf(":not(#a)"), (1, 0, 0))
    assertEquals(specOf(":not(.a, .b, .c)"), (0, 1, 0))
  }

  test(":matches(...) uses the max of its arguments") {
    assertEquals(specOf(":matches(.a, #b)"), (1, 0, 0))
  }

  test(":where(...) does not contribute to a compound") {
    // .a:where(.b.c) -> only .a counts -> (0, 1, 0)
    assertEquals(specOf(".a:where(.b.c)"), (0, 1, 0))
  }

  test(":has(:not(.x)) -> class") {
    // :has takes max of its components; single component is :not(.x);
    // :not(.x) takes max of its components -> .x -> (0, 1, 0).
    assertEquals(specOf(":has(:not(.x))"), (0, 1, 0))
  }

  test("plain :hover is a single class") {
    assertEquals(specOf(":hover"), (0, 1, 0))
  }

  test("pseudo-elements are a single type") {
    assertEquals(specOf("::before"), (0, 0, 1))
    assertEquals(specOf(":before"), (0, 0, 1)) // syntactic class, real element
  }

  test("id + two classes") {
    assertEquals(specOf("#a.b.c"), (1, 2, 0))
  }

  test(":nth-child without an inner selector is a single class") {
    // Plain `:nth-child(2n)` has no selector argument -> falls through to the
    // default pseudo-class specificity of (0, 1, 0). `:nth-child(... of S)`
    // would add the inner selector's max specificity on top of that; our
    // current SelectorParser doesn't surface the inner selector argument,
    // so we only assert the base-case here.
    assertEquals(specOf(":nth-child(2n)"), (0, 1, 0))
  }

  test("PseudoSelector companion exposes specialization sets") {
    assert(PseudoSelector.selectorPseudoClasses.contains("not"))
    assert(PseudoSelector.selectorPseudoClasses.contains("is"))
    assert(PseudoSelector.selectorPseudoClasses.contains("has"))
    assert(PseudoSelector.selectorPseudoClasses.contains("host"))
    assert(PseudoSelector.selectorPseudoClasses.contains("host-context"))
    assert(PseudoSelector.selectorPseudoElements.contains("slotted"))
    assert(PseudoSelector.rootishPseudoClasses == Set("host", "host-context"))
  }
}
