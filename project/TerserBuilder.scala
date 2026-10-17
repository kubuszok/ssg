import balticporter.frontend.ts.{ NonJavaBodies, ParityDerive, Rast, RastFile, RastFunctions, RastNode, RastValue, ReferenceDeclarations, ReferenceSignatures }
import balticporter.frontend.ts.dedicated.{ DefmethodBodyTranslator, DefmethodEntry, JsConventions }

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

  /** No uncompilable-pattern guard: every body the translator cannot render faithfully it refuses itself, with its reason in bodies.tsv. */
  val policy: ParityDerive.Policy =
    ParityDerive.Policy(
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
    "MAP" -> "MAP",
    // utils: the constant-returning helpers terser assigns as methods
    "return_false" -> "false",
    "return_true" -> "true",
    "return_this" -> "this",
    "pass_through" -> "true",
    // tree walker abort sentinel
    "walkAbort" -> "TreeWalker.WalkAbort",
    // compress/compressor-flags.js
    "has_flag" -> "CompressorFlags.hasFlag",
    "set_flag" -> "CompressorFlags.setFlag",
    "clear_flag" -> "CompressorFlags.clearFlag"
  )

  /** How terser spells its AST in JS: `AST_X` is the class `AstX` (`AstNode` at the root), `node.TYPE` is `nodeType`, `make_node(AST_X, orig, {…})` builds a node, and `node.size()` lives in
    * `AstSize`.
    */
  private val conventions: JsConventions = JsConventions(
    classPrefix = Some("AST_" -> "Ast"),
    rootClass = Some("AstNode"),
    typeTag = Some("TYPE" -> "nodeType"),
    nodeFactories = Set("make_node"),
    methodRewrites = Map("size" -> "AstSize.size($receiver)")
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

  /** The table of reference members that keep their reference body, by (reference file, member): DEFMETHOD/OPT callbacks the reference restructured into named methods or pattern-matching dispatch,
    * Scala-specific helpers and the ssg glue, each with its reason. File-scoped, so a name kept in one file never keeps a namesake another file translates.
    */
  val referenceOnlyTable: String = "ssg-js/port/reference-only.tsv"

  private def referenceOnlyAt(ssgRoot: Path): Map[(String, String), String] =
    Files
      .readAllLines(ssgRoot.resolve(referenceOnlyTable), StandardCharsets.UTF_8)
      .asScala
      .toList
      .filter(l => l.trim.nonEmpty && !l.startsWith("#"))
      .map { l =>
        l.split("\t", -1) match {
          case Array(file, member, reason) => (file, member) -> reason
          case _                           => sys.error(s"[Baltic Porter] $referenceOnlyTable: a row needs file, member and reason: $l")
        }
      }
      .toMap

  /** Translate the RAST bodies from one or more files into parity-derive bodies. */
  private def buildTranslatedBodyMap(
    rastFiles:     List[RastFile],
    refObjectName: String,
    oracle:        ReferenceSignatures.TypeOracle,
    calleeIdx:     ReferenceSignatures.CalleeIndex,
    memberIdx:     ReferenceSignatures.MemberIndex,
    ctorSchema:    ReferenceSignatures.ConstructorSchema,
    enumIdx:       ReferenceSignatures.EnumIndex,
    owners:        ReferenceOwners,
    fileNames:     ReferenceDeclarations.FileNames
  ): ParityDerive.Bodies = {
    val result = mutable.Map.empty[String, mutable.ListBuffer[ParityDerive.TranslatedBody]]

    for {
      rastFile <- rastFiles
      RastFunctions.Function(owner, name, params, body, outer) <- RastFunctions.of(rastFile)
    } {
      val key       = camelCase(name)
      val sig       = oracle.get(refObjectName, key)
      val retType   = sig.map(_.returnType)
      val paramTpes = sig.map(s => s.params.map(p => p.name -> p.tpe).toMap).getOrElse(Map.empty)
      val entry     = DefmethodEntry(owner, name, params, body, outer)
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
          oracle = oracle,
          enclosingOwner = owners.ownerOf(refObjectName, key, policy.aliases),
          tsTypes = rastFile.types,
          conventions = conventions,
          enclosingClass = owners.classOf(refObjectName, key, policy.aliases),
          fileNames = fileNames,
          // the reference's parameter names in order: one it renamed is read under its own name
          paramNames = sig.map(_.params.map(_.name)).getOrElse(Nil)
        )
        val bodyText = translated.scalaBody.trim
        val reasons  = if (bodyText.isEmpty) "empty-body" :: translated.refusalReasons else translated.refusalReasons
        result.getOrElseUpdate(key, mutable.ListBuffer.empty) += ParityDerive.TranslatedBody(translated.scalaBody, reasons, translated.refusalSites.map(_.render(rastFile.path)))
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
  def library(ssgRoot: Path, referenceDir: Path): NonJavaBodies.Library = {
    val refSources                                  = readReferenceSources(referenceDir)
    val (calleeIdx, memberIdx, ctorSchema, enumIdx) = ReferenceSignatures.buildIndices(refSources)
    val oracle                                      = ReferenceSignatures.TypeOracle.fromEntries(refSources.flatMap((n, s) => ReferenceSignatures.parseFile(n, s)))
    val owners                                      = ReferenceOwners.of(refSources)

    NonJavaBodies.Library(
      name = "terser",
      policy = policy,
      readRast = Rast.readFile(_: java.nio.file.Path),
      referenceOnlyAt = referenceOnlyAt(ssgRoot),
      modules = _ =>
        Right(
          allModules.map { case (rastPath, refPath) =>
            val refObjectName = refPath.stripSuffix(".scala").split('/').last
            NonJavaBodies.Module(
              refPath,
              rastPath,
              Nil,
              rasts =>
                buildTranslatedBodyMap(
                  rasts,
                  refObjectName,
                  oracle,
                  calleeIdx,
                  memberIdx,
                  ctorSchema,
                  enumIdx,
                  owners,
                  ReferenceNames.of(referenceDir, refPath)
                )
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
