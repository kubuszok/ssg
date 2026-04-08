package ssgdev
package testing

/** Test orchestration commands. */
object TestCmd {

  def run(args: List[String]): Unit = {
    args match {
      case Nil | "--help" :: _ =>
        printUsage()
      case "unit" :: rest => unit(Cli.parse(rest))
      case "verify" :: _ => verify()
      case "sass-spec" :: rest => sassSpec(Cli.parse(rest))
      case other :: _ =>
        Term.err(s"Unknown test command: $other")
        sys.exit(1)
    }
  }

  /** Phase 1 wrapper around the hardened SassSpecRunner. */
  private def sassSpec(args: Cli.Args): Unit = {
    val subdir = args.flag("subdir")
    val baseline = args.flag("baseline")
    val strict = args.hasFlag("strict")
    val snapshot = args.hasFlag("snapshot")

    val outcome =
      if (snapshot) SassSpec.runSnapshot()
      else if (subdir.isDefined) SassSpec.runSubdir(subdir.get)
      else if (strict) SassSpec.runStrict()
      else if (baseline.isDefined) SassSpec.runRegression()  // baseline file is read from default
      else SassSpec.runRegression()

    if (outcome.ok) {
      Term.ok(s"sass-spec: ${outcome.passCount}/${outcome.total} passing")
    } else {
      Term.err(s"sass-spec FAILED")
      outcome.details.foreach(d => println(s"  $d"))
      sys.exit(1)
    }
  }

  private def unit(args: Cli.Args): Unit = {
    val module = args.flagOrDefault("module", "ssg-md")
    val jvm = args.hasFlag("jvm")
    val js = args.hasFlag("js")
    val native = args.hasFlag("native")
    val all = args.hasFlag("all") || (!jvm && !js && !native)
    val only = args.flag("only")

    val targets = scala.collection.mutable.ListBuffer.empty[String]
    if (all || jvm) targets += module
    if (all || js) targets += s"${module}JS"
    if (all || native) targets += s"${module}Native"

    for (target <- targets) {
      val cmd = only match {
        case Some(suite) => s"$target/testOnly *$suite*"
        case None => s"$target/test"
      }
      Term.info(s"Testing $target...")
      val exit = Proc.exec("sbt", List("--client", cmd), cwd = Some(Paths.projectRoot))
      if (exit != 0) {
        Term.err(s"Tests failed for $target")
        sys.exit(exit)
      }
    }
    Term.ok("All tests passed")
  }

  private def verify(): Unit = {
    Term.info("Verifying all modules on all platforms...")
    val modules = List("ssg-md", "ssg-liquid", "ssg-sass", "ssg-minify", "ssg-js", "ssg")
    for (module <- modules) {
      for (suffix <- List("", "JS", "Native")) {
        val target = s"$module$suffix"
        Term.info(s"Compiling $target...")
        val exit = Proc.exec("sbt", List("--client", s"$target/compile"), cwd = Some(Paths.projectRoot))
        if (exit != 0) {
          Term.err(s"Verification failed for $target")
          sys.exit(exit)
        }
      }
    }
    Term.ok("All modules compile on all platforms")
  }

  private def printUsage(): Unit = {
    println("""Usage: ssg-dev test <command>
              |
              |Commands:
              |  unit [--jvm] [--js] [--native] [--all] [--module M] [--only SUITE]
              |  verify               Compile all modules on all platforms
              |  sass-spec [--subdir P] [--baseline F] [--snapshot] [--strict]
              |                       Run the sass-spec compliance runner.
              |                       --subdir: require 100% strict-pass in subdir
              |                       --snapshot: rewrite scripts/data/sass-spec-baseline.tsv
              |                       --strict: fail on any leak-category result""".stripMargin)
  }
}
