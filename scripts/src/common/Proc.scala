package ssgdev

import scala.sys.process.{Process, ProcessLogger}

/** Subprocess runner with stdout/stderr capture. */
object Proc {

  private def resolveCmd(cmd: String): String = {
    if (cmd.startsWith("/") || cmd.contains("/")) cmd
    else {
      val path = sys.env.getOrElse("PATH", "")
      val found = path.split(java.io.File.pathSeparatorChar).iterator.map { dir =>
        new java.io.File(dir, cmd)
      }.find(f => f.isFile && f.canExecute)
      found.map(_.getAbsolutePath).getOrElse(cmd)
    }
  }

  final case class Result(exitCode: Int, stdout: String, stderr: String) {
    def ok: Boolean = exitCode == 0
  }

  def run(cmd: String, args: List[String] = Nil, cwd: Option[String] = None,
          env: Map[String, String] = Map.empty): Result = {
    val cmdList = resolveCmd(cmd) :: args
    val stdoutBuf = new StringBuilder
    val stderrBuf = new StringBuilder
    val logger = ProcessLogger(
      line => { stdoutBuf.append(line); stdoutBuf.append('\n') },
      line => { stderrBuf.append(line); stderrBuf.append('\n') }
    )
    val cwdFile = cwd.map(new java.io.File(_))
    val envPairs = env.toSeq
    val exitCode = try {
      Process(cmdList, cwdFile, envPairs*).!(logger)
    } catch {
      case e: java.io.IOException =>
        stderrBuf.append(s"Failed to run: ${cmdList.mkString(" ")}: ${e.getMessage}\n")
        127
    }
    Result(exitCode, stdoutBuf.toString.stripTrailing, stderrBuf.toString.stripTrailing)
  }

  def runWithTimeout(cmd: String, args: List[String] = Nil, timeoutSec: Int = 10,
                     cwd: Option[String] = None): Result = {
    val timeoutCmd = Seq("timeout", "gtimeout").find { t =>
      val check = try { Process(List(resolveCmd("which"), t)).!(ProcessLogger(_ => (), _ => ())) } catch { case _: Exception => 1 }
      check == 0
    }
    timeoutCmd match {
      case Some(t) => run(t, timeoutSec.toString :: cmd :: args, cwd = cwd)
      case None => run(cmd, args, cwd = cwd)
    }
  }

  def spawn(cmd: String, args: List[String] = Nil, cwd: Option[String] = None,
            logFile: String = "/dev/null"): Option[Long] = {
    val cmdStr = (cmd :: args).map(a => s"'$a'").mkString(" ")
    val shell = s"$cmdStr > '$logFile' 2>&1 & echo $$!"
    val result = run("sh", List("-c", shell), cwd = cwd)
    if (result.ok) result.stdout.trim.toLongOption else None
  }

  def signalProcess(pid: Long, signal: Int = 15): Boolean = {
    run("sh", List("-c", s"kill -$signal $pid")).ok
  }

  def isAlive(pid: Long): Boolean = {
    run("sh", List("-c", s"kill -0 $pid 2>/dev/null")).ok
  }

  def exec(cmd: String, args: List[String] = Nil, cwd: Option[String] = None,
           env: Map[String, String] = Map.empty): Int = {
    val cmdList = resolveCmd(cmd) :: args
    val cwdFile = cwd.map(new java.io.File(_))
    val envPairs = env.toSeq
    try {
      Process(cmdList, cwdFile, envPairs*).!
    } catch {
      case e: java.io.IOException =>
        Term.err(s"Failed to run: ${cmdList.mkString(" ")}: ${e.getMessage}")
        127
    }
  }
}
