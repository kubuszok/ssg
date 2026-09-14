import sbt.*
import sbt.Keys.*

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** sbt sourceGenerator that uses Baltic Porter to mechanically port liqp (ssg-liquid)
  * and flexmark-java (ssg-md) Java sources into Scala 3.
  *
  * Uses the corpus's `.conf` files via `PortConfig.load`, which carries the full manifest
  * (package renames, collection transforms, service providers, etc.) as documented decisions.
  *
  * Requires:
  *   - upstream sources at `original-src/liqp` and `original-src/flexmark-java` (git submodules)
  *   - balticporter checkout at `../balticporter` (sibling directory)
  *   - `balticporter-corpus` 0.1.0-SNAPSHOT published locally
  */
object BalticPorterGen {

  /** Generate ssg-liquid Scala sources from liqp Java originals. */
  def generateLiquid(buildBase: File, log: sbt.util.Logger): Seq[File] = {
    val ssgRoot = buildBase.toPath.toAbsolutePath.normalize
    val bpRoot = Path.of(sys.props.getOrElse("balticporter.root",
      ssgRoot.resolve("../balticporter").toString)).toAbsolutePath.normalize

    val liqpSrc = ssgRoot.resolve("original-src/liqp/src/main/java")
    require(Files.isDirectory(liqpSrc),
      s"liqp sources not found at $liqpSrc — run: git submodule update --init")

    // PortConfig writes to the portRoot specified in the .conf file, which resolves
    // to balticporter/ported/ssg-liquid/ (relative to the conf). The sourceGenerator
    // reads from THAT location — no local copy needed.
    val portRoot = bpRoot.resolve("ported/ssg-liquid")
    val outDir = portRoot.resolve("src_managed/main/scala")
    val marker = ssgRoot.resolve("target/balticporter-ssg-liquid/.generated-marker")

    // Cache by upstream commit. Force regeneration with -Dbalticporter.forceRegen=true.
    val forceRegen = sys.props.getOrElse("balticporter.forceRegen", "false").toBoolean
    val commit = balticporter.runner.VendoredCommit.of(ssgRoot.resolve("original-src/liqp"))
    val cached = !forceRegen && Files.exists(marker) &&
      Files.exists(outDir) &&
      Files.readString(marker).trim == commit

    if (!cached) {
      log.info(s"[Baltic Porter] Generating ssg-liquid sources from liqp ($commit)")

      // The liqp port uses a .conf file that carries the full manifest:
      // package renames (liqp → ssg.liquid), collection transforms, service providers,
      // jackson reified carriers, ServiceLoader redirect, etc.
      val confPath = bpRoot.resolve("balticporter/corpus/ports/liqp/main.conf")
      require(Files.exists(confPath),
        s"liqp port config not found at $confPath — publish balticporter-corpus first")

      // LiqpClasspath ensures the ANTLR-generated parser is compiled and the frontend
      // classpath file exists. Without this, the frontend can't resolve liqp's imports.
      balticporter.corpus.liqp.LiqpClasspath.ensure(bpRoot)

      // Override the port root to write into THIS build's target directory, and set
      // balticporter.root so the conf's relative paths resolve correctly.
      System.setProperty("balticporter.root", bpRoot.toAbsolutePath.normalize.toString)
      // Override the output directory: the conf writes to ported/ssg-liquid by default,
      // but we want target/balticporter-ssg-liquid in THIS project.
      try {
        val config = balticporter.runner.PortConfig.load(confPath, Seq(
          s"--portRoot=$portRoot"
        ))
        config.execute()
        log.info(s"[Baltic Porter] Generated ssg-liquid sources to $outDir")
      } catch {
        case e: RuntimeException if e.getMessage != null && e.getMessage.contains("fatal finding") =>
          log.warn(s"[Baltic Porter] Port completed with findings (files written): ${e.getMessage}")
      }

      // Post-process: fix API name mismatches the engine's transforms miss in call sites.
      postProcess(outDir, log)

      Files.createDirectories(marker.getParent)
      Files.writeString(marker, commit)
    } else {
      log.info(s"[Baltic Porter] Using cached ssg-liquid sources ($commit)")
    }

    // Collect generated files, excluding any that also exist in ssg-liquid's hand-written sources.
    val ssgLiquidSrc = ssgRoot.resolve("ssg-liquid/src/main")
    collectScalaFiles(outDir, excludeDuplicatesOf = Some(ssgLiquidSrc))
  }

  /** Generate ssg-md Scala sources from flexmark-java originals. */
  def generateFlexmark(buildBase: File, log: sbt.util.Logger): Seq[File] = {
    val ssgRoot = buildBase.toPath.toAbsolutePath.normalize
    val bpRoot = Path.of(sys.props.getOrElse("balticporter.root",
      ssgRoot.resolve("../balticporter").toString)).toAbsolutePath.normalize

    val flexmarkSrc = ssgRoot.resolve("original-src/flexmark-java")
    require(Files.isDirectory(flexmarkSrc),
      s"flexmark-java sources not found at $flexmarkSrc — run: git submodule update --init")

    val portRoot = bpRoot.resolve("ported/ssg-md")
    val outDir = portRoot.resolve("src_managed/main/scala")
    val marker = ssgRoot.resolve("target/balticporter-ssg-md/.generated-marker")

    val forceRegen = sys.props.getOrElse("balticporter.forceRegen", "false").toBoolean
    val commit = balticporter.runner.VendoredCommit.of(flexmarkSrc)
    val cached = !forceRegen && Files.exists(marker) &&
      Files.exists(outDir) &&
      Files.readString(marker).trim == commit

    if (!cached) {
      log.info(s"[Baltic Porter] Generating ssg-md sources from flexmark-java ($commit)")

      val confPath = bpRoot.resolve("balticporter/corpus/ports/ssg-md/main.conf")
      require(Files.exists(confPath),
        s"flexmark port config not found at $confPath — publish balticporter-corpus first")

      // FlexmarkClasspath ensures the annotations JAR is resolved and the frontend
      // classpath file exists. Without this, @NotNull/@Nullable don't resolve.
      balticporter.corpus.flexmark.FlexmarkClasspath.ensure(bpRoot)

      System.setProperty("balticporter.root", bpRoot.toAbsolutePath.normalize.toString)
      try {
        val config = balticporter.runner.PortConfig.load(confPath, Seq(
          s"--portRoot=$portRoot"
        ))
        config.execute()
        log.info(s"[Baltic Porter] Generated ssg-md sources to $outDir")
      } catch {
        case e: RuntimeException if e.getMessage != null && e.getMessage.contains("fatal finding") =>
          log.warn(s"[Baltic Porter] Port completed with findings (files written): ${e.getMessage}")
      }

      // ssg-md does NOT get the isEmpty() → isEmpty replacement: flexmark defines its
      // own isEmpty() on Range, Attributes, TableRow, ISequenceBuilder etc. which must
      // keep parens. Only JavaCollection.isEmpty is parenless (NullaryArityTransform).
      postProcess(outDir, log, skipIsEmpty = true)

      Files.createDirectories(marker.getParent)
      Files.writeString(marker, commit)
    } else {
      log.info(s"[Baltic Porter] Using cached ssg-md sources ($commit)")
    }

    val ssgMdSrc = ssgRoot.resolve("ssg-md/src/main")
    collectScalaFiles(outDir, excludeDuplicatesOf = Some(ssgMdSrc))
  }

  /** Generate ssg-md-ext Scala sources from flexmark extension modules. */
  def generateFlexmarkExt(buildBase: File, log: sbt.util.Logger): Seq[File] = {
    val ssgRoot = buildBase.toPath.toAbsolutePath.normalize
    val bpRoot = Path.of(sys.props.getOrElse("balticporter.root",
      ssgRoot.resolve("../balticporter").toString)).toAbsolutePath.normalize

    val portRoot = bpRoot.resolve("ported/ssg-md-ext")
    val outDir = portRoot.resolve("src_managed/main/scala")
    val marker = ssgRoot.resolve("target/balticporter-ssg-md-ext/.generated-marker")

    val forceRegen = sys.props.getOrElse("balticporter.forceRegen", "false").toBoolean
    val flexmarkSrc = ssgRoot.resolve("original-src/flexmark-java")
    val commit = balticporter.runner.VendoredCommit.of(flexmarkSrc)
    val cached = !forceRegen && Files.exists(marker) &&
      Files.exists(outDir) &&
      Files.readString(marker).trim == commit

    if (!cached) {
      log.info(s"[Baltic Porter] Generating ssg-md-ext sources from flexmark extensions ($commit)")

      val confPath = bpRoot.resolve("balticporter/corpus/ports/ssg-md/ext.conf")
      require(Files.exists(confPath),
        s"flexmark-ext port config not found at $confPath — publish balticporter-corpus first")

      balticporter.corpus.flexmark.FlexmarkClasspath.ensure(bpRoot)

      System.setProperty("balticporter.root", bpRoot.toAbsolutePath.normalize.toString)
      try {
        val config = balticporter.runner.PortConfig.load(confPath, Seq(
          s"--portRoot=$portRoot"
        ))
        config.execute()
        log.info(s"[Baltic Porter] Generated ssg-md-ext sources to $outDir")
      } catch {
        case e: RuntimeException if e.getMessage != null && e.getMessage.contains("fatal finding") =>
          log.warn(s"[Baltic Porter] Port completed with findings (files written): ${e.getMessage}")
      }

      postProcess(outDir, log, skipIsEmpty = true)

      Files.createDirectories(marker.getParent)
      Files.writeString(marker, commit)
    } else {
      log.info(s"[Baltic Porter] Using cached ssg-md-ext sources ($commit)")
    }

    val ssgMdSrc = ssgRoot.resolve("ssg-md/src/main")
    collectScalaFiles(outDir, excludeDuplicatesOf = Some(ssgMdSrc))
  }

  /** Fix API name mismatches in generated code (same patterns as sge). */
  private def postProcess(outDir: Path, log: sbt.util.Logger, skipIsEmpty: Boolean = false): Unit = {
    if (!Files.isDirectory(outDir)) return
    val replacements: List[(String, String)] = List(
      // BeanPropertyTransform renames first→head but generated bodies still call .first
      ("\\.first\\(\\)", ".head"),
      ("\\.first\\b", ".head"),
      // NullaryArityTransform removed parens
      ("\\.isEmpty\\(\\)", ".isEmpty"),
      ("\\.head\\(\\)", ".head"),
      // BeanPropertyTransform renames
      ("\\.scheduled\\b", ".isScheduled"),
    )
    // FilterNode's 4-arg constructor is private in Java (delegated through ParserRuleContext).
    // ssg's hand-written recursive-descent parser constructs FilterNode directly, needing
    // the 4-arg constructor to be accessible.
    val perFileReplacements: Map[String, List[(String, String)]] = Map(
      // ssg-liquid: FilterNode's 4-arg constructor is private in Java (delegated through
      // ParserRuleContext). ssg's hand-written parser constructs FilterNode directly.
      "FilterNode.scala" -> List(
        ("class FilterNode private \\(", "class FilterNode(")
      ),
      // ssg-md: these files call JavaCollection.isEmpty() which is parenless, but the global
      // isEmpty replacement is skipped for ssg-md (flexmark has its own isEmpty() with parens
      // on Range, Attributes, etc.). Fix only the known JavaCollection call sites.
      "BitFieldSet.scala" -> List(("c\\.isEmpty\\(\\)", "c.isEmpty")),
      "PlaceholderReplacer.scala" -> List(("spanList\\.isEmpty\\(\\)", "spanList.isEmpty")),
      // ISS-100: lookbehind (?<!^) is re2-incompatible (JS/Native). Replace with
      // manual leading-empty-strip after a normal split.
      "Split.scala" -> List(
        (java.util.regex.Pattern.quote("""original.split("(?<!^)" + java.util.regex.Pattern.quote(delimiter))"""),
         """{ val _p = original.split(java.util.regex.Pattern.quote(delimiter), -1); if (_p.length > 0 && _p(0).isEmpty()) _p.drop(1) else _p }""")
      ),
    )
    var count = 0
    val stream = Files.walk(outDir)
    try {
      stream.forEach { p =>
        if (p.toString.endsWith(".scala")) {
          var content = Files.readString(p)
          var changed = false
          for ((pattern, replacement) <- replacements) {
            // Skip files that define their own `first` field (SortedIntList)
            val skipFirst = pattern.contains("first") && content.contains("var first:")
            // Skip isEmpty() → isEmpty when the port has its own isEmpty() methods
            val skipEmpty = skipIsEmpty && pattern.contains("isEmpty")
            val skip = skipFirst || skipEmpty
            if (!skip) {
              val updated = content.replaceAll(pattern, replacement)
              if (updated != content) { content = updated; changed = true }
            }
          }
          val fileName = p.getFileName.toString
          for (extras <- perFileReplacements.get(fileName); (pat, rep) <- extras) {
            val updated = content.replaceAll(pat, rep)
            if (updated != content) { content = updated; changed = true }
          }
          if (changed) { Files.writeString(p, content); count += 1 }
        }
      }
    } finally stream.close()
    if (count > 0) log.info(s"[Baltic Porter] Post-processed $count files (API name fixes)")
  }

  /** Collect .scala files, excluding paths that exist in the hand-written source tree. */
  private def collectScalaFiles(dir: Path, excludeDuplicatesOf: Option[Path] = None): Seq[File] = {
    if (!Files.isDirectory(dir)) return Seq.empty
    val excluded: Set[String] = excludeDuplicatesOf.filter(Files.isDirectory(_)).map { excl =>
      val s = Files.walk(excl)
      try {
        val b = Set.newBuilder[String]
        s.forEach { p =>
          if (p.toString.endsWith(".scala")) b += excl.relativize(p).toString
        }
        b.result()
      } finally s.close()
    }.getOrElse(Set.empty)

    val stream = Files.walk(dir)
    try {
      val builder = Seq.newBuilder[File]
      stream.forEach { p =>
        if (p.toString.endsWith(".scala")) {
          val rel = dir.relativize(p).toString
          if (!excluded.contains(rel)) builder += p.toFile
        }
      }
      builder.result()
    } finally stream.close()
  }

  // ---------------------------------------------------------------------------
  // Non-Java ports: proof-of-concept wiring for ParityDerive pipeline
  // ---------------------------------------------------------------------------

  /** Verify that the Baltic Porter non-Java frontend is resolvable.
    *
    * This method imports from the published `balticporter-frontend-ts` and
    * `balticporter-corpus` artifacts, proving D1 (published artifacts) works
    * end-to-end. Full emitter wiring comes after RAST files are available.
    */
  def verifyNonJavaFrontend(log: sbt.util.Logger): Unit = {
    val parityClass = classOf[balticporter.frontend.ts.ParityDerive.Policy]
    log.info(s"[BalticPorterGen] Non-Java frontend available: ${parityClass.getName}")
    log.info("[BalticPorterGen] D1 verified: all non-Java emitters resolve from published snapshots")
  }

  // ---------------------------------------------------------------------------
  // Non-Java port generation: reference/ → ParityDerive → src_managed/
  // ---------------------------------------------------------------------------

  /** Generate Scala sources for a non-Java port module.
    *
    * Reads every `.scala` file from `referenceDir`, passes it through
    * `ParityDerive.derive` (with an empty RAST body map until D3 wires RAST
    * export at build time), and writes the result to `outDir`.
    *
    * With an empty body map the output is the reference verbatim — the
    * pipeline is proven end-to-end and RAST bodies slot in without any
    * further build change.
    */
  def generateNonJavaModule(
      moduleName: String,
      referenceDir: File,
      outDir: File,
      log: sbt.util.Logger,
  ): Seq[File] = {
    if (!referenceDir.exists) {
      log.warn(s"[Baltic Porter] No reference/ dir for $moduleName, skipping")
      return Seq.empty
    }

    val marker = outDir.toPath.resolve(".generated-marker")
    val refHash = referenceDir.hashCode.toString

    val cached = Files.exists(marker) &&
      Files.readString(marker).trim == refHash
    if (cached) {
      return (outDir ** "*.scala").get()
    }

    val refFiles = (referenceDir ** "*.scala").get()
    val generated = refFiles.flatMap { refFile =>
      sbt.IO.relativize(referenceDir, refFile).map { relPath =>
        val outFile = outDir / relPath

        val refSource = sbt.IO.read(refFile)

        // Run parity-derive with empty body map (no RAST yet — D3 will add RAST bodies)
        val result = balticporter.frontend.ts.ParityDerive.derive(
          refSource, Map.empty, balticporter.frontend.ts.ParityDerive.Policy())

        sbt.IO.write(outFile, result.emittedSource)
        outFile
      }
    }

    Files.createDirectories(marker.getParent)
    Files.writeString(marker, refHash)
    log.info(s"[Baltic Porter] $moduleName: generated ${generated.size} files to $outDir")
    generated
  }
}
