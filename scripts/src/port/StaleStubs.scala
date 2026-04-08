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
    * Streaming implementation with a 7-line ring buffer (3 before +
    * current + 3 after). The detector emits a stale-stub when the
    * MIDDLE slot of the ring buffer contains a suspect comment, and
    * any of the 7 slots contains an identifier that's in the index.
    *
    * This way the file is read line by line and at most 7 lines are
    * held in memory regardless of file size.
    */
  def scanFile(path: String, index: Set[String]): List[StaleHit] = {
    val file = new File(path)
    if (!file.exists()) return Nil

    val out = ListBuffer.empty[StaleHit]
    // Ring buffer holding the last 7 lines, with their original line numbers.
    // Slot 3 is the "current" position whose comment we test.
    val ring = Array.fill[String](7)("")
    val ringNum = Array.fill[Int](7)(0)
    var head = 0 // next write position
    var filled = 0 // how many slots are populated (1..7)

    val reader = new BufferedReader(new FileReader(file))
    try {
      var lineNum = 0
      var inHeader = true
      var line = reader.readLine()
      while (line != null) {
        lineNum += 1
        val trimmed = line.trim
        // Skip Apache header
        if (inHeader) {
          if (lineNum == 1 && !trimmed.startsWith("/*")) inHeader = false
          else if (trimmed == "*/" || trimmed.endsWith("*" + "/")) inHeader = false
        }

        if (!inHeader) {
          // Append line to ring buffer
          ring(head) = line
          ringNum(head) = lineNum
          head = (head + 1) % 7
          if (filled < 7) filled += 1

          // When the buffer is full, check the slot 3 positions before head
          // (the current "middle" of the window).
          if (filled == 7) {
            val midSlot = (head + 3) % 7
            checkMiddle(path, ring, ringNum, midSlot, index, out)
          }
        }
        line = reader.readLine()
      }

      // Drain: process the last 3 lines that never had 3 successors.
      // For each remaining slot from head-3 to head-1 (in ring order),
      // check the middle.
      if (filled > 0) {
        var drained = 0
        while (drained < math.min(3, filled)) {
          // Insert empty padding so we can shift the window forward.
          ring(head) = ""
          ringNum(head) = 0
          head = (head + 1) % 7
          if (filled < 7) filled += 1
          if (filled == 7) {
            val midSlot = (head + 3) % 7
            checkMiddle(path, ring, ringNum, midSlot, index, out)
          }
          drained += 1
        }
      }
    } finally reader.close()
    out.toList
  }

  /** Check whether the line at `midSlot` in the ring buffer contains a
    * suspect comment, and if so cross-reference identifiers from the
    * full ±3 window against the index.
    */
  private def checkMiddle(
    path: String,
    ring: Array[String],
    ringNum: Array[Int],
    midSlot: Int,
    index: Set[String],
    out: ListBuffer[StaleHit]
  ): Unit = {
    val cur = ring(midSlot)
    val midLineNum = ringNum(midSlot)
    if (cur.isEmpty || midLineNum == 0) return
    if (suspectComment.findFirstIn(cur).isEmpty) return

    // Walk all 7 ring slots in order — the order doesn't matter for
    // identifier extraction, only for the marker-line check above.
    val seen = scala.collection.mutable.Set.empty[String]
    var slot = 0
    while (slot < 7) {
      val l = ring(slot)
      if (l.nonEmpty) {
        for (m <- classMember.findAllMatchIn(l)) {
          val full = s"${m.group(1)}.${m.group(2)}"
          val mem  = m.group(2)
          if (!seen.contains(full) && index.contains(full)) {
            out += StaleHit(path, midLineNum, full, "(in index)", cur.trim.take(120))
            seen += full
          }
          if (!seen.contains(mem) && index.contains(mem)) {
            out += StaleHit(path, midLineNum, mem, "(in index)", cur.trim.take(120))
            seen += mem
          }
        }
        for (m <- screamingSnake.findAllMatchIn(l)) {
          val id = m.group(1)
          if (!ExcludeIdentifiers.contains(id) && !seen.contains(id) && index.contains(id)) {
            out += StaleHit(path, midLineNum, id, "(in index)", cur.trim.take(120))
            seen += id
          }
        }
      }
      slot += 1
    }
  }

  // ---------- Streaming line-level definition patterns ---------------------
  // These run on a single line at a time, so the buildIndex pass can read
  // each file line-by-line and never holds more than one line in memory.
  // The (?m)^... anchors from the multi-line variants in Methods.scala are
  // unnecessary here because input is one line at a time.

  private val lineScalaDef: Regex =
    """^[ \t]*(?:override\s+|final\s+|private(?:\[[^\]]+\])?\s+|protected(?:\[[^\]]+\])?\s+|implicit\s+|lazy\s+|inline\s+|transparent\s+)*def\s+(?:`([^`]+)`|([A-Za-z_][A-Za-z0-9_$]*))""".r

  private val lineScalaVal: Regex =
    """^[ \t]{0,4}(?:override\s+|final\s+|private(?:\[[^\]]+\])?\s+|protected(?:\[[^\]]+\])?\s+|implicit\s+|lazy\s+|inline\s+)*(?:val|var)\s+([A-Za-z_][A-Za-z0-9_$]*)""".r

  private val lineScalaType: Regex =
    """^[ \t]*(?:sealed\s+|final\s+|abstract\s+|private(?:\[[^\]]+\])?\s+|protected(?:\[[^\]]+\])?\s+|open\s+|case\s+)*(?:class|trait|object|enum)\s+([A-Za-z_][A-Za-z0-9_$]*)""".r

  /** Build an index of every defined identifier under the given source
    * directories. The index is the union of:
    *   - every Scala def/val/var/class/trait/object/enum name
    *   - every SCREAMING_SNAKE_CASE constant
    *
    * Streaming implementation: opens each file once, reads it line by
    * line, applies patterns per line, accumulates into the global set,
    * then closes the file. The transient allocations per file are bounded
    * to the size of one line, not one file.
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
      streamFileForIndex(f.getAbsolutePath, out)
    }
  }

  /** Single-pass streaming index extraction: read line by line, apply
    * each definition pattern + SCREAMING_SNAKE pattern, dump matches into
    * the shared mutable set, drop the line. Never holds more than one
    * line in memory.
    */
  private def streamFileForIndex(path: String, out: scala.collection.mutable.Set[String]): Unit = {
    val reader = new BufferedReader(new FileReader(path))
    try {
      var line = reader.readLine()
      while (line != null) {
        // Definition patterns
        for (m <- lineScalaDef.findAllMatchIn(line)) {
          val name = Option(m.group(1)).getOrElse(m.group(2))
          if (name != null) out += name
        }
        for (m <- lineScalaVal.findAllMatchIn(line)) {
          val name = m.group(1)
          if (name != null) out += name
        }
        for (m <- lineScalaType.findAllMatchIn(line)) {
          val name = m.group(1)
          if (name != null) out += name
        }
        // SCREAMING_SNAKE_CASE constant references
        for (m <- screamingSnake.findAllMatchIn(line)) {
          val id = m.group(1)
          if (!ExcludeIdentifiers.contains(id)) out += id
        }
        line = reader.readLine()
      }
    } finally reader.close()
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
