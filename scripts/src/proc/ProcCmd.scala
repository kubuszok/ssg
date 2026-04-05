package ssgdev
package proc

import java.io.File

/** Process management commands. */
object ProcCmd {

  def run(args: List[String]): Unit = {
    args match {
      case Nil | "--help" :: _ =>
        println("""Usage: ssg-dev proc <command>
                  |
                  |Commands:
                  |  list       List project-related processes
                  |  kill       Kill all project processes (sbt, forked JVMs)
                  |  kill-sbt   Kill sbt server and forked test JVMs""".stripMargin)
      case "list" :: _ => list()
      case "kill" :: _ => killAll()
      case "kill-sbt" :: _ => killSbt()
      case other :: _ =>
        Term.err(s"Unknown proc command: $other")
        sys.exit(1)
    }
  }

  private final case class ProcessInfo(pid: String, procType: String, cmdSummary: String)

  private def findProcesses(pattern: String, dirFilter: String): List[ProcessInfo] = {
    val result = Proc.run("ps", List("aux"))
    if (!result.ok) {
      Nil
    } else {
      result.stdout.linesIterator.filter { line =>
        line.contains(pattern) &&
        (dirFilter.isEmpty || line.contains(dirFilter)) &&
        !line.contains("grep") &&
        !line.contains("ssg-dev proc")
      }.flatMap { line =>
        val parts = line.trim.split("\\s+", 11)
        if (parts.length >= 11) {
          val pid = parts(1)
          val cmd = parts(10)
          val procType = pattern match {
            case "sbt" if cmd.contains("ForkMain") => "sbt-fork"
            case "sbt" => "sbt"
            case "sbt.ForkMain" => "sbt-fork"
            case "scala-cli" => "scala-cli"
            case _ => "unknown"
          }
          val summary = if (cmd.length > 60) cmd.take(57) + "..." else cmd
          Some(ProcessInfo(pid, procType, summary))
        } else {
          None
        }
      }.toList
    }
  }

  private def cleanSbtSocket(): Unit = {
    val socketDir = new File(s"${System.getProperty("user.home")}/.sbt/1.0/server")
    if (socketDir.exists() && socketDir.isDirectory) {
      val subdirs = socketDir.listFiles()
      if (subdirs != null) {
        subdirs.foreach { subdir =>
          val sock = new File(subdir, "sock")
          if (sock.exists()) {
            sock.delete()
          }
        }
      }
    }
  }

  private def list(): Unit = {
    val projectDir = Paths.projectRoot
    Term.info(s"Processes related to: $projectDir")
    println()

    val sbtProcs = findProcesses("sbt", projectDir)
    val forkProcs = findProcesses("sbt.ForkMain", "")
    val scalaCliProcs = findProcesses("scala-cli", projectDir)

    val allProcs = sbtProcs ++ forkProcs ++ scalaCliProcs
    if (allProcs.isEmpty) {
      println("  No project processes found.")
    } else {
      println(f"  ${"PID"}%-8s ${"TYPE"}%-15s ${"COMMAND"}%s")
      println(f"  ${"---"}%-8s ${"----"}%-15s ${"-------"}%s")
      allProcs.foreach { p =>
        println(f"  ${p.pid}%-8s ${p.procType}%-15s ${p.cmdSummary}%s")
      }
      println()
      println(s"  Total: ${allProcs.size} processes")
    }
  }

  private def killAll(): Unit = {
    val projectDir = Paths.projectRoot
    val sbtProcs = findProcesses("sbt", projectDir)
    val forkProcs = findProcesses("sbt.ForkMain", "")

    val allProcs = sbtProcs ++ forkProcs
    if (allProcs.nonEmpty) {
      allProcs.foreach { p =>
        Term.info(s"Killing ${p.procType} (pid ${p.pid})")
        Proc.run("kill", List("-9", p.pid))
      }
      Term.ok(s"Killed ${allProcs.size} processes")
    }
    // Always clean socket — stale sockets cause --client to hang
    cleanSbtSocket()
    Term.ok("Cleaned sbt server sockets")
  }

  private def killSbt(): Unit = {
    Term.info("Shutting down sbt server...")
    val result = Proc.runWithTimeout("sbt", List("--client", "shutdown"), timeoutSec = 5,
      cwd = Some(Paths.projectRoot))
    if (result.ok) {
      cleanSbtSocket()
      Term.ok("sbt server shut down gracefully")
    } else {
      Term.warn("Graceful shutdown failed, force killing...")
      val sbtProcs = findProcesses("sbt", Paths.projectRoot)
      val forkProcs = findProcesses("sbt.ForkMain", "")
      (sbtProcs ++ forkProcs).foreach { p =>
        Proc.run("kill", List("-9", p.pid))
      }
      cleanSbtSocket()
      Term.ok("sbt processes force killed")
    }
  }
}
