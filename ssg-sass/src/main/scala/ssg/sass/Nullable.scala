/*
 * Copyright (c) 2025-2026 Mateusz Kubuszok
 * SPDX-License-Identifier: Apache-2.0
 *
 * Re-exports lowlevel.Nullable into ssg.sass package scope so that
 * all ssg-sass files see Nullable without an explicit import.
 *
 * Covenant: full-port
 * Covenant-verified: 2026-04-26
 */
package ssg
package sass

/** Re-export of `lowlevel.Nullable` — the canonical implementation lives in lls; this file keeps the symbol visible in `ssg.sass` package scope without requiring 100+ import edits.
  */
type Nullable[A] = lowlevel.Nullable[A]
val Nullable = lowlevel.Nullable // scalastyle:ignore
