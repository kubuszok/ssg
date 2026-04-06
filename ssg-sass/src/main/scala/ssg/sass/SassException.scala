/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/exception.dart
 * Original: Copyright (c) 2016 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: exception.dart -> SassException.scala
 *   Convention: Dart SourceSpanException -> extends RuntimeException
 *   Idiom: Simplified toCssString (no SassString dependency yet);
 *          diamond hierarchy flattened (MultiSpanSassRuntimeException extends SassRuntimeException)
 */
package ssg
package sass

import ssg.sass.util.{ FileSpan, Frame, Trace }

import scala.language.implicitConversions

/** An exception thrown by Sass. */
class SassException(
  val sassMessage: String,
  val span:        FileSpan,
  val loadedUrls:  Set[String] = Set.empty
) extends RuntimeException(s"Error: $sassMessage\n${span.highlight()}") {

  /** The Sass stack trace at the point this exception was thrown. */
  def trace: Trace = Trace(List(Frame.fromSpan(span, "root stylesheet")))

  /** Returns a copy with an additional span labeled. */
  def withAdditionalSpan(additionalSpan: FileSpan, label: String): SassException =
    MultiSpanSassException(sassMessage, span, "", Map(additionalSpan -> label), loadedUrls)

  /** Returns a copy as a SassRuntimeException with the given trace. */
  def withTrace(t: Trace): SassRuntimeException =
    SassRuntimeException(sassMessage, span, t, loadedUrls)

  /** Returns a copy with the given loadedUrls. */
  def withLoadedUrls(urls: Set[String]): SassException =
    SassException(sassMessage, span, urls)

  /** Returns CSS that will display this error message. */
  def toCssString: String = {
    val openComment    = "/" + "*"
    val closeComment   = "*" + "/"
    val commentMessage = toString.replace(closeComment, "*\u2215").replaceAll("\r\n", "\n")

    val commentBody  = commentMessage.split("\n").mkString("\n * ")
    val contentValue = escapeForCssContent(sassMessage)
    val sb           = new StringBuilder()
    sb.append(openComment)
    sb.append(" ")
    sb.append(commentBody)
    sb.append(" ")
    sb.append(closeComment)
    sb.append("\n\nbody::before {\n")
    sb.append("  font-family: \"Source Code Pro\", \"SF Mono\", Monaco, Inconsolata, \"Fira Mono\",\n")
    sb.append("      \"Droid Sans Mono\", monospace, monospace;\n")
    sb.append("  white-space: pre;\n")
    sb.append("  display: block;\n")
    sb.append("  padding: 1em;\n")
    sb.append("  margin-bottom: 1em;\n")
    sb.append("  border-bottom: 2px solid black;\n")
    sb.append(s"""  content: "$contentValue";\n""")
    sb.append("}")
    sb.toString()
  }

  private def escapeForCssContent(s: String): String = {
    val sb = new StringBuilder()
    for (c <- s)
      if (c > 0x7f) {
        sb.append('\\')
        sb.append(c.toInt.toHexString)
        sb.append(' ')
      } else if (c == '"') {
        sb.append("\\\"")
      } else if (c == '\\') {
        sb.append("\\\\")
      } else if (c == '\n') {
        sb.append("\\a ")
      } else {
        sb.append(c)
      }
    sb.toString()
  }

  override def toString: String = {
    val sb = new StringBuilder()
    sb.append(s"Error: $sassMessage\n")
    sb.append(span.highlight())
    val traceStr = trace.toString
    for (frame <- traceStr.split("\n"))
      if (frame.nonEmpty) {
        sb.append("\n  ")
        sb.append(frame)
      }
    sb.toString()
  }
}

/** A SassException with secondary spans for multi-location messages. */
class MultiSpanSassException(
  sassMessage:        String,
  span:               FileSpan,
  val primaryLabel:   String,
  val secondarySpans: Map[FileSpan, String],
  loadedUrls:         Set[String] = Set.empty
) extends SassException(sassMessage, span, loadedUrls) {

  override def withAdditionalSpan(additionalSpan: FileSpan, label: String): MultiSpanSassException =
    MultiSpanSassException(sassMessage, span, primaryLabel, secondarySpans + (additionalSpan -> label), loadedUrls)

  override def withTrace(t: Trace): SassRuntimeException =
    MultiSpanSassRuntimeException(sassMessage, span, primaryLabel, secondarySpans, t, loadedUrls)

  override def withLoadedUrls(urls: Set[String]): MultiSpanSassException =
    MultiSpanSassException(sassMessage, span, primaryLabel, secondarySpans, urls)
}

/** An exception thrown during Sass evaluation (has a stack trace). */
class SassRuntimeException(
  sassMessage:        String,
  span:               FileSpan,
  override val trace: Trace,
  loadedUrls:         Set[String] = Set.empty
) extends SassException(sassMessage, span, loadedUrls) {

  override def withAdditionalSpan(additionalSpan: FileSpan, label: String): SassException =
    MultiSpanSassRuntimeException(sassMessage, span, "", Map(additionalSpan -> label), trace, loadedUrls)

  override def withLoadedUrls(urls: Set[String]): SassRuntimeException =
    SassRuntimeException(sassMessage, span, trace, urls)
}

/** A SassRuntimeException with secondary spans. */
class MultiSpanSassRuntimeException(
  sassMessage:        String,
  span:               FileSpan,
  val primaryLabel:   String,
  val secondarySpans: Map[FileSpan, String],
  override val trace: Trace,
  loadedUrls:         Set[String] = Set.empty
) extends SassRuntimeException(sassMessage, span, trace, loadedUrls) {

  override def withAdditionalSpan(additionalSpan: FileSpan, label: String): MultiSpanSassRuntimeException =
    MultiSpanSassRuntimeException(sassMessage, span, primaryLabel, secondarySpans + (additionalSpan -> label), trace, loadedUrls)

  override def withLoadedUrls(urls: Set[String]): MultiSpanSassRuntimeException =
    MultiSpanSassRuntimeException(sassMessage, span, primaryLabel, secondarySpans, trace, urls)
}

/** An exception thrown during Sass parsing. */
class SassFormatException(
  sassMessage: String,
  span:        FileSpan,
  loadedUrls:  Set[String] = Set.empty
) extends SassException(sassMessage, span, loadedUrls) {

  /** The source text that caused the error. */
  def source: String = span.file.text

  /** The offset in the source where the error occurred. */
  def offset: Int = span.start.offset

  override def withAdditionalSpan(additionalSpan: FileSpan, label: String): SassException =
    MultiSpanSassFormatException(sassMessage, span, "", Map(additionalSpan -> label), loadedUrls)

  override def withLoadedUrls(urls: Set[String]): SassFormatException =
    SassFormatException(sassMessage, span, urls)
}

/** A SassFormatException with secondary spans. */
class MultiSpanSassFormatException(
  sassMessage:        String,
  span:               FileSpan,
  val primaryLabel:   String,
  val secondarySpans: Map[FileSpan, String],
  loadedUrls:         Set[String] = Set.empty
) extends SassFormatException(sassMessage, span, loadedUrls) {

  override def withAdditionalSpan(additionalSpan: FileSpan, label: String): MultiSpanSassFormatException =
    MultiSpanSassFormatException(sassMessage, span, primaryLabel, secondarySpans + (additionalSpan -> label), loadedUrls)

  override def withLoadedUrls(urls: Set[String]): MultiSpanSassFormatException =
    MultiSpanSassFormatException(sassMessage, span, primaryLabel, secondarySpans, urls)
}

/** An exception thrown by SassScript (no span yet; caught and wrapped with span later). */
class SassScriptException(
  val sassMessage: String,
  argumentName:    Option[String] = None
) extends RuntimeException(
      argumentName.fold(sassMessage)(name => s"$$$name: $sassMessage")
    ) {

  /** The full message including argument name. */
  def fullMessage: String = getMessage

  /** Converts this to a SassException with the given span. */
  def withSpan(span: FileSpan): SassException =
    SassException(fullMessage, span)

  override def toString: String = s"$fullMessage\n\nBUG: This should include a source span!"
}

/** A SassScriptException with secondary spans. */
class MultiSpanSassScriptException(
  sassMessage:        String,
  val primaryLabel:   String,
  val secondarySpans: Map[FileSpan, String]
) extends SassScriptException(sassMessage) {

  override def withSpan(span: FileSpan): MultiSpanSassException =
    MultiSpanSassException(fullMessage, span, primaryLabel, secondarySpans)
}
