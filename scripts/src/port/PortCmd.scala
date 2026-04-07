package ssgdev
package port

import java.io.File

/** Porting task registry and verification commands.
  *
  * `port-tasks.tsv` is the hand-curated registry of dart-sass → ssg-sass porting
  * tasks. Each row is a single unit of faithful porting work (typically 150–600
  * dart LoC) gated on a sass-spec subdirectory reaching 100% strict-pass and on
  * the target file carrying a `Covenant: full-port` header.
  *
  * The single command an executing agent is told to run is
  * `ssg-dev port verify <id>`. It runs four sub-checks (regression, subdir
  * strict-pass, shortcuts, method-set) and prints `OK` or a structured failure.
  * No porting task may be marked done on any other basis.
  */
object PortCmd {

  def run(args: List[String]): Unit = {
    args match {
      case Nil | "--help" :: _ =>
        printUsage()
      case "list" :: rest => list(Cli.parse(rest))
      case "next" :: rest => next(Cli.parse(rest))
      case "baseline" :: rest => baseline(rest)
      case "verify" :: rest => verify(rest)
      case "done" :: rest => done(rest)
      case "blocker" :: rest => blocker(rest)
      case "note" :: rest => note(rest)
      case "snapshot" :: _ => snapshot()
      case "covenant" :: "verify" :: rest => covenantVerify(Cli.parse(rest))
      case other :: _ =>
        Term.err(s"Unknown port command: $other")
        printUsage()
        sys.exit(1)
    }
  }

  // --- Commands --------------------------------------------------------------

  private def list(args: Cli.Args): Unit = {
    val table = loadTasks()
    var filtered = table
    args.flag("status").foreach(s => filtered = filtered.filter(_.getOrElse("status", "") == s))
    args.flag("assignee").foreach(a => filtered = filtered.filter(_.getOrElse("assignee", "") == a))

    if (filtered.rows.isEmpty) {
      println("(no tasks match)")
      return
    }
    val header = List("id", "status", "file", "spec_subdir", "loc_cap")
    val rows = filtered.rows.map { r =>
      List(
        r.getOrElse("id", ""),
        r.getOrElse("status", ""),
        shortenFile(r.getOrElse("file", "")),
        r.getOrElse("spec_subdir", ""),
        r.getOrElse("loc_cap", "")
      )
    }
    println(Term.table(header, rows))
    println(s"\nTotal: ${filtered.rows.size}")
  }

  private def next(args: Cli.Args): Unit = {
    val table = loadTasks()
    val pending = table.filter { r =>
      val status = r.getOrElse("status", "")
      status == "pending" || status.isEmpty
    }
    val candidates = args.flag("assignee") match {
      case Some(a) => pending.filter(_.getOrElse("assignee", "") == a)
      case None    => pending
    }
    candidates.rows.headOption match {
      case Some(row) =>
        println(s"Next task: ${row.getOrElse("id", "?")}")
        printTask(row)
      case None =>
        println("(no pending tasks)")
    }
  }

  private def baseline(args: List[String]): Unit = {
    if (args.isEmpty) {
      Term.err("Missing task id: ssg-dev port baseline <id>")
      sys.exit(1)
    }
    val id = args.head
    val table = loadTasks()
    table.find(_.getOrElse("id", "") == id) match {
      case Some(task) =>
        Term.info(s"Recording baseline for task $id")
        Term.info("Running sass-spec snapshot to refresh baseline TSV...")
        val outcome = ssgdev.testing.SassSpec.runSnapshot()
        if (!outcome.ok) {
          Term.err("Failed to capture sass-spec baseline")
          outcome.details.foreach(d => println(s"  $d"))
          sys.exit(1)
        }
        // Step 2: capture method set of the target file
        val file = task.getOrElse("file", "")
        val methods = if (file.nonEmpty) extractScalaMethods(file) else Nil
        val loc = if (file.nonEmpty) countLoc(file) else 0

        // Step 3: write the baseline fields into the task row
        val updated = table.updateRow(
          _.getOrElse("id", "") == id,
          Map(
            "baseline_pass" -> readBaselinePassCount().toString,
            "baseline_methods" -> methods.mkString(","),
            "baseline_loc" -> loc.toString,
            "last_updated" -> nowIso(),
            "status" -> "in-progress"
          )
        )
        saveTasks(updated)
        Term.ok(s"Baseline recorded for $id: ${methods.size} methods, $loc LoC")
      case None =>
        Term.err(s"Task not found: $id")
        sys.exit(1)
    }
  }

  private def verify(args: List[String]): Unit = {
    if (args.isEmpty) {
      Term.err("Missing task id: ssg-dev port verify <id>")
      sys.exit(1)
    }
    val id = args.head
    val table = loadTasks()
    table.find(_.getOrElse("id", "") == id) match {
      case Some(task) =>
        runVerifyChecks(task) match {
          case VerifyResult.Ok =>
            println("OK")
            sys.exit(0)
          case VerifyResult.Fail(reason, details) =>
            println(s"FAIL: $reason")
            details.take(5).foreach(d => println(s"  $d"))
            sys.exit(1)
        }
      case None =>
        Term.err(s"Task not found: $id")
        sys.exit(1)
    }
  }

  private def done(args: List[String]): Unit = {
    if (args.isEmpty) {
      Term.err("Missing task id: ssg-dev port done <id>")
      sys.exit(1)
    }
    val id = args.head
    val table = loadTasks()
    table.find(_.getOrElse("id", "") == id) match {
      case Some(task) =>
        runVerifyChecks(task) match {
          case VerifyResult.Ok =>
            val updated = table.updateRow(
              _.getOrElse("id", "") == id,
              Map("status" -> "done", "last_updated" -> nowIso())
            )
            saveTasks(updated)
            Term.ok(s"Task $id marked done")
          case VerifyResult.Fail(reason, _) =>
            Term.err(s"Cannot mark done: $reason")
            Term.err("Run `ssg-dev port verify <id>` for details")
            sys.exit(1)
        }
      case None =>
        Term.err(s"Task not found: $id")
        sys.exit(1)
    }
  }

  private def blocker(args: List[String]): Unit = {
    if (args.length < 2) {
      Term.err("Usage: ssg-dev port blocker <id> <reason>")
      sys.exit(1)
    }
    val id = args.head
    val reason = args.tail.mkString(" ")
    val table = loadTasks()
    if (table.find(_.getOrElse("id", "") == id).isEmpty) {
      Term.err(s"Task not found: $id")
      sys.exit(1)
    }
    val updated = table.updateRow(
      _.getOrElse("id", "") == id,
      Map("status" -> "blocked", "blocker" -> reason, "last_updated" -> nowIso())
    )
    saveTasks(updated)
    Term.warn(s"Task $id marked blocked: $reason")
    Term.warn("Session MUST terminate now. Do not attempt another task in this session.")
  }

  private def note(args: List[String]): Unit = {
    if (args.length < 2) {
      Term.err("Usage: ssg-dev port note <id> <text>")
      sys.exit(1)
    }
    val id = args.head
    val text = args.tail.mkString(" ")
    val table = loadTasks()
    val existing = table.find(_.getOrElse("id", "") == id) match {
      case Some(row) => row.getOrElse("notes", "")
      case None =>
        Term.err(s"Task not found: $id")
        sys.exit(1)
    }
    val newNotes = if (existing.isEmpty) text else s"$existing; $text"
    val updated = table.updateRow(
      _.getOrElse("id", "") == id,
      Map("notes" -> newNotes, "last_updated" -> nowIso())
    )
    saveTasks(updated)
    Term.ok(s"Note added to $id")
  }

  private def snapshot(): Unit = {
    Term.info("Refreshing sass-spec baseline snapshot...")
    val outcome = ssgdev.testing.SassSpec.runSnapshot()
    if (!outcome.ok) {
      outcome.details.foreach(d => println(s"  $d"))
      sys.exit(1)
    }
    Term.ok(s"Baseline refreshed at ${Paths.dataDir}/sass-spec-baseline.tsv")
  }

  private def covenantVerify(args: Cli.Args): Unit = {
    val file = args.flag("file")
    val staged = args.hasFlag("staged")
    if (file.isEmpty && !staged) {
      Term.err("Usage: ssg-dev port covenant verify [--file F | --staged]")
      sys.exit(1)
    }
    val filesToCheck: List[String] =
      if (staged) {
        val result = Proc.run("git", List("diff", "--cached", "--name-only"), cwd = Some(Paths.projectRoot))
        if (!result.ok) {
          Term.err(s"git diff failed: ${result.stderr}")
          sys.exit(1)
        }
        result.stdout.split("\n").toList.filter(_.nonEmpty)
      } else List(file.get)

    var anyFailed = false
    for (f <- filesToCheck) {
      val abs = if (f.startsWith("/")) f else s"${Paths.projectRoot}/$f"
      val covenantFile = new File(abs)
      if (covenantFile.exists() && parseCovenantHeader(abs).isDefined) {
        Covenant.verify(abs) match {
          case Right(_) =>
            println(s"COVENANT OK: $f")
          case Left(reason) =>
            println(s"COVENANT FAIL: $f — $reason")
            anyFailed = true
        }
      }
    }
    if (anyFailed) sys.exit(1)
    println("All covenant checks passed")
  }

  // --- Verify sub-checks -----------------------------------------------------

  private enum VerifyResult { case Ok; case Fail(reason: String, details: List[String]) }

  private def runVerifyChecks(task: Map[String, String]): VerifyResult = {
    val id = task.getOrElse("id", "?")
    val file = task.getOrElse("file", "")
    val dartRef = task.getOrElse("dart_ref_file", "")
    val specSubdir = task.getOrElse("spec_subdir", "")

    // Check 1: shortcuts must return zero hits for the target file.
    if (file.nonEmpty) {
      val absFile = if (file.startsWith("/")) file else s"${Paths.projectRoot}/$file"
      if (!new File(absFile).exists()) {
        return VerifyResult.Fail("target file does not exist", List(absFile))
      }
      val hits = ssgdev.quality.Shortcuts.scanFile(absFile)
      if (hits.nonEmpty)
        return VerifyResult.Fail(
          s"shortcuts: ${hits.size} hit(s) in $file",
          hits.take(5).map(h => s"${h.pattern}: line ${h.line}: ${h.text}")
        )
    }

    // Check 2: method-set strict comparison against the dart reference.
    if (file.nonEmpty && dartRef.nonEmpty) {
      val absFile = if (file.startsWith("/")) file else s"${Paths.projectRoot}/$file"
      val absDart = if (dartRef.startsWith("/")) dartRef else s"${Paths.projectRoot}/$dartRef"
      if (new File(absFile).exists() && new File(absDart).exists()) {
        val gap = ssgdev.compare.Methods.strictCompare(absFile, absDart)
        if (gap.missing.nonEmpty)
          return VerifyResult.Fail(
            s"methods: ${gap.missing.size} missing from Scala",
            gap.missing.take(5)
          )
        if (gap.shortBody.nonEmpty)
          return VerifyResult.Fail(
            s"methods: ${gap.shortBody.size} method(s) below 70% AST node count",
            gap.shortBody.take(5).map(m => s"$m: too short")
          )
      }
    }

    // Check 3: sass-spec subdir strict-pass + baseline regression.
    val _ = id // suppress unused
    if (specSubdir.nonEmpty) {
      val specResult = ssgdev.testing.SassSpec.runSubdir(specSubdir)
      if (!specResult.ok)
        return VerifyResult.Fail(
          s"spec subdir '$specSubdir' not 100%",
          specResult.details
        )
    }
    val regression = ssgdev.testing.SassSpec.runRegression()
    if (!regression.ok)
      return VerifyResult.Fail("sass-spec regression detected", regression.details)

    VerifyResult.Ok
  }

  // --- Helpers ---------------------------------------------------------------

  private def loadTasks(): Tsv.Table = {
    val path = s"${Paths.dataDir}/port-tasks.tsv"
    if (new File(path).exists()) Tsv.read(path)
    else {
      val headers = List(
        "id", "file", "method_or_range", "dart_ref_file", "dart_ref_start", "dart_ref_end",
        "spec_subdir", "spec_delta_floor", "loc_cap", "status", "assignee",
        "baseline_pass", "baseline_methods", "baseline_loc", "blocker", "notes", "last_updated"
      )
      Tsv.Table(headers, Nil, Nil)
    }
  }

  private def saveTasks(table: Tsv.Table): Unit = {
    val path = s"${Paths.dataDir}/port-tasks.tsv"
    Tsv.write(path, table)
  }

  private def shortenFile(path: String): String = {
    val marker = "ssg-sass/src/main/scala/ssg/sass/"
    val idx = path.indexOf(marker)
    if (idx >= 0) path.substring(idx + marker.length) else path
  }

  private def printTask(row: Map[String, String]): Unit = {
    println(s"  id:            ${row.getOrElse("id", "")}")
    println(s"  file:          ${row.getOrElse("file", "")}")
    println(s"  method/range:  ${row.getOrElse("method_or_range", "")}")
    println(s"  dart ref:      ${row.getOrElse("dart_ref_file", "")}:${row.getOrElse("dart_ref_start", "")}-${row.getOrElse("dart_ref_end", "")}")
    println(s"  spec subdir:   ${row.getOrElse("spec_subdir", "")}")
    println(s"  spec floor:    ${row.getOrElse("spec_delta_floor", "5")} cases")
    println(s"  loc cap:       ${row.getOrElse("loc_cap", "600")}")
    println(s"  status:        ${row.getOrElse("status", "pending")}")
    if (row.getOrElse("blocker", "").nonEmpty)
      println(s"  blocker:       ${row("blocker")}")
    if (row.getOrElse("notes", "").nonEmpty)
      println(s"  notes:         ${row("notes")}")
  }

  private def readBaselinePassCount(): Int = {
    val path = s"${Paths.dataDir}/sass-spec-baseline.tsv"
    if (!new File(path).exists()) return 0
    val reader = new java.io.BufferedReader(new java.io.FileReader(path))
    try {
      var count = 0
      var line = reader.readLine()
      while (line != null) {
        if (!line.startsWith("#") && line.contains("\t")) {
          val parts = line.split("\t", -1)
          if (parts.length >= 2 && (parts(1) == "Pass" || parts(1) == "ExpectedErrorOk"))
            count += 1
        }
        line = reader.readLine()
      }
      count
    } finally reader.close()
  }

  private def countLoc(path: String): Int = {
    val abs = if (path.startsWith("/")) path else s"${Paths.projectRoot}/$path"
    val f = new File(abs)
    if (!f.exists()) return 0
    val reader = new java.io.BufferedReader(new java.io.FileReader(f))
    try {
      var count = 0
      var line = reader.readLine()
      while (line != null) {
        count += 1
        line = reader.readLine()
      }
      count
    } finally reader.close()
  }

  private def extractScalaMethods(path: String): List[String] = {
    val abs = if (path.startsWith("/")) path else s"${Paths.projectRoot}/$path"
    val f = new File(abs)
    if (!f.exists()) return Nil
    ssgdev.compare.Methods.extractScalaMethods(abs)
  }

  private def parseCovenantHeader(path: String): Option[Covenant.Header] =
    Covenant.parse(path)

  private def nowIso(): String = {
    val now = java.time.Instant.now()
    now.toString
  }

  // --- Usage -----------------------------------------------------------------

  private def printUsage(): Unit = {
    println("""Usage: ssg-dev port <command>
              |
              |Commands:
              |  list [--status S] [--assignee A]    List tasks in the registry
              |  next [--assignee A]                 Show next pending task
              |  baseline <id>                       Record pre-port snapshot
              |  verify <id>                         Run all sub-checks, print OK or FAIL
              |  done <id>                           Mark done (verify must be OK)
              |  blocker <id> <reason>               Mark blocked, terminate session
              |  note <id> <text>                    Add free-text note
              |  snapshot                            Refresh sass-spec baseline
              |  covenant verify [--file F | --staged]
              |                                      Re-verify covenanted file(s)
              |
              |The single command an executing agent must run is:
              |  ssg-dev port verify <id>
              |
              |`verify` runs four sub-checks and prints OK only if all four pass:
              |  1. shortcuts:    zero markers in the target file
              |  2. methods:      every dart method has a Scala analogue and AST node count >= 70%
              |  3. spec subdir:  spec/<subdir>/ at 100% strict-pass
              |  4. regression:   no pass->fail transitions vs the sass-spec baseline
              |
              |Task registry: scripts/data/port-tasks.tsv
              |Spec baseline: scripts/data/sass-spec-baseline.tsv""".stripMargin)
  }
}
