package ssgdev
package compare

import java.io.File

/** Compare original source files with SSG ported files. */
object CompareCmd {

  private val libToModule = Map(
    "flexmark" -> "ssg-md",
    "liqp" -> "ssg-liquid",
    "dart-sass" -> "ssg-sass",
    "jekyll-minifier" -> "ssg-minify",
    "terser" -> "ssg-js"
  )

  def run(args: List[String]): Unit = {
    args match {
      case Nil | "--help" :: _ =>
        printUsage()
      case "file" :: rest => file(Cli.parse(rest))
      case "package" :: rest => packageCmd(Cli.parse(rest))
      case "find" :: rest => find(Cli.parse(rest))
      case "status" :: rest => status(Cli.parse(rest))
      case "next-batch" :: rest => nextBatch(Cli.parse(rest))
      case "methods" :: rest => methodsCmd(rest)
      case "loc" :: rest => locCmd(Cli.parse(rest))
      case "exception-leaks" :: _ => exceptionLeaks()
      case other :: _ =>
        Term.err(s"Unknown compare command: $other")
        sys.exit(1)
    }
  }

  /** Phase 1 enforcement: method-set diff between a Scala file and its
    * dart reference. `--strict` additionally requires every common
    * method's Scala body AST-node-count to be at least 70% of the dart
    * body's, catching one-line shim ports.
    */
  private def methodsCmd(rawArgs: List[String]): Unit = {
    val args = Cli.parse(rawArgs)
    val strict = args.hasFlag("strict")
    // Two positionals: ssg-file dart-file. But we also accept the ssg file
    // alone and auto-resolve the dart reference from the migration DB.
    val positional = args.positional
    if (positional.isEmpty) {
      Term.err("Usage: ssg-dev compare methods <ssg-file> [<dart-file>] [--strict]")
      sys.exit(1)
    }
    val ssgFile = resolveFile(positional.head)
    val dartFile = positional.lift(1).map(resolveFile).orElse(resolveDartRef(ssgFile))
    dartFile match {
      case None =>
        Term.err(s"Dart reference not found for $ssgFile (pass explicitly or add to migration DB)")
        sys.exit(1)
      case Some(ref) =>
        val gap = if (strict) Methods.strictCompare(ssgFile, ref) else Methods.compare(ssgFile, ref)
        println(s"Scala: ${ssgFile.stripPrefix(Paths.projectRoot + "/")}")
        println(s"Dart:  ${ref.stripPrefix(Paths.projectRoot + "/")}")
        println(s"  common:  ${gap.common.size}")
        println(s"  missing: ${gap.missing.size}")
        println(s"  extra:   ${gap.extra.size}")
        if (strict) println(s"  short:   ${gap.shortBody.size}")
        if (gap.missing.nonEmpty) {
          println("\nMissing from Scala:")
          gap.missing.foreach(m => println(s"  $m"))
        }
        if (strict && gap.shortBody.nonEmpty) {
          println("\nShort body (< 70% AST nodes):")
          gap.shortBody.foreach(m => println(s"  $m"))
        }
        if (gap.extra.nonEmpty) {
          println("\nExtra in Scala (informational):")
          gap.extra.take(20).foreach(m => println(s"  $m"))
          if (gap.extra.size > 20) println(s"  ... (${gap.extra.size - 20} more)")
        }
        if (gap.missing.nonEmpty || (strict && gap.shortBody.nonEmpty)) sys.exit(1)
    }
  }

  /** Phase 1 triage: report LoC ratio per file vs dart reference, ranked. */
  private def locCmd(args: Cli.Args): Unit = {
    val module = args.flagOrDefault("module", "ssg-sass")
    val moduleDir = Paths.moduleSrc(module)
    if (!new File(moduleDir).exists()) {
      Term.err(s"Module not found: $module")
      sys.exit(1)
    }
    // Walk ssg-<module> scala files; for each, find the dart counterpart
    // under original-src/dart-sass/lib/src/ and compute the LoC ratio.
    val rows = scala.collection.mutable.ListBuffer.empty[(String, Int, Int, Double)]
    walkFiles(new File(moduleDir)).foreach { scalaFile =>
      if (scalaFile.getName.endsWith(".scala")) {
        val scalaLoc = countLines(scalaFile)
        val dartPath = guessDartPath(scalaFile.getAbsolutePath, module)
        dartPath.foreach { dp =>
          val f = new File(dp)
          if (f.exists()) {
            val dartLoc = countLines(f)
            val ratio = if (dartLoc == 0) 0.0 else scalaLoc.toDouble * 100 / dartLoc.toDouble
            rows += ((scalaFile.getAbsolutePath.stripPrefix(Paths.projectRoot + "/"), scalaLoc, dartLoc, ratio))
          }
        }
      }
    }
    val sorted = rows.sortBy(_._4)
    println(f"${"file"}%-55s ${"scala"}%6s ${"dart"}%6s ${"ratio"}%8s")
    println("-" * 80)
    sorted.foreach { case (f, s, d, r) =>
      val flag = if (r < 50) "!" else if (r < 70) "?" else " "
      println(f"$flag $f%-53s $s%6d $d%6d ${r}%7.1f%%")
    }
  }

  // --- helpers for methods / loc ---------------------------------------------

  private def resolveFile(p: String): String =
    if (p.startsWith("/")) p else s"${Paths.projectRoot}/$p"

  private def resolveDartRef(scalaFile: String): Option[String] = {
    // Heuristic: map ssg-sass/.../sass/X/Y.scala to original-src/dart-sass/lib/src/X/Y.dart
    val marker = "ssg-sass/src/main/scala/ssg/sass/"
    val idx = scalaFile.indexOf(marker)
    if (idx < 0) return None
    val rel = scalaFile.substring(idx + marker.length)
    // Convert ClassName.scala to class_name.dart using a snake-case transform.
    val parts = rel.split("/").toList
    val dartName = parts.last.stripSuffix(".scala")
    val snake = camelToSnake(dartName)
    val dartParts = parts.init :+ s"$snake.dart"
    val candidates = List(
      s"${Paths.dartSassSrc}/lib/src/${dartParts.mkString("/")}",
      s"${Paths.dartSassSrc}/lib/src/${parts.init.mkString("/")}/${dartName.toLowerCase}.dart"
    )
    candidates.find(p => new File(p).exists())
  }

  private def camelToSnake(name: String): String = {
    val sb = new StringBuilder
    for ((c, i) <- name.zipWithIndex)
      if (i > 0 && c.isUpper) sb.append('_').append(c.toLower)
      else sb.append(c.toLower)
    sb.toString
  }

  private def walkFiles(root: File): List[File] = {
    val buf = scala.collection.mutable.ListBuffer.empty[File]
    def rec(f: File): Unit = {
      if (f.isDirectory) {
        val kids = f.listFiles()
        if (kids != null) kids.foreach(rec)
      } else buf += f
    }
    rec(root)
    buf.toList
  }

  private def countLines(f: File): Int = {
    val reader = new java.io.BufferedReader(new java.io.FileReader(f))
    try {
      var c = 0
      var line = reader.readLine()
      while (line != null) { c += 1; line = reader.readLine() }
      c
    } finally reader.close()
  }

  private def guessDartPath(scalaFile: String, module: String): Option[String] =
    if (module == "ssg-sass") resolveDartRef(scalaFile) else None

  /** Phase 5: scan ssg-sass/target/sass-spec-failures.txt for leak
    * categories (uncaught-*, null-pointer, index-bounds, match-error,
    * stack-overflow, no-such-element, illegal-state) and print one
    * representative reproducer per category. These are always real
    * ssg-sass bugs, never legitimate spec failures.
    */
  private def exceptionLeaks(): Unit = {
    val path = s"${Paths.projectRoot}/ssg-sass/target/sass-spec-failures.txt"
    val f = new File(path)
    if (!f.exists()) {
      Term.err(s"sass-spec failures file not found at $path")
      Term.err("Run `ssg-dev test sass-spec` first to generate it.")
      sys.exit(1)
    }
    val leakCats = Set(
      "stack-overflow",
      "null-pointer",
      "index-bounds",
      "no-such-element",
      "match-error",
      "illegal-state",
      "illegal-argument",
      "number-format",
      "script-error"
    )
    val byCat = scala.collection.mutable.LinkedHashMap.empty[String, scala.collection.mutable.ListBuffer[(String, String)]]
    val reader = new java.io.BufferedReader(new java.io.FileReader(f))
    try {
      var line = reader.readLine()
      var currentRel: Option[String] = None
      var currentCat: Option[String] = None
      var currentDetail = new StringBuilder
      def flush(): Unit = {
        for {
          rel <- currentRel
          cat <- currentCat
          if leakCats.contains(cat) || cat.startsWith("uncaught-")
        } {
          val list = byCat.getOrElseUpdate(cat, scala.collection.mutable.ListBuffer.empty)
          list += ((rel, currentDetail.toString.trim))
        }
        currentRel = None
        currentCat = None
        currentDetail = new StringBuilder
      }
      while (line != null) {
        if (line.startsWith("## ")) {
          flush()
          currentRel = Some(line.drop(3).trim)
        } else if (line.startsWith("category: ")) {
          currentCat = Some(line.drop("category: ".length).trim)
        } else if (line.startsWith("  ") && currentRel.isDefined) {
          if (currentDetail.nonEmpty) currentDetail.append('\n')
          currentDetail.append(line.trim)
        }
        line = reader.readLine()
      }
      flush()
    } finally reader.close()

    if (byCat.isEmpty) {
      println("No leak-category failures found in sass-spec-failures.txt")
      return
    }
    println("=== Exception leaks in sass-spec ===")
    println("(Each category is a real ssg-sass bug — fix as Phase 4 micro-tasks)\n")
    for ((cat, items) <- byCat.toList.sortBy(-_._2.size)) {
      println(s"## $cat (${items.size} cases)")
      val (rel, detail) = items.head
      println(s"  example: $rel")
      if (detail.nonEmpty) println(s"  detail:  ${detail.linesIterator.take(2).mkString(" | ")}")
      println()
    }
  }

  private def file(args: Cli.Args): Unit = {
    val path = args.requirePositional(0, "path")
    val lib = args.flagOrDefault("lib", guessLib(path))
    val srcRoot = Paths.originalSrc(lib)
    val fullPath = if (path.startsWith("/")) path else s"$srcRoot/$path"
    if (new File(fullPath).exists()) {
      println(s"  Source: $fullPath")
      println(s"  Module: ${libToModule.getOrElse(lib, "?")}")
    } else {
      Term.err(s"File not found: $fullPath")
    }
  }

  private def packageCmd(args: Cli.Args): Unit = {
    val pkg = args.requirePositional(0, "package")
    val lib = args.flagOrDefault("lib", "flexmark")
    val srcRoot = Paths.originalSrc(lib)

    val extensions = lib match {
      case "flexmark" | "liqp" => List(".java")
      case "dart-sass" => List(".dart")
      case "jekyll-minifier" => List(".rb")
      case _ => List(".java")
    }

    val dir = findPackageDir(srcRoot, pkg)
    dir match {
      case Some(d) =>
        val files = d.listFiles().filter(f => extensions.exists(f.getName.endsWith)).sortBy(_.getName)
        println(s"Package: $pkg ($lib)")
        println(s"Directory: ${d.getAbsolutePath.stripPrefix(Paths.projectRoot + "/")}")
        files.foreach(f => println(s"  ${f.getName}"))
        println(s"  Total: ${files.length} files")
      case None =>
        Term.err(s"Package not found: $pkg in $lib")
    }
  }

  private def find(args: Cli.Args): Unit = {
    val pattern = args.requirePositional(0, "pattern")
    val lib = args.flag("lib")
    val libs = lib.map(List(_)).getOrElse(libToModule.keys.toList)

    for (l <- libs) {
      val srcRoot = Paths.originalSrc(l)
      if (new File(srcRoot).exists()) {
        val result = Proc.run("sh", List("-c",
          s"""find '$srcRoot' -name '*$pattern*' -type f | sort"""))
        if (result.ok && result.stdout.trim.nonEmpty) {
          println(s"\n=== $l ===")
          result.stdout.split("\n").foreach { line =>
            println(s"  ${line.stripPrefix(srcRoot + "/")}")
          }
        }
      }
    }
  }

  private def status(args: Cli.Args): Unit = {
    val lib = args.flag("lib")
    val module = args.flag("module")

    val table = {
      val path = Paths.migrationTsv
      if (new File(path).exists()) Tsv.read(path)
      else { println("Migration database is empty. Run: ssg-dev db migration sync"); return }
    }

    var filtered = table
    lib.foreach(l => filtered = filtered.filter(_.getOrElse("source_lib", "") == l))
    module.foreach(m => filtered = filtered.filter(_.getOrElse("module", "") == m))

    val byStatus = filtered.stats("status").toList.sortBy(-_._2)
    val total = filtered.size
    val done = filtered.rows.count(r => {
      val s = r.getOrElse("status", "")
      s != "not_started" && s != "skipped"
    })
    val skipped = filtered.rows.count(_.getOrElse("status", "") == "skipped")
    val active = total - skipped

    println("=== Porting Status ===")
    byStatus.foreach { case (s, c) => println(f"  $s%-20s $c%d") }
    println(f"  ${"Total"}%-20s $total%d")
    if (active > 0) {
      println(f"\n  Progress: $done%d / $active%d (${done * 100 / active}%%)")
    }
  }

  private def nextBatch(args: Cli.Args): Unit = {
    val n = args.flagOrDefault("n", "10").toInt
    val lib = args.flag("lib")

    val table = {
      val path = Paths.migrationTsv
      if (new File(path).exists()) Tsv.read(path)
      else { println("Migration database is empty. Run: ssg-dev db migration sync"); return }
    }

    var candidates = table.filter(_.getOrElse("status", "") == "not_started")
    lib.foreach(l => candidates = candidates.filter(_.getOrElse("source_lib", "") == l))

    val batch = candidates.rows.take(n)
    if (batch.isEmpty) {
      println("No files remaining with status 'not_started'")
    } else {
      println(s"Next ${batch.size} files to port:")
      batch.foreach { row =>
        val l = row.getOrElse("source_lib", "?")
        val p = row.getOrElse("source_path", "?")
        println(s"  [$l] $p")
      }
    }
  }

  private def guessLib(path: String): String = {
    if (path.contains("flexmark") || path.endsWith(".java")) "flexmark"
    else if (path.contains("liqp")) "liqp"
    else if (path.contains("sass") || path.endsWith(".dart")) "dart-sass"
    else if (path.contains("minifier") || path.endsWith(".rb")) "jekyll-minifier"
    else "flexmark" // default
  }

  private def findPackageDir(root: String, pkg: String): Option[File] = {
    val pkgPath = pkg.replace(".", "/")
    val candidates = List(
      new File(root, pkgPath),
      new File(root, s"src/main/java/$pkgPath"),
      new File(root, s"lib/src/$pkgPath"),
      new File(root, s"lib/$pkgPath")
    )
    candidates.find(_.isDirectory).orElse {
      // Try recursive search
      val result = Proc.run("sh", List("-c", s"""find '$root' -type d -name '${pkg.split('.').last}' | head -1"""))
      if (result.ok && result.stdout.trim.nonEmpty) Some(new File(result.stdout.trim))
      else None
    }
  }

  private def printUsage(): Unit = {
    println("""Usage: ssg-dev compare <command>
              |
              |Commands:
              |  file <path> [--lib L]         Show source file location
              |  package <pkg> [--lib L]       List files in a source package
              |  find <pattern> [--lib L]      Find files in source trees
              |  status [--lib L] [--module M] Porting status from migration db
              |  next-batch [-n N] [--lib L]   Suggest next files to port
              |  methods <ssg> [<dart>] [--strict]
              |                                Method-set diff (Phase 1 enforcement)
              |                                --strict: 70% AST-node-count body floor
              |  loc [--module M]              LoC ratio per file vs dart reference
              |  exception-leaks               Group sass-spec leaks by category
              |
              |Libraries: flexmark, liqp, dart-sass, jekyll-minifier""".stripMargin)
  }
}
