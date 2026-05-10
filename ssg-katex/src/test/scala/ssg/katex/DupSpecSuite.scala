/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Tests that no function names are duplicated in the macro registry.
 *
 * Original source: katex test/dup-spec.ts
 */
package ssg
package katex

import ssg.katex.data.{ Macros, Symbols }

class DupSpecSuite extends KaTeXTestSuite {

  // The original iterates over all macros and checks that none shadow a symbol.
  // In the TypeScript, macros is an object and symbols is an object with "math"/"text" keys.

  test("Symbols and macros: macro should not shadow a symbol") {
    // Ensure all macros are registered
    Macros.registerAll()

    val macroNames = MacroDef._macros.keys.toSet

    for (macroName <- macroNames) {
      // Check that this macro name is not in math or text symbol tables
      val mathSymbol = Symbols.mathMap.get(macroName)
      val textSymbol = Symbols.textMap.get(macroName)

      assert(mathSymbol.isEmpty, s"macro $macroName should not shadow a math symbol")
      assert(textSymbol.isEmpty, s"macro $macroName should not shadow a text symbol")
    }
  }
}
