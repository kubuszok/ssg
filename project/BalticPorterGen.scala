import sbt.*
import sbt.Keys.*

import java.nio.file.{ Files, Path }

// sbt sourceGenerator that uses Baltic Porter to mechanically port Java and
// non-Java sources into Scala 3. Java ports run the full engine; non-Java ports
// interleave RAST bodies into reference files via ParityDerive.
object BalticPorterGen {

  // ---------------------------------------------------------------------------
  // The Java ports: ssg-liquid (liqp), ssg-md (flexmark core + the eleven util
  // libraries) and ssg-md-ext (the extensions, a dependent of ssg-md). The published
  // engine is generic; every port is described by ssg's own files (`<module>/port/`).
  // All run in ONE pass under ONE marker, so CI carries one generated tree.
  // ---------------------------------------------------------------------------

  /** The liquid port's root: the shared sources, and one directory per platform row for the few answers that differ by platform (reading an object's fields by reflection exists on the JVM only). */
  def liquidPortRoot(ssgRoot: Path): Path = mdPortsRoot(ssgRoot).resolve("ssg-liquid")

  def liquidOutDir(ssgRoot: Path): Path = liquidPortRoot(ssgRoot).resolve("src_managed/main/scala")

  /** `row` is `jvm`, `js` or `native`. */
  def liquidRowDir(ssgRoot: Path, row: String): Path = liquidPortRoot(ssgRoot).resolve(s"src_managed/$row/scala")

  /** Generate ssg-liquid Scala sources from liqp: the shared tree plus this platform row's. */
  def generateLiquid(buildBase: File, row: String, log: sbt.util.Logger): Seq[File] =
    BalticPorterGen.synchronized {
      val ssgRoot = buildBase.toPath.toAbsolutePath.normalize
      markdown(ssgRoot, log)
      collectScalaFiles(liquidOutDir(ssgRoot)) ++ collectScalaFiles(liquidRowDir(ssgRoot, row))
    }

  def liquidResources(buildBase: File, log: sbt.util.Logger): Seq[File] =
    BalticPorterGen.synchronized {
      val ssgRoot = buildBase.toPath.toAbsolutePath.normalize
      markdown(ssgRoot, log)
      balticporter.sbtgen.SbtGen.resourceFiles(liquidPortRoot(ssgRoot), "main").map(_.toFile)
    }

  /** Parent of the two markdown port roots, under ssg's own `target/`. The leaf names are the configurations' own (`@ports/ssg-md`, `@ports/ssg-md-ext`); only the parent is ours to choose. */
  private def mdPortsRoot(ssgRoot: Path): Path = ssgRoot.resolve("target/balticporter")

  def mdOutDir(ssgRoot:    Path): Path = mdPortsRoot(ssgRoot).resolve("ssg-md/src_managed/main/scala")
  def mdExtOutDir(ssgRoot: Path): Path = mdPortsRoot(ssgRoot).resolve("ssg-md-ext/src_managed/main/scala")

  def mdTestOutDir(ssgRoot:    Path): Path = mdPortsRoot(ssgRoot).resolve("ssg-md/src_managed/test/scala")
  def mdExtTestOutDir(ssgRoot: Path): Path = mdPortsRoot(ssgRoot).resolve("ssg-md-ext/src_managed/test/scala")

  /** flexmark's own suites (core + utilities, then the extensions), generated beside the main sources. */
  def generateFlexmarkTests(buildBase: File, log: sbt.util.Logger): Seq[File] =
    BalticPorterGen.synchronized {
      val ssgRoot = buildBase.toPath.toAbsolutePath.normalize
      markdown(ssgRoot, log)
      collectScalaFiles(mdTestOutDir(ssgRoot)) ++ collectScalaFiles(mdExtTestOutDir(ssgRoot))
    }

  /** The test suites' classpath resources (the spec files they read). */
  def markdownTestResources(buildBase: File, log: sbt.util.Logger): Seq[File] =
    BalticPorterGen.synchronized {
      val ssgRoot = buildBase.toPath.toAbsolutePath.normalize
      markdown(ssgRoot, log)
      List("ssg-md", "ssg-md-ext").flatMap(p => balticporter.sbtgen.SbtGen.resourceFiles(mdPortsRoot(ssgRoot).resolve(p), "test")).map(_.toFile)
    }

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
      Files.isDirectory(liquidOutDir(ssgRoot)) &&
      Files.isDirectory(mdOutDir(ssgRoot)) &&
      Files.isDirectory(mdExtOutDir(ssgRoot)) &&
      Files.isDirectory(mdTestOutDir(ssgRoot)) &&
      Files.isDirectory(mdExtTestOutDir(ssgRoot)) &&
      Files.readString(marker).trim == expected

    if (cached) {
      log.info(s"[Baltic Porter] Using cached markdown sources ($expected)")
      return
    }

    val liqpSrc = ssgRoot.resolve("original-src/liqp")
    if (!Files.isDirectory(flexmarkSrc.resolve("flexmark")) || !Files.isDirectory(liqpSrc.resolve("src/main/java")))
      sys.error(
        "[Baltic Porter] The generated sources are missing or stale (" + marker + " does not read `" + expected + "`) and the upstream submodules are not " +
          "initialised. Run `git submodule update --init --depth=1 original-src/flexmark-java original-src/liqp`, or place a generated tree with a matching marker under " + portsRoot + "."
      )

    // Every port is described by ssg's OWN files — `ssg-liquid/port/` and `ssg-md/port/` hold the
    // configurations and the hand-written sources their policies inject. The engine artifact pinned
    // in project/plugins.sbt knows nothing about liqp or flexmark, so changing how a library is
    // ported is an edit to those files and a regeneration; no engine release is involved.
    val liquidPort = ssgRoot.resolve("ssg-liquid/port")
    val mdPort     = ssgRoot.resolve("ssg-md/port")
    val work       = ssgRoot.resolve("target/balticporter-work")
    Files.createDirectories(work)

    // Each port reports under a shared root: PortMap.reportRoot is the PARENT of a run's report
    // directory, so the extension (a dependent) discovers the base's published port map there.
    // CheckReport's artifact layer is off unless `reportDir` is set explicitly — under sbt no
    // identity can be derived from the main class.
    val reports = portsRoot.resolve("port-report")

    log.info(s"[Baltic Porter] Generating ssg-liquid, ssg-md and ssg-md-ext ($expected)")
    try {
      // liquid first: a base port of its own. Its frontend also reads liqp's ANTLR-generated parser
      // (`./mvnw generate-sources` in the submodule produces it; a missing one is refused by path),
      // which `LiqpClasspath` (project/LiqpParserClasspath.scala) compiles under `work`.
      LiqpClasspath.ensureIn(work, liqpSrc, liquidPort)
      System.setProperty("balticporter.reportDir", reports.resolve("ssg-liquid").toString)
      balticporter.runner.PortConfig.load(liquidPort.resolve("main.conf")).execute()
      log.info(s"[Baltic Porter] Generated ssg-liquid sources to ${liquidOutDir(ssgRoot)}")

      System.setProperty("balticporter.reportDir", reports.resolve("ssg-md").toString)
      balticporter.runner.PortConfig.load(mdPort.resolve("main.conf")).execute()
      log.info(s"[Baltic Porter] Generated ssg-md sources to ${mdOutDir(ssgRoot)}")

      System.setProperty("balticporter.reportDir", reports.resolve("ssg-md-ext").toString)
      System.setProperty("balticporter.baseReports", reports.toString)
      balticporter.runner.PortConfig.load(mdPort.resolve("ext.conf")).execute()
      log.info(s"[Baltic Porter] Generated ssg-md-ext sources to ${mdExtOutDir(ssgRoot)}")

      // flexmark's OWN JUnit suites, ported as MUnit suites: the behavioural gate of the two ports
      // above. Each is a dependent of its main port and writes that port root's `test` source set.
      System.setProperty("balticporter.reportDir", reports.resolve("ssg-md-test").toString)
      balticporter.runner.PortConfig.load(mdPort.resolve("test.conf")).execute()
      System.setProperty("balticporter.reportDir", reports.resolve("ssg-md-ext-test").toString)
      balticporter.runner.PortConfig.load(mdPort.resolve("ext-test.conf")).execute()
      log.info(s"[Baltic Porter] Generated flexmark's test suites to ${mdTestOutDir(ssgRoot)} and ${mdExtTestOutDir(ssgRoot)}")
    } finally {
      System.clearProperty("balticporter.baseReports")
      System.clearProperty("balticporter.reportDir")
    }

    Files.createDirectories(marker.getParent)
    Files.writeString(marker, expected)
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
    val pin = """balticporter-engine" % "([^"]+)"""".r
      .findFirstMatchIn(Files.readString(ssgRoot.resolve("project/plugins.sbt")))
      .map(_.group(1))
      .getOrElse(sys.error("[Baltic Porter] project/plugins.sbt pins no balticporter-engine version"))
    def upstreamCommit(path: String): String = {
      val submodule = ssgRoot.resolve(path)
      (if (Files.exists(submodule.resolve(".git"))) git(submodule, "rev-parse", "HEAD") else None)
        .orElse(git(ssgRoot, "ls-tree", "HEAD", path).flatMap(_.split("\\s+").lift(2)))
        .getOrElse(sys.error(s"[Baltic Porter] cannot read the commit this checkout records for $path (git ls-tree HEAD $path)"))
    }
    val flexmark  = upstreamCommit("original-src/flexmark-java")
    val liqp      = upstreamCommit("original-src/liqp")
    val source    = Files.readString(ssgRoot.resolve("project/BalticPorterGen.scala")).replace("\r", "")
    val generator = java.security.MessageDigest.getInstance("SHA-256").digest(source.getBytes("UTF-8")).take(8).map(b => f"$b%02x").mkString
    // the JDK the generator runs on decides what a member overrides
    // the ports' own files: their configurations and the hand-written sources they inject
    val ports = {
      val md = java.security.MessageDigest.getInstance("SHA-256")
      for (dir <- List("ssg-liquid/port", "ssg-md/port", "project/LiqpParserClasspath.scala").map(ssgRoot.resolve(_))) {
        // Sorted by the normalised relative path, never by `Path`: Windows orders paths without
        // regard to case, so the Linux-generated tree's marker would never match there.
        val s     = Files.walk(dir)
        val files =
          try scala.jdk.CollectionConverters.IteratorHasAsScala(s.filter(Files.isRegularFile(_)).iterator()).asScala.toList
          finally s.close()
        files.map(p => ssgRoot.relativize(p).toString.replace('\\', '/') -> p).sortBy(_._1).foreach { case (rel, p) =>
          md.update(rel.getBytes("UTF-8"))
          md.update(Files.readString(p).replace("\r", "").getBytes("UTF-8"))
        }
      }
      md.digest().take(8).map(b => f"$b%02x").mkString
    }
    s"engine=$pin flexmark=$flexmark liqp=$liqp ports=$ports generator=$generator jdk=${java.lang.Runtime.version().feature()}"
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
  // KaTeX: export RAST from original-src/katex, then derive via NonJavaBodies.
  // Both the export and the derived tree live under target/balticporter/ so the
  // generated-port cache stores and restores them together. A single marker
  // records the fingerprint; when it matches, no submodule is needed.
  // ---------------------------------------------------------------------------

  /** Read a submodule commit the same way `fingerprint` does: the submodule's HEAD when it is checked out, else `git ls-tree HEAD <path>` (works on a shallow checkout without the submodule).
    */
  private def submoduleCommit(ssgRoot: Path, path: String): String = {
    def git(dir: Path, args: String*): Option[String] = {
      val pb = new ProcessBuilder(("git" +: args)*)
      pb.directory(dir.toFile)
      pb.redirectErrorStream(true)
      val p   = pb.start()
      val out = new String(p.getInputStream.readAllBytes()).trim
      if (p.waitFor() == 0 && out.nonEmpty) Some(out) else None
    }
    val submodule = ssgRoot.resolve(path)
    (if (Files.exists(submodule.resolve(".git"))) git(submodule, "rev-parse", "HEAD") else None).orElse(git(ssgRoot, "ls-tree", "HEAD", path).flatMap(_.split("\\s+").lift(2))).getOrElse("unknown")
  }

  /** The exporter version from the manifest bundled in the frontend-ts jar. */
  private def exporterVersion(): String = {
    val mfUrl = getClass.getClassLoader.getResource("balticporter/frontend/ts/exporter/manifest.properties")
    if (mfUrl == null) return "unknown"
    val props  = new java.util.Properties()
    val stream = mfUrl.openStream()
    try props.load(stream)
    finally stream.close()
    props.getProperty("exporter.version", "unknown")
  }

  /** Fingerprint for the ssg-katex generated tree: the inputs that, when any changes, require a full re-export and re-derive. Readable WITHOUT the submodule checked out.
    */
  private def katexFingerprint(ssgRoot: Path): String = {
    val pin     = """balticporter-engine" % "([^"]+)"""".r.findFirstMatchIn(Files.readString(ssgRoot.resolve("project/plugins.sbt"))).map(_.group(1)).getOrElse("unknown")
    val katex   = submoduleCommit(ssgRoot, "original-src/katex")
    val expVer  = exporterVersion()
    val builder = {
      val md = java.security.MessageDigest.getInstance("SHA-256")
      md.update(Files.readString(ssgRoot.resolve("project/KaTeXBuilder.scala")).replace("\r", "").getBytes("UTF-8"))
      md.digest().take(8).map(b => f"$b%02x").mkString
    }
    val reference = {
      val refDir = ssgRoot.resolve("ssg-katex/reference/scala")
      val md     = java.security.MessageDigest.getInstance("SHA-256")
      if (Files.isDirectory(refDir)) {
        val s = Files.walk(refDir)
        try {
          val files = scala.jdk.CollectionConverters.IteratorHasAsScala(s.filter(Files.isRegularFile(_)).iterator()).asScala.toList
          files.map(p => refDir.relativize(p).toString.replace('\\', '/') -> p).sortBy(_._1).foreach { case (rel, p) =>
            md.update(rel.getBytes("UTF-8"))
            md.update(Files.readAllBytes(p))
          }
        } finally s.close()
      }
      md.digest().take(8).map(b => f"$b%02x").mkString
    }
    s"engine=$pin katex=$katex exporter=$expVer builder=$builder reference=$reference"
  }

  /** Generate ssg-katex: export RAST from original-src/katex (if needed), then derive.
    *
    * Everything lives under `target/balticporter/ssg-katex/`. When the marker matches the fingerprint, both the export and the derived tree are reused without touching the submodule. Only a
    * fingerprint mismatch needs the submodule initialised.
    */
  def generateKatex(buildBase: File, log: sbt.util.Logger): Seq[File] =
    BalticPorterGen.synchronized {
      val ssgRoot      = buildBase.toPath.toAbsolutePath.normalize
      val katexRoot    = ssgRoot.resolve("target/balticporter/ssg-katex")
      val rastDir      = katexRoot.resolve("rast")
      val outDir       = katexRoot.resolve("src_managed/main/scala")
      val reportDir    = katexRoot.resolve("report")
      val marker       = katexRoot.resolve(".generated-marker")
      val referenceDir = ssgRoot.resolve("ssg-katex/reference/scala")

      val expected = katexFingerprint(ssgRoot)

      val cached = Files.exists(marker) && Files.isDirectory(outDir) && Files.isDirectory(rastDir) &&
        Files.readString(marker).trim == expected

      if (cached) {
        log.info(s"[Baltic Porter] Using cached ssg-katex tree ($expected)")
        return collectScalaFiles(outDir)
      }

      // --- fingerprint mismatch: need the submodule ---
      val katexSrc = ssgRoot.resolve("original-src/katex")
      if (!Files.isDirectory(katexSrc.resolve("src")))
        sys.error(
          s"[Baltic Porter] ssg-katex sources are missing or stale ($marker does not read `$expected`) " +
            "and original-src/katex is not initialised. Run `git submodule update --init --depth=1 original-src/katex`, " +
            s"or place a generated tree with a matching marker under $katexRoot."
        )

      log.info(s"[Baltic Porter] Generating ssg-katex ($expected)")

      // 1. Extract the exporter from the jar
      val exporterDir = ssgRoot.resolve("target/balticporter-work/ts-exporter")
      Files.createDirectories(exporterDir)
      val exporterScript = exporterDir.resolve("export.js")

      val jsUrl = getClass.getClassLoader.getResource("balticporter/frontend/ts/exporter/export.js")
      if (jsUrl == null) sys.error("[Baltic Porter] export.js not found in the frontend-ts jar")
      Files.copy(jsUrl.openStream(), exporterScript, java.nio.file.StandardCopyOption.REPLACE_EXISTING)

      val mfUrl = getClass.getClassLoader.getResource("balticporter/frontend/ts/exporter/manifest.properties")
      if (mfUrl != null) {
        val mfTarget = exporterDir.resolve("manifest.properties")
        Files.copy(mfUrl.openStream(), mfTarget, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
      }
      val mfProps = new java.util.Properties()
      if (mfUrl != null) {
        val s = mfUrl.openStream();
        try mfProps.load(s)
        finally s.close()
      }
      val tsVersion = mfProps.getProperty("typescript.version", "5.8.3")

      // 2. Ensure the typescript npm package is available
      val exporterNodeModules = exporterDir.resolve("node_modules")
      val katexNodeModules    = katexSrc.resolve("node_modules")
      if (!Files.isDirectory(exporterNodeModules.resolve("typescript"))) {
        if (Files.isDirectory(katexNodeModules.resolve("typescript"))) {
          Files.deleteIfExists(exporterNodeModules)
          Files.createSymbolicLink(exporterNodeModules, katexNodeModules)
        } else {
          val npmPb = new ProcessBuilder("npm", "install", "--no-save", s"typescript@$tsVersion")
          npmPb.directory(exporterDir.toFile)
          npmPb.redirectErrorStream(true)
          val npmP   = npmPb.start()
          val npmOut = new String(npmP.getInputStream.readAllBytes())
          if (npmP.waitFor() != 0) sys.error(s"[Baltic Porter] npm install typescript failed: $npmOut")
        }
      }

      // 3. Export RAST
      if (Files.exists(rastDir)) {
        val s = Files.walk(rastDir)
        try s.sorted(java.util.Comparator.reverseOrder()).forEach(Files.delete(_))
        finally s.close()
      }
      Files.createDirectories(rastDir)

      val pb = new ProcessBuilder("node", exporterScript.toString, "--project", katexSrc.resolve("tsconfig.json").toString, "--out", rastDir.toString)
      pb.directory(exporterDir.toFile)
      pb.redirectErrorStream(true)
      val p   = pb.start()
      val out = new String(p.getInputStream.readAllBytes())
      if (p.waitFor() != 0) sys.error(s"[Baltic Porter] KaTeX RAST export failed:\n$out")
      log.info(s"[Baltic Porter] Exported KaTeX RAST to $rastDir")

      // 4. Derive
      val lib = KaTeXBuilder.library(referenceDir)
      balticporter.frontend.ts.NonJavaBodies.build(lib, referenceDir, rastDir) match {
        case refused: balticporter.frontend.ts.NonJavaBodies.Refused =>
          log.warn(s"[Baltic Porter] ssg-katex: ${refused.message}")
          deriveReferenceOnly("ssg-katex", referenceDir, outDir, reportDir, lib.policy, log)

        case built: balticporter.frontend.ts.NonJavaBodies.Built =>
          val run = built.derive(outDir, reportDir)
          log.info(s"[Baltic Porter] ssg-katex: ${run.summary.line}")
          Files.createDirectories(marker.getParent)
          Files.writeString(marker, expected)
          run.written.map(_.toFile)
      }
    }

  // ---------------------------------------------------------------------------
  // Graphs-commons: export RAST from the five rough.js family submodules,
  // then derive via NonJavaBodies. Structured like KaTeX: one marker, one
  // fingerprint, one generated tree under target/balticporter/ssg-graphs-commons/.
  // ---------------------------------------------------------------------------

  /** Fingerprint for the ssg-graphs-commons generated tree: engine pin, the five submodule commits, exporter version, builder hash, and reference hash.
    */
  private def graphsCommonsFingerprint(ssgRoot: Path): String = {
    val pin     = """balticporter-engine" % "([^"]+)"""".r.findFirstMatchIn(Files.readString(ssgRoot.resolve("project/plugins.sbt"))).map(_.group(1)).getOrElse("unknown")
    val expVer  = exporterVersion()
    val commits = RoughBuilder.upstreamNames.map { name =>
      name -> submoduleCommit(ssgRoot, s"original-src/$name")
    }
    val builder = {
      val md = java.security.MessageDigest.getInstance("SHA-256")
      md.update(Files.readString(ssgRoot.resolve("project/RoughBuilder.scala")).replace("\r", "").getBytes("UTF-8"))
      md.digest().take(8).map(b => f"$b%02x").mkString
    }
    val reference = {
      val refDir = ssgRoot.resolve("ssg-graphs-commons/reference/scala")
      val md     = java.security.MessageDigest.getInstance("SHA-256")
      if (Files.isDirectory(refDir)) {
        val s = Files.walk(refDir)
        try {
          val files = scala.jdk.CollectionConverters.IteratorHasAsScala(s.filter(Files.isRegularFile(_)).iterator()).asScala.toList
          files.map(p => refDir.relativize(p).toString.replace('\\', '/') -> p).sortBy(_._1).foreach { case (rel, p) =>
            md.update(rel.getBytes("UTF-8"))
            md.update(Files.readAllBytes(p))
          }
        } finally s.close()
      }
      md.digest().take(8).map(b => f"$b%02x").mkString
    }
    val upstreams = commits.map { case (n, c) => s"$n=$c" }.mkString(" ")
    s"engine=$pin $upstreams exporter=$expVer builder=$builder reference=$reference"
  }

  /** Generate ssg-graphs-commons: export RAST from the five rough.js family submodules, then derive.
    *
    * Structured like `generateKatex`: everything under `target/balticporter/ssg-graphs-commons/`, cached on the fingerprint. Each upstream's RAST goes into `rast/<name>/` so the module table can
    * reference `<name>/src/<file>.rast.json`.
    */
  def generateGraphsCommons(buildBase: File, log: sbt.util.Logger): Seq[File] =
    BalticPorterGen.synchronized {
      val ssgRoot      = buildBase.toPath.toAbsolutePath.normalize
      val gcRoot       = ssgRoot.resolve("target/balticporter/ssg-graphs-commons")
      val rastDir      = gcRoot.resolve("rast")
      val outDir       = gcRoot.resolve("src_managed/main/scala")
      val reportDir    = gcRoot.resolve("report")
      val marker       = gcRoot.resolve(".generated-marker")
      val referenceDir = ssgRoot.resolve("ssg-graphs-commons/reference/scala")

      val expected = graphsCommonsFingerprint(ssgRoot)

      val cached = Files.exists(marker) && Files.isDirectory(outDir) && Files.isDirectory(rastDir) &&
        Files.readString(marker).trim == expected

      if (cached) {
        log.info(s"[Baltic Porter] Using cached ssg-graphs-commons tree ($expected)")
        return collectScalaFiles(outDir)
      }

      // --- fingerprint mismatch: need the submodules ---
      val missing = RoughBuilder.upstreamNames.filter { name =>
        val src = ssgRoot.resolve(s"original-src/$name/src")
        !Files.isDirectory(src)
      }
      if (missing.nonEmpty)
        sys.error(
          s"[Baltic Porter] ssg-graphs-commons sources are missing or stale ($marker does not read `$expected`) " +
            s"and these submodules are not initialised: ${missing.mkString(", ")}. " +
            s"Run `git submodule update --init --depth=1 ${missing.map(n => s"original-src/$n").mkString(" ")}`, " +
            s"or place a generated tree with a matching marker under $gcRoot."
        )

      log.info(s"[Baltic Porter] Generating ssg-graphs-commons ($expected)")

      // 1. Extract the exporter from the jar (shared with KaTeX)
      val exporterDir = ssgRoot.resolve("target/balticporter-work/ts-exporter")
      Files.createDirectories(exporterDir)
      val exporterScript = exporterDir.resolve("export.js")

      val jsUrl = getClass.getClassLoader.getResource("balticporter/frontend/ts/exporter/export.js")
      if (jsUrl == null) sys.error("[Baltic Porter] export.js not found in the frontend-ts jar")
      Files.copy(jsUrl.openStream(), exporterScript, java.nio.file.StandardCopyOption.REPLACE_EXISTING)

      val mfUrl = getClass.getClassLoader.getResource("balticporter/frontend/ts/exporter/manifest.properties")
      if (mfUrl != null) {
        val mfTarget = exporterDir.resolve("manifest.properties")
        Files.copy(mfUrl.openStream(), mfTarget, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
      }
      val mfProps = new java.util.Properties()
      if (mfUrl != null) {
        val s = mfUrl.openStream();
        try mfProps.load(s)
        finally s.close()
      }
      val tsVersion = mfProps.getProperty("typescript.version", "5.8.3")

      // 2. Ensure the typescript npm package is available
      val exporterNodeModules = exporterDir.resolve("node_modules")
      if (!Files.isDirectory(exporterNodeModules.resolve("typescript"))) {
        // Try katex's node_modules first, then ssg root, then npm install
        val katexNodeModules = ssgRoot.resolve("original-src/katex/node_modules")
        val ssgNodeModules   = ssgRoot.resolve("node_modules")
        if (Files.isDirectory(katexNodeModules.resolve("typescript"))) {
          Files.deleteIfExists(exporterNodeModules)
          Files.createSymbolicLink(exporterNodeModules, katexNodeModules)
        } else if (Files.isDirectory(ssgNodeModules.resolve("typescript"))) {
          Files.deleteIfExists(exporterNodeModules)
          Files.createSymbolicLink(exporterNodeModules, ssgNodeModules)
        } else {
          val npmPb = new ProcessBuilder("npm", "install", "--no-save", s"typescript@$tsVersion")
          npmPb.directory(exporterDir.toFile)
          npmPb.redirectErrorStream(true)
          val npmP   = npmPb.start()
          val npmOut = new String(npmP.getInputStream.readAllBytes())
          if (npmP.waitFor() != 0) sys.error(s"[Baltic Porter] npm install typescript failed: $npmOut")
        }
      }

      // 3. Export RAST for each upstream
      if (Files.exists(rastDir)) {
        val s = Files.walk(rastDir)
        try s.sorted(java.util.Comparator.reverseOrder()).forEach(Files.delete(_))
        finally s.close()
      }
      Files.createDirectories(rastDir)

      for (name <- RoughBuilder.upstreamNames) {
        val submoduleDir = ssgRoot.resolve(s"original-src/$name")
        RoughBuilder.exportRast(name, submoduleDir, rastDir, exporterDir, log)
      }

      // 4. Derive
      val lib = RoughBuilder.library(referenceDir)
      balticporter.frontend.ts.NonJavaBodies.build(lib, referenceDir, rastDir) match {
        case refused: balticporter.frontend.ts.NonJavaBodies.Refused =>
          log.warn(s"[Baltic Porter] ssg-graphs-commons: ${refused.message}")
          deriveReferenceOnly("ssg-graphs-commons", referenceDir, outDir, reportDir, lib.policy, log)

        case built: balticporter.frontend.ts.NonJavaBodies.Built =>
          val run = built.derive(outDir, reportDir)
          log.info(s"[Baltic Porter] ssg-graphs-commons: ${run.summary.line}")
          Files.createDirectories(marker.getParent)
          Files.writeString(marker, expected)
          run.written.map(_.toFile)
      }
    }

  // ---------------------------------------------------------------------------
  // Terser: export RAST from original-src/terser with allowJs and a committed
  // ast.d.ts, then derive via NonJavaBodies. Structured like KaTeX and
  // graphs-commons.
  // ---------------------------------------------------------------------------

  /** Fingerprint for the ssg-js generated tree. */
  private def terserFingerprint(ssgRoot: Path): String = {
    val pin     = """balticporter-engine" % "([^"]+)"""".r.findFirstMatchIn(Files.readString(ssgRoot.resolve("project/plugins.sbt"))).map(_.group(1)).getOrElse("unknown")
    val terser  = submoduleCommit(ssgRoot, "original-src/terser")
    val expVer  = exporterVersion()
    val builder = {
      val md = java.security.MessageDigest.getInstance("SHA-256")
      md.update(Files.readString(ssgRoot.resolve("project/TerserBuilder.scala")).replace("\r", "").getBytes("UTF-8"))
      md.digest().take(8).map(b => f"$b%02x").mkString
    }
    val astDts = {
      val dtsFile = ssgRoot.resolve("ssg-js/port/ast.d.ts")
      val md      = java.security.MessageDigest.getInstance("SHA-256")
      if (Files.isRegularFile(dtsFile))
        md.update(Files.readAllBytes(dtsFile))
      md.digest().take(8).map(b => f"$b%02x").mkString
    }
    val reference = {
      val refDir = ssgRoot.resolve("ssg-js/reference/scala")
      val md     = java.security.MessageDigest.getInstance("SHA-256")
      if (Files.isDirectory(refDir)) {
        val s = Files.walk(refDir)
        try {
          val files = scala.jdk.CollectionConverters.IteratorHasAsScala(s.filter(Files.isRegularFile(_)).iterator()).asScala.toList
          files.map(p => refDir.relativize(p).toString.replace('\\', '/') -> p).sortBy(_._1).foreach { case (rel, p) =>
            md.update(rel.getBytes("UTF-8"))
            md.update(Files.readAllBytes(p))
          }
        } finally s.close()
      }
      md.digest().take(8).map(b => f"$b%02x").mkString
    }
    s"engine=$pin terser=$terser exporter=$expVer builder=$builder astdts=$astDts reference=$reference"
  }

  /** Generate ssg-js: export RAST from original-src/terser (with allowJs + ast.d.ts), then derive. */
  def generateTerser(buildBase: File, log: sbt.util.Logger): Seq[File] =
    BalticPorterGen.synchronized {
      val ssgRoot      = buildBase.toPath.toAbsolutePath.normalize
      val jsRoot       = ssgRoot.resolve("target/balticporter/ssg-js")
      val rastDir      = jsRoot.resolve("rast")
      val outDir       = jsRoot.resolve("src_managed/main/scala")
      val reportDir    = jsRoot.resolve("report")
      val marker       = jsRoot.resolve(".generated-marker")
      val referenceDir = ssgRoot.resolve("ssg-js/reference/scala")
      val astDtsFile   = ssgRoot.resolve("ssg-js/port/ast.d.ts")

      val expected = terserFingerprint(ssgRoot)

      val cached = Files.exists(marker) && Files.isDirectory(outDir) && Files.isDirectory(rastDir) &&
        Files.readString(marker).trim == expected

      if (cached) {
        log.info(s"[Baltic Porter] Using cached ssg-js tree ($expected)")
        return collectScalaFiles(outDir)
      }

      // --- fingerprint mismatch: need the submodule ---
      val terserSrc = ssgRoot.resolve("original-src/terser")
      if (!Files.isDirectory(terserSrc.resolve("lib")))
        sys.error(
          s"[Baltic Porter] ssg-js sources are missing or stale ($marker does not read `$expected`) " +
            "and original-src/terser is not initialised. Run `git submodule update --init --depth=1 original-src/terser`, " +
            s"or place a generated tree with a matching marker under $jsRoot."
        )

      if (!Files.isRegularFile(astDtsFile))
        sys.error(s"[Baltic Porter] ssg-js/port/ast.d.ts is missing — it must be committed as a build input")

      log.info(s"[Baltic Porter] Generating ssg-js ($expected)")

      // 1. Extract the exporter from the jar (shared with KaTeX/graphs-commons)
      val exporterDir = ssgRoot.resolve("target/balticporter-work/ts-exporter")
      Files.createDirectories(exporterDir)
      val exporterScript = exporterDir.resolve("export.js")

      val jsUrl = getClass.getClassLoader.getResource("balticporter/frontend/ts/exporter/export.js")
      if (jsUrl == null) sys.error("[Baltic Porter] export.js not found in the frontend-ts jar")
      Files.copy(jsUrl.openStream(), exporterScript, java.nio.file.StandardCopyOption.REPLACE_EXISTING)

      val mfUrl = getClass.getClassLoader.getResource("balticporter/frontend/ts/exporter/manifest.properties")
      if (mfUrl != null) {
        val mfTarget = exporterDir.resolve("manifest.properties")
        Files.copy(mfUrl.openStream(), mfTarget, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
      }
      val mfProps = new java.util.Properties()
      if (mfUrl != null) {
        val s = mfUrl.openStream();
        try mfProps.load(s)
        finally s.close()
      }
      val tsVersion = mfProps.getProperty("typescript.version", "5.8.3")

      // 2. Ensure the typescript npm package is available
      val exporterNodeModules = exporterDir.resolve("node_modules")
      if (!Files.isDirectory(exporterNodeModules.resolve("typescript"))) {
        val ssgNodeModules = ssgRoot.resolve("node_modules")
        if (Files.isDirectory(ssgNodeModules.resolve("typescript"))) {
          Files.deleteIfExists(exporterNodeModules)
          Files.createSymbolicLink(exporterNodeModules, ssgNodeModules)
        } else {
          val npmPb = new ProcessBuilder("npm", "install", "--no-save", s"typescript@$tsVersion")
          npmPb.directory(exporterDir.toFile)
          npmPb.redirectErrorStream(true)
          val npmP   = npmPb.start()
          val npmOut = new String(npmP.getInputStream.readAllBytes())
          if (npmP.waitFor() != 0) sys.error(s"[Baltic Porter] npm install typescript failed: $npmOut")
        }
      }

      // 3. Export RAST
      if (Files.exists(rastDir)) {
        val s = Files.walk(rastDir)
        try s.sorted(java.util.Comparator.reverseOrder()).forEach(Files.delete(_))
        finally s.close()
      }
      Files.createDirectories(rastDir)

      TerserBuilder.exportRast(terserSrc, astDtsFile, rastDir, exporterDir, log)

      // 4. Derive
      val lib = TerserBuilder.library(referenceDir)
      balticporter.frontend.ts.NonJavaBodies.build(lib, referenceDir, rastDir) match {
        case refused: balticporter.frontend.ts.NonJavaBodies.Refused =>
          log.warn(s"[Baltic Porter] ssg-js: ${refused.message}")
          deriveReferenceOnly("ssg-js", referenceDir, outDir, reportDir, lib.policy, log)

        case built: balticporter.frontend.ts.NonJavaBodies.Built =>
          val run = built.derive(outDir, reportDir)
          log.info(s"[Baltic Porter] ssg-js: ${run.summary.line}")
          Files.createDirectories(marker.getParent)
          Files.writeString(marker, expected)
          run.written.map(_.toFile)
      }
    }

  /** Derive a module from reference only, with no RAST — every body is kept from the reference. Used when the RAST export is not available (the other four non-Java modules until they get their own
    * export).
    */
  def deriveReferenceOnly(
    moduleName:   String,
    referenceDir: java.io.File,
    outDir:       java.io.File,
    log:          sbt.util.Logger,
    policy:       balticporter.frontend.ts.ParityDerive.Policy = balticporter.frontend.ts.ParityDerive.Policy()
  ): Seq[File] = {
    val refPath    = referenceDir.toPath
    val outPath    = outDir.toPath
    val reportPath = outPath.resolveSibling("report")
    deriveReferenceOnly(moduleName, refPath, outPath, reportPath, policy, log)
  }

  private def deriveReferenceOnly(
    moduleName:   String,
    referenceDir: Path,
    outDir:       Path,
    reportDir:    Path,
    policy:       balticporter.frontend.ts.ParityDerive.Policy,
    log:          sbt.util.Logger
  ): Seq[File] = {
    if (!Files.isDirectory(referenceDir)) {
      log.warn(s"[Baltic Porter] No reference/ dir for $moduleName, skipping")
      return Seq.empty
    }

    val marker  = outDir.resolve(".generated-marker")
    val refHash = referenceDir.hashCode.toString + "-reference-only"

    val cached = Files.exists(marker) && Files.readString(marker).trim == refHash
    if (cached) {
      return collectScalaFiles(outDir)
    }

    // Build a Library with an empty module list — every body stays reference
    val lib = balticporter.frontend.ts.NonJavaBodies.Library(
      name = moduleName,
      policy = policy,
      readRast = balticporter.frontend.ts.Rast.readFile(_: Path),
      modules = _ => Right(Nil)
    )

    // Create a trivial rast dir (the build method requires it to exist)
    val dummyRastDir = outDir.resolveSibling("dummy-rast")
    Files.createDirectories(dummyRastDir)

    balticporter.frontend.ts.NonJavaBodies.build(lib, referenceDir, dummyRastDir) match {
      case built: balticporter.frontend.ts.NonJavaBodies.Built =>
        val run = built.derive(outDir, reportDir)
        log.info(s"[Baltic Porter] $moduleName (reference only): ${run.summary.line}")
        Files.createDirectories(marker.getParent)
        Files.writeString(marker, refHash)
        run.written.map(_.toFile)
      case refused: balticporter.frontend.ts.NonJavaBodies.Refused =>
        log.warn(s"[Baltic Porter] $moduleName: ${refused.message}")
        Seq.empty
    }
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
