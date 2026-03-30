package ssgdev
package proc

/** Process management commands. */
object ProcCmd {

  def run(args: List[String]): Unit = {
    args match {
      case Nil | "--help" :: _ =>
        println("""Usage: ssg-dev proc <command>
                  |
                  |Commands:
                  |  list       List project-related processes
                  |  kill       Kill all project processes (sbt, metals)
                  |  kill-sbt   Kill sbt server only""".stripMargin)
      case "list" :: _ => list()
      case "kill" :: _ => killAll()
      case "kill-sbt" :: _ => killSbt()
      case other :: _ =>
        Term.err(s"Unknown proc command: $other")
        sys.exit(1)
    }
  }

  private def list(): Unit = {
    val result = Proc.run("sh", List("-c",
      "ps aux | grep -E '(sbt|metals|bloop|scala-cli)' | grep -v grep"))
    if (result.ok && result.stdout.trim.nonEmpty) {
      println("Project-related processes:")
      result.stdout.split("\n").foreach(line => println(s"  $line"))
    } else {
      println("No project-related processes found")
    }
  }

  private def killAll(): Unit = {
    killSbt()
    // Also try to kill metals/bloop
    val result = Proc.run("sh", List("-c",
      s"ps aux | grep -E '(metals|bloop)' | grep '${Paths.projectRoot}' | grep -v grep | awk '{print $$2}'"))
    if (result.ok && result.stdout.trim.nonEmpty) {
      val pids = result.stdout.trim.split("\n")
      for (pid <- pids) {
        pid.toLongOption.foreach { p =>
          Proc.signalProcess(p)
          Term.info(s"Sent SIGTERM to PID $p")
        }
      }
    }
    Term.ok("Kill signals sent")
  }

  private def killSbt(): Unit = {
    Term.info("Stopping sbt server...")
    val result = Proc.runWithTimeout("sbt", List("--client", "shutdown"), timeoutSec = 5,
      cwd = Some(Paths.projectRoot))
    if (result.ok) {
      Term.ok("sbt server stopped")
    } else {
      // Force kill sbt processes for this project
      val psResult = Proc.run("sh", List("-c",
        s"ps aux | grep sbt | grep '${Paths.projectRoot}' | grep -v grep | awk '{print $$2}'"))
      if (psResult.ok && psResult.stdout.trim.nonEmpty) {
        val pids = psResult.stdout.trim.split("\n")
        for (pid <- pids) {
          pid.toLongOption.foreach(Proc.signalProcess(_))
        }
        Term.ok(s"Force-killed ${pids.length} sbt process(es)")
      } else {
        Term.warn("No sbt processes found")
      }
    }
  }
}
