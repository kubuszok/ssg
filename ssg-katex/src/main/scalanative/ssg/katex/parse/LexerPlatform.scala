package ssg
package katex
package parse

private[parse] object LexerPlatform {
  // Scala Native re2 doesn't support \x{HHHH} or \uHHHH for supplementary chars.
  // Use UTF-16 surrogate pair character class instead.
  val supplementaryCharPattern: String = "[\uD800-\uDBFF][\uDC00-\uDFFF]"
}
