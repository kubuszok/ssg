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
    // Wrong property access
    "span.",
    "toNode(",
    // Per-body guards: bodies that cannot compile without these patterns
    "sqrtMain(",
    "delimFromValue(",
    "canCombine(",
    ".depth",
    "var delim = null"
  )

  val policy: ParityDerive.Policy =
    ParityDerive.Policy(
      uncompilablePatterns = uncompilablePatterns,
      keepReferenceOnRefusal = true,
      aliases = Map(
        // camelCase("_getExpansion") produces "GetExpansion" but the reference member is _getExpansion
        "_getExpansion" -> List("GetExpansion"),
        // KaTeX.scala wraps these TS module-level functions under __ prefixed API names
        "__parse" -> List("generateParseTree"),
        "__renderToDomTree" -> List("renderToDomTree"),
        "__renderToHTMLTree" -> List("renderToHTMLTree")
      )
    )

  /** JS property names that the Scala reference renamed. The translator's `snakeToCamel` would mangle underscore-prefixed names; `type` is a Scala reserved word renamed to `nodeType`. */
  private val memberRenames: Map[String, String] = Map(
    // mathMLTree.ts `this.type` -> MathMLTree.scala `val nodeType: MathNodeType`
    "type" -> "nodeType",
    // Options.ts `this._fontMetrics` -> Options.scala `private var _fontMetrics`; snakeToCamel mangles to FontMetrics
    "_fontMetrics" -> "_fontMetrics",
    // MacroExpander.ts `this._getExpansion(name)` -> MacroExpander.scala `private def _getExpansion`; snakeToCamel mangles to GetExpansion
    "_getExpansion" -> "_getExpansion"
  )

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

  /** Collision-prone names cleared: the module table now maps each RAST file to its own reference file, so names appearing in multiple RAST files no longer collide. The former entries (get, text,
    * sizeAtStyle, havingStyle, reportNonstrict, setHLinePos, initNode, toMarkup, newDocumentFragment) are all correctly isolated by their module and need not be suppressed.
    */
  private val collisionProneNames: Set[String] = Set.empty

  /** Members the reference declares that have no TS counterpart: the engine keeps the reference body and records the reason in bodies.tsv. Keyed by member name (matched globally across all files). */
  private val referenceOnly: Map[String, String] = Map(
    // Registration wrappers (48 function files + 3 environment files + 2 registries)
    "register" -> "module-level-side-effect",
    "registerAll" -> "module-level-side-effect",
    // KaTeX.scala: Scala-only entry points and cross-module delegation
    "ensureRegistered" -> "scala-registration-wrapper",
    "renderToStringResult" -> "scala-diagnostic-api",
    "positionOf" -> "scala-diagnostic-api",
    "__setFontMetrics" -> "cross-module-delegation",
    "__defineSymbol" -> "cross-module-delegation",
    "__defineFunction" -> "cross-module-delegation",
    "__defineMacro" -> "cross-module-delegation",
    // MacroDef.scala: abstract trait members (consumeArgs and mode also appear in MacroExpander
    // where they are classified structurally; the referenceOnly reason takes precedence globally)
    "mode" -> "abstract-trait-method",
    "expandAfterFuture" -> "abstract-trait-method",
    "consumeArgs" -> "abstract-trait-method",
    // ParseNode.scala: sealed-trait discriminator fields (nodeType and text also appear as
    // skeleton-cannot-offer:override in subclasses; referenceOnly takes precedence globally)
    "nodeType" -> "type-discriminator-field",
    "bodyNode" -> "type-discriminator-field",
    "bodyNodes" -> "type-discriminator-field",
    "text" -> "type-discriminator-field",
    // ParseError.scala: constructor-extracted helpers
    "buildMessage" -> "constructor-extracted-helper",
    "computePosition" -> "constructor-extracted-helper",
    "computeLength" -> "constructor-extracted-helper",
    // Settings.scala
    "command" -> "constructor-parameter",
    // BuildCommon.scala
    "childType" -> "case-class-field",
    // BuildHTML.scala
    "traverseNonSpaceNodesInner" -> "scala-specific-split",
    // BuildTree.scala
    "validateOutput" -> "scala-specific-helper",
    // Macros.scala
    "defineMacroFn" -> "scala-wrapping-helper",
    // FontMetrics.scala: data accessor delegation (35 metrics + apply + get)
    "cssEmPerMu" -> "data-accessor-delegation",
    "slant" -> "data-accessor-delegation",
    "space" -> "data-accessor-delegation",
    "stretch" -> "data-accessor-delegation",
    "shrink" -> "data-accessor-delegation",
    "xHeight" -> "data-accessor-delegation",
    "quad" -> "data-accessor-delegation",
    "extraSpace" -> "data-accessor-delegation",
    "num1" -> "data-accessor-delegation",
    "num2" -> "data-accessor-delegation",
    "num3" -> "data-accessor-delegation",
    "denom1" -> "data-accessor-delegation",
    "denom2" -> "data-accessor-delegation",
    "sup1" -> "data-accessor-delegation",
    "sup2" -> "data-accessor-delegation",
    "sup3" -> "data-accessor-delegation",
    "sub1" -> "data-accessor-delegation",
    "sub2" -> "data-accessor-delegation",
    "supDrop" -> "data-accessor-delegation",
    "subDrop" -> "data-accessor-delegation",
    "delim1" -> "data-accessor-delegation",
    "delim2" -> "data-accessor-delegation",
    "axisHeight" -> "data-accessor-delegation",
    "defaultRuleThickness" -> "data-accessor-delegation",
    "bigOpSpacing1" -> "data-accessor-delegation",
    "bigOpSpacing2" -> "data-accessor-delegation",
    "bigOpSpacing3" -> "data-accessor-delegation",
    "bigOpSpacing4" -> "data-accessor-delegation",
    "bigOpSpacing5" -> "data-accessor-delegation",
    "sqrtRuleThickness" -> "data-accessor-delegation",
    "ptPerEm" -> "data-accessor-delegation",
    "doubleRuleSep" -> "data-accessor-delegation",
    "arrayRuleWidth" -> "data-accessor-delegation",
    "fboxsep" -> "data-accessor-delegation",
    "fboxrule" -> "data-accessor-delegation",
    // Symbols.scala
    "math" -> "scala-accessor",
    "symbolsForMode" -> "scala-accessor",
    "getSymbol" -> "scala-accessor",
    "initSymbols" -> "scala-initialization",
    // Units.scala
    "fontMetrics" -> "scala-accessor",
    // DomTree.scala (toMarkup, isEmpty, nonEmpty also appear in subclasses as
    // skeleton-cannot-offer:override; referenceOnly takes precedence globally)
    "foreachEntry" -> "scala-specific-helper",
    "isEmpty" -> "scala-specific-helper",
    "nonEmpty" -> "scala-specific-helper",
    "virtualChildren" -> "scala-specific-accessor",
    "toInMemoryNode" -> "scala-specific-helper",
    "toMarkup" -> "parameter-mismatch",
    // Style.scala
    "toString" -> "scala-override",
    // FontMetrics.scala and Symbols.scala share these names; both are reference-only
    "apply" -> "data-accessor-delegation",
    "get" -> "data-accessor-delegation"
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
    rastFiles:     List[RastFile],
    refObjectName: String,
    oracle:        ReferenceSignatures.TypeOracle,
    calleeIdx:     ReferenceSignatures.CalleeIndex,
    memberIdx:     ReferenceSignatures.MemberIndex,
    ctorSchema:    ReferenceSignatures.ConstructorSchema,
    enumIdx:       ReferenceSignatures.EnumIndex
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
        val sig        = oracle.get(refObjectName, key)
        val retType    = sig.map(_.returnType)
        val paramTpes  = sig.map(s => s.params.map(p => p.name -> p.tpe).toMap).getOrElse(Map.empty)
        val entry      = DefmethodEntry("_free_", name, params, body)
        val translated = DefmethodBodyTranslator.translateBody(
          entry,
          Nil,
          "    ",
          apiLookup = apiLookup,
          returnType = retType,
          paramTypes = paramTpes,
          calleeIndex = calleeIdx,
          memberIndex = memberIdx,
          ctorSchema = ctorSchema,
          enumIndex = enumIdx,
          memberRenames = memberRenames,
          oracle = oracle
        )
        // An empty or whitespace-only body is a translator failure; record it as a refusal
        val bodyText = translated.scalaBody.trim
        val reasons  = if (bodyText.isEmpty) "empty-body" :: translated.refusalReasons else translated.refusalReasons
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
    try
      stream
        .iterator()
        .asScala
        .filter(p => Files.isRegularFile(p) && p.getFileName.toString.endsWith(".scala"))
        .map { p =>
          val stem   = p.getFileName.toString.stripSuffix(".scala")
          val source = new String(Files.readAllBytes(p), StandardCharsets.UTF_8)
          (stem, source)
        }
        .toList
    finally stream.close()
  }

  /** The Library value for `NonJavaBodies.build`.
    *
    * Builds type indices from the reference Scala tree once, then passes them to every module's body translator so it can resolve return types, parameter types, callees, members, constructors and
    * enums.
    */
  def library(referenceDir: Path): NonJavaBodies.Library = {
    val refSources                                  = readReferenceSources(referenceDir)
    val (calleeIdx, memberIdx, ctorSchema, enumIdx) = ReferenceSignatures.buildIndices(refSources)
    val oracle                                      = ReferenceSignatures.TypeOracle.fromEntries(refSources.flatMap((n, s) => ReferenceSignatures.parseFile(n, s)))

    NonJavaBodies.Library(
      name = "katex",
      policy = policy,
      readRast = Rast.readFile(_: java.nio.file.Path),
      referenceOnly = referenceOnly,
      modules = _ =>
        Right(
          allModules.map { case (rastPath, refPath) =>
            val refObjectName = refPath.stripSuffix(".scala").split('/').last
            NonJavaBodies.Module(
              refPath,
              rastPath,
              Nil,
              rasts => buildTranslatedBodyMap(rasts, refObjectName, oracle, calleeIdx, memberIdx, ctorSchema, enumIdx)
            )
          }
        )
    )
  }
}
