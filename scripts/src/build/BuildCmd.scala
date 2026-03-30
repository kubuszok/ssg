package ssgdev
package build

/** Build command dispatcher. */
object BuildCmd {

  def run(args: List[String]): Unit = {
    args match {
      case Nil | "--help" :: _ =>
        printUsage()
      case "compile" :: rest => compile(Cli.parse(rest))
      case "compile-fmt" :: _ => compileFmt()
      case "fmt" :: _ => fmt()
      case "publish-local" :: rest => publishLocal(Cli.parse(rest))
      case "kill-sbt" :: _ => killSbt()
      case other :: _ =>
        Term.err(s"Unknown build command: $other")
        sys.exit(1)
    }
  }

  private def compile(args: Cli.Args): Unit = {
    val targets = resolveTargets(args)
    val errorsOnly = args.hasFlag("errors-only")
    val warnings = args.hasFlag("warnings")

    for (target <- targets) {
      val cmd = s"$target/compile"
      Term.info(s"Compiling $target...")
      val exit = if (errorsOnly) {
        // Filter to only show errors
        val result = Proc.run("sbt", List("--client", cmd), cwd = Some(Paths.projectRoot))
        val lines = result.stderr.split("\n").filter(_.contains("[error]"))
        lines.foreach(println)
        if (result.stdout.contains("[error]")) {
          result.stdout.split("\n").filter(_.contains("[error]")).foreach(println)
        }
        result.exitCode
      } else if (warnings) {
        Proc.exec("sbt", List("--client", cmd), cwd = Some(Paths.projectRoot))
      } else {
        Proc.exec("sbt", List("--client", cmd), cwd = Some(Paths.projectRoot))
      }
      if (exit != 0) {
        Term.err(s"Compilation failed for $target")
        sys.exit(exit)
      }
    }
    Term.ok("Compilation successful")
  }

  private def compileFmt(): Unit = {
    fmt()
    compile(Cli.Args(Map.empty, Nil))
  }

  private def fmt(): Unit = {
    Term.info("Formatting...")
    val exit = Proc.exec("sbt", List("--client", "scalafmtAll"), cwd = Some(Paths.projectRoot))
    if (exit != 0) {
      Term.err("Format failed")
      sys.exit(exit)
    }
    Term.ok("Format complete")
  }

  private def publishLocal(args: Cli.Args): Unit = {
    val targets = resolveTargets(args)
    for (target <- targets) {
      Term.info(s"Publishing $target locally...")
      val exit = Proc.exec("sbt", List("--client", s"$target/publishLocal"), cwd = Some(Paths.projectRoot))
      if (exit != 0) {
        Term.err(s"Publish failed for $target")
        sys.exit(exit)
      }
    }
    Term.ok("Publish local complete")
  }

  private def killSbt(): Unit = {
    Term.info("Killing sbt server...")
    val exit = Proc.exec("sbt", List("--client", "shutdown"), cwd = Some(Paths.projectRoot))
    if (exit == 0) Term.ok("sbt server stopped")
    else Term.warn("sbt server may not have been running")
  }

  /** Resolve sbt project targets from flags. */
  private def resolveTargets(args: Cli.Args): List[String] = {
    val module = args.flag("module")
    val jvm = args.hasFlag("jvm")
    val js = args.hasFlag("js")
    val native = args.hasFlag("native")
    val all = args.hasFlag("all")

    val modules = module match {
      case Some(m) => List(m)
      case None if all => List("ssg-md", "ssg-liquid", "ssg-sass", "ssg-html", "ssg")
      case None => List("ssg-md") // default to ssg-md
    }

    if (all || (!jvm && !js && !native)) {
      // All platforms: use base name (JVM default) — sbt will compile default axis
      modules
    } else {
      modules.flatMap { m =>
        val targets = scala.collection.mutable.ListBuffer.empty[String]
        if (jvm) targets += m
        if (js) targets += s"${m}JS"
        if (native) targets += s"${m}Native"
        targets.toList
      }
    }
  }

  private def printUsage(): Unit = {
    println("""Usage: ssg-dev build <command>
              |
              |Commands:
              |  compile [--jvm] [--js] [--native] [--all] [--module M] [--errors-only] [--warnings]
              |  compile-fmt         Compile after formatting
              |  fmt                 Run scalafmt
              |  publish-local [--jvm] [--js] [--native] [--all]
              |  kill-sbt            Kill sbt server""".stripMargin)
  }
}
