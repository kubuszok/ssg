/*
 * Copyright (c) 2025-2026 Mateusz Kubuszok
 * SPDX-License-Identifier: Apache-2.0
 *
 * Re-export of Nullable from lls for backwards compatibility.
 * New code should import from lowlevel.Nullable directly.
 *
 * Covenant: full-port
 * Covenant-verified: 2026-04-26
 */
package ssg
package md

/** Re-export of Nullable from lls. */
type Nullable[A] = lowlevel.Nullable[A]
val Nullable = lowlevel.Nullable
