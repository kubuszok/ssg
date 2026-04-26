/*
 * Copyright (c) 2025-2026 Mateusz Kubuszok
 * SPDX-License-Identifier: Apache-2.0
 *
 * Re-exports ssg.commons.Nullable into ssg.sass package scope so that
 * all ssg-sass files see Nullable without an explicit import.
 *
 * Covenant: full-port
 * Covenant-verified: 2026-04-26
 */
package ssg
package sass

/** Re-export of `ssg.commons.Nullable` — the canonical implementation lives in ssg-commons; this file keeps the symbol visible in `ssg.sass` package scope without requiring 100+ import edits.
  */
type Nullable[A] = ssg.commons.Nullable[A]
val Nullable = ssg.commons.Nullable // scalastyle:ignore
