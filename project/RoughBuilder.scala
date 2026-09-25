import balticporter.frontend.ts.{ NonJavaBodies, ParityDerive, Rast, RastFile, RastNode, RastValue, ReferenceSignatures }
import balticporter.frontend.ts.dedicated.{ DefmethodBodyTranslator, DefmethodEntry }

import java.nio.charset.StandardCharsets
import java.nio.file.{ Files, Path }
import scala.collection.mutable
import scala.jdk.CollectionConverters.*

/** Rough.js body builder for parity-derive: the per-library policy for the five rough.js family upstreams (roughjs, path-data-parser, points-on-curve, points-on-path, hachure-fill), adapted to the
  * generic `NonJavaBodies.build` entry point.
  *
  * The mechanism (ParityDerive, bodies.tsv, refusal recording) lives in the engine. This file carries only what is specific to the rough.js family: the module table, the referenceOnly entries, the
  * aliases, and the body translator invocation.
  */
object RoughBuilder {

  /** The five upstream submodule names, in the order they are exported. */
  val upstreamNames: List[String] = List(
    "roughjs",
    "path-data-parser",
    "points-on-curve",
    "points-on-path",
    "hachure-fill"
  )

  /** Patterns in a translated RAST body that cannot compile in ssg-graphs-commons.
    *
    * Each catches a construct the body translator emits that breaks compilation, documented with the defect class and affected members. A pattern is the LAST resort, one per defect class; defects are
    * recorded in the tracker.
    */
  private val uncompilablePatterns: List[String] = List(
    // JS ** exponentiation operator emitted as literal token name (randomSeed, next in RoughMath)
    "AsteriskAsteriskToken",
    // TS tuple type [number, number] emitted as array-index access on Point/Line case classes:
    // p(0), p(1), line(0) etc. (lineLength in Geometry, distance/distanceSq/distanceToSegmentSq/
    // lerp/flatness/simplify/getPointsOnBezierCurveWithSplitting in PointsOnCurve, clone in
    // CurveToBezier, rotatePoints in HachureFill, rotate in Normalize)
    "(0) -",
    // Point.toArray (clone in CurveToBezier)
    ".toArray",
    // Wrong case for _fillPolygons: translator produces this.FillPolygons (HachureFiller)
    "this.FillPolygons",
    // Val reassignment: ret.shape = "circle" (circle in RoughGenerator)
    ".shape =",
    // Destructuring in filter callback: ((d, i) => (...)) (_mergedShape in RoughGenerator)
    "((d, i)",
    // Int truthiness: if (this.seed) where seed is Int (next in RoughMath)
    "if (this.seed)",
    // Extra ctor arg from dropped DOM param: new RoughSVG(svg, config) (svg in Rough)
    "new RoughSVG(svg",
    // TS tuple mutation: p(0) = ... / p(1) = ... on Point case class (rotatePoints in HachureFill)
    "p(0) =",
    // Wrong self-reference: translator prefixes own-object methods with the object name
    // (getPointsOnBezierCurveWithSplitting, simplify in PointsOnCurve)
    "PointsOnCurve.",
    // ArrayBuffer where Vector or tuple expected (getPointsOnBezierCurveWithSplitting in
    // PointsOnCurve, rotate in Normalize)
    "ArrayBuffer[Point](",
    "ArrayBuffer(X",
    // JS truthiness on Option/Vector: if (newPoints), points && points.length
    // (getPointsOnBezierCurveWithSplitting, rotatePoints)
    "if (newPoints)",
    "points && points"
  )

  val policy: ParityDerive.Policy =
    ParityDerive.Policy(
      uncompilablePatterns = uncompilablePatterns,
      keepReferenceOnRefusal = true,
      aliases = Map(
        // Underscore-prefixed TS names whose camelCase form differs from the reference member name.
        // camelCase("_fillPolygons") -> "FillPolygons" but reference keeps "_fillPolygons"
        "_fillPolygons" -> List("FillPolygons"),
        // renderer.ts private helpers
        "_offset" -> List("Offset"),
        "_offsetOpt" -> List("OffsetOpt"),
        "_doubleLine" -> List("DoubleLine"),
        "_line" -> List("Line"),
        "_curveWithOffset" -> List("CurveWithOffset"),
        "_curve" -> List("Curve"),
        "_computeEllipsePoints" -> List("ComputeEllipsePoints"),
        "_arc" -> List("Arc"),
        "_bezierTo" -> List("BezierTo"),
        // generator.ts
        "_o" -> List("O"),
        "_d" -> List("D"),
        "_mergedShape" -> List("MergedShape")
      )
    )

  /** JS property names that the Scala reference renamed. The body translator's `snakeToCamel` would mangle underscore-prefixed names; `type` is a Scala reserved word.
    */
  private val memberRenames: Map[String, String] = Map(
    // path-data-parser Parser.scala: TS PathToken `type` field -> `tokenType`
    "type" -> "`type`"
  )

  /** API name lookup for the body translator: JS identifier to Scala equivalent. Module-level functions and cross-module references that the translator needs to resolve.
    */
  private val apiLookup: Map[String, String] = Map(
    // roughjs cross-module references
    "lineLength" -> "Geometry.lineLength",
    "polygonHachureLines" -> "ScanLineHachure.polygonHachureLines",
    "hachureLines" -> "HachureFill.hachureLines",
    "getFiller" -> "Filler.getFiller",
    "randomSeed" -> "RoughMath.randomSeed",
    // path-data-parser cross-module references
    "parsePath" -> "Parser.parsePath",
    "serialize" -> "Parser.serialize",
    "absolutize" -> "Absolutize.absolutize",
    "normalize" -> "Normalize.normalize",
    // points-on-curve
    "curveToBezier" -> "CurveToBezier.curveToBezier",
    "pointsOnBezierCurves" -> "PointsOnCurve.pointsOnBezierCurves",
    // points-on-path
    "pointsOnPath" -> "PointsOnPath.pointsOnPath"
  )

  private def camelCase(s: String): String =
    if (s.contains("_")) {
      val parts = s.split("_")
      parts.head + parts.tail.map(p => if (p.nonEmpty) p(0).toUpper + p.substring(1) else "").mkString
    } else s

  // -------------------------------------------------------------------------
  // Module table: every reference file and the RAST file that feeds it.
  // RAST paths are <upstream>/<path>.rast.json, resolved under the common rast dir.
  // Reference paths are relative to the reference dir's anchor.
  // -------------------------------------------------------------------------

  private val roughjsModules: List[(String, String)] = List(
    ("roughjs/src/core.rast.json", "Core.scala"),
    ("roughjs/src/geometry.rast.json", "Geometry.scala"),
    ("roughjs/src/rough.rast.json", "Rough.scala"),
    ("roughjs/src/generator.rast.json", "RoughGenerator.scala"),
    ("roughjs/src/math.rast.json", "RoughMath.scala"),
    ("roughjs/src/renderer.rast.json", "RoughRenderer.scala"),
    ("roughjs/src/svg.rast.json", "RoughSVG.scala"),
    // canvas.ts is platform-inapplicable (browser DOM only, no reference file)
    // fillers
    ("roughjs/src/fillers/dashed-filler.rast.json", "fillers/DashedFiller.scala"),
    ("roughjs/src/fillers/dot-filler.rast.json", "fillers/DotFiller.scala"),
    ("roughjs/src/fillers/filler.rast.json", "fillers/Filler.scala"),
    // FillerInterface.scala declares ABSTRACT trait methods (TS interface); these have no RAST body.
    // Using a non-existent RAST so the members report as translator-refusal:missing-rast
    // rather than no-translated-body (the concrete implementations in the fillers get their own RAST).
    ("__no-export__/FillerInterface", "fillers/FillerInterface.scala"),
    ("roughjs/src/fillers/hachure-filler.rast.json", "fillers/HachureFiller.scala"),
    ("roughjs/src/fillers/hatch-filler.rast.json", "fillers/HatchFiller.scala"),
    ("roughjs/src/fillers/scan-line-hachure.rast.json", "fillers/ScanLineHachure.scala"),
    ("roughjs/src/fillers/zigzag-filler.rast.json", "fillers/ZigZagFiller.scala"),
    ("roughjs/src/fillers/zigzag-line-filler.rast.json", "fillers/ZigZagLineFiller.scala")
  )

  private val pathDataParserModules: List[(String, String)] = List(
    ("path-data-parser/src/absolutize.rast.json", "pathdata/Absolutize.scala"),
    ("path-data-parser/src/normalize.rast.json", "pathdata/Normalize.scala"),
    ("path-data-parser/src/parser.rast.json", "pathdata/Parser.scala"),
    ("path-data-parser/src/index.rast.json", "pathdata/PathDataParser.scala")
  )

  private val pointsOnCurveModules: List[(String, String)] = List(
    ("points-on-curve/src/curve-to-bezier.rast.json", "curve/CurveToBezier.scala"),
    ("points-on-curve/src/index.rast.json", "curve/PointsOnCurve.scala")
  )

  private val pointsOnPathModules: List[(String, String)] = List(
    ("points-on-path/src/index.rast.json", "curve/PointsOnPath.scala")
  )

  private val hachureFillModules: List[(String, String)] = List(
    ("hachure-fill/src/hachure.rast.json", "fillers/HachureFill.scala")
  )

  private val allModules: List[(String, String)] =
    roughjsModules ++ pathDataParserModules ++ pointsOnCurveModules ++ pointsOnPathModules ++ hachureFillModules

  /** Members the reference declares that have no TS counterpart: the engine keeps the reference body and records the reason in bodies.tsv. Keyed by member name (matched globally across all files).
    */
  private val referenceOnly: Map[String, String] = Map(
    // RoughSVG.scala: Scala-specific helpers for SVG attribute formatting (no TS counterpart;
    // the TS version uses browser DOM setAttribute which handles numeric conversion implicitly)
    "numStr" -> "scala-specific-helper",
    "numTruthy" -> "scala-specific-helper",
    // RoughSVG.scala + RoughRenderer.scala + RoughGenerator.scala: Scala-specific helpers
    // for SVG attribute formatting and numeric truthiness (no TS counterpart)
    "numStr" -> "scala-specific-helper",
    "numTruthy" -> "scala-specific-helper",
    // HachureFill.scala + ScanLineHachure.scala + ZigZagFiller.scala: Scala-specific helpers
    // for JS truthiness semantics (no TS counterpart; the TS uses JS truthiness directly)
    "truthy" -> "scala-truthiness-helper",
    // RoughSVG.scala: TS get accessor for the gen field, not extracted from RAST;
    // Rough.scala: factory method, trivially correct in the reference (one-liner)
    "generator" -> "get-accessor-or-trivial",
    // Rough.scala: the TS has canvas() but the Scala body is a deliberate platform-inapplicable
    // throw (RoughCanvas does not exist in SSG); keep the reference body
    "canvas" -> "platform-inapplicable",
    // RoughGenerator.scala: Scala-specific helpers with no TS counterpart
    "mergeOptions" -> "scala-specific-helper",
    "fillTruthy" -> "scala-specific-helper",
    "toCurvePoint" -> "scala-specific-helper",
    "toGeomPoint" -> "scala-specific-helper",
    "bezierPolyPoints" -> "scala-specific-helper",
    "ecmaFormat" -> "scala-specific-helper",
    // HachureFill.scala: Scala comparators replacing inline JS sort arrow functions
    "edgeCompare" -> "scala-comparator-helper",
    "activeCompare" -> "scala-comparator-helper",
    // Parser.scala: Scala-specific JS number formatting helper
    "jsNum" -> "scala-specific-helper"
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

  /** Translate the RAST bodies from one or more files into parity-derive bodies. */
  private def buildTranslatedBodyMap(
    rastFiles:     List[RastFile],
    refObjectName: String,
    oracle:        ReferenceSignatures.TypeOracle,
    calleeIdx:     ReferenceSignatures.CalleeIndex,
    memberIdx:     ReferenceSignatures.MemberIndex,
    ctorSchema:    ReferenceSignatures.ConstructorSchema,
    enumIdx:       ReferenceSignatures.EnumIndex,
    owners:        ReferenceOwners
  ): ParityDerive.Bodies = {
    val result = mutable.Map.empty[String, mutable.ListBuffer[ParityDerive.TranslatedBody]]

    for {
      rastFile <- rastFiles
      (name, params, body) <- functionBodies(rastFile)
    } {
      val key        = camelCase(name)
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
        oracle = oracle,
        enclosingOwner = owners.ownerOf(refObjectName, key, policy.aliases)
      )
      val bodyText = translated.scalaBody.trim
      val reasons  = if (bodyText.isEmpty) "empty-body" :: translated.refusalReasons else translated.refusalReasons
      result.getOrElseUpdate(key, mutable.ListBuffer.empty) += ParityDerive.TranslatedBody(translated.scalaBody, reasons)
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

  /** The Library value for `NonJavaBodies.build`.
    *
    * Builds type indices from the reference Scala tree once, then passes them to every module's body translator.
    */
  def library(referenceDir: Path): NonJavaBodies.Library = {
    val refSources                                  = readReferenceSources(referenceDir)
    val (calleeIdx, memberIdx, ctorSchema, enumIdx) = ReferenceSignatures.buildIndices(refSources)
    val oracle                                      = ReferenceSignatures.TypeOracle.fromEntries(refSources.flatMap((n, s) => ReferenceSignatures.parseFile(n, s)))
    val owners                                      = ReferenceOwners.of(refSources)

    NonJavaBodies.Library(
      name = "graphs-commons",
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
              rasts => buildTranslatedBodyMap(rasts, refObjectName, oracle, calleeIdx, memberIdx, ctorSchema, enumIdx, owners)
            )
          }
        )
    )
  }

  // -------------------------------------------------------------------------
  // RAST export: one export per upstream, cached on commit + exporter version
  // -------------------------------------------------------------------------

  /** Export RAST for one upstream into `<parentDir>/<name>/`, cached on the submodule's commit and the exporter version. Returns the output directory.
    */
  def exportRast(
    name:         String,
    submoduleDir: Path,
    parentDir:    Path,
    exporterDir:  Path,
    log:          sbt.util.Logger
  ): Path = {
    val outDir = parentDir.resolve(name)
    Files.createDirectories(outDir)

    val exporterScript = exporterDir.resolve("export.js")
    val tsconfigPath   = submoduleDir.resolve("tsconfig.json")

    if (!Files.isRegularFile(tsconfigPath))
      sys.error(s"[Baltic Porter] $name has no tsconfig.json at $tsconfigPath")

    val pb = new ProcessBuilder("node", exporterScript.toString, "--project", tsconfigPath.toString, "--out", outDir.toString)
    pb.directory(exporterDir.toFile)
    pb.redirectErrorStream(true)
    val p   = pb.start()
    val out = new String(p.getInputStream.readAllBytes())
    if (p.waitFor() != 0) sys.error(s"[Baltic Porter] $name RAST export failed:\n$out")
    log.info(s"[Baltic Porter] Exported $name RAST to $outDir")
    outDir
  }
}
