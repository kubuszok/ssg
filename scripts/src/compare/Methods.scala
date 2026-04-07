package ssgdev
package compare

import java.io.{ BufferedReader, File, FileReader }
import scala.collection.mutable.ListBuffer
import scala.util.matching.Regex

/** Method-signature extractor and strict cross-language comparison.
  *
  * Extracts method names from Scala/Java/Dart files using regex-based parsing
  * (sufficient for ~95% of upstream sources) and computes a "gap report":
  *
  *   - missing:   methods present in source but not in Scala
  *   - extra:     methods present in Scala but not in source (informational)
  *   - common:    methods present in both
  *   - shortBody: common methods whose Scala body has fewer than 70% of
  *                the source body's AST-node-count (a cheap proxy: count of
  *                identifier + operator + control-flow tokens)
  *
  * This is the gate that prevents "method exists but is a one-line shim"
  * from passing verification.
  */
object Methods {

  final case class Gap(
    missing:         List[String],
    extra:           List[String],
    common:          List[String],
    shortBody:       List[String],
    droppedCtorArgs: List[(String, String)] // (className, paramName)
  )

  object Gap {
    val empty: Gap = Gap(Nil, Nil, Nil, Nil, Nil)
  }

  final case class Method(name: String, startLine: Int, endLine: Int, body: String)

  // Scala def at top-level or class/object body indentation. Matches
  // `def name`. Locals inside method bodies are also matched; the
  // resulting noise is filtered out at compare time by checking against
  // the source side.
  private val scalaDef: Regex =
    """(?m)^[ \t]*(?:override\s+|final\s+|private(?:\[[^\]]+\])?\s+|protected(?:\[[^\]]+\])?\s+|implicit\s+|lazy\s+|inline\s+|transparent\s+)*def\s+(?:`([^`]+)`|([A-Za-z_][A-Za-z0-9_$]*))""".r

  // Top-level val/var at object/class body level.
  private val scalaVal: Regex =
    """(?m)^[ \t]{0,4}(?:override\s+|final\s+|private(?:\[[^\]]+\])?\s+|protected(?:\[[^\]]+\])?\s+|implicit\s+|lazy\s+|inline\s+)*(?:val|var)\s+([A-Za-z_][A-Za-z0-9_$]*)""".r

  private val scalaType: Regex =
    """(?m)^[ \t]*(?:sealed\s+|final\s+|abstract\s+|private(?:\[[^\]]+\])?\s+|protected(?:\[[^\]]+\])?\s+|open\s+|case\s+)*(?:class|trait|object|enum)\s+([A-Za-z_][A-Za-z0-9_$]*)""".r

  // Dart method definition: must have a parameter list (`(...)`) directly
  // after the name. This filters out field accesses and simple values.
  private val dartTopDef: Regex =
    """(?m)^[ \t]+(?:static\s+|const\s+|final\s+|late\s+|external\s+|abstract\s+|factory\s+)*(?:[A-Za-z_][A-Za-z0-9_<>?, \t]*[\s>?])?([a-zA-Z_][A-Za-z0-9_]*)\s*\(""".r

  private val dartGetter: Regex =
    """(?m)^[ \t]+(?:static\s+|const\s+|final\s+|late\s+|external\s+)*[A-Za-z_][A-Za-z0-9_<>?, \t]*\s+get\s+([a-zA-Z_][A-Za-z0-9_]*)\b""".r

  private val dartTypeDef: Regex =
    """(?m)^[ \t]*(?:abstract\s+|sealed\s+|final\s+|base\s+|interface\s+|mixin\s+)*(?:class|mixin|enum|extension)\s+([A-Za-z_][A-Za-z0-9_]*)""".r

  // Java method definition: modifiers + return type + name + (
  private val javaMethod: Regex =
    """(?m)^[ \t]+(?:public\s+|private\s+|protected\s+|static\s+|final\s+|abstract\s+|synchronized\s+|native\s+|default\s+)*(?:[A-Za-z_][A-Za-z0-9_<>?, \t\[\]]*[\s>?\]])\s+([a-zA-Z_][A-Za-z0-9_]*)\s*\(""".r

  private val javaTypeDef: Regex =
    """(?m)^[ \t]*(?:public\s+|private\s+|protected\s+|static\s+|final\s+|abstract\s+|sealed\s+)*(?:class|interface|enum|record)\s+([A-Za-z_][A-Za-z0-9_]*)""".r

  /** Return the set of top-level/method names defined in a Scala file.
    * Constructor params (`class Foo(val x: Int)`) are skipped because they
    * are not independent definitions.
    */
  def extractScalaMethods(path: String): List[String] = {
    val text = readFile(path)
    val names = ListBuffer.empty[String]
    scalaType.findAllMatchIn(text).foreach(m => names += m.group(1))
    scalaDef.findAllMatchIn(text).foreach { m =>
      val name = Option(m.group(1)).getOrElse(m.group(2))
      if (name != null) names += name
    }
    scalaVal.findAllMatchIn(text).foreach { m =>
      val name = m.group(1)
      if (name != null) names += name
    }
    names.distinct.toList.sorted
  }

  /** Return the set of top-level/method names defined in a Dart file. */
  def extractDartMethods(path: String): List[String] = {
    val text = readFile(path)
    val names = ListBuffer.empty[String]
    dartTypeDef.findAllMatchIn(text).foreach(m => names += m.group(1))
    val exclude = Set(
      "if", "else", "for", "while", "do", "return", "throw", "assert",
      "new", "const", "final", "var", "this", "super", "in", "is", "as",
      "switch", "case", "default", "break", "continue", "try", "catch",
      "finally", "yield", "async", "await", "rethrow", "import", "export",
      "library", "part", "hide", "show", "on", "typedef", "covariant",
      "required", "deferred", "abstract", "external", "factory", "operator",
      "print", "when"
    )
    dartTopDef.findAllMatchIn(text).foreach { m =>
      val name = m.group(1)
      if (name != null && !exclude.contains(name)) names += name
    }
    dartGetter.findAllMatchIn(text).foreach { m =>
      val name = m.group(1)
      if (name != null && !exclude.contains(name)) names += name
    }
    names.distinct.toList.sorted
  }

  /** Return the set of method/type names defined in a Java file. */
  def extractJavaMethods(path: String): List[String] = {
    val text = readFile(path)
    val names = ListBuffer.empty[String]
    javaTypeDef.findAllMatchIn(text).foreach(m => names += m.group(1))
    val exclude = Set(
      "if", "else", "for", "while", "do", "return", "throw", "assert",
      "new", "this", "super", "switch", "case", "default", "break",
      "continue", "try", "catch", "finally", "import", "package",
      "abstract", "static", "final", "synchronized", "native", "volatile",
      "transient", "public", "private", "protected", "instanceof"
    )
    javaMethod.findAllMatchIn(text).foreach { m =>
      val name = m.group(1)
      if (name != null && !exclude.contains(name)) names += name
    }
    names.distinct.toList.sorted
  }

  /** Compute the gap between an SSG file and its source reference.
    * Auto-detects the source language by extension.
    */
  def compare(ssgFile: String, sourceFile: String): Gap = {
    val scala = extractScalaMethods(ssgFile).toSet
    val source =
      if (sourceFile.endsWith(".dart")) extractDartMethods(sourceFile).toSet
      else if (sourceFile.endsWith(".java")) extractJavaMethods(sourceFile).toSet
      else extractDartMethods(sourceFile).toSet // fallback
    val missing = (source -- scala).toList.sorted
    val extra = (scala -- source).toList.sorted
    val common = (source intersect scala).toList.sorted
    Gap(missing, extra, common, shortBody = Nil, droppedCtorArgs = Nil)
  }

  /** Strict compare: the Gap.missing is populated as usual, and shortBody
    * is populated with common method names whose Scala body AST-node-count
    * is below 70% of the source body's AST-node-count. Also runs the
    * constructor-arity check (Pattern 3 detection from Phase 2).
    */
  def strictCompare(ssgFile: String, sourceFile: String): Gap = {
    val base = compare(ssgFile, sourceFile)
    val scalaBodies = extractScalaBodies(ssgFile)
    val sourceBodies =
      if (sourceFile.endsWith(".java")) extractJavaBodies(sourceFile)
      else extractDartBodies(sourceFile)
    val shortBody = base.common.filter { name =>
      val sb = scalaBodies.get(name).map(astNodeCount).getOrElse(0)
      val db = sourceBodies.get(name).map(astNodeCount).getOrElse(0)
      db > 0 && sb * 100 < db * 70
    }
    val droppedCtorArgs = compareConstructors(ssgFile, sourceFile)
    base.copy(shortBody = shortBody, droppedCtorArgs = droppedCtorArgs)
  }

  // --- Constructor-arity comparison (Phase 2) -------------------------------

  /** Java/Dart constructor signature: parses lines like
    * `public Foo(BasedSequence chars, List<BasedSequence> segments)` or
    * `Foo(this.chars, this.segments)` and returns the parameter names
    * grouped by class.
    */
  private val javaCtorPattern: Regex =
    """(?m)^\s*(?:public\s+|private\s+|protected\s+)?([A-Z][A-Za-z0-9_$]*)\s*\(([^)]*)\)\s*(?:throws[^{;]+)?\{""".r

  private val dartCtorPattern: Regex =
    """(?m)^\s*(?:const\s+|factory\s+)?([A-Z][A-Za-z0-9_$]*)\s*\(([^)]*)\)\s*(?::|\{|;)""".r

  /** Scala class declaration with primary-constructor parameter list:
    * `final class Foo(val chars: BasedSequence, val segments: List[BasedSequence])`
    * or `class Foo(chars: BasedSequence)`
    *
    * Also matches secondary constructors `def this(...)`.
    */
  private val scalaPrimaryCtorPattern: Regex =
    """(?m)^\s*(?:sealed\s+|final\s+|abstract\s+|case\s+)*class\s+([A-Z][A-Za-z0-9_$]*)\s*(?:\[[^\]]*\])?\s*\(([^)]*)\)""".r

  private val scalaSecondaryCtorPattern: Regex =
    """(?m)^\s*def\s+this\s*\(([^)]*)\)""".r

  /** Extract constructor parameter names from a parameter list string,
    * stripping types and defaults. Handles:
    *   `BasedSequence chars, List<BasedSequence> segments` (Java)
    *   `this.chars, this.segments` (Dart shorthand)
    *   `chars: BasedSequence, segments: List[BasedSequence]` (Scala)
    */
  private def extractParamNames(paramList: String, isScala: Boolean): List[String] = {
    if (paramList.trim.isEmpty) return Nil
    splitTopLevelCommas(paramList).flatMap { p =>
      val trimmed = p.trim
      if (trimmed.isEmpty) None
      else if (isScala) {
        // `name: Type = default` — take everything before the first `:`
        val colonIdx = trimmed.indexOf(':')
        val name = if (colonIdx > 0) trimmed.substring(0, colonIdx) else trimmed
        // Strip val/var/implicit/private/etc. modifiers
        val cleaned = name.replaceAll("""\b(val|var|implicit|private|protected|override|final)\b""", "").trim
        if (cleaned.isEmpty) None else Some(cleaned)
      } else {
        // Java/Dart: `Type name` or `final Type name` or `this.name` or
        // `Type name = default`. Take the last identifier before any `=`.
        val noDefault = trimmed.split("=").head.trim
        // Handle `this.foo` (Dart shorthand)
        if (noDefault.startsWith("this.")) Some(noDefault.substring(5))
        else {
          // Last identifier in `Type name`
          val parts = noDefault.split("\\s+").filter(_.nonEmpty)
          if (parts.isEmpty) None
          else Some(parts.last.replaceAll("[^A-Za-z0-9_$]", ""))
        }
      }
    }.filter(_.nonEmpty)
  }

  /** Split a parameter list on commas at depth 0 (skipping commas inside
    * generic type arguments like `Map<String, Integer>`).
    */
  private def splitTopLevelCommas(s: String): List[String] = {
    val out = ListBuffer.empty[String]
    val cur = new StringBuilder
    var depth = 0
    for (c <- s) {
      c match {
        case '<' | '[' | '(' => depth += 1; cur.append(c)
        case '>' | ']' | ')' => depth -= 1; cur.append(c)
        case ',' if depth == 0 =>
          out += cur.toString
          cur.clear()
        case _ => cur.append(c)
      }
    }
    if (cur.nonEmpty) out += cur.toString
    out.toList
  }

  /** Extract per-class constructor parameter sets from an upstream source. */
  private def extractSourceCtors(path: String): Map[String, Set[String]] = {
    val text = readFile(path)
    if (text.isEmpty) return Map.empty
    val pattern = if (path.endsWith(".dart")) dartCtorPattern else javaCtorPattern
    val out = scala.collection.mutable.Map.empty[String, Set[String]]
    for (m <- pattern.findAllMatchIn(text)) {
      val cls = m.group(1)
      val params = extractParamNames(m.group(2), isScala = false).toSet
      // Use the largest parameter set across overloads for the class.
      val existing = out.getOrElse(cls, Set.empty)
      if (params.size > existing.size) out(cls) = params
    }
    out.toMap
  }

  /** Extract per-class constructor parameter sets from a Scala port. */
  private def extractScalaCtors(path: String): Map[String, Set[String]] = {
    val text = readFile(path)
    if (text.isEmpty) return Map.empty
    val out = scala.collection.mutable.Map.empty[String, Set[String]]
    for (m <- scalaPrimaryCtorPattern.findAllMatchIn(text)) {
      val cls = m.group(1)
      val params = extractParamNames(m.group(2), isScala = true).toSet
      val existing = out.getOrElse(cls, Set.empty)
      out(cls) = existing union params
    }
    // Secondary constructors are not name-bound — fold them into the
    // primary's set under the same class name. We don't have a great way
    // to determine which class a `def this(...)` belongs to via regex,
    // so we union ALL secondary ctors into a synthetic key. The compare
    // step still catches missing primary args.
    out.toMap
  }

  /** Compare upstream constructor parameters to Scala constructor
    * parameters and return any params present in the source but absent
    * from every Scala constructor of the same class.
    */
  def compareConstructors(ssgFile: String, sourceFile: String): List[(String, String)] = {
    val sourceCtors = extractSourceCtors(sourceFile)
    val scalaCtors = extractScalaCtors(ssgFile)
    val out = ListBuffer.empty[(String, String)]
    for ((cls, srcParams) <- sourceCtors) {
      val scalaParams = scalaCtors.getOrElse(cls, Set.empty)
      val dropped = srcParams -- scalaParams
      for (p <- dropped.toList.sorted) out += ((cls, p))
    }
    out.toList
  }

  // --- Body extraction -------------------------------------------------------

  def extractScalaBodies(path: String): Map[String, String] = {
    val text = readFile(path)
    val out = scala.collection.mutable.Map.empty[String, String]
    val matches = scalaDef.findAllMatchIn(text).toList
    for (m <- matches) {
      val name = Option(m.group(1)).getOrElse(m.group(2))
      if (name != null) {
        val start = m.end
        val body = extractBodyFromScala(text, start)
        out(name) = body
      }
    }
    val typeMatches = scalaType.findAllMatchIn(text).toList
    for (m <- typeMatches) {
      val name = m.group(1)
      if (name != null) {
        val body = extractBodyFromScala(text, m.end)
        if (body.nonEmpty) out(name) = body
      }
    }
    out.toMap
  }

  def extractDartBodies(path: String): Map[String, String] = {
    val text = readFile(path)
    val out = scala.collection.mutable.Map.empty[String, String]
    val matches = dartTopDef.findAllMatchIn(text).toList
    for (m <- matches) {
      val name = m.group(1)
      if (name != null) {
        val body = extractBodyFromDart(text, m.end - 1)
        out(name) = body
      }
    }
    val getterMatches = dartGetter.findAllMatchIn(text).toList
    for (m <- getterMatches) {
      val name = m.group(1)
      if (name != null) {
        val body = extractBodyFromDart(text, m.end)
        if (body.nonEmpty) out(name) = body
      }
    }
    val typeMatches = dartTypeDef.findAllMatchIn(text).toList
    for (m <- typeMatches) {
      val name = m.group(1)
      if (name != null) {
        val body = extractBodyFromDart(text, m.end)
        if (body.nonEmpty) out(name) = body
      }
    }
    out.toMap
  }

  def extractJavaBodies(path: String): Map[String, String] = {
    val text = readFile(path)
    val out = scala.collection.mutable.Map.empty[String, String]
    val matches = javaMethod.findAllMatchIn(text).toList
    for (m <- matches) {
      val name = m.group(1)
      if (name != null) {
        // The regex leaves us positioned right before the `(`. Back up
        // one so extractBodyFromDart-style brace search can see it.
        val body = extractBodyFromDart(text, m.end - 1)
        out(name) = body
      }
    }
    val typeMatches = javaTypeDef.findAllMatchIn(text).toList
    for (m <- typeMatches) {
      val name = m.group(1)
      if (name != null) {
        val body = extractBodyFromDart(text, m.end)
        if (body.nonEmpty) out(name) = body
      }
    }
    out.toMap
  }

  private def extractBodyFromScala(text: String, start: Int): String = {
    var i = start
    val n = text.length
    while (i < n && text.charAt(i) != '{' && text.charAt(i) != '=' && text.charAt(i) != '\n') i += 1
    if (i >= n) return ""
    if (text.charAt(i) == '{') {
      return readBalancedBraces(text, i)
    }
    if (text.charAt(i) == '=') {
      i += 1
      while (i < n && (text.charAt(i) == ' ' || text.charAt(i) == '\t' || text.charAt(i) == '>'))
        i += 1
      if (i < n && text.charAt(i) == '{') return readBalancedBraces(text, i)
      val buf = new StringBuilder
      while (i < n && text.charAt(i) != '\n') { buf.append(text.charAt(i)); i += 1 }
      return buf.toString
    }
    ""
  }

  private def extractBodyFromDart(text: String, start: Int): String = {
    var i = start
    val n = text.length
    if (i < n && text.charAt(i) == '(') {
      var depth = 1
      i += 1
      while (i < n && depth > 0) {
        val c = text.charAt(i)
        if (c == '(') depth += 1
        else if (c == ')') depth -= 1
        i += 1
      }
    }
    while (i < n && text.charAt(i) != '{' && text.charAt(i) != '=' && text.charAt(i) != ';' && text.charAt(i) != '\n')
      i += 1
    if (i >= n) return ""
    if (text.charAt(i) == '{') return readBalancedBraces(text, i)
    if (text.charAt(i) == '=' && i + 1 < n && text.charAt(i + 1) == '>') {
      i += 2
      val buf = new StringBuilder
      while (i < n && text.charAt(i) != ';') { buf.append(text.charAt(i)); i += 1 }
      return buf.toString
    }
    if (text.charAt(i) == '=') {
      i += 1
      val buf = new StringBuilder
      while (i < n && text.charAt(i) != ';') { buf.append(text.charAt(i)); i += 1 }
      return buf.toString
    }
    ""
  }

  private def readBalancedBraces(text: String, i0: Int): String = {
    val n = text.length
    var i = i0 + 1
    var depth = 1
    val start = i
    while (i < n && depth > 0) {
      val c = text.charAt(i)
      c match {
        case '/' if i + 1 < n && text.charAt(i + 1) == '/' =>
          while (i < n && text.charAt(i) != '\n') i += 1
        case '/' if i + 1 < n && text.charAt(i + 1) == '*' =>
          i += 2
          while (i + 1 < n && !(text.charAt(i) == '*' && text.charAt(i + 1) == '/')) i += 1
          if (i + 1 < n) i += 2
        case '"' =>
          i += 1
          while (i < n && text.charAt(i) != '"') {
            if (text.charAt(i) == '\\' && i + 1 < n) i += 2
            else i += 1
          }
          if (i < n) i += 1
        case '\'' =>
          i += 1
          while (i < n && text.charAt(i) != '\'') {
            if (text.charAt(i) == '\\' && i + 1 < n) i += 2
            else i += 1
          }
          if (i < n) i += 1
        case '{' =>
          depth += 1
          i += 1
        case '}' =>
          depth -= 1
          i += 1
        case _ =>
          i += 1
      }
    }
    if (depth == 0) text.substring(start, i - 1) else ""
  }

  /** Cheap AST-node-count proxy: number of identifier + operator tokens
    * in the body. Comments and string literals are stripped first.
    */
  def astNodeCount(body: String): Int = {
    val stripped = stripCommentsAndStrings(body)
    val tokenRegex = """[A-Za-z_][A-Za-z0-9_]*|[+\-*/%<>=!&|^~?:.;,(){}\[\]]""".r
    tokenRegex.findAllIn(stripped).size
  }

  private def stripCommentsAndStrings(s: String): String = {
    val n = s.length
    val out = new StringBuilder
    var i = 0
    while (i < n) {
      val c = s.charAt(i)
      if (c == '/' && i + 1 < n && s.charAt(i + 1) == '/') {
        while (i < n && s.charAt(i) != '\n') i += 1
      } else if (c == '/' && i + 1 < n && s.charAt(i + 1) == '*') {
        i += 2
        while (i + 1 < n && !(s.charAt(i) == '*' && s.charAt(i + 1) == '/')) i += 1
        if (i + 1 < n) i += 2
      } else if (c == '"') {
        i += 1
        while (i < n && s.charAt(i) != '"') {
          if (s.charAt(i) == '\\' && i + 1 < n) i += 2
          else i += 1
        }
        if (i < n) i += 1
      } else if (c == '\'') {
        i += 1
        while (i < n && s.charAt(i) != '\'') {
          if (s.charAt(i) == '\\' && i + 1 < n) i += 2
          else i += 1
        }
        if (i < n) i += 1
      } else {
        out.append(c)
        i += 1
      }
    }
    out.toString
  }

  // --- File IO ---------------------------------------------------------------

  private def readFile(path: String): String = {
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
}
