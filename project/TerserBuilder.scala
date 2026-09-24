import balticporter.frontend.ts.{ NonJavaBodies, ParityDerive, Rast, RastFile, RastNode, RastValue, ReferenceSignatures }
import balticporter.frontend.ts.dedicated.{ DefmethodBodyTranslator, DefmethodEntry }

import java.nio.charset.StandardCharsets
import java.nio.file.{ Files, Path }
import scala.collection.mutable
import scala.jdk.CollectionConverters.*

/** Terser body builder for parity-derive: the per-library policy for the terser JavaScript minifier, adapted to the generic `NonJavaBodies.build` entry point.
  *
  * Terser is JavaScript (not TypeScript): the TS exporter runs with `allowJs` and needs a committed `ast.d.ts` (under `ssg-js/port/`) declaring the AST class hierarchy so the checker can resolve
  * field accesses.
  */
object TerserBuilder {

  /** Patterns in a translated RAST body that cannot compile in ssg-js.
    *
    * Each catches a construct the body translator emits that breaks compilation, documented with the defect class and affected members. A pattern is the LAST resort, one per defect class; defects are
    * recorded in the tracker. Starts EMPTY; patterns added only for bodies that compile but fail a test or whose compile error the translator did not refuse.
    */
  private val uncompilablePatterns: List[String] = List(
    // Wrong object constructor: translator emits `new AstFunction()` with field assignments
    // using wrong types (ArrayBuffer.empty[Any]) (makeEmptyFunction, makeSequence in Common)
    "new AstFunction()",
    "new AstSequence()",
    // Wrong field access on untyped JS globals (initialize in AstSymbols)
    "this.Annotations",
    // Wrong property access on Compressor (getToplevel)
    "this.Toplevel",
    // Wrong API name: translator uses bestOfExpression instead of bestOf (Compressor)
    "bestOfExpression(",
    // Wrong dispatch: translator calls .hoistProperties/.hoistDeclarations on a node
    // instead of Hoisting.hoistProperties (before in Compressor)
    "node.hoistProperties",
    "node.hoistDeclarations",
    // Wrong dispatch: translator calls .optimize(this) on a node (before in Compressor)
    ".optimize(this)",
    // Wrong API: translator calls OutputStream.print instead of this.print (14 OutputStream
    // methods: star, forceSemicolon, withBlock, withParens, withSquare, comma, colon, get, makeNum)
    "OutputStream.print(",
    // Wrong API: translator calls OutputStream.X for instance methods (OutputStream methods)
    "OutputStream.newline(",
    "OutputStream.space(",
    "OutputStream.ensureLineLen(",
    "OutputStream.withIndent(",
    "OutputStream.nextIndent(",
    "OutputStream.makeString(",
    "OutputStream.toUtf8(",
    // Wrong chaining: translator produces `name = name.toString` (makeName in OutputStream)
    "name = name.toString",
    // Wrong method reference: firstInStatement(compressor) (maintainThisBinding in Common)
    "firstInStatement(compressor)",
    // Wrong API: translator uses Common.bestOfExpression (bestOfStatement in Common)
    "Common.bestOfExpression(",
    // Wrong constructor: new AstNumber() / new AstTrue() without proper args (maintainThisBinding,
    // literalsInBooleanContext in Compressor)
    "new AstNumber()",
    "new AstTrue()",
    // Wrong variable name: val_ instead of value (maintainThisBinding in Common)
    "val_",
    // Wrong regex application on option result (isStrict in Inference)
    ".findFirstIn(compressor",
    // JS truthiness: thing && thing.aborts() (aborts in Inference)
    "thing && thing",
    // Wrong dispatch: Parser.isToken, Parser.croak (is, tokenError in Parser)
    "Parser.isToken(",
    "Parser.croak(",
    // Wrong return type: Token.getFullChar returns String not AstToken (peek in Parser)
    "Token.getFullChar(",
    // Wrong self-reference: OutputStream methods called as this.length(), this.charAt
    // (hasNLB in OutputStream)
    "this.length()",
    "this.charAt(",
    // Wrong arithmetic: options.indentStart + indentation - back (makeIndent in OutputStream)
    "options.indentStart",
    // Wrong regex usage in replace (makeNum in OutputStream)
    ".replace(\"^0",
    // Wrong match assignment: match_ = regex.findFirstMatchIn (makeNum in OutputStream)
    "match_ =",
    // Wrong dispatch: SourceMap.clean (getEncoded in SourceMap)
    "SourceMap.clean(",
    // Wrong dispatch: frequency.get without default (consider in Mangler)
    "frequency.get(",
    // Wrong dispatch: .optimize(compressor) on AstNode (literalsInBooleanContext in Compressor)
    ".optimize(compressor)",
    // Wrong method reference: Common.requiresSequenceToMaintainBinding (maintainThisBinding)
    "Common.requiresSequenceToMaintainBinding(",
    // Wrong state reference: S.text, S.pos, S.token etc. -- the tokenizer state in the Scala
    // reference is class fields, not an S object (all Tokenizer and Parser translated bodies)
    "S.text",
    "S.pos",
    "S.token",
    "S.line",
    "S.col",
    "S.tokline",
    "S.tokcol",
    "S.tokpos",
    "S.inGenerator",
    "S.inAsync",
    "S.inFunction",
    // Wrong dispatch: Mangler.mergeSort (sort in Mangler)
    "Mangler.mergeSort(",
    // Wrong regex usage: BASICIDENT.findFirstIn (isBasicIdentifierString in Token)
    "BASICIDENT.findFirstIn",
    // Wrong throw reference (find, findEol in Tokenizer)
    "throw EXEOF",
    // Wrong NEWLINECHARS reference (findEol in Tokenizer)
    "NEWLINECHARS.contains(",
    // Modifying val parameter: code -= 65536 (fromCharCode in Token)
    "code -= 65536",
    // Wrong method: token.`type`.toString (unexpected in Parser)
    "token.`type`.toString",
    // Wrong dispatch: Parser.expect, Parser.expression (parenthesised in Parser)
    "Parser.expect(",
    "Parser.expression(",
    // Wrong parameter reference: str.charAt in getFullChar uses wrong scope (Token)
    "isSurrogatePairHead(str",
    "isSurrogatePairTail(str"
  )

  val policy: ParityDerive.Policy =
    ParityDerive.Policy(
      uncompilablePatterns = uncompilablePatterns,
      keepReferenceOnRefusal = true,
      aliases = Map(
        // Underscore-prefixed JS names whose camelCase form differs from the reference member name.
        // camelCase("_walk") -> "Walk" but reference keeps "_walk" or uses "walk_"
        "_walk" -> List("Walk"),
        "_children_backwards" -> List("ChildrenBackwards"),
        "_size" -> List("Size"),
        "_eval" -> List("Eval"),
        "_dot_throw" -> List("DotThrow"),
        "_do_print" -> List("DoPrint"),
        "_do_print_body" -> List("DoPrintBody"),
        // Output stream internal methods
        "_print_call" -> List("PrintCall")
      )
    )

  /** JS property names that the Scala reference renamed. The body translator's `snakeToCamel` would mangle underscore-prefixed names; `type` is a Scala reserved word.
    */
  private val memberRenames: Map[String, String] = Map(
    // AST_Token.type -> AstToken.tokenType (Scala reserved word)
    "type" -> "`type`"
  )

  /** API name lookup for the body translator: JS identifier to Scala equivalent. Module-level functions and cross-module references that the translator needs to resolve.
    */
  private val apiLookup: Map[String, String] = Map(
    // parse.js
    "parse" -> "Parser.parse",
    // scope.js
    "figure_out_scope" -> "ScopeAnalysis.figureOutScope",
    "base54" -> "Base54",
    // propmangle.js
    "mangle_properties" -> "PropMangler.mangleProperties",
    // output.js
    "OutputStream" -> "OutputStream",
    // compress cross-module references
    "merge_sequence" -> "Common.mergeSequence",
    "make_sequence" -> "Common.makeSequence",
    "best_of" -> "Common.bestOf",
    "make_node_from_constant" -> "Common.makeNodeFromConstant",
    "is_empty" -> "Common.isEmpty",
    "is_identifier_atom" -> "Common.isIdentifierAtom",
    "first_in_statement" -> "FirstInStatement.firstInStatement",
    "tighten_body" -> "TightenBody.tightenBody",
    "inline_single_use" -> "Inline.inlineSingleUse",
    // equivalent-to.js
    "equivalent_to" -> "AstEquivalent.equivalentTo",
    // transform.js
    "MAP" -> "MAP"
  )

  private def camelCase(s: String): String =
    if (s.contains("_")) {
      val parts = s.split("_")
      parts.head + parts.tail.map(p => if (p.nonEmpty) p(0).toUpper + p.substring(1) else "").mkString
    } else s

  // -------------------------------------------------------------------------
  // Module table: every reference file and the RAST file that feeds it.
  // RAST paths are lib/<path>.rast.json under the terser rast dir.
  // Reference paths are relative to the reference dir.
  // -------------------------------------------------------------------------

  /** Multiple reference files map to lib/ast.rast.json (the single upstream ast.js). */
  private val astModules: List[(String, String)] = List(
    ("lib/ast.rast.json", "ast/AstNode.scala"),
    ("lib/ast.rast.json", "ast/AstClasses.scala"),
    ("lib/ast.rast.json", "ast/AstConstants.scala"),
    ("lib/ast.rast.json", "ast/AstDefinitions.scala"),
    ("lib/ast.rast.json", "ast/AstExpressions.scala"),
    ("lib/ast.rast.json", "ast/AstScope.scala"),
    ("lib/ast.rast.json", "ast/AstStatements.scala"),
    ("lib/ast.rast.json", "ast/AstSymbols.scala"),
    ("lib/ast.rast.json", "ast/AstToken.scala")
  )

  private val compressModules: List[(String, String)] = List(
    ("lib/compress/common.rast.json", "compress/Common.scala"),
    ("lib/compress/compressor-flags.rast.json", "compress/CompressorFlags.scala"),
    ("lib/compress/drop-side-effect-free.rast.json", "compress/DropSideEffectFree.scala"),
    ("lib/compress/drop-unused.rast.json", "compress/DropUnused.scala"),
    ("lib/compress/evaluate.rast.json", "compress/Evaluate.scala"),
    ("lib/compress/global-defs.rast.json", "compress/GlobalDefs.scala"),
    ("lib/compress/index.rast.json", "compress/Compressor.scala"),
    ("lib/compress/index.rast.json", "compress/CompressorLike.scala"),
    ("lib/compress/index.rast.json", "compress/CompressorOptions.scala"),
    ("lib/compress/index.rast.json", "compress/Hoisting.scala"),
    ("lib/compress/index.rast.json", "compress/TightenBody.scala"),
    ("lib/compress/inference.rast.json", "compress/Inference.scala"),
    ("lib/compress/inline.rast.json", "compress/Inline.scala"),
    ("lib/compress/native-objects.rast.json", "compress/NativeObjects.scala"),
    ("lib/compress/reduce-vars.rast.json", "compress/ReduceVars.scala"),
    ("lib/compress/tighten-body.rast.json", "compress/TightenBody.scala")
  )

  private val otherModules: List[(String, String)] = List(
    ("lib/equivalent-to.rast.json", "ast/AstEquivalent.scala"),
    ("lib/size.rast.json", "ast/AstSize.scala"),
    ("lib/minify.rast.json", "Terser.scala"),
    ("lib/output.rast.json", "output/OutputStream.scala"),
    ("lib/output.rast.json", "output/OutputOptions.scala"),
    ("lib/parse.rast.json", "parse/Parser.scala"),
    ("lib/parse.rast.json", "parse/Token.scala"),
    ("lib/parse.rast.json", "parse/Tokenizer.scala"),
    ("lib/scope.rast.json", "scope/ScopeAnalysis.scala"),
    ("lib/scope.rast.json", "scope/Mangler.scala"),
    ("lib/scope.rast.json", "scope/SymbolDef.scala"),
    ("lib/propmangle.rast.json", "scope/PropMangler.scala"),
    ("lib/sourcemap.rast.json", "sourcemap/SourceMap.scala"),
    ("lib/transform.rast.json", "ast/AstNode.scala"),
    ("lib/utils/first_in_statement.rast.json", "output/FirstInStatement.scala"),
    ("lib/utils/index.rast.json", "Terser.scala")
  )

  /** Reference files with no corresponding RAST export: Scala-specific files, intentionally empty files, SSG glue, and files ported from external dependencies (not terser itself).
    */
  private val noExportModules: List[(String, String)] = List(
    ("__no-export__/Nodes", "ast/Nodes.scala"),
    ("__no-export__/Precedence", "parse/Precedence.scala"),
    ("__no-export__/UnicodeIdentifierTables", "parse/UnicodeIdentifierTables.scala"),
    ("__no-export__/JsNumber", "output/JsNumber.scala"),
    ("__no-export__/DomProps", "scope/DomProps.scala"),
    ("__no-export__/TerserJsCompressor", "TerserJsCompressor.scala"),
    // Source map files ported from @jridgewell libraries, not terser
    ("__no-export__/Base64", "sourcemap/Base64.scala"),
    ("__no-export__/InlineSourceMap", "sourcemap/InlineSourceMap.scala"),
    ("__no-export__/SourceMapConsumer", "sourcemap/SourceMapConsumer.scala"),
    ("__no-export__/SourceMapGenerator", "sourcemap/SourceMapGenerator.scala"),
    ("__no-export__/SourceMapJson", "sourcemap/SourceMapJson.scala"),
    ("__no-export__/SourceMapTypes", "sourcemap/SourceMapTypes.scala"),
    ("__no-export__/VlqCodec", "sourcemap/VlqCodec.scala")
  )

  private val allModules: List[(String, String)] =
    astModules ++ compressModules ++ otherModules ++ noExportModules

  /** Members the reference declares that have no JS counterpart: the engine keeps the reference body and records the reason in bodies.tsv. Keyed by member name.
    *
    * Terser uses DEFMETHOD and OPT macros to register anonymous callbacks on AST class prototypes. The Scala reference restructures these as named methods in dedicated objects (OutputStream,
    * Compressor, Evaluate, etc.) and as pattern-matching dispatch. These members have no extractable JS function body in the RAST.
    *
    * Groups (498 members total):
    *   - defnode-type-constant (103): DEFNODE TYPE string, one per AST class
    *   - defmethod-codegen (53): DEFMETHOD _codegen callbacks in output.js
    *   - defmethod-opt-dispatch (43): OPT() macro dispatch in compress/index.js
    *   - defmethod-inference (29): DEFMETHOD is_string/is_number/etc in inference.js
    *   - cross-module-restructured (22): JS function in a different file than the reference puts it
    *   - defmethod-needs-parens (16): DEFMETHOD needs_parens in output.js
    *   - defmethod-drop-side-effect (8): DEFMETHOD drop_side_effect_free
    *   - defmethod-eval (8): DEFMETHOD _eval in evaluate.js
    *   - scope-analysis-pass (7): scope analysis passes in scope.js
    *   - defmethod-reduce-vars (5): DEFMETHOD reduce_vars in reduce-vars.js
    *   - defmethod-negate (1): DEFMETHOD negate
    *   - scala-restructured (179): closure-to-class, helper extraction, Scala-specific API
    *   - pattern-match-dispatch, external-library-port, data-table, ssg-glue, etc. (34 from original)
    */
  private val referenceOnly: Map[String, String] = {
    def group(reason: String, names: String*): Seq[(String, String)] = names.map(_ -> reason)
    (
      // --- original entries (specific reasons) ---
      group("scala-specific-traversal", "walk", "walkInner", "transform") ++
        group("scala-specific-method", "clone") ++
        group("scala-renamed-field", "tokenType", "mangledName") ++
        group(
          "pattern-match-dispatch",
          "equivalentTo",
          "shallowCmp",
          "nodeSize",
          "listOverhead",
          "optimizeNode",
          "isString",
          "isNumber",
          "isBoolean",
          "isNullish",
          "dropSideEffectFree",
          "evalNode",
          "isConstant",
          "reduceNode"
        ) ++
        group(
          "scala-specific-helper",
          "findParent",
          "inBooleanContext",
          "compressionLevel",
          "numToString",
          "expect",
          "expectToken",
          "format"
        ) ++
        group("scala-option-accessor", "option") ++
        group("extracted-method", "hoistDeclarations", "hoistProperties") ++
        group("external-library-port", "encode", "decode", "stringify") ++
        group("ssg-glue", "compress") ++
        group("data-table", "domprops", "isIdentifierStart", "isIdentifierChar") ++
        // --- DEFNODE type constant: every AST class overrides nodeType with its TYPE string (103) ---
        group("defnode-type-constant", "nodeType") ++
        // --- DEFMETHOD _codegen callbacks: anonymous generators per AST class in output.js (53) ---
        group(
          "defmethod-codegen",
          "printArray",
          "printArrow",
          "printAwait",
          "printBigInt",
          "printBinary",
          "printCall",
          "printCase",
          "printCatch",
          "printClass",
          "printClassPrivateProperty",
          "printClassProperty",
          "printConciseMethod",
          "printConditional",
          "printDefinitions",
          "printDestructuring",
          "printDirective",
          "printDo",
          "printDot",
          "printDotHash",
          "printExit",
          "printExpansion",
          "printExport",
          "printFor",
          "printForIn",
          "printGetterSetter",
          "printIf",
          "printImport",
          "printLambdaBody",
          "printLoopControl",
          "printName",
          "printNameMapping",
          "printNode",
          "printNumber",
          "printObject",
          "printObjectKeyVal",
          "printPrefixedTemplateString",
          "printRegExp",
          "printSequence",
          "printString",
          "printSub",
          "printSwitch",
          "printSwitchBody",
          "printSymbol",
          "printTemplateString",
          "printTemplateStringChars",
          "printToString",
          "printTry",
          "printUnaryPostfix",
          "printUnaryPrefix",
          "printVarDef",
          "printWhile",
          "printWith",
          "printYield"
        ) ++
        // --- OPT() macro dispatch: per-AST-class optimizer callbacks in compress/index.js (43) ---
        group(
          "defmethod-opt-dispatch",
          "optimizeArray",
          "optimizeAssign",
          "optimizeBinary",
          "optimizeBlock",
          "optimizeBlockStatement",
          "optimizeBoolean",
          "optimizeCall",
          "optimizeChain",
          "optimizeClass",
          "optimizeConciseMethod",
          "optimizeConditional",
          "optimizeDebugger",
          "optimizeDefaultAssign",
          "optimizeDestructuring",
          "optimizeDirective",
          "optimizeDo",
          "optimizeDot",
          "optimizeFor",
          "optimizeFunction",
          "optimizeIf",
          "optimizeInfinity",
          "optimizeLabeledStatement",
          "optimizeLambda",
          "optimizeList",
          "optimizeNaN",
          "optimizeNew",
          "optimizeObject",
          "optimizeObjectKeyVal",
          "optimizeReturn",
          "optimizeSequence",
          "optimizeSimpleStatement",
          "optimizeSub",
          "optimizeSwitch",
          "optimizeSymbolRef",
          "optimizeTemplateString",
          "optimizeTree",
          "optimizeTry",
          "optimizeUnaryPostfix",
          "optimizeUnaryPrefix",
          "optimizeUndefined",
          "optimizeVarDef",
          "optimizeWhile",
          "optimizeYield"
        ) ++
        // --- DEFMETHOD inference: is_string/is_number/etc per AST class (29) ---
        group(
          "defmethod-inference",
          "isBigInt",
          "isBinNumber",
          "isBlockScope",
          "isCallPure",
          "isCalleePure",
          "isConstSymbolShorterThanInitValue",
          "isConstantExpression",
          "isDecNumber",
          "isEs6OctNumber",
          "isFirstInStatement",
          "isGlobal",
          "isHexChar",
          "isHexNumber",
          "isIdentifierCharCodePoint",
          "isIdentifierStartCodePoint",
          "isNumberOrBigInt",
          "isObjectLike",
          "isOctNumber",
          "isPureNativeFn",
          "isPureNativeMethod",
          "isPureNativeValue",
          "isQuotedKept",
          "isRecursiveRefByInfo",
          "isRefDeclared",
          "isRefImmutable",
          "isReservedWord",
          "isSelfReferential",
          "isTruthy",
          "isValidIdentifier"
        ) ++
        // --- cross-module-restructured: JS function in one file, Scala puts it in another (22) ---
        group(
          "cross-module-restructured",
          "declarationsAsNames",
          "findVariable",
          "firstInStatement",
          "fixedValue",
          "get",
          "hasAnnotation",
          "hasDirective",
          "isRecursiveRef",
          "isWithinLoop",
          "keepName",
          "length",
          "makeVoid0",
          "mergeSort",
          "parent",
          "pushUniq",
          "regexpIsSafe",
          "regexpSourceFix",
          "retainTopFunc",
          "semicolon",
          "statement",
          "visitNondeferredClassParts",
          "walkParent"
        ) ++
        // --- DEFMETHOD needs_parens: per-AST-class parenthesization in output.js (16) ---
        group(
          "defmethod-needs-parens",
          "needsParens",
          "needsParensArrow",
          "needsParensAssignConditional",
          "needsParensAwait",
          "needsParensBigInt",
          "needsParensBinary",
          "needsParensCall",
          "needsParensChain",
          "needsParensFunction",
          "needsParensNew",
          "needsParensNumber",
          "needsParensPrivateIn",
          "needsParensPropAccess",
          "needsParensSequence",
          "needsParensUnary",
          "needsParensYield"
        ) ++
        // --- DEFMETHOD drop_side_effect_free (8) ---
        group(
          "defmethod-drop-side-effect",
          "dropAssign",
          "dropBinary",
          "dropCall",
          "dropClass",
          "dropConditional",
          "dropConsole",
          "dropUnary",
          "dropUnused"
        ) ++
        // --- DEFMETHOD _eval (8) ---
        group(
          "defmethod-eval",
          "evalArray",
          "evalBinary",
          "evalCall",
          "evalConditional",
          "evalObject",
          "evalPropAccess",
          "evalSymbolRef",
          "evalUnaryPrefix"
        ) ++
        // --- scope analysis passes in scope.js (7) ---
        group("scope-analysis-pass", "pass1", "pass1Visit", "pass2", "pass2HandleRef", "pass2Visit", "pass3", "pass4") ++
        // --- DEFMETHOD reduce_vars (5) ---
        group("defmethod-reduce-vars", "reduceAssign", "reduceSymbolRef", "reduceUnary", "reduceVarDef", "reduceVars") ++
        // --- DEFMETHOD negate (1) ---
        group("defmethod-negate", "negate") ++
        // --- scala-restructured: closure-to-class, helper extraction, Scala-specific API (179) ---
        group(
          "scala-restructured",
          "_visit",
          "addChildScope",
          "addDirective",
          "addMapping",
          "addSourceMap",
          "allCodePointsAreIdentifierChars",
          "allSymbols",
          "anyHasSideEffects",
          "anyMayThrow",
          "apply",
          "applyCompressShorthand",
          "applyMangleShorthand",
          "applyOutputShorthand",
          "applyPropertyCache",
          "argsAsNames",
          "arrayLiteral",
          "asSymbolOpt",
          "assignAsUnused",
          "awaitExpression",
          "awaitUsingDef",
          "bitwiseNegate",
          "buildCommentFilter",
          "buildParentWalker",
          "checkWrapFuncArgs",
          "checkWrapIifeAndArgs",
          "childrenBackwards",
          "classDef",
          "cloneNode",
          "codeGen",
          "col",
          "collapseVars",
          "computeCharFrequency",
          "conflictingDef",
          "conflictingDefShallow",
          "constDef",
          "containsInOperator",
          "containsOptional",
          "containsThis",
          "createAccessor",
          "createSymbol",
          "currentIndentation",
          "currentWidth",
          "deepClone",
          "defFunction",
          "defGlobal",
          "defVariable",
          "definition",
          "dispatchDefault",
          "dispatchToken",
          "doAddMapping",
          "dotThrow",
          "ensureLineLen",
          "evaluate",
          "expandNames",
          "figureOutScope",
          "findCollidingNames",
          "findDefs",
          "findDefsWithSuffix",
          "findScopeLive",
          "flattenObject",
          "forStatement",
          "formatOptions",
          "fromAny",
          "fromRawNull",
          "functionBody",
          "functionDef",
          "getDefunScope",
          "getSymbolName",
          "handleBlockScope",
          "handleSymbolDecl",
          "handleVarLikeDecl",
          "hasOwnProperty",
          "hasParens",
          "hasSideEffects",
          "ifStatement",
          "inTryDeadCode",
          "indent",
          "initArrowScopeVars",
          "initLambdaScopeVars",
          "initScopeVars",
          "inlineIntoSymbolRef",
          "is32BitInteger",
          "jsCoerceToString",
          "lastOutput",
          "leadingZeroRunLength",
          "letDef",
          "liftSequencesAssign",
          "liftSequencesBinaryRight",
          "liftSequencesUnary",
          "line",
          "liveFindParent",
          "liveParent",
          "liveSelf",
          "makeEmpty",
          "makeNumber",
          "makeSymbolFromPropertyName",
          "makeUniqueName",
          "mangleNames",
          "markEnclosed",
          "mayThrow",
          "mayThrowOnAccess",
          "minifyFiles",
          "minifyResult",
          "minifySeq",
          "minifyToString",
          "name",
          "newExpression",
          "newline",
          "nextMangledFunction",
          "nextMangledToplevel",
          "nlb",
          "nlb_",
          "nullArrayEq",
          "nullEq",
          "numberSize",
          "objectOrDestructuring",
          "parenthesizeForNoIn",
          "parseExpForm",
          "parseExpression",
          "parseToplevel",
          "pinned",
          "popDirectivesStack",
          "popNode",
          "pos",
          "processDefinitions",
          "processExpression",
          "propEq",
          "propertiesEnabled",
          "propertyNode",
          "pureFuncs",
          "pushDirectivesStack",
          "pushNode",
          "quote",
          "quote_",
          "readNameHard",
          "readRegexp",
          "readString",
          "readTemplateCharacters",
          "reference",
          "removeFromArrayBuffer",
          "reservesQuotedGlobally",
          "resetIds",
          "resetOptFlags",
          "resolveDefaults",
          "resolveDefs",
          "resolveFixedValue",
          "resolveProperties",
          "sameScopeAsDef",
          "sameType",
          "sequenceize",
          "sequenceize2",
          "sequencesLimit",
          "shallowCmpByType",
          "shouldBreak",
          "size",
          "skipMultilineComment",
          "space",
          "spliceOrig",
          "statementInner",
          "stripLeadingZeroDot",
          "symbolSize",
          "templateEnd",
          "templateEnd_",
          "toAssignments",
          "toRawNull",
          "toUtf8",
          "trailingZeroRunLength",
          "transformWithSplice",
          "tryStatement",
          "unreferenced",
          "usingDef",
          "varDef",
          "verifySymbol",
          "walkSymbolDeclarations",
          "walkWithVisitor",
          "withIndent",
          "wrapCommonjs",
          "wrapEnclose",
          "yieldExpression"
        )
    ).toMap
  }

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

  /** Translate the RAST bodies from one or more files into parity-derive bodies. */
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
      val key       = camelCase(name)
      val sig       = oracle.get(refObjectName, key)
      val retType   = sig.map(_.returnType)
      val paramTpes = sig.map(s => s.params.map(p => p.name -> p.tpe).toMap).getOrElse(Map.empty)
      val entry     = DefmethodEntry("_free_", name, params, body)
      try {
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
        val bodyText = translated.scalaBody.trim
        val reasons  = if (bodyText.isEmpty) "empty-body" :: translated.refusalReasons else translated.refusalReasons
        result.getOrElseUpdate(key, mutable.ListBuffer.empty) += ParityDerive.TranslatedBody(translated.scalaBody, reasons)
      } catch {
        case e: Exception =>
          // A translator crash is a refusal, not a build failure
          result.getOrElseUpdate(key, mutable.ListBuffer.empty) += ParityDerive.TranslatedBody("", List(s"translator-crash:${e.getClass.getSimpleName}"))
      }
    }

    ParityDerive.Bodies(result.map { case (k, v) => k -> v.toList }.toMap)
  }

  /** Read all `.scala` files under a directory as `(objectName, source)` pairs. */
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

  /** The Library value for `NonJavaBodies.build`. */
  def library(referenceDir: Path): NonJavaBodies.Library = {
    val refSources                                  = readReferenceSources(referenceDir)
    val (calleeIdx, memberIdx, ctorSchema, enumIdx) = ReferenceSignatures.buildIndices(refSources)
    val oracle                                      = ReferenceSignatures.TypeOracle.fromEntries(refSources.flatMap((n, s) => ReferenceSignatures.parseFile(n, s)))

    NonJavaBodies.Library(
      name = "terser",
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

  // -------------------------------------------------------------------------
  // RAST export: writes a tsconfig with allowJs, copies ast.d.ts, runs the
  // exporter.
  // -------------------------------------------------------------------------

  /** Export terser RAST into `outDir`, writing a temporary tsconfig and ast.d.ts. */
  def exportRast(
    terserSrc:   Path,
    astDtsFile:  Path,
    outDir:      Path,
    exporterDir: Path,
    log:         sbt.util.Logger
  ): Unit = {
    Files.createDirectories(outDir)

    // Copy ast.d.ts next to lib/ast.js so the TS checker sees typed fields
    val astDtsDest = terserSrc.resolve("lib/ast.d.ts")
    Files.copy(astDtsFile, astDtsDest, java.nio.file.StandardCopyOption.REPLACE_EXISTING)

    // Write a tsconfig for allowJs export
    val tsconfig = terserSrc.resolve(".bp-tsconfig.json")
    Files.writeString(
      tsconfig,
      """{
  "compilerOptions": {
    "target": "ES2022",
    "module": "nodenext",
    "allowJs": true,
    "checkJs": false,
    "strict": false,
    "noEmit": true,
    "skipLibCheck": true,
    "esModuleInterop": true,
    "resolveJsonModule": true
  },
  "include": ["lib/**/*.js"]
}
"""
    )

    val exporterScript = exporterDir.resolve("export.js")
    val pb             = new ProcessBuilder("node", exporterScript.toString, "--project", tsconfig.toString, "--out", outDir.toString)
    pb.directory(exporterDir.toFile)
    pb.redirectErrorStream(true)
    val p   = pb.start()
    val out = new String(p.getInputStream.readAllBytes())
    if (p.waitFor() != 0) sys.error(s"[Baltic Porter] Terser RAST export failed:\n$out")
    log.info(s"[Baltic Porter] Exported terser RAST to $outDir")

    // Clean up temporary files from the submodule
    Files.deleteIfExists(astDtsDest)
    Files.deleteIfExists(tsconfig)
  }
}
