package ssgdev
package db

/** Database query dispatcher. */
object DbCmd {

  def run(args: List[String]): Unit = {
    args match {
      case Nil | "--help" :: _ =>
        println("""Usage: ssg-dev db <table> <command>
                  |
                  |Tables:
                  |  migration   File porting status (source_lib → SSG)
                  |  issues      Quality issues tracker
                  |  audit       Audit results per file""".stripMargin)
      case "migration" :: rest => MigrationDb.run(rest)
      case "issues" :: rest => IssuesDb.run(rest)
      case "audit" :: rest => AuditDb.run(rest)
      case other :: _ =>
        Term.err(s"Unknown table: $other")
        sys.exit(1)
    }
  }
}
