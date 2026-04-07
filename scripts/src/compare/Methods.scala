package ssgdev
package compare

import java.io.{ BufferedReader, File, FileReader }
import scala.collection.mutable.ListBuffer
import scala.util.matching.Regex

/** Method-signature extractor and strict cross-language comparison.
  *
  * Extracts method names from Scala and Dart files using regex-based parsing
  * (sufficient for 95% of dart-sass) and computes a "gap report":
  *
  *   - missing: methods present in dart but not in Scala
  *   - extra:   methods present in Scala but not in dart (informational)
  *   - common:  methods present in both
  *   - shortBody: common methods whose Scala body has fewer than 70% of
  *                the dart body's AST-node-count (a cheap proxy: count of
  *                identifier + operator + control-flow tokens)
  *
  * This is the gate that prevents "method exists but is a one-line shim"
  * from passing verification.
  */
object Methods {

  final case class Gap(
    missing:   List[String],
    extra:     List[String],
    common:    List[String],
    shortBody: List[String]
  )

  final case class Method(name: String, startLine: Int, endLine: Int, body: String)

  // Scala def at top-level or class/object body indentation. Matches
  // `def name`. Locals inside method bodies are also matched because
  // ssg-sass uses `def helper(...)` patterns; the resulting noise is
  // filtered out at compare time by checking against the dart side.
  private val scalaDef: Regex =
    """(?m)^[ \t]*(?:override\s+|final\s+|private(?:\[[^\]]+\])?\s+|protected(?:\[[^\]]+\])?\s+|implicit\s+|lazy\s+|inline\s+|transparent\s+)*def\s+(?:`([^`]+)`|([A-Za-z_][A-Za-z0-9_$]*))""".r

  // Top-level val/var at object/class body level. Matches the patterns
  // ssg-sass uses for built-in callable definitions like
  // `private val unquoteFn: BuiltInCallable = …`. Restricted to indent
  // levels 0-4 spaces to skip locals inside deeply-nested method bodies.
  private val scalaVal: Regex =
    """(?m)^[ \t]{0,4}(?:override\s+|final\s+|private(?:\[[^\]]+\])?\s+|protected(?:\[[^\]]+\])?\s+|implicit\s+|lazy\s+|inline\s+)*(?:val|var)\s+([A-Za-z_][A-Za-z0-9_$]*)""".r

  private val scalaType: Regex =
    """(?m)^[ \t]*(?:sealed\s+|final\s+|abstract\s+|private(?:\[[^\]]+\])?\s+|protected(?:\[[^\]]+\])?\s+|open\s+|case\s+)*(?:class|trait|object|enum)\s+([A-Za-z_][A-Za-z0-9_$]*)""".r

  // Dart method definition: must have a parameter list (`(...)`) directly
  // after the name. This filters out field accesses and simple values.
  // Getters are matched separately so we don't miss `bool get isEmpty => ...`.
  private val dartTopDef: Regex =
    """(?m)^[ \t]+(?:static\s+|const\s+|final\s+|late\s+|external\s+|abstract\s+|factory\s+)*(?:[A-Za-z_][A-Za-z0-9_<>?, \t]*[\s>?])?([a-zA-Z_][A-Za-z0-9_]*)\s*\(""".r

  private val dartGetter: Regex =
    """(?m)^[ \t]+(?:static\s+|const\s+|final\s+|late\s+|external\s+)*[A-Za-z_][A-Za-z0-9_<>?, \t]*\s+get\s+([a-zA-Z_][A-Za-z0-9_]*)\b""".r

  private val dartTypeDef: Regex =
    """(?m)^[ \t]*(?:abstract\s+|sealed\s+|final\s+|base\s+|interface\s+|mixin\s+)*(?:class|mixin|enum|extension)\s+([A-Za-z_][A-Za-z0-9_]*)""".r

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

  /** Return the set of top-level/method names defined in a Dart file.
    * Heuristics only — extension members may be missed; class members
    * that span multiple lines in the return type may need the regex
    * broadened later.
    *
    * Filters applied:
    *   - Dart keywords (return, throw, etc.) are excluded.
    *   - Names starting with an uppercase letter are excluded from the
    *     topDef extraction. `dartTopDef` matches `<type> name(` at
    *     indented lines, which falsely captures `return SassBoolean(`
    *     as "SassBoolean". Filtering upper-camel from the method set
    *     is safe because dart methods are lowerCamelCase by convention.
    *     Class-level names are collected separately via `dartTypeDef`,
    *     which is narrower and does not misfire.
    */
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
      "print", "assert", "when",
      // Common parameter names that the dartTopDef regex catches when a
      // multi-line function-typed parameter is declared on its own line
      // (e.g. `Value callback(List<Value> args),` in `_function` or
      // `Value modify(Value old),` in `_modify`'s signature). The dart-
      // sass codebase doesn't define top-level functions with these
      // names, so excluding them is safe.
      "callback", "modify"
    )
    def startsUpper(s: String): Boolean =
      s.nonEmpty && s.charAt(0).isUpper
    dartTopDef.findAllMatchIn(text).foreach { m =>
      val name = m.group(1)
      if (name != null && !exclude.contains(name) && !startsUpper(name)) names += name
    }
    dartGetter.findAllMatchIn(text).foreach { m =>
      val name = m.group(1)
      if (name != null && !exclude.contains(name)) names += name
    }
    names.distinct.toList.sorted
  }

  /** Normalize a name for cross-language comparison. Dart private-member
    * convention prefixes an underscore; Scala's ssg-sass port prefixes
    * `Fn` to avoid shadowing built-in value names. Strip both so a
    * Dart `_unquote` matches a Scala `unquoteFn`.
    *
    * This is the minimum normalization that matches the current port
    * convention. If the convention changes, extend this function.
    */
  def normalizeName(name: String): String = {
    var n = name
    if (n.startsWith("_")) n = n.substring(1)
    if (n.endsWith("Fn") && n.length > 2) n = n.substring(0, n.length - 2)
    n
  }

  /** Compute the gap between an SSG file and its dart reference.
    * Does NOT enforce the short-body check unless called via strictCompare.
    *
    * Both sides are passed through `normalizeName` so the underscore-
    * prefix dart convention and the `Fn`-suffix Scala convention align.
    */
  def compare(ssgFile: String, dartFile: String): Gap = {
    val scalaRaw = extractScalaMethods(ssgFile)
    val dartRaw = extractDartMethods(dartFile)
    val scala = scalaRaw.map(normalizeName).toSet
    val dart = dartRaw.map(normalizeName).toSet
    val missing = (dart -- scala).toList.sorted
    val extra = (scala -- dart).toList.sorted
    val common = (dart intersect scala).toList.sorted
    Gap(missing, extra, common, shortBody = Nil)
  }

  /** Strict compare: the Gap.missing is populated as usual, and shortBody
    * is populated with common method names whose Scala body AST-node-count
    * is below 70% of the dart body's AST-node-count.
    *
    * "AST node count" is a regex-based token count: identifiers, keywords,
    * operators, and delimiters. Not a real parser. It is enough to catch
    * one-line delegates and empty bodies.
    */
  def strictCompare(ssgFile: String, dartFile: String): Gap = {
    val base = compare(ssgFile, dartFile)
    // Index bodies by their NORMALIZED name so the body lookup matches
    // the same convention as the method-set comparison.
    val scalaBodies = extractScalaBodies(ssgFile).map { case (k, v) => normalizeName(k) -> v }
    val dartBodies = extractDartBodies(dartFile).map { case (k, v) => normalizeName(k) -> v }
    val shortBody = base.common.filter { name =>
      val sb = scalaBodies.get(name).map(astNodeCount).getOrElse(0)
      val db = dartBodies.get(name).map(astNodeCount).getOrElse(0)
      db > 0 && sb * 100 < db * 70
    }
    base.copy(shortBody = shortBody)
  }

  // --- Body extraction -------------------------------------------------------

  /** Extract method bodies from a Scala file. Key is the method name; value
    * is the text between the `=` (or `{`) and the matching close.
    *
    * This is not a real parser — it uses brace-balancing with awareness of
    * string literals and line comments. It handles `def f(...) = { ... }`,
    * `def f(...) = expr`, `val x: T = expr`, and `object Foo { ... }`.
    */
  def extractScalaBodies(path: String): Map[String, String] = {
    val text = readFile(path)
    val out = scala.collection.mutable.Map.empty[String, String]
    // Find every `def X`/`val X`/`var X` and extract the following body.
    val matches = scalaDef.findAllMatchIn(text).toList
    for (m <- matches) {
      val name = Option(m.group(1)).getOrElse(m.group(2))
      if (name != null) {
        val start = m.end
        val body = extractBodyFromScala(text, start)
        out(name) = body
      }
    }
    // Also body of class/trait/object/enum blocks.
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
        // The regex leaves us positioned right before the `(`. Back up
        // one so extractBodyFromDart can see it.
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

  /** Read the text from `start` in `text` up to the end of the enclosing
    * brace-block or the end of the line (for one-liner `= expr` forms).
    *
    * Returns an empty string if the body cannot be located (e.g., abstract
    * method with no `=` / `{`).
    *
    * The cursor at `start` may be positioned right after the method name
    * but before the parameter list. We skip a balanced `(...)` if present
    * (for multi-line signatures), then optionally skip a `: ReturnType`
    * declaration before searching for `{` / `=` / end-of-line.
    */
  private def extractBodyFromScala(text: String, start: Int): String = {
    var i = start
    val n = text.length
    // Skip leading whitespace.
    while (i < n && (text.charAt(i) == ' ' || text.charAt(i) == '\t')) i += 1
    // Skip parameter lists (possibly multiple, e.g. curried forms or
    // multi-line shape `def foo(\n  x: Int,\n  y: Int\n): Result = ...`).
    while (i < n && text.charAt(i) == '(') {
      var depth = 1
      i += 1
      while (i < n && depth > 0) {
        val c = text.charAt(i)
        if (c == '(') depth += 1
        else if (c == ')') depth -= 1
        i += 1
      }
      // Skip whitespace between curried parameter lists.
      while (i < n && (text.charAt(i) == ' ' || text.charAt(i) == '\t' || text.charAt(i) == '\n')) i += 1
    }
    // Skip optional return-type ascription `: Type` (which may itself
    // contain `[A, B]`, `(A, B)`, or wrap across lines).
    if (i < n && text.charAt(i) == ':') {
      i += 1
      var bracket = 0
      while (i < n) {
        val c = text.charAt(i)
        if (c == '[' || c == '(') { bracket += 1; i += 1 }
        else if (c == ']' || c == ')') { bracket -= 1; i += 1 }
        else if (bracket == 0 && (c == '{' || c == '=' || c == '\n')) return readBodyAt(text, i)
        else i += 1
      }
      return ""
    }
    readBodyAt(text, i)
  }

  /** Helper for the post-signature body read. `i` should point at the
    * `{`, `=`, or `\n` that terminates the signature.
    */
  private def readBodyAt(text: String, start: Int): String = {
    var i = start
    val n = text.length
    // Skip whitespace before the body marker.
    while (i < n && (text.charAt(i) == ' ' || text.charAt(i) == '\t')) i += 1
    if (i >= n) return ""
    if (text.charAt(i) == '\n') return ""
    if (text.charAt(i) == '{') return readBalancedBraces(text, i)
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

  /** Same idea as extractBodyFromScala but for Dart syntax. Dart function
    * definitions use `(...) { ... }`, `(...) => expr;`, or `(...)`.
    */
  private def extractBodyFromDart(text: String, start: Int): String = {
    var i = start
    val n = text.length
    // Skip past the parameter list.
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
    // Optional initializer list (for constructors): `: super()...`
    while (i < n && text.charAt(i) != '{' && text.charAt(i) != '=' && text.charAt(i) != ';' && text.charAt(i) != '\n')
      i += 1
    if (i >= n) return ""
    if (text.charAt(i) == '{') return readBalancedBraces(text, i)
    if (text.charAt(i) == '=' && i + 1 < n && text.charAt(i + 1) == '>') {
      // Arrow function: read until next `;`.
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

  /** Given that `text(i) == '{'`, read until the matching `}` with awareness
    * of string literals and line comments. Return the inner body text
    * (without the outer braces). Returns an empty string on unbalanced input.
    */
  private def readBalancedBraces(text: String, i0: Int): String = {
    val n = text.length
    var i = i0 + 1
    var depth = 1
    val start = i
    while (i < n && depth > 0) {
      val c = text.charAt(i)
      c match {
        case '/' if i + 1 < n && text.charAt(i + 1) == '/' =>
          // line comment
          while (i < n && text.charAt(i) != '\n') i += 1
        case '/' if i + 1 < n && text.charAt(i + 1) == '*' =>
          // block comment
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
