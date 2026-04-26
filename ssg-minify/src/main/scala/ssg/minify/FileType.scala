/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * File type enum for minification dispatch.
 *
 * Covenant: full-port
 * Covenant-verified: 2026-04-26
 */
package ssg
package minify

enum FileType extends java.lang.Enum[FileType] {
  case Html, Css, Js, Json, Xml
}
