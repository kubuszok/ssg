/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * File type enum for minification dispatch.
 */
package ssg
package minify

enum FileType extends java.lang.Enum[FileType] {
  case Html, Css, Js, Json, Xml
}
