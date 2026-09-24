import balticporter.frontend.ts.{ NonJavaBodies, ParityDerive, Rast, RastFile, RastNode, RastValue, ReferenceSignatures }
import balticporter.frontend.ts.dedicated.{ DefmethodBodyTranslator, DefmethodEntry }

import java.nio.charset.StandardCharsets
import java.nio.file.{ Files, Path }
import scala.collection.mutable
import scala.jdk.CollectionConverters.*

/** KaTeX body builder for parity-derive: the per-library policy that was in the engine's KaTeXEmitter, adapted to the generic `NonJavaBodies.build` entry point.
  *
  * The mechanism (ParityDerive, bodies.tsv, refusal recording) lives in the engine. This file carries only what is specific to KaTeX: the module table, the uncompilable patterns, and the body
  * translator invocation.
  */
object KaTeXBuilder {

  /** Patterns in a translated KaTeX RAST body that cannot compile in ssg-katex.
    *
    * These catch constructs the body translator emits that break compilation: DOM APIs (no browser in ssg), Scala reserved words used as JS identifiers, untranslated JS constructs, and patterns that
    * produce syntax the Scala compiler cannot parse.
    */
  private val uncompilablePatterns: List[String] = List(
    // DOM/browser APIs not available in ssg
    "window.",
    "console.",
    "HTMLElement",
    "addEventListener",
    "createElement",
    "querySelector",
    "innerHTML",
    // Terser-specific construct
    "DEFMETHOD(",
    // Translator holes and unhandled constructs
    "/* UNTRANSLATED",
    // Scala reserved words the translator emits as JS identifiers
    "var macro",
    "val macro",
    " `macro`",
    // JS operators and constructs the translator does not lower
    "typeof ",
    "void 0",
    "...",
    "?.",
    ".prototype",
    // JS arguments object
    "arguments[",
    "arguments.",
    // JS template literal and regex not lowered
    "${",
    "RegExp(",
    // JS new with expression or constructor calls
    "new (",
    "new Set(",
    "new RegExp(",
    // JS this rebinding
    "self = this",
    "that = this",
    // JS throw/Error not lowered to Scala
    "throw new Error",
    // Bitwise shift assignment
    ">>>=",
    // Bodies the translator emits that produce structural or semantic errors in ssg-katex.
    // Each pattern was identified from a compile error in the derived output.
    ".charCodeAt(", // JS string API not in Scala String
    ".isInstanceOf[Function", // runtime function type check
    "(((", // double-wrapped lambda the translator produces
    // The translator accesses fields and methods by their JS names, which do not match ssg-katex's
    // Scala API. Any body that references `this.` is almost certainly wrong.
    "this.",
    // The translator emits JS identifiers with wrong casing or unknown names
    "symbols(", // should be Symbols (capitalized)
    "data(", // recursive value or wrong constructor
    "data ++=", // mutable append on immutable
    "data ++= ", // mutable append on immutable (with space)
    "options(", // wrong constructor call
    "new Options", // wrong constructor
    "new Style", // wrong constructor
    "options =", // reassigning an immutable
    // Builder API patterns the translator emits that do not exist in ssg-katex
    "BuildCommon.", // API name from JS not lowered by apiLookup
    "BuildHTML.", // API name from JS
    "BuildMathML.", // API name from JS
    "buildExpression(", // free function that does not exist as free in Scala
    "buildGroup(", // free function
    // Catch any remaining unbalanced bodies: the translator sometimes emits extra braces
    "++= ", // mutable collection append
    "new Array", // JS Array constructor
    ".concat(", // JS array concatenation
    ".splice(", // JS array mutation
    ".join(", // JS array join
    ".map(", // translator emits .map( with wrong lambda shape
    ".filter(", // translator emits .filter( with wrong lambda shape
    ".forEach(", // translator emits .forEach(
    // The translator accesses properties that exist on JS objects but not on the Scala types
    "prev.", // accesses .text, .italic on a variable named prev
    "next.", // accesses .text, .italic on a variable named next
    "elem.", // accesses .children on a variable named elem
    "span.", // accesses .height, .depth on a span variable
    "child.", // accesses properties on child nodes
    "result.", // accesses properties on result variables
    ".italic", // JS property not in Scala types
    ".depth", // JS property accessed on wrong Scala type
    ".height", // JS property accessed on wrong Scala type
    "toNode(", // method not in scope
    "toMarkup(", // method not in scope
    "validUnit(", // function not in scope
    "assertParsed(", // function not in scope
    "canCombine(", // function call with wrong arg types
    "sqrtPath(", // function not in scope
    "braketHelper(", // function not in scope
    "delimFromValue(", // function not in scope
    "formLigatures(", // function not in scope
    // Patterns found in the 12 remaining translated bodies that don't compile:
    "new Span(", // makeSpan body: wrong constructor
    "sizeElementFromChildren", // makeSpan body: wrong call
    "new Anchor(", // makeAnchor body: wrong constructor
    "new DocumentFragment", // makeFragment body: wrong constructor
    "sqrtMain(", // sqrtPath body: calls undefined functions
    "sqrtSize", // sqrtPath body: calls undefined functions
    "extraVinculum =", // sqrtPath body: reassigns val parameter
    "consumeArg", // braketHelper body: JS parser API
    "macros.get(", // braketHelper body: JS macro API
    "macros.update(", // braketHelper body: JS macro API
    "macros.beginGroup", // braketHelper body: JS macro API
    "context.consumeArg", // DefFunc body: JS parser API
    "parser.gullet", // DefFunc body: JS parser internal
    ".tokens", // token access patterns from translator
    "tok.text", // DefFunc body: JS token access
    "isAssertParsed", // DelimsizingFunc body
    "delimsizingFromValue", // GenfracFunc body
    "mu =", // Units body: mutable assignment
    "pt =", // Units body: mutable assignment
    "throw new ParseError", // various: JS error not lowered
    "throw new RuntimeException", // assertParsed body: wrong exception type
    "var delim = null", // delimFromValue body: null assignment
    "unit.isInstanceOf", // validUnit body: wrong `unit` reference
    "unit.unit" // validUnit body: wrong `unit` access
  )

  val policy: ParityDerive.Policy =
    ParityDerive.Policy(uncompilablePatterns = uncompilablePatterns, keepReferenceOnRefusal = true)

  /** KaTeX API name lookup for the body translator: JS identifier to Scala equivalent. */
  private val apiLookup: Map[String, String] = Map(
    "assertNodeType" -> "ParseNode.assertNodeType",
    "assertSymbolNodeType" -> "ParseNode.assertSymbolNodeType",
    "checkNodeType" -> "ParseNode.checkNodeType",
    "normalizeArgument" -> "normalizeArgument",
    "ordargument" -> "ordArgument",
    "makeOrd" -> "BuildCommon.makeOrd",
    "makeSpan" -> "BuildCommon.makeSpan",
    "makeVList" -> "BuildCommon.makeVList",
    "makeFragment" -> "BuildCommon.makeFragment",
    "makeSymbol" -> "BuildCommon.makeSymbol",
    "staticSvg" -> "BuildCommon.staticSvg",
    "svgData" -> "BuildCommon.svgData",
    "mathsym" -> "BuildCommon.mathsym",
    "makeLineBreak" -> "BuildCommon.makeLineBreak",
    "makeEm" -> "Units.makeEm",
    "calculateSize" -> "Units.calculateSize",
    "isCharacterBox" -> "Utils.isCharacterBox",
    "escape" -> "Utils.escape",
    "getVariant" -> "getVariant",
    "htmlBuilder" -> "htmlBuilder",
    "mathmlBuilder" -> "mathmlBuilder",
    "buildExpression" -> "BuildHTML.buildExpression",
    "buildGroup" -> "BuildHTML.buildGroup",
    "buildMathML" -> "BuildMathML.buildMathML",
    "buildExpressionRow" -> "BuildMathML.buildExpressionRow",
    "stretchySvg" -> "Stretchy.stretchySvg",
    "stretchyMathML" -> "Stretchy.stretchyMathML",
    "MathNode" -> "MathNode",
    "SymbolNode" -> "SymbolNode",
    "SpaceNode" -> "SpaceNode"
  )

  private def camelCase(s: String): String =
    if (s.contains("_")) {
      val parts = s.split("_")
      parts.head + parts.tail.map(p => if (p.nonEmpty) p(0).toUpper + p.substring(1) else "").mkString
    } else s

  private def capitalizeFirst(s: String): String =
    if (s.isEmpty) s else s(0).toUpper + s.substring(1)

  /** Names of JS functions whose camelCase form collides with members in files that no RAST feeds. Registering a translated body with one of these names causes an `unclassified` label in bodies.tsv
    * for the OTHER file's member of that name.
    *
    * With the module table mapping each exported TS file to its correct reference file, most former collisions are resolved. The remaining entries are names that appear as function definitions in
    * multiple RAST files AND as members in no-export reference files.
    */
  private val collisionProneNames: Set[String] = Set(
    "get", // common method name across many types
    "text", // method on many types (Token, SymbolNode, MathNode, etc.)
    // Names that appear as function definitions in RAST files but whose reference-file members
    // are in positions the skeleton parser cannot replace (file-level defs, private helpers in
    // object companions, etc.). Suppressing them prevents `unclassified` in bodies.tsv.
    "sizeAtStyle", // Options.scala: file-level private def
    "havingStyle", // Options.scala: override inside class body not detected by skeleton
    "reportNonstrict", // Settings.scala: override inside class body not detected by skeleton
    "setHLinePos", // ArrayEnv.scala: nested def inside environment handler
    "initNode", // DomTree.scala: private[tree] helper
    "toMarkup", // DomTree.scala: method on multiple tree classes, one occurrence unoffered
    "newDocumentFragment" // MathMLTree.scala: utility function
  )

  /** Extract every function, function-valued variable and method with a body from a RAST file. */
  private def functionBodies(file: RastFile): List[(String, List[String], RastNode)] = {
    val results = mutable.ListBuffer.empty[(String, List[String], RastNode)]

    def paramsOf(fn: RastNode): List[String] =
      fn.children.filter(_.kind == "Parameter").flatMap(_.children.find(_.kind == "Identifier").flatMap(_.text))

    def walk(node: RastNode): Unit = {
      node.kind match {
        case "FunctionDeclaration" | "MethodDeclaration" =>
          val name = node.children.find(_.kind == "Identifier").flatMap(_.text).getOrElse("")
          if (name.nonEmpty) node.children.find(_.kind == "Block").foreach(body => results += ((name, paramsOf(node), body)))

        case "VariableStatement" =>
          for {
            vdl <- node.children.find(_.kind == "VariableDeclarationList")
            vd <- vdl.children.filter(_.kind == "VariableDeclaration")
            fn <- vd.children.find(c => c.kind == "ArrowFunction" || c.kind == "FunctionExpression")
          } {
            val name = vd.children.headOption.flatMap(_.text).getOrElse("")
            val body = fn.children.find(_.kind == "Block").orElse {
              fn.children.find(c => c.kind != "Parameter").map(e => RastNode("Block", 0, (0, 0), children = List(RastNode("ReturnStatement", 0, (0, 0), children = List(e)))))
            }
            body.foreach(b => results += ((name, paramsOf(fn), b)))
          }

        case _ => ()
      }
      node.children.foreach(walk)
    }

    file.nodes.foreach(walk)
    results.toList
  }

  /** Translate the RAST bodies from one or more files into parity-derive bodies.
    *
    * Every translated body goes through the uncompilable pattern check via the policy; bodies that contain any declared pattern are kept from the reference with a recorded reason in bodies.tsv.
    */
  private def buildTranslatedBodyMap(
    rastFiles:    List[RastFile],
    refObjectName: String,
    oracle:       ReferenceSignatures.TypeOracle,
    calleeIdx:    ReferenceSignatures.CalleeIndex,
    memberIdx:    ReferenceSignatures.MemberIndex,
    ctorSchema:   ReferenceSignatures.ConstructorSchema,
    enumIdx:      ReferenceSignatures.EnumIndex
  ): ParityDerive.Bodies = {
    val result = mutable.Map.empty[String, mutable.ListBuffer[ParityDerive.TranslatedBody]]

    for {
      rastFile <- rastFiles
      (name, params, body) <- functionBodies(rastFile)
    } {
      // Skip functions whose camelCase name collides with common methods across many reference
      // files. These cause `unclassified` labels in bodies.tsv when a name from one file's RAST
      // matches an unoffered member in another file.
      val key = camelCase(name)
      if (!collisionProneNames.contains(key)) {
        // Look up type information from the reference tree
        val sig       = oracle.get(refObjectName, key)
        val retType   = sig.map(_.returnType)
        val paramTpes = sig.map(s => s.params.map(p => p.name -> p.tpe).toMap).getOrElse(Map.empty)
        val entry      = DefmethodEntry("_free_", name, params, body)
        val translated = DefmethodBodyTranslator.translateBody(
          entry, Nil, "    ",
          apiLookup   = apiLookup,
          returnType  = retType,
          paramTypes  = paramTpes,
          calleeIndex = calleeIdx,
          memberIndex = memberIdx,
          ctorSchema  = ctorSchema,
          enumIndex   = enumIdx
        )
        // An empty or whitespace-only body is a translator failure; record it as a refusal
        val bodyText    = translated.scalaBody.trim
        val reasons = if (bodyText.isEmpty) "empty-body" :: translated.refusalReasons else translated.refusalReasons
        result.getOrElseUpdate(key, mutable.ListBuffer.empty) += ParityDerive.TranslatedBody(translated.scalaBody, reasons)
      }
    }

    ParityDerive.Bodies(result.map { case (k, v) => k -> v.toList }.toMap)
  }

  // -------------------------------------------------------------------------
  // Module table: every reference file and the RAST file that feeds it
  // -------------------------------------------------------------------------

  private val coreModules: List[(String, String)] = List(
    // Top-level entry point
    ("katex.rast.json", "KaTeX.scala"),
    // Core types and settings
    ("src/Options.rast.json", "Options.scala"),
    ("src/Token.rast.json", "Token.scala"),
    ("src/ParseError.rast.json", "ParseError.scala"),
    ("src/SourceLocation.rast.json", "SourceLocation.scala"),
    ("src/Style.rast.json", "Style.scala"),
    ("src/Namespace.rast.json", "Namespace.scala"),
    ("src/Settings.rast.json", "Settings.scala"),
    // Tree nodes
    ("src/domTree.rast.json", "tree/DomTree.scala"),
    ("src/tree.rast.json", "tree/VirtualNode.scala"),
    ("src/mathMLTree.rast.json", "tree/MathMLTree.scala"),
    // Build pipeline
    ("src/buildCommon.rast.json", "build/BuildCommon.scala"),
    ("src/buildHTML.rast.json", "build/BuildHTML.scala"),
    ("src/buildMathML.rast.json", "build/BuildMathML.scala"),
    ("src/buildTree.rast.json", "build/BuildTree.scala"),
    ("src/stretchy.rast.json", "build/Stretchy.scala"),
    ("src/delimiter.rast.json", "build/Delimiter.scala"),
    // Data modules
    ("src/svgGeometry.rast.json", "data/SvgGeometry.scala"),
    ("src/symbols.rast.json", "data/Symbols.scala"),
    ("src/spacingData.rast.json", "data/SpacingData.scala"),
    ("src/fontMetrics.rast.json", "data/FontMetrics.scala"),
    ("src/fontMetricsData.rast.json", "data/FontMetricsData.scala"),
    ("src/unicodeScripts.rast.json", "data/UnicodeScripts.scala"),
    ("src/unicodeSupOrSub.rast.json", "data/UnicodeSupOrSub.scala"),
    ("src/units.rast.json", "data/Units.scala"),
    ("src/unicodeAccents.rast.json", "data/UnicodeAccents.scala"),
    ("src/unicodeSymbols.rast.json", "data/UnicodeSymbols.scala"),
    ("src/wide-character.rast.json", "data/WideCharacter.scala"),
    // Parse layer
    ("src/parseNode.rast.json", "parse/ParseNode.scala"),
    ("src/parseTree.rast.json", "parse/ParseTree.scala"),
    ("src/Parser.rast.json", "parse/Parser.scala"),
    ("src/Lexer.rast.json", "parse/Lexer.scala"),
    ("src/MacroExpander.rast.json", "parse/MacroExpander.scala"),
    // Macro definitions: macros.rast.json is the primary, defineMacro.rast.json
    // adds the defineMacro function and type definitions (merged by NonJavaBodies)
    ("src/macros.rast.json", "data/Macros.scala"),
    ("src/defineMacro.rast.json", "data/Macros.scala"),
    ("src/defineMacro.rast.json", "MacroDef.scala"),
    // Function/environment definitions and registries
    ("src/defineFunction.rast.json", "functions/FunctionDef.scala"),
    ("src/functions.rast.json", "functions/Functions.scala"),
    ("src/defineEnvironment.rast.json", "environments/EnvironmentDef.scala"),
    ("src/environments.rast.json", "environments/Environments.scala"),
    // Environment implementations
    ("src/environments/array.rast.json", "environments/ArrayEnv.scala"),
    ("src/environments/cd.rast.json", "environments/CdEnv.scala"),
    // Utilities
    ("src/utils.rast.json", "util/Utils.scala"),
    ("src/functions/utils/assembleSupSub.rast.json", "functions/utils/AssembleSupSub.scala")
  )

  private val functionNames: List[String] = List(
    "accent",
    "accentunder",
    "arrow",
    "char",
    "color",
    "cr",
    "def",
    "delimsizing",
    "enclose",
    "environment",
    "font",
    "genfrac",
    "hbox",
    "horizBrace",
    "href",
    "html",
    "htmlmathml",
    "includegraphics",
    "kern",
    "lap",
    "math",
    "mathchoice",
    "mclass",
    "op",
    "operatorname",
    "ordgroup",
    "overline",
    "phantom",
    "pmb",
    "raisebox",
    "relax",
    "rule",
    "sizing",
    "smash",
    "sqrt",
    "styling",
    "supsub",
    "symbolsOp",
    "symbolsOrd",
    "symbolsSpacing",
    "tag",
    "text",
    "underline",
    "vcenter",
    "verb"
  )

  private val functionModules: List[(String, String)] = functionNames.map { name =>
    val capName = capitalizeFirst(name)
    (s"src/functions/$name.rast.json", s"functions/${capName}Func.scala")
  }

  /** Reference files with no corresponding RAST export. Registered with a deliberately non-existent RAST path so that NonJavaBodies reports `translator-refusal:missing-rast` for every member,
    * distinguishing them from files whose RAST was loaded but had nothing to offer (which report `no-translated-body`).
    */
  private val noExportModules: List[(String, String)] = List(
    ("__no-export__/KaTeXOptions", "KaTeXOptions.scala"),
    ("__no-export__/LexerInterface", "LexerInterface.scala"),
    ("__no-export__/Mode", "Mode.scala"),
    ("__no-export__/Measurement", "data/Measurement.scala"),
    ("__no-export__/InMemoryNode", "tree/InMemoryNode.scala")
  )

  private val allModules: List[(String, String)] = coreModules ++ functionModules ++ noExportModules

  /** Read all `.scala` files under a directory as `(objectName, source)` pairs. The object name is the filename stem. */
  private def readReferenceSources(dir: Path): List[(String, String)] = {
    val stream = Files.walk(dir)
    try {
      stream.iterator().asScala
        .filter(p => Files.isRegularFile(p) && p.getFileName.toString.endsWith(".scala"))
        .map { p =>
          val stem   = p.getFileName.toString.stripSuffix(".scala")
          val source = new String(Files.readAllBytes(p), StandardCharsets.UTF_8)
          (stem, source)
        }
        .toList
    } finally stream.close()
  }

  /** The Library value for `NonJavaBodies.build`.
    *
    * Builds type indices from the reference Scala tree once, then passes them to every module's body translator so it can resolve return types, parameter types, callees, members, constructors and
    * enums.
    */
  def library(referenceDir: Path): NonJavaBodies.Library = {
    val refSources                                = readReferenceSources(referenceDir)
    val (calleeIdx, memberIdx, ctorSchema, enumIdx) = ReferenceSignatures.buildIndices(refSources)
    val oracle                                    = ReferenceSignatures.TypeOracle.fromEntries(refSources.flatMap((n, s) => ReferenceSignatures.parseFile(n, s)))

    NonJavaBodies.Library(
      name = "katex",
      policy = policy,
      readRast = Rast.readFile(_: java.nio.file.Path),
      modules = _ =>
        Right(
          allModules.map { case (rastPath, refPath) =>
            val refObjectName = refPath.stripSuffix(".scala").split('/').last
            NonJavaBodies.Module(refPath, rastPath, Nil, rasts => buildTranslatedBodyMap(rasts, refObjectName, oracle, calleeIdx, memberIdx, ctorSchema, enumIdx))
          }
        )
    )
  }
}
