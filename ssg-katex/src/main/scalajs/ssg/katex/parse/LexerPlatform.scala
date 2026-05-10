package ssg
package katex
package parse

private[parse] object LexerPlatform {
  // JS regex engine supports \u{HHHH} for supplementary chars
  // but Scala.js java.util.regex.Pattern uses \x{HHHH}
  val supplementaryCharPattern: String = "[\\x{10000}-\\x{10FFFF}]"
}
