package ssgdev
package port

import java.io.{ BufferedReader, File, FileReader }
import scala.collection.mutable.ListBuffer
import scala.util.matching.Regex

/** Stale-stub detector — Phase 3 of the anti-cheat plan.
  *
  * Scans every Scala file for lines containing "not yet ported" / "would
  * be used here" / "would do" / "TODO" comments that mention a specific
  * identifier, then cross-references the identifier against the current
  * SSG codebase. If the identifier IS now defined somewhere, the comment
  * is stale — the stub should be replaced with a real call to the
  * now-available dependency.
  *
  * Examples of stale stubs caught by this detector (real cases from the
  * gap audit):
  *
  *   ast/RefNode.scala:167-169
  *     // Parser.REFERENCES is not yet ported
  *     return null
  *   --> Parser.REFERENCES is now defined in parser/Parser.scala:224
  *
  *   ast/Heading.scala:53-65
  *     // HEADER_ID_REF_TEXT_TRIM_LEADING_SPACES is not yet ported
  *     trimLeadingSpaces = true
  *   --> HEADER_ID_REF_TEXT_TRIM_LEADING_SPACES is now defined in
  *       html/HtmlRenderer.scala:161
  *
  * The detector indexes all `def`/`val`/`var`/`class`/`trait`/`object`/
  * `enum` definitions in the codebase, then for each suspect comment
  * extracts capitalized identifiers (`PascalCase`, `SCREAMING_SNAKE_CASE`,
  * and `Class.MEMBER` paths) and checks each one against the index.
  */
object StaleStubs {

  final case class StaleHit(file: String, line: Int, identifier: String, definedAt: String, comment: String)

  // Comments that hint at unfinished work — every line matching one of
  // these is a candidate for cross-reference.
  private val suspectComment: Regex =
    """(?i)\b(not\s+yet\s+ported|would\s+be\s+used\s+here|would\s+(do|implement|handle)|handled\s+below|for\s+now|stale|TODO)\b""".r

  // Identifier patterns we look for in suspect lines.
  // SCREAMING_SNAKE_CASE constants (3+ chars, includes underscore)
  private val screamingSnake: Regex =
    """\b([A-Z][A-Z0-9_]{2,})\b""".r

  // PascalCase.MEMBER references (e.g. `Parser.REFERENCES`)
  private val classMember: Regex =
    """\b([A-Z][A-Za-z0-9_]+)\.([A-Z][A-Za-z0-9_]+)\b""".r

  /** Scan a single file. Cross-references against the provided index.
    *
    * Two-pass approach:
    *   Pass 1 — read every non-header line into a buffer and find all line
    *            numbers that contain a suspect-comment marker.
    *   Pass 2 — for each marker line, look at the line itself plus the
    *            previous 3 and next 3 lines (±3 window) for candidate
    *            identifiers. Cross-check each against the index. This
    *            catches multi-line comments where the identifier is on
    *            one line and "not yet ported" is on a sibling line.
    */
  def scanFile(path: String, index: Set[String]): List[StaleHit] = {
    val file = new File(path)
    if (!file.exists()) return Nil

    // Pass 1: read all lines, skipping the Apache license header.
    val lines = ListBuffer.empty[String]
    val reader = new BufferedReader(new FileReader(file))
    try {
      var inHeader = true
      var line = reader.readLine()
      var lineNum = 0
      while (line != null) {
        lineNum += 1
        val trimmed = line.trim
        if (inHeader) {
          if (lineNum == 1 && !trimmed.startsWith("/*")) inHeader = false
          else if (trimmed == "*/" || trimmed.endsWith("*" + "/")) {
            inHeader = false
            lines += "" // placeholder so line numbers stay 1-based
          } else {
            lines += "" // header line — placeholder
          }
        } else {
          lines += line
        }
        line = reader.readLine()
      }
    } finally reader.close()

    val out = ListBuffer.empty[StaleHit]
    val n = lines.size
    for (i <- 0 until n) {
      val cur = lines(i)
      if (cur.nonEmpty && suspectComment.findFirstIn(cur).isDefined) {
        // Window: ±3 lines around the marker.
        val winStart = math.max(0, i - 3)
        val winEnd   = math.min(n - 1, i + 3)
        val candidates = ListBuffer.empty[String]
        for (j <- winStart to winEnd) {
          val l = lines(j)
          if (l.nonEmpty) {
            for (m <- classMember.findAllMatchIn(l)) {
              candidates += s"${m.group(1)}.${m.group(2)}"
              candidates += m.group(2)
            }
            for (m <- screamingSnake.findAllMatchIn(l)) {
              val id = m.group(1)
              if (!ExcludeIdentifiers.contains(id)) candidates += id
            }
          }
        }
        for (id <- candidates.distinct) {
          if (index.contains(id)) {
            out += StaleHit(path, i + 1, id, "(in index)", cur.trim.take(120))
          }
        }
      }
    }
    out.toList
  }

  /** Build an index of every defined identifier under the given source
    * directories. The index is the union of:
    *   - every Scala def/val/var/class/trait/object/enum name
    *   - every SCREAMING_SNAKE_CASE constant
    */
  def buildIndex(srcDirs: List[String]): Set[String] = {
    val out = scala.collection.mutable.Set.empty[String]
    for (dir <- srcDirs) {
      val root = new File(dir)
      if (root.exists()) walkBuild(root, out)
    }
    out.toSet
  }

  private def walkBuild(f: File, out: scala.collection.mutable.Set[String]): Unit = {
    if (f.isDirectory) {
      val kids = f.listFiles()
      if (kids != null) kids.foreach(walkBuild(_, out))
    } else if (f.getName.endsWith(".scala")) {
      out ++= compare.Methods.extractScalaMethods(f.getAbsolutePath)
      // Also extract SCREAMING_SNAKE_CASE constants from the file
      val text = readText(f.getAbsolutePath)
      for (m <- screamingSnake.findAllMatchIn(text)) {
        val id = m.group(1)
        if (!ExcludeIdentifiers.contains(id)) out += id
      }
    }
  }

  /** Scan every `.scala` file under a directory recursively. */
  def scanDir(dir: String, index: Set[String]): List[StaleHit] = {
    val out = ListBuffer.empty[StaleHit]
    val root = new File(dir)
    if (!root.exists()) return Nil
    walkScan(root, index, out)
    out.toList
  }

  private def walkScan(f: File, index: Set[String], out: ListBuffer[StaleHit]): Unit = {
    if (f.isDirectory) {
      val kids = f.listFiles()
      if (kids != null) kids.foreach(walkScan(_, index, out))
    } else if (f.getName.endsWith(".scala")) {
      out ++= scanFile(f.getAbsolutePath, index)
    }
  }

  private def readText(path: String): String = {
    val f = new File(path)
    if (!f.exists()) return ""
    val reader = new BufferedReader(new FileReader(f))
    try {
      val buf = new StringBuilder
      var line = reader.readLine()
      while (line != null) {
        buf.append(line).append('\n')
        line = reader.readLine()
      }
      buf.toString
    } finally reader.close()
  }

  /** Identifiers we never want to flag as "stale-referenced".
    * These are common SCREAMING_SNAKE marker tokens or build constants.
    */
  private val ExcludeIdentifiers: Set[String] = Set(
    "TODO", "FIXME", "HACK", "XXX", "NOTE", "BUG", "WARNING",
    "API", "URL", "URI", "UUID", "JSON", "XML", "HTML", "CSS", "JS", "MD",
    "SSG", "JVM", "JS", "AST", "DOM", "CLI", "CI", "OK", "ERR",
    "TRUE", "FALSE", "NULL", "NIL"
  )
}
