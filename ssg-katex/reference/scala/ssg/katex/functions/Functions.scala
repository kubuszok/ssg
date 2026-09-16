/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * KaTeX function registry — imports and registers all function definitions.
 * Replaces the original functions.ts which relied on side-effect imports.
 *
 * Original source: katex src/functions.ts
 * Original author: Khan Academy and contributors
 * Original license: MIT
 *
 * upstream-commit: 90de9794
 *
 * Migration notes:
 *   Renames: side-effect imports -> explicit register() calls
 *   Convention: module-level imports -> object init
 */
package ssg
package katex
package functions

import scala.util.boundary
import scala.util.boundary.break

/** Include this to ensure that all functions are defined. Call `Functions.registerAll()` once at startup.
  */
object Functions {

  @volatile private var registered: Boolean = false

  def registerAll(): Unit = boundary {
    if (registered) break(())
    registered = true

    AccentFunc.register()
    AccentunderFunc.register()
    ArrowFunc.register()
    PmbFunc.register()
    // Note: CD environment registration would be separate
    CharFunc.register()
    ColorFunc.register()
    CrFunc.register()
    DefFunc.register()
    DelimsizingFunc.register()
    EncloseFunc.register()
    EnvironmentFunc.register()
    FontFunc.register()
    GenfracFunc.register()
    HorizBraceFunc.register()
    HrefFunc.register()
    HboxFunc.register()
    HtmlFunc.register()
    HtmlmathmlFunc.register()
    IncludegraphicsFunc.register()
    KernFunc.register()
    LapFunc.register()
    MathFunc.register()
    MathchoiceFunc.register()
    MclassFunc.register()
    OpFunc.register()
    OperatornameFunc.register()
    OrdgroupFunc.register()
    OverlineFunc.register()
    PhantomFunc.register()
    RaiseboxFunc.register()
    RelaxFunc.register()
    RuleFunc.register()
    SizingFunc.register()
    SmashFunc.register()
    SqrtFunc.register()
    StylingFunc.register()
    SupsubFunc.register()
    SymbolsOpFunc.register()
    SymbolsOrdFunc.register()
    SymbolsSpacingFunc.register()
    TagFunc.register()
    TextFunc.register()
    UnderlineFunc.register()
    VcenterFunc.register()
    VerbFunc.register()
  }
}
