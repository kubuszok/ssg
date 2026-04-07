package ssgdev
package quality

import java.io.{ BufferedReader, File, FileReader }
import scala.collection.mutable.ListBuffer
import scala.util.matching.Regex

// Shortcut / stub / shim detector.
//
// Scans a Scala source file for textual patterns that indicate shortcut
// implementations. The pattern set is deliberately broad: an agent routing
// around one marker (e.g. deferred instead of TODO) falls into another.
//
// Patterns are classified by line context so legitimate identifiers are
// not flagged. "Always" rules fire anywhere on a line. "CommentOnly" rules
// fire only when the line begins with a comment marker.
//
// The Apache license header block at the top of every SSG file is skipped
// so boilerplate phrases do not count.
//
// Phase 1 of plan: extends the main-repo Shortcuts.scala with the 5 cheat
// patterns discovered by the 9-agent audit campaign. The new rules are
// listed under "// Phase 1 additions" below.
object Shortcuts {

  final case class Hit(file: String, line: Int, pattern: String, text: String)

  private sealed trait PatternRule {
    def name:  String
    def regex: Regex
  }
  private final case class Always(name: String, regex: Regex) extends PatternRule
  private final case class CommentOnly(name: String, regex: Regex) extends PatternRule

  /** Distinctive markers that are rare as legitimate identifiers and
    * therefore safe to match anywhere in the line.
    */
  private val alwaysRules: List[Always] = List(
    Always("todo",            """\bTODO\b""".r),
    Always("fixme",           """\bFIXME\b""".r),
    Always("hack",            """\bHACK\b""".r),
    Always("xxx",             """\bXXX\b""".r),
    Always("unsupported-op",  """UnsupportedOperationException""".r),
    Always("not-implemented", """throw\s+new\s+NotImplementedError""".r),
    Always("scala-unimpl",    """\?\?\?""".r),
    Always("catch-throwable", """catch\s*\{[^}]*case\s+_?\s*:?\s*Throwable\b""".r),
    Always("cssStub-fn",      """\bcssStub\s*\(""".r),
    // -------- Phase 1 additions (anti-cheat patterns from audit) --------
    // Pattern 4: null casts to satisfy non-Nullable trait returns
    Always("null-cast",              """null\.asInstanceOf\[""".r),
    Always("nullable-null-fallback", """Nullable\.empty\.getOrElse\(null\)""".r),
    Always("get-or-else-null",       """\.getOrElse\(null\)""".r),
    Always("this-null-arg",          """\bthis\(\s*null\s*[,)]""".r),
    // Pattern 1: incomplete return/break migration
    Always("flag-break-var",         """var\s+(done|continue|finished|stop|canBreak|shouldBreak|shouldContinue)\s*[:=]""".r),
    Always("scalastyle-ignore-return", """return\s+.*//\s*scalastyle:ignore""".r),
    // "Not yet ported" exceptions thrown at runtime
    Always("throw-not-yet",          """throw\s+new\s+(Runtime|IllegalState)Exception\(\s*"[^"]*not\s+yet""".r),
    // Misleading @nowarn marking a field as "unused stub" — but if the
    // field is in use, this is a stale comment from the conversion phase.
    // Only flag the @nowarn line itself; cross-checking actual usage is
    // done at audit-skill level, not here.
    Always("nowarn-stub",            """@nowarn\("msg=unused[^"]*"\)\s*//.*\bstub\b""".r)
  )

  /** Softer markers that appear in legitimate code as variable names or
    * class parts (e.g. `PlaceholderSelector`, `val simplified = …`). These
    * only count when the line is a comment.
    */
  private val commentRules: List[CommentOnly] = List(
    CommentOnly("stub-comment",          """(?i)\bstub\b""".r),
    CommentOnly("simplified-comment",    """(?i)\bsimplified\b""".r),
    CommentOnly("minimal-comment",       """(?i)\bminimal(?:\s+viable)?\b""".r),
    CommentOnly("placeholder-comment",   """(?i)\bplaceholder\b""".r),
    CommentOnly("tbd-comment",           """(?i)\bTBD\b""".r),
    CommentOnly("pending-comment",       """(?i)\bpending\b""".r),
    CommentOnly("shim-comment",          """(?i)\bshim\b""".r),
    CommentOnly("best-effort-comment",   """(?i)\bbest[-\s]effort\b""".r),
    CommentOnly("approximation-comment", """(?i)\bapproximation\b""".r),
    CommentOnly("deferred-comment",      """(?i)\bdeferred\b""".r),
    CommentOnly("phase-n-comment",       """(?i)Phase\s*\d+""".r),
    CommentOnly("not-yet-comment",       """(?i)\bnot\s+yet\b""".r),
    // -------- Phase 1 additions (anti-cheat patterns from audit) --------
    // Pattern 2: comments that hint at unfinished work
    CommentOnly("would-be-used",   """(?i)would\s+(be\s+)?used\s+here""".r),
    CommentOnly("handled-below",   """(?i)handled\s+below""".r),
    CommentOnly("for-now-comment", """(?i)\bfor\s+now\b""".r),
    // Pattern 1: break/return rewritten to a comment without actually breaking
    CommentOnly("break-comment",     """^\s*//\s*break\b""".r),
    CommentOnly("return-comment",    """^\s*//\s*return\b""".r),
    CommentOnly("would-do-comment",  """(?i)would\s+(do|implement|handle)\b""".r),
    CommentOnly("aspirational-com",  """(?i)\baspirational\b""".r)
  )

  /** Scan a single file and return all matching hits. */
  def scanFile(path: String): List[Hit] = {
    val file = new File(path)
    if (!file.exists()) return Nil
    val buf = ListBuffer.empty[Hit]
    val reader = new BufferedReader(new FileReader(file))
    try {
      var lineNum = 0
      var inBlockComment = false
      var inHeader = true
      var line = reader.readLine()
      while (line != null) {
        lineNum += 1
        val trimmed = line.trim

        // Skip the Apache 2.0 header block: lines 1-N until we exit the
        // initial block-comment. Header always terminates with a close
        // comment on its own line.
        if (inHeader) {
          if (lineNum == 1 && !trimmed.startsWith("/*") && !trimmed.startsWith("/**")) {
            inHeader = false
          } else if (trimmed == "*/" || trimmed.endsWith(CloseComment)) {
            // Header ends on this line; subsequent lines are real source.
            inHeader = false
          }
        }

        if (!inHeader) {
          // Track block-comment state so a soft marker inside a block
          // comment still counts as a commentOnly hit.
          val lineIsComment = isCommentLine(trimmed) || inBlockComment

          // Update block-comment state for the NEXT line.
          val OpenComment = "/" + "*"
          if (trimmed.contains(OpenComment) && !trimmed.contains(CloseComment)) inBlockComment = true
          else if (inBlockComment && trimmed.contains(CloseComment)) inBlockComment = false

          // Always rules.
          for (rule <- alwaysRules)
            if (rule.regex.findFirstIn(line).isDefined)
              buf += Hit(path, lineNum, rule.name, line.trim.take(120))

          // CommentOnly rules.
          if (lineIsComment) {
            for (rule <- commentRules)
              if (rule.regex.findFirstIn(line).isDefined)
                buf += Hit(path, lineNum, rule.name, line.trim.take(120))
          }
        }
        line = reader.readLine()
      }
    } finally reader.close()
    buf.toList
  }

  // A line is a comment if its first non-whitespace chars are a line
  // comment marker, a block-comment open, a scaladoc asterisk, or a
  // block-comment close.
  private def isCommentLine(trimmed: String): Boolean = {
    val OpenComment = "/" + "*"
    if (trimmed.startsWith("//")) return true
    if (trimmed.startsWith(OpenComment)) return true
    if (trimmed.startsWith(CloseComment)) return true
    if (trimmed.startsWith("*") && !trimmed.startsWith(CloseComment)) return true
    false
  }

  private val CloseComment: String = "*" + "/"

  /** Scan every `.scala` file under a directory recursively. */
  def scanDir(dir: String): List[Hit] = {
    val root = new File(dir)
    if (!root.exists()) return Nil
    val buf = ListBuffer.empty[Hit]
    walk(root, buf)
    buf.toList
  }

  private def walk(f: File, buf: ListBuffer[Hit]): Unit = {
    if (f.isDirectory) {
      val kids = f.listFiles()
      if (kids != null) kids.foreach(walk(_, buf))
    } else if (f.getName.endsWith(".scala")) {
      buf ++= scanFile(f.getAbsolutePath)
    }
  }
}
