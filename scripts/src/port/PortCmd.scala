package ssgdev
package port

import java.io.File

/** Port enforcement command — covenant verify, shortcut scan, quality gate.
  *
  * This is a stripped-down version of the main repo's PortCmd that only
  * supports the subcommands needed by Phase 0–10 of the anti-cheat plan.
  * The main repo's `port-tasks.tsv` registry, `next`/`baseline`/`done`
  * task workflow, and `SassSpec` dependencies are intentionally not
  * carried over because they are sass-spec-specific and not needed for
  * the ssg-md / ssg-liquid / ssg-minify / ssg-js gap-fix work.
  *
  * Subcommands provided here:
  *   ssg-dev port verify <file>           verify one file's covenant
  *   ssg-dev port verify --staged         verify all staged files
  *   ssg-dev port verify --all            verify every covenanted file
  *   ssg-dev port covenant verify ...     alias for the above (main-repo compat)
  *   ssg-dev port skip list               list skip-policy entries
  *   ssg-dev port skip add <path> <cat> <reason>
  *   ssg-dev port skip verify             every original without a Scala
  *                                        counterpart must be in skip-policy
  */
object PortCmd {

  def run(args: List[String]): Unit = {
    args match {
      case Nil | "--help" :: _ =>
        printUsage()
      case "verify" :: rest                  => verifyCmd(Cli.parse(rest))
      case "covenant" :: "verify" :: rest    => verifyCmd(Cli.parse(rest))
      case "skip" :: "list" :: rest          => skipList(Cli.parse(rest))
      case "skip" :: "add" :: rest           => skipAdd(rest)
      case "skip" :: "verify" :: _           => skipVerify()
      case other :: _ =>
        Term.err(s"Unknown port command: ${other}")
        printUsage()
        sys.exit(1)
    }
  }

  // --- verify ----------------------------------------------------------------

  private def verifyCmd(args: Cli.Args): Unit = {
    val fileArg = args.flag("file").orElse(args.positional.headOption)
    val staged = args.hasFlag("staged")
    val all = args.hasFlag("all")

    val targets: List[String] =
      if (all) {
        // Walk every covenanted file under all SSG modules
        val buf = scala.collection.mutable.ListBuffer.empty[String]
        Paths.allSsgSrcDirs.filter(d => new File(d).exists()).foreach { d =>
          walkCovenanted(new File(d), buf)
        }
        buf.toList
      } else if (staged) {
        val result = Proc.run("git", List("diff", "--cached", "--name-only"), cwd = Some(Paths.projectRoot))
        if (!result.ok) {
          Term.err(s"git diff failed: ${result.stderr}")
          sys.exit(1)
        }
        result.stdout.split("\n").toList.filter(_.nonEmpty)
      } else {
        fileArg match {
          case Some(f) => List(f)
          case None    =>
            Term.err("Usage: ssg-dev port verify (<file> | --staged | --all)")
            sys.exit(1)
        }
      }

    var anyFailed = false
    var checked = 0
    for (rel <- targets) {
      val abs = if (rel.startsWith("/")) rel else s"${Paths.projectRoot}/${rel}"
      val f = new File(abs)
      if (f.exists() && Covenant.parse(abs).isDefined) {
        checked += 1
        Covenant.verify(abs) match {
          case Right(_) =>
            println(s"OK     ${rel.stripPrefix(Paths.projectRoot + "/")}")
          case Left(reason) =>
            println(s"FAIL   ${rel.stripPrefix(Paths.projectRoot + "/")}: $reason")
            anyFailed = true
        }
      }
    }
    println(s"\n$checked covenanted file(s) checked")
    if (anyFailed) sys.exit(1)
  }

  private def walkCovenanted(f: File, buf: scala.collection.mutable.ListBuffer[String]): Unit = {
    if (f.isDirectory) {
      val kids = f.listFiles()
      if (kids != null) kids.foreach(walkCovenanted(_, buf))
    } else if (f.getName.endsWith(".scala")) {
      Covenant.parse(f.getAbsolutePath) match {
        case Some(_) => buf += f.getAbsolutePath
        case None    => ()
      }
    }
  }

  // --- skip policy -----------------------------------------------------------
  // Phase 4 stub: machine-readable skip-policy.tsv. The full implementation
  // (including verify-against-source-tree) will be wired in Phase 4.

  private def skipList(args: Cli.Args): Unit = {
    val table = loadSkipPolicy()
    val library = args.flag("library")
    val category = args.flag("category")
    var filtered = table
    library.foreach(l => filtered = filtered.filter(_.getOrElse("library", "") == l))
    category.foreach(c => filtered = filtered.filter(_.getOrElse("category", "") == c))
    if (filtered.rows.isEmpty) { println("(no skip rows match)"); return }
    val header = List("original_path", "library", "category", "justification")
    val rows = filtered.rows.map { r =>
      List(
        r.getOrElse("original_path", ""),
        r.getOrElse("library", ""),
        r.getOrElse("category", ""),
        r.getOrElse("justification", "").take(60)
      )
    }
    println(Term.table(header, rows))
    println(s"\nTotal: ${filtered.rows.size}")
  }

  private def skipAdd(args: List[String]): Unit = {
    if (args.length < 3) {
      Term.err("Usage: ssg-dev port skip add <original-path> <category> <justification>")
      sys.exit(1)
    }
    val origPath = args(0)
    val category = args(1)
    val justification = args.drop(2).mkString(" ")
    val library = guessLibrary(origPath)
    val table = loadSkipPolicy()
    val row = Map(
      "original_path" -> origPath,
      "library"       -> library,
      "category"      -> category,
      "justification" -> justification,
      "decided_by"    -> "manual",
      "review_date"   -> "",
      "replacement"   -> ""
    )
    val updated = table.addRow(row)
    saveSkipPolicy(updated)
    Term.ok(s"Added skip: $origPath ($category)")
  }

  private def skipVerify(): Unit = {
    Term.warn("port skip verify: full implementation pending Phase 4")
    Term.warn("Currently only validates that every skip-policy.tsv row has a justification.")
    val table = loadSkipPolicy()
    var bad = 0
    table.rows.foreach { r =>
      if (r.getOrElse("justification", "").isEmpty || r.getOrElse("category", "").isEmpty) {
        println(s"INCOMPLETE  ${r.getOrElse("original_path", "?")}")
        bad += 1
      }
    }
    if (bad > 0) {
      Term.err(s"$bad row(s) missing justification or category")
      sys.exit(1)
    }
    Term.ok(s"${table.rows.size} skip-policy row(s) — all complete")
  }

  private def loadSkipPolicy(): Tsv.Table = {
    val path = s"${Paths.dataDir}/skip-policy.tsv"
    if (new File(path).exists()) Tsv.read(path)
    else {
      val headers = List("original_path", "library", "category", "justification", "decided_by", "review_date", "replacement")
      Tsv.Table(headers, Nil, List("# SSG Skip Policy"))
    }
  }

  private def saveSkipPolicy(table: Tsv.Table): Unit = {
    Tsv.write(s"${Paths.dataDir}/skip-policy.tsv", table)
  }

  private def guessLibrary(origPath: String): String = {
    if (origPath.contains("flexmark-java")) "flexmark"
    else if (origPath.contains("liqp")) "liqp"
    else if (origPath.contains("dart-sass")) "dart-sass"
    else if (origPath.contains("jekyll-minifier")) "jekyll-minifier"
    else if (origPath.contains("terser")) "terser"
    else "unknown"
  }

  // --- usage -----------------------------------------------------------------

  private def printUsage(): Unit = {
    println("""Usage: ssg-dev port <command>
              |
              |Verification (gates the audit-pass status):
              |  verify <file>                Verify one covenanted file
              |  verify --staged              Verify every staged covenanted file
              |  verify --all                 Verify every covenanted file under SSG modules
              |  covenant verify [...]        Alias for the above (main-repo compat)
              |
              |Skip policy:
              |  skip list [--library L] [--category C]
              |  skip add <original-path> <category> <justification>
              |  skip verify                  Every skip row must have justification + category
              |
              |Each `verify` runs Covenant.verify, which composes:
              |  - method-set check (every baseline method must still exist)
              |  - shortcut scan (zero hits via Shortcuts.scanFile)
              |
              |This is the worktree-sunny-wiggling-phoenix variant. The main repo
              |has a richer port command set tied to the sass-spec test runner.""".stripMargin)
  }
}
