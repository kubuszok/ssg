import sbt.*
import sbt.Keys.*

import java.nio.file.{ Files, Path }
import scala.jdk.CollectionConverters.*

// sbt sourceGenerator that uses Baltic Porter to mechanically port Java and
// non-Java sources into Scala 3. Java ports run the full engine; non-Java ports
// interleave RAST bodies into reference files via ParityDerive.
object BalticPorterGen {

  private def bpRoot(ssgRoot: Path): Path =
    Path.of(sys.props.getOrElse("balticporter.root", ssgRoot.resolve("../balticporter").toString)).toAbsolutePath.normalize

  private def hasBpSibling(ssgRoot: Path): Boolean =
    Files.isDirectory(bpRoot(ssgRoot).resolve("balticporter/corpus"))

  /** Generate ssg-liquid Scala sources from liqp Java originals. */
  def generateLiquid(buildBase: File, outDir: File, log: sbt.util.Logger): Seq[File] = {
    val ssgRoot = buildBase.toPath.toAbsolutePath.normalize
    val liqpSrc = ssgRoot.resolve("original-src/liqp/src/main/java")
    if (!hasBpSibling(ssgRoot) || !Files.isDirectory(liqpSrc)) {
      log.warn("[Baltic Porter] No balticporter sibling or liqp submodule — skipping ssg-liquid generation")
      return collectScalaFiles(outDir.toPath)
    }
    val bp = bpRoot(ssgRoot)

    val portRoot = outDir.toPath
    val outPath  = portRoot.resolve("src_managed/main/scala")
    val marker   = ssgRoot.resolve("target/balticporter-ssg-liquid/.generated-marker")

    val forceRegen = sys.props.getOrElse("balticporter.forceRegen", "false").toBoolean
    val commit     = balticporter.runner.VendoredCommit.of(ssgRoot.resolve("original-src/liqp"))
    val cached     = !forceRegen && Files.exists(marker) &&
      Files.exists(outPath) &&
      Files.readString(marker).trim == commit

    if (!cached) {
      log.info(s"[Baltic Porter] Generating ssg-liquid sources from liqp ($commit)")

      val confPath = bp.resolve("balticporter/corpus/ports/liqp/main.conf")
      require(Files.exists(confPath), s"liqp port config not found at $confPath — publish balticporter-corpus first")

      balticporter.corpus.liqp.LiqpClasspath.ensure(bp)

      System.setProperty("balticporter.root", bp.toAbsolutePath.normalize.toString)
      try {
        val config = balticporter.runner.PortConfig.load(confPath,
                                                         Seq(
                                                           s"--portRoot=$portRoot"
                                                         )
        )
        config.execute()
        log.info(s"[Baltic Porter] Generated ssg-liquid sources to $outPath")
      } catch {
        case e: Exception =>
          log.warn(s"[Baltic Porter] Port completed with findings (files may have been written): ${e.getMessage}")
      }

      postProcess(outPath, log)

      Files.createDirectories(marker.getParent)
      Files.writeString(marker, commit)
    } else {
      log.info(s"[Baltic Porter] Using cached ssg-liquid sources ($commit)")
    }

    val ssgLiquidSrc = ssgRoot.resolve("ssg-liquid/src/main")
    collectScalaFiles(outPath, excludeDuplicatesOf = Some(ssgLiquidSrc))
  }

  /** Generate ssg-md Scala sources from flexmark-java originals. */
  def generateFlexmark(buildBase: File, outDir: File, log: sbt.util.Logger): Seq[File] = {
    val ssgRoot     = buildBase.toPath.toAbsolutePath.normalize
    val flexmarkSrc = ssgRoot.resolve("original-src/flexmark-java")
    if (!hasBpSibling(ssgRoot) || !Files.isDirectory(flexmarkSrc)) {
      log.warn("[Baltic Porter] No balticporter sibling or flexmark submodule — skipping ssg-md generation")
      return collectScalaFiles(outDir.toPath)
    }
    val bp = bpRoot(ssgRoot)

    val portRoot = outDir.toPath
    val outPath  = portRoot.resolve("src_managed/main/scala")
    val marker   = ssgRoot.resolve("target/balticporter-ssg-md/.generated-marker")

    val forceRegen = sys.props.getOrElse("balticporter.forceRegen", "false").toBoolean
    val commit     = balticporter.runner.VendoredCommit.of(flexmarkSrc)
    val cached     = !forceRegen && Files.exists(marker) &&
      Files.exists(outPath) &&
      Files.readString(marker).trim == commit

    if (!cached) {
      log.info(s"[Baltic Porter] Generating ssg-md sources from flexmark-java ($commit)")

      val confPath = bp.resolve("balticporter/corpus/ports/ssg-md/main.conf")
      require(Files.exists(confPath), s"flexmark port config not found at $confPath — publish balticporter-corpus first")

      balticporter.corpus.flexmark.FlexmarkClasspath.ensure(bp)

      System.setProperty("balticporter.root", bp.toAbsolutePath.normalize.toString)
      try {
        val config = balticporter.runner.PortConfig.load(confPath,
                                                         Seq(
                                                           s"--portRoot=$portRoot"
                                                         )
        )
        config.execute()
        log.info(s"[Baltic Porter] Generated ssg-md sources to $outPath")
      } catch {
        case e: Exception =>
          log.warn(s"[Baltic Porter] Port completed with findings (files may have been written): ${e.getMessage}")
      }

      postProcess(outPath, log, skipIsEmpty = true)

      Files.createDirectories(marker.getParent)
      Files.writeString(marker, commit)
    } else {
      log.info(s"[Baltic Porter] Using cached ssg-md sources ($commit)")
    }

    val ssgMdSrc = ssgRoot.resolve("ssg-md/src/main")
    val result = collectScalaFiles(outPath, excludeDuplicatesOf = Some(ssgMdSrc))
    log.info(s"[Baltic Porter] ssg-md: collected ${result.size} files from $outPath (exists=${Files.isDirectory(outPath)})")
    if (result.isEmpty && Files.isDirectory(portRoot)) {
      val all = Files.walk(portRoot).iterator().asScala.filter(_.toString.endsWith(".scala")).toList
      log.warn(s"[Baltic Porter] ssg-md: 0 files collected but ${all.size} scala files exist under portRoot=$portRoot")
      if (all.nonEmpty) log.warn(s"[Baltic Porter] ssg-md: first 5: ${all.take(5).mkString(", ")}")
    }
    result
  }

  /** Generate ssg-md-ext Scala sources from flexmark extension modules. */
  def generateFlexmarkExt(buildBase: File, outDir: File, log: sbt.util.Logger): Seq[File] = {
    val ssgRoot     = buildBase.toPath.toAbsolutePath.normalize
    val flexmarkSrc = ssgRoot.resolve("original-src/flexmark-java")
    if (!hasBpSibling(ssgRoot) || !Files.isDirectory(flexmarkSrc)) {
      log.warn("[Baltic Porter] No balticporter sibling or flexmark submodule — skipping ssg-md-ext generation")
      return collectScalaFiles(outDir.toPath)
    }
    val bp = bpRoot(ssgRoot)

    val portRoot = outDir.toPath
    val outPath  = portRoot.resolve("src_managed/main/scala")
    val marker   = ssgRoot.resolve("target/balticporter-ssg-md-ext/.generated-marker")

    val forceRegen = sys.props.getOrElse("balticporter.forceRegen", "false").toBoolean
    val commit     = balticporter.runner.VendoredCommit.of(flexmarkSrc)
    val cached     = !forceRegen && Files.exists(marker) &&
      Files.exists(outPath) &&
      Files.readString(marker).trim == commit

    if (!cached) {
      log.info(s"[Baltic Porter] Generating ssg-md-ext sources from flexmark extensions ($commit)")

      val confPath = bp.resolve("balticporter/corpus/ports/ssg-md/ext.conf")
      require(Files.exists(confPath), s"flexmark-ext port config not found at $confPath — publish balticporter-corpus first")

      balticporter.corpus.flexmark.FlexmarkClasspath.ensure(bp)

      System.setProperty("balticporter.root", bp.toAbsolutePath.normalize.toString)
      try {
        val config = balticporter.runner.PortConfig.load(confPath,
                                                         Seq(
                                                           s"--portRoot=$portRoot"
                                                         )
        )
        config.execute()
        log.info(s"[Baltic Porter] Generated ssg-md-ext sources to $outPath")
      } catch {
        case e: Exception =>
          log.warn(s"[Baltic Porter] Port completed with findings (files may have been written): ${e.getMessage}")
      }

      postProcess(outPath, log, skipIsEmpty = true)

      Files.createDirectories(marker.getParent)
      Files.writeString(marker, commit)
    } else {
      log.info(s"[Baltic Porter] Using cached ssg-md-ext sources ($commit)")
    }

    val ssgMdSrc = ssgRoot.resolve("ssg-md/src/main")
    collectScalaFiles(outPath, excludeDuplicatesOf = Some(ssgMdSrc))
  }

  /** Fix API name mismatches in generated code (same patterns as sge). */
  private def postProcess(outDir: Path, log: sbt.util.Logger, skipIsEmpty: Boolean = false): Unit = {
    if (!Files.isDirectory(outDir)) return
    val replacements: List[(String, String)] = List(
      ("\\.first\\(\\)", ".head"),
      ("\\.first\\b", ".head"),
      ("\\.isEmpty\\(\\)", ".isEmpty"),
      ("\\.head\\(\\)", ".head"),
      ("\\.scheduled\\b", ".isScheduled")
    )
    val perFileReplacements: Map[String, List[(String, String)]] = Map(
      "FilterNode.scala" -> List(
        ("class FilterNode private \\(", "class FilterNode(")
      ),
      "BitFieldSet.scala" -> List(("c\\.isEmpty\\(\\)", "c.isEmpty")),
      "PlaceholderReplacer.scala" -> List(("spanList\\.isEmpty\\(\\)", "spanList.isEmpty")),
      "Split.scala" -> List(
        (java.util.regex.Pattern.quote("""original.split("(?<!^)" + java.util.regex.Pattern.quote(delimiter))"""),
         """{ val _p = original.split(java.util.regex.Pattern.quote(delimiter), -1); if (_p.length > 0 && _p(0).isEmpty()) _p.drop(1) else _p }"""
        )
      )
    )
    var count  = 0
    val stream = Files.walk(outDir)
    try
      stream.forEach { p =>
        if (p.toString.endsWith(".scala")) {
          var content = Files.readString(p)
          var changed = false
          for ((pattern, replacement) <- replacements) {
            val skipFirst = pattern.contains("first") && content.contains("var first:")
            val skipEmpty = skipIsEmpty && pattern.contains("isEmpty")
            val skip      = skipFirst || skipEmpty
            if (!skip) {
              val updated = content.replaceAll(pattern, replacement)
              if (updated != content) { content = updated; changed = true }
            }
          }
          val fileName = p.getFileName.toString
          for {
            extras <- perFileReplacements.get(fileName)
            (pat, rep) <- extras
          } {
            val updated = content.replaceAll(pat, rep)
            if (updated != content) { content = updated; changed = true }
          }
          if (changed) { Files.writeString(p, content); count += 1 }
        }
      }
    finally stream.close()
    if (count > 0) log.info(s"[Baltic Porter] Post-processed $count files (API name fixes)")
  }

  /** Collect .scala files, excluding paths that exist in the hand-written source tree. */
  private def collectScalaFiles(dir: Path, excludeDuplicatesOf: Option[Path] = None): Seq[File] = {
    if (!Files.isDirectory(dir)) return Seq.empty
    val excluded: Set[String] = excludeDuplicatesOf
      .filter(Files.isDirectory(_))
      .map { excl =>
        val s = Files.walk(excl)
        try {
          val b = Set.newBuilder[String]
          s.forEach { p =>
            if (p.toString.endsWith(".scala")) b += excl.relativize(p).toString
          }
          b.result()
        } finally s.close()
      }
      .getOrElse(Set.empty)

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
  // Non-Java port generation: reference/ → ParityDerive → src_managed/
  // ---------------------------------------------------------------------------

  def generateNonJavaModule(
    moduleName:     String,
    referenceDir:   File,
    outDir:         File,
    log:            sbt.util.Logger,
    rastDir:        Option[File] = None,
    policy:         balticporter.frontend.ts.ParityDerive.Policy = balticporter.frontend.ts.ParityDerive.Policy(),
    bodyMapBuilder: Option[(File, File) => Map[String, (String, Int)]] = None
  ): Seq[File] = {
    if (!referenceDir.exists) {
      log.warn(s"[Baltic Porter] No reference/ dir for $moduleName, skipping")
      return Seq.empty
    }

    val marker  = outDir.toPath.resolve(".generated-marker")
    val refHash = referenceDir.hashCode.toString +
      rastDir.map(_.hashCode.toString).getOrElse("")

    val cached = Files.exists(marker) &&
      Files.readString(marker).trim == refHash
    if (cached) {
      return (outDir ** "*.scala").get()
    }

    val globalBodies: Map[String, (String, Int)] =
      (for {
        rd <- rastDir if rd.exists
        builder <- bodyMapBuilder
      } yield
        try {
          val bodies = builder(referenceDir, rd)
          log.info(s"[Baltic Porter] $moduleName: loaded ${bodies.size} RAST bodies from ${rd.getName}")
          bodies
        } catch {
          case e: Exception =>
            log.warn(s"[Baltic Porter] $moduleName: RAST body map failed: ${e.getMessage}, using empty")
            Map.empty[String, (String, Int)]
        }).getOrElse(Map.empty)

    val refFiles = (referenceDir ** "*.scala").get()
    var rastUsed = 0
    var refUsed  = 0

    val generated = refFiles.flatMap { refFile =>
      sbt.IO.relativize(referenceDir, refFile).map { relPath =>
        val outFile = outDir / relPath

        val refSource = sbt.IO.read(refFile)

        val result = balticporter.frontend.ts.ParityDerive.derive(refSource, globalBodies, policy)

        rastUsed += result.rastCount
        refUsed += result.referenceCount

        sbt.IO.write(outFile, result.emittedSource)
        outFile
      }
    }

    Files.createDirectories(marker.getParent)
    Files.writeString(marker, refHash)

    val total = rastUsed + refUsed
    val pct   = if (total > 0) f"${rastUsed * 100.0 / total}%.1f" else "0.0"
    log.info(
      s"[Baltic Porter] $moduleName: generated ${generated.size} files, " +
        s"$rastUsed/$total ($pct%) RAST-derived bodies"
    )
    generated
  }
}
