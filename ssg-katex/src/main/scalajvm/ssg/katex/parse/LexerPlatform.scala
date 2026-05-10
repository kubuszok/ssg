package ssg
package katex
package parse

private[parse] object LexerPlatform {
  // JVM java.util.regex supports \x{HHHH} for supplementary chars
  val supplementaryCharPattern: String = "[\\x{10000}-\\x{10FFFF}]"
}
