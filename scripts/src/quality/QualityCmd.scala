package ssgdev
package quality

/** Code quality scanning. */
object QualityCmd {

  def run(args: List[String]): Unit = {
    args match {
      case Nil | "--help" :: _ =>
        printUsage()
      case "scan" :: rest => scan(Cli.parse(rest))
      case "grep" :: rest => grepCmd(rest)
      case "scalafix" :: rest => scalafix(Cli.parse(rest))
      case other :: _ =>
        Term.err(s"Unknown quality command: $other")
        sys.exit(1)
    }
  }

  private def scan(args: Cli.Args): Unit = {
    val doReturn = args.hasFlag("return")
    val doNull = args.hasFlag("null")
    val doTodo = args.hasFlag("todo")
    val doJava = args.hasFlag("java-syntax")
    val doAll = args.hasFlag("all") || (!doReturn && !doNull && !doTodo && !doJava)
    val summary = args.hasFlag("summary")

    val srcDirs = Paths.allSsgSrcDirs.filter(d => new java.io.File(d).exists())
    if (srcDirs.isEmpty) {
      Term.warn("No source directories found")
      return
    }

    if (doAll || doReturn) scanPattern("return", """\breturn\b""", srcDirs, summary)
    if (doAll || doNull) scanPattern("null", """== null|!= null""", srcDirs, summary)
    if (doAll || doTodo) scanPattern("todo", """\b(TODO|FIXME|HACK|XXX)\b""", srcDirs, summary)
    if (doAll || doJava) scanPattern("java-syntax", """\b(public|static|void|boolean|implements)\b""", srcDirs, summary)
  }

  private def scanPattern(name: String, pattern: String, dirs: List[String], summary: Boolean): Unit = {
    println(s"\n=== Scan: $name ===")
    var totalFiles = 0
    var totalMatches = 0

    for (dir <- dirs) {
      val result = Proc.run("sh", List("-c",
        s"""find '$dir' -name '*.scala' -exec grep -l '$pattern' {} \\;"""))
      if (result.ok && result.stdout.trim.nonEmpty) {
        val files = result.stdout.trim.split("\n")
        totalFiles += files.length
        for (file <- files) {
          val countResult = Proc.run("sh", List("-c",
            s"""grep -c '$pattern' '$file'"""))
          val count = countResult.stdout.trim.toIntOption.getOrElse(0)
          totalMatches += count
          if (!summary) {
            val relPath = file.stripPrefix(Paths.projectRoot + "/")
            println(s"  $relPath ($count)")
          }
        }
      }
    }

    println(s"  Files: $totalFiles, Matches: $totalMatches")
  }

  private def grepCmd(rest: List[String]): Unit = {
    if (rest.isEmpty) {
      Term.err("Pattern required: ssg-dev quality grep <pattern>")
      sys.exit(1)
    }
    val pattern = rest.head
    val args = Cli.parse(rest.tail)
    val count = args.hasFlag("count")
    val filesOnly = args.hasFlag("files-only")

    val srcDirs = Paths.allSsgSrcDirs.filter(d => new java.io.File(d).exists())
    for (dir <- srcDirs) {
      val flags = if (count) "-c" else if (filesOnly) "-l" else "-n"
      val result = Proc.run("sh", List("-c",
        s"""grep -r $flags '$pattern' '$dir' --include='*.scala'"""))
      if (result.ok && result.stdout.trim.nonEmpty) {
        // Relativize paths
        result.stdout.split("\n").foreach { line =>
          println(line.stripPrefix(Paths.projectRoot + "/"))
        }
      }
    }
  }

  private def scalafix(args: Cli.Args): Unit = {
    val rule = args.requirePositional(0, "rule")
    val file = args.flag("file")
    val cmd = file match {
      case Some(f) => s"""scalafix --rules $rule --files $f"""
      case None => s"""scalafix --rules $rule"""
    }
    Term.info(s"Running scalafix rule: $rule")
    val exit = Proc.exec("sbt", List("--client", cmd), cwd = Some(Paths.projectRoot))
    if (exit != 0) sys.exit(exit)
  }

  private def printUsage(): Unit = {
    println("""Usage: ssg-dev quality <command>
              |
              |Commands:
              |  scan [--return] [--null] [--todo] [--java-syntax] [--all] [--summary]
              |  grep <pattern> [--count] [--files-only]
              |  scalafix <rule> [--file PATH]""".stripMargin)
  }
}
