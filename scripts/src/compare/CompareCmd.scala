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
      case other :: _ =>
        Term.err(s"Unknown compare command: $other")
        sys.exit(1)
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
              |
              |Libraries: flexmark, liqp, dart-sass, jekyll-minifier""".stripMargin)
  }
}
