/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * KaTeX environment registry — imports and registers all environment definitions.
 * Replaces the original environments.ts which relied on side-effect imports.
 *
 * Original source: katex src/environments.ts
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
package environments

import scala.util.boundary
import scala.util.boundary.break

/**
 * Include this to ensure that all environments are defined.
 * Call `Environments.registerAll()` once at startup.
 */
object Environments {

  @volatile private var registered: Boolean = false

  def registerAll(): Unit = boundary {
    if (registered) break(())
    registered = true

    // Register all environment definitions
    ArrayEnv.register()

    // Register CD-related internal functions (\\cdleft, \\cdright, \\cdparent)
    CdEnv.register()
  }
}
