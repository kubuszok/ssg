package ssgdev
package git

/** Git and GitHub CLI operations. */
object GitCmd {

  def run(args: List[String]): Unit = {
    args match {
      case Nil | "--help" :: _ =>
        printUsage()
      // Read-only git
      case "status" :: rest => gitPass("status" :: rest)
      case "diff" :: rest => gitDiff(rest)
      case "diff-staged" :: rest => gitPass("diff" :: "--cached" :: rest)
      case "diff-stat" :: rest => gitPass("diff" :: "--stat" :: rest)
      case "diff-count" :: _ => diffCount()
      case "log" :: rest => gitLog(rest)
      case "log-full" :: rest => gitLogFull(rest)
      case "show" :: rest => gitPass("show" :: rest)
      case "branch" :: rest => gitPass("branch" :: rest)
      case "blame" :: rest => gitPass("blame" :: rest)
      case "tags" :: _ => gitPass(List("tag", "-l"))
      // Write operations
      case "stage" :: rest => gitPass("add" :: rest)
      case "stage-all" :: _ => gitPass(List("add", "-A"))
      case "commit" :: rest => gitCommit(rest)
      case "push" :: rest => gitPass("push" :: rest)
      // GitHub
      case "gh" :: rest => gh(rest)
      case other :: _ =>
        Term.err(s"Unknown git command: $other")
        sys.exit(1)
    }
  }

  private def gitPass(args: List[String]): Unit = {
    val exit = Proc.exec("git", args, cwd = Some(Paths.projectRoot))
    if (exit != 0) sys.exit(exit)
  }

  private def gitDiff(rest: List[String]): Unit = {
    val args = if (rest.isEmpty) List("diff") else "diff" :: rest
    gitPass(args)
  }

  private def gitLog(rest: List[String]): Unit = {
    val n = rest.headOption.filter(_.startsWith("-n")).map(_.drop(2)).getOrElse("20")
    gitPass(List("log", s"--oneline", s"-n", n))
  }

  private def gitLogFull(rest: List[String]): Unit = {
    val args = Cli.parse(rest)
    val n = args.flagOrDefault("n", "10")
    gitPass(List("log", "--stat", s"-n", n))
  }

  private def diffCount(): Unit = {
    val result = Proc.run("git", List("diff", "--stat"), cwd = Some(Paths.projectRoot))
    if (result.ok) {
      val lines = result.stdout.split("\n")
      if (lines.nonEmpty) println(lines.last)
      else println("No changes")
    }
  }

  private def gitCommit(rest: List[String]): Unit = {
    val args = Cli.parse(rest)
    val message = args.flag("m").orElse(args.flag("message"))
    message match {
      case Some(msg) => gitPass(List("commit", "-m", msg))
      case None =>
        Term.err("Commit message required: ssg-dev git commit --m 'message'")
        sys.exit(1)
    }
  }

  private def gh(rest: List[String]): Unit = {
    rest match {
      case Nil | "--help" :: _ =>
        println("""Usage: ssg-dev git gh <command>
                  |
                  |Commands:
                  |  pr list/view/diff/checks/comments
                  |  issue list/view
                  |  run list/view/log
                  |  api <endpoint>""".stripMargin)
      case "pr" :: sub :: rest2 => ghExec("pr" :: sub :: rest2)
      case "issue" :: sub :: rest2 => ghExec("issue" :: sub :: rest2)
      case "run" :: sub :: rest2 => ghExec("run" :: sub :: rest2)
      case "api" :: rest2 => ghExec("api" :: rest2)
      case other :: _ =>
        Term.err(s"Unknown gh command: $other")
        sys.exit(1)
    }
  }

  private def ghExec(args: List[String]): Unit = {
    val exit = Proc.exec("gh", args, cwd = Some(Paths.projectRoot))
    if (exit != 0) sys.exit(exit)
  }

  private def printUsage(): Unit = {
    println("""Usage: ssg-dev git <command>
              |
              |Read-only:
              |  status, diff, diff-staged, diff-stat, diff-count
              |  log [-n N], log-full [-n N], show, branch, blame, tags
              |
              |Write:
              |  stage <files>, stage-all, commit --m 'msg', push
              |
              |GitHub:
              |  gh pr list/view/diff/checks/comments
              |  gh issue list/view
              |  gh run list/view/log
              |  gh api <endpoint>""".stripMargin)
  }
}
