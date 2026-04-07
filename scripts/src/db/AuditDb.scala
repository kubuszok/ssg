package ssgdev
package db

import java.io.File
import java.time.LocalDate

/** Audit results database operations. */
object AuditDb {

  private val headers = List("file_path", "package", "status", "tested", "last_audited", "notes", "source")

  def run(args: List[String]): Unit = {
    args match {
      case Nil | "--help" :: _ =>
        println("""Usage: ssg-dev db audit <command>
                  |
                  |Commands:
                  |  list [--status S] [--package P] [--tested T] [--limit N] [--offset N]
                  |  get <file_path>
                  |  set <file_path> --status S [--tested T] [--notes TEXT]
                  |  stats      Summary counts""".stripMargin)
      case "list" :: rest => list(Cli.parse(rest))
      case "get" :: rest => get(Cli.parse(rest))
      case "set" :: rest => set(Cli.parse(rest))
      case "stats" :: _ => stats()
      case other :: _ =>
        Term.err(s"Unknown audit command: $other")
        sys.exit(1)
    }
  }

  def list(args: Cli.Args): Unit = {
    var table = load()
    args.flag("status").foreach(s => table = table.filter(_.getOrElse("status", "") == s))
    args.flag("package").foreach(p => table = table.filter(_.getOrElse("package", "").contains(p)))
    args.flag("tested").foreach(t => table = table.filter(_.getOrElse("tested", "") == t))
    table = table.paginate(
      args.flag("limit").map(_.toInt),
      args.flag("offset").map(_.toInt)
    )
    printTable(table)
  }

  def get(args: Cli.Args): Unit = {
    val path = args.requirePositional(0, "file_path")
    val table = load()
    table.find(r => r.getOrElse("file_path", "").contains(path)) match {
      case Some(row) =>
        headers.foreach(h => println(s"  $h: ${row.getOrElse(h, "")}"))
      case None =>
        Term.err(s"Not found: $path")
        sys.exit(1)
    }
  }

  def set(args: Cli.Args): Unit = {
    val path = args.requirePositional(0, "file_path")
    val updates = scala.collection.mutable.Map.empty[String, String]
    args.flag("status").foreach(s => updates("status") = s)
    args.flag("tested").foreach(t => updates("tested") = t)
    args.flag("notes").foreach(n => updates("notes") = n)
    updates("last_audited") = LocalDate.now().toString
    val updatesMap = updates.toMap

    Tsv.modify(Paths.auditTsv) { loaded =>
      val table = if (loaded.headers.isEmpty) Tsv.Table(headers, Nil, List("# SSG Audit Database")) else loaded
      val found = table.rows.exists(_.getOrElse("file_path", "") == path)
      if (found) {
        table.updateRow(_.getOrElse("file_path", "") == path, updatesMap)
      } else {
        val pkg = path.split("/").dropRight(1).lastOption.getOrElse("")
        val row = Map(
          "file_path" -> path,
          "package" -> pkg,
          "status" -> updatesMap.getOrElse("status", "pass"),
          "tested" -> updatesMap.getOrElse("tested", "no"),
          "last_audited" -> updatesMap.getOrElse("last_audited", LocalDate.now().toString),
          "notes" -> updatesMap.getOrElse("notes", "")
        )
        table.addRow(row)
      }
    }
    Term.ok(s"Updated: $path")
  }

  def stats(): Unit = {
    val table = load()
    println("=== Audit Summary ===")
    val byStatus = table.stats("status").toList.sortBy(-_._2)
    byStatus.foreach { case (s, c) => println(f"  $s%-20s $c%d") }
    println(f"  ${"Total"}%-20s ${table.size}%d")
    println()
    println("=== By Package ===")
    val byPkg = table.stats("package").toList.sortBy(_._1)
    byPkg.foreach { case (pkg, c) => println(f"  $pkg%-30s $c%d") }
    println()
    println("=== Test Coverage ===")
    val byTested = table.stats("tested").toList.sortBy(-_._2)
    byTested.foreach { case (t, c) => println(f"  $t%-15s $c%d") }
  }

  private def load(): Tsv.Table = {
    val path = Paths.auditTsv
    if (new File(path).exists()) Tsv.read(path)
    else Tsv.Table(headers, Nil, List("# SSG Audit Database"))
  }

  private def save(table: Tsv.Table): Unit = {
    Tsv.write(Paths.auditTsv, table)
  }

  private def printTable(table: Tsv.Table): Unit = {
    if (table.rows.isEmpty) { println("(no results)"); return }
    val display = List("file_path", "package", "status", "tested", "notes")
    println(Term.table(display, table.rows.map(r => display.map(h => r.getOrElse(h, "")))))
  }
}
