/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/util/lazy_file_span.dart
 * Original: Copyright (c) 2023 Google LLC.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: lazy_file_span.dart → LazyFileSpan.scala
 *   Convention: Lazy delegation to FileSpan
 */
package ssg
package sass
package util

import ssg.sass.Nullable

/** A wrapper for FileSpan that defers creation until the span is needed.
  */
final class LazyFileSpan(builder: () => FileSpan) {

  lazy val span: FileSpan = builder()

  def file:                                           SourceFile       = span.file
  def start:                                          FileLocation     = span.start
  def end:                                            FileLocation     = span.end
  def text:                                           String           = span.text
  def sourceUrl:                                      Nullable[String] = span.sourceUrl
  def length:                                         Int              = span.length
  def message(msg:         String):                   String           = span.message(msg)
  def highlight():                                    String           = span.highlight()
  def expand(other:        FileSpan):                 FileSpan         = span.expand(other)
  def subspan(startOffset: Int, endOffset: Int = -1): FileSpan         = span.subspan(startOffset, endOffset)
  def trim():                                         FileSpan         = span.trim()
  def pointSpan():                                    FileSpan         = span.pointSpan()
  def between(other:       FileSpan):                 FileSpan         = span.between(other)
  def before(inner:        FileSpan):                 FileSpan         = span.before(inner)
  def after(inner:         FileSpan):                 FileSpan         = span.after(inner)
  def contains(target:     FileSpan):                 Boolean          = span.contains(target)

  override def toString: String = span.toString
}
