//> using scala 3.8.2
//> using platform scala-native
//> using options -deprecation -feature -no-indent -Werror
//> using dep io.github.cquiroz::scala-java-time::2.6.0

package ssgdev

object Main {

  private val version = "0.1.0"

  def main(args: Array[String]): Unit = {
    args.toList match {
      case Nil | "--help" :: _ | "-h" :: _ =>
        printUsage()
      case "--version" :: _ =>
        println(s"ssg-dev $version")
      case "hook" :: rest =>
        hook.HookCmd.run(rest)
      case "db" :: rest =>
        db.DbCmd.run(rest)
      case "git" :: rest =>
        git.GitCmd.run(rest)
      case "build" :: rest =>
        build.BuildCmd.run(rest)
      case "quality" :: rest =>
        quality.QualityCmd.run(rest)
      case "test" :: rest =>
        testing.TestCmd.run(rest)
      case "compare" :: rest =>
        compare.CompareCmd.run(rest)
      case "proc" :: rest =>
        proc.ProcCmd.run(rest)
      case "port" :: rest =>
        port.PortCmd.run(rest)
      case unknown :: _ =>
        Term.err(s"Unknown command: $unknown")
        printUsage()
        sys.exit(1)
    }
  }

  private def printUsage(): Unit = {
    println(s"""ssg-dev $version — SSG development toolkit
               |
               |Usage: ssg-dev <command> [args...]
               |
               |Commands:
               |  hook       PreToolUse validator (reads JSON from stdin)
               |  db         Database queries (migration, issues, audit)
               |  git        Git and GitHub operations
               |  build      Build commands (compile, fmt, publish-local)
               |  quality    Quality scans (grep, count, scalafix)
               |  test       Test orchestration (unit, verify)
               |  compare    Original/SSG file comparison
               |  proc       Process listing and killing (project-scoped)
               |  port       Porting task registry + verification (Phase 1+)
               |
               |Options:
               |  --help     Show this help
               |  --version  Show version""".stripMargin)
  }
}
