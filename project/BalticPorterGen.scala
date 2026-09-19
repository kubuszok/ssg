import sbt.*
import sbt.Keys.*

import java.nio.file.{ Files, Path }

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

    val portRoot = bp.resolve("ported/ssg-liquid")
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

      System.setProperty("balticporter.root", bp.toAbsolutePath.normalize.toString)
      try {
        balticporter.corpus.liqp.LiqpClasspath.ensure(bp)
        val config = balticporter.runner.PortConfig.load(confPath)
        config.execute()
        log.info(s"[Baltic Porter] Generated ssg-liquid sources to $outPath")
        postProcess(outPath, log)
      } catch {
        case e: Exception =>
          log.warn(s"[Baltic Porter] ssg-liquid generation failed (files may have been written): ${e.getMessage}")
      }

      Files.createDirectories(marker.getParent)
      Files.writeString(marker, commit)
    } else {
      log.info(s"[Baltic Porter] Using cached ssg-liquid sources ($commit)")
    }

    val ssgLiquidSrc = ssgRoot.resolve("ssg-liquid/src/main")
    collectScalaFiles(outPath, excludeDuplicatesOf = Some(ssgLiquidSrc))
  }

  // ---------------------------------------------------------------------------
  // Markdown: ssg-md (flexmark core + the eleven util libraries) and ssg-md-ext
  // (the extensions, a dependent of it). Both run from the PUBLISHED artifacts
  // alone — the port configurations and the files their policies inject are
  // unpacked from the balticporter-corpus jar. No engine checkout is involved.
  // ---------------------------------------------------------------------------

  /** Parent of the two markdown port roots, under ssg's own `target/`. The leaf names are the configurations' own (`@ports/ssg-md`, `@ports/ssg-md-ext`); only the parent is ours to choose. */
  private def mdPortsRoot(ssgRoot: Path): Path = ssgRoot.resolve("target/balticporter")

  def mdOutDir(ssgRoot:    Path): Path = mdPortsRoot(ssgRoot).resolve("ssg-md/src_managed/main/scala")
  def mdExtOutDir(ssgRoot: Path): Path = mdPortsRoot(ssgRoot).resolve("ssg-md-ext/src_managed/main/scala")

  /** Generate ssg-md Scala sources from flexmark-java originals. */
  def generateFlexmark(buildBase: File, log: sbt.util.Logger): Seq[File] =
    BalticPorterGen.synchronized {
      val ssgRoot = buildBase.toPath.toAbsolutePath.normalize
      markdown(ssgRoot, log)
      collectScalaFiles(mdOutDir(ssgRoot))
    }

  /** Generate ssg-md-ext Scala sources from flexmark's extension modules. */
  def generateFlexmarkExt(buildBase: File, log: sbt.util.Logger): Seq[File] =
    BalticPorterGen.synchronized {
      val ssgRoot = buildBase.toPath.toAbsolutePath.normalize
      markdown(ssgRoot, log)
      collectScalaFiles(mdExtOutDir(ssgRoot))
    }

  /** The markdown port's classpath resources, for a `resourceGenerators` task: the second output beside the emitted Scala, and the one a build that collects only sources drops (without it
    * `Html5Entities` fails its class initialiser).
    */
  def markdownResources(buildBase: File, log: sbt.util.Logger): Seq[File] =
    BalticPorterGen.synchronized {
      val ssgRoot = buildBase.toPath.toAbsolutePath.normalize
      markdown(ssgRoot, log)
      // The BASE port's tree only. The extension port ships the admonition assets, but
      // `ssg-md/src/main/resources` already carries byte-identical copies of every one of them —
      // the JS row embeds THAT directory (`MultiArchResourcesPlugin`, which reads
      // `Compile / resourceDirectory` and sees no managed tree), and putting both on the classpath
      // makes packageBin fail with `duplicate entry: admonition.css`.
      balticporter.sbtgen.SbtGen.resourceFiles(mdPortsRoot(ssgRoot).resolve("ssg-md"), "main").map(_.toFile)
    }

  /** Run both markdown ports, once, unless the marker already records this fingerprint.
    *
    * sbt evaluates the JVM/JS/Native rows' managedSources in parallel; the rows share one output tree and the two ports hand state to each other through system properties, so the rows serialise on
    * this object and the later ones read the marker the first one wrote.
    */
  private def markdown(ssgRoot: Path, log: sbt.util.Logger): Unit = {
    val flexmarkSrc = ssgRoot.resolve("original-src/flexmark-java")
    val portsRoot   = mdPortsRoot(ssgRoot)
    val marker      = portsRoot.resolve(".generated-marker")

    // Cache key: everything the generated tree depends on (see `fingerprint`), readable without the
    // submodule's files — so a checkout that RECEIVED the generated tree (a CI job restoring the
    // `generatePort` job's output) reuses it and needs neither the submodule nor a generation run.
    // Force a regeneration with -Dbalticporter.forceRegen=true, or delete the marker.
    val forceRegen = sys.props.getOrElse("balticporter.forceRegen", "false").toBoolean
    val expected   = fingerprint(ssgRoot)
    val cached     = !forceRegen && Files.exists(marker) &&
      Files.isDirectory(mdOutDir(ssgRoot)) &&
      Files.isDirectory(mdExtOutDir(ssgRoot)) &&
      Files.readString(marker).trim == expected

    if (cached) {
      log.info(s"[Baltic Porter] Using cached markdown sources ($expected)")
      return
    }

    if (!Files.isDirectory(flexmarkSrc))
      sys.error(
        "[Baltic Porter] The generated ssg-md sources are missing or stale (" + marker + " does not read `" + expected + "`) and the flexmark-java submodule is not " +
          "initialised. Run `git submodule update --init --depth=1 original-src/flexmark-java`, or place a generated tree with a matching marker under " + portsRoot + "."
      )

    // The port configurations, and the files their policies inject by path, ship inside the
    // published corpus jar and are unpacked under target/; -Dbalticporter.root=<engine checkout>
    // reads them from a checkout instead (engine development only).
    val bp = sys.props.get("balticporter.root") match {
      case Some(root) => Path.of(root).toAbsolutePath.normalize
      case None       => balticporter.corpus.BundledCorpus.root(ssgRoot.resolve("target/balticporter-engine"))
    }
    val confDir = bp.resolve("balticporter/corpus/ports/ssg-md")
    require(
      Files.isDirectory(confDir),
      s"markdown port configurations not found at $confDir — check the balticporter-corpus pin in project/plugins.sbt"
    )

    // The three roots the configurations declare: the consumer's checkout (upstream submodule and
    // the reference port), a scratch directory for the resolved frontend classpath, and where the
    // generated code goes. All three under ssg's own tree.
    val work = ssgRoot.resolve("target/balticporter-work")
    Files.createDirectories(work)
    balticporter.corpus.flexmark.FlexmarkClasspath.ensureIn(work)
    val roots = Map("consumer" -> ssgRoot, "work" -> work, "ports" -> portsRoot)

    // Each port reports under a shared root: PortMap.reportRoot is the PARENT of a run's report
    // directory, so the extension (a dependent) discovers the base's published port map there.
    // CheckReport's artifact layer is off unless `reportDir` is set explicitly — under sbt no
    // identity can be derived from the main class.
    val reports = portsRoot.resolve("port-report")

    log.info(s"[Baltic Porter] Generating ssg-md + ssg-md-ext from flexmark-java ($expected)")
    try {
      System.setProperty("balticporter.reportDir", reports.resolve("ssg-md").toString)
      balticporter.runner.PortConfig.load(confDir.resolve("main.conf"), roots = roots).execute()
      log.info(s"[Baltic Porter] Generated ssg-md sources to ${mdOutDir(ssgRoot)}")

      System.setProperty("balticporter.reportDir", reports.resolve("ssg-md-ext").toString)
      System.setProperty("balticporter.baseReports", reports.toString)
      balticporter.runner.PortConfig.load(confDir.resolve("ext.conf"), roots = roots).execute()
      log.info(s"[Baltic Porter] Generated ssg-md-ext sources to ${mdExtOutDir(ssgRoot)}")

      temporaryMarkdownPatches(ssgRoot, log)
    } finally {
      System.clearProperty("balticporter.baseReports")
      System.clearProperty("balticporter.reportDir")
    }

    Files.createDirectories(marker.getParent)
    Files.writeString(marker, expected)
  }

  // TEMPORARY — DELETE WHEN THE ENGINE EMITS THESE TWO SITES CORRECTLY.
  //
  // Editing generated text with a regular expression is not a port policy: it is invisible to every
  // check the run makes and it cannot say why. These are the only two sites the markdown port does
  // not compile without, and both are one shape — a java `Collection.isEmpty()` call emitted with
  // its empty argument list onto a parenless Scala member:
  //
  //   ssg/md/util/misc/BitFieldSet.scala:1000        if (c.isEmpty())
  //   ssg/md/util/sequence/PlaceholderReplacer.scala:18  if (spanList.isEmpty())
  //
  // The fix belongs in the engine's nullary-arity policy for external collection receivers; it is
  // being moved there separately. Until then the module cannot compile at all, so the two sites are
  // repaired here, named one by one so a third one fails loudly instead of being absorbed.
  private def temporaryMarkdownPatches(ssgRoot: Path, log: sbt.util.Logger): Unit = {
    val sites = List(
      mdOutDir(ssgRoot).resolve("ssg/md/util/misc/BitFieldSet.scala") -> ("c.isEmpty()", "c.isEmpty"),
      mdOutDir(ssgRoot).resolve("ssg/md/util/sequence/PlaceholderReplacer.scala") -> ("spanList.isEmpty()", "spanList.isEmpty")
    )
    for ((file, (from, to)) <- sites if Files.isRegularFile(file)) {
      val before = Files.readString(file)
      val after  = before.replace(from, to)
      if (after != before) {
        Files.writeString(file, after)
        log.warn(s"[Baltic Porter] TEMPORARY patch applied to ${ssgRoot.relativize(file)}: `$from` -> `$to`")
      }
    }
  }

  /** What the generated markdown tree depends on, as one line, readable on a shallow checkout WITHOUT the submodule's files: the engine artifact pinned in `project/plugins.sbt`, the flexmark commit
    * (the submodule's HEAD when it is initialised, else the commit this checkout records for it), this generator (line endings normalised, so every OS agrees) and the JDK feature version.
    */
  def fingerprint(ssgRoot: Path): String = {
    def git(dir: Path, args: String*): Option[String] = {
      val pb = new ProcessBuilder(("git" +: args)*)
      pb.directory(dir.toFile)
      pb.redirectErrorStream(true)
      val p   = pb.start()
      val out = new String(p.getInputStream.readAllBytes()).trim
      if (p.waitFor() == 0 && out.nonEmpty) Some(out) else None
    }
    val pin = """balticporter-corpus" % "([^"]+)"""".r
      .findFirstMatchIn(Files.readString(ssgRoot.resolve("project/plugins.sbt")))
      .map(_.group(1))
      .getOrElse(sys.error("[Baltic Porter] project/plugins.sbt pins no balticporter-corpus version"))
    val submodule = ssgRoot.resolve("original-src/flexmark-java")
    val flexmark  = (if (Files.exists(submodule.resolve(".git"))) git(submodule, "rev-parse", "HEAD") else None)
      .orElse(git(ssgRoot, "ls-tree", "HEAD", "original-src/flexmark-java").flatMap(_.split("\\s+").lift(2)))
      .getOrElse(
        sys.error(
          "[Baltic Porter] cannot read the flexmark-java commit this checkout records (git ls-tree HEAD original-src/flexmark-java)"
        )
      )
    val source    = Files.readString(ssgRoot.resolve("project/BalticPorterGen.scala")).replace("\r", "")
    val generator = java.security.MessageDigest.getInstance("SHA-256").digest(source.getBytes("UTF-8")).take(8).map(b => f"$b%02x").mkString
    // the JDK the generator runs on decides what a member overrides
    s"engine=$pin flexmark=$flexmark generator=$generator jdk=${java.lang.Runtime.version().feature()}"
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
