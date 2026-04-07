package ssgdev
package db

import java.io.File
import java.time.LocalDate

/** Issues tracking database operations. */
object IssuesDb {

  private val headers = List("id", "file_path", "category", "status", "severity", "description", "last_updated", "source")

  def run(args: List[String]): Unit = {
    args match {
      case Nil | "--help" :: _ =>
        println("""Usage: ssg-dev db issues <command>
                  |
                  |Commands:
                  |  list [--status S] [--category C] [--file PATH] [--severity SEV] [--limit N] [--offset N]
                  |  add <file> <category> <severity> <description>
                  |  resolve <id> [--notes TEXT]
                  |  stats      Summary counts""".stripMargin)
      case "list" :: rest => list(Cli.parse(rest))
      case "add" :: rest => add(Cli.parse(rest))
      case "resolve" :: rest => resolve(Cli.parse(rest))
      case "stats" :: _ => stats()
      case other :: _ =>
        Term.err(s"Unknown issues command: $other")
        sys.exit(1)
    }
  }

  def list(args: Cli.Args): Unit = {
    var table = load()
    args.flag("status").foreach(s => table = table.filter(_.getOrElse("status", "") == s))
    args.flag("category").foreach(c => table = table.filter(_.getOrElse("category", "").contains(c)))
    args.flag("file").foreach(f => table = table.filter(_.getOrElse("file_path", "").contains(f)))
    args.flag("severity").foreach(s => table = table.filter(_.getOrElse("severity", "") == s))
    table = table.paginate(
      args.flag("limit").map(_.toInt),
      args.flag("offset").map(_.toInt)
    )
    printTable(table)
  }

  def add(args: Cli.Args): Unit = {
    val filePath = args.requirePositional(0, "file_path")
    val category = args.requirePositional(1, "category")
    val severity = args.requirePositional(2, "severity")
    val description = args.requirePositional(3, "description")

    var assignedId: String = ""
    Tsv.modify(Paths.issuesTsv) { loaded =>
      val table = if (loaded.headers.isEmpty) Tsv.Table(headers, Nil, List("# SSG Issues Database")) else loaded
      val nextId = {
        val existing = table.rows.flatMap(_.get("id")).filter(_.startsWith("ISS-"))
        if (existing.isEmpty) 1
        else existing.map(_.stripPrefix("ISS-").toIntOption.getOrElse(0)).max + 1
      }
      assignedId = f"ISS-$nextId%03d"
      val row = Map(
        "id" -> assignedId,
        "file_path" -> filePath,
        "category" -> category,
        "status" -> "open",
        "severity" -> severity,
        "description" -> description,
        "last_updated" -> LocalDate.now().toString,
        "source" -> "manual"
      )
      table.addRow(row)
    }
    Term.ok(s"Added: $assignedId — $description")
  }

  def resolve(args: Cli.Args): Unit = {
    val id = args.requirePositional(0, "id")
    val updates = scala.collection.mutable.Map[String, String](
      "status" -> "resolved",
      "last_updated" -> LocalDate.now().toString
    )
    args.flag("notes").foreach(n => updates("description") = updates.getOrElse("description", "") + " — " + n)
    val updatesMap = updates.toMap

    var notFound = false
    Tsv.modify(Paths.issuesTsv) { table =>
      val found = table.rows.exists(_.getOrElse("id", "") == id)
      if (!found) {
        notFound = true
        table
      } else {
        table.updateRow(_.getOrElse("id", "") == id, updatesMap)
      }
    }
    if (notFound) {
      Term.err(s"Not found: $id")
      sys.exit(1)
    }
    Term.ok(s"Resolved: $id")
  }

  def stats(): Unit = {
    val table = load()
    println("=== Issues Summary ===")
    val byStatus = table.stats("status").toList.sortBy(-_._2)
    byStatus.foreach { case (s, c) => println(f"  $s%-20s $c%d") }
    println(f"  ${"Total"}%-20s ${table.size}%d")
    println()
    println("=== By Category ===")
    val byCat = table.stats("category").toList.sortBy(-_._2)
    byCat.foreach { case (cat, c) => println(f"  $cat%-25s $c%d") }
    println()
    println("=== By Severity ===")
    val bySev = table.stats("severity").toList.sortBy(-_._2)
    bySev.foreach { case (sev, c) => println(f"  $sev%-15s $c%d") }
  }

  private def load(): Tsv.Table = {
    val path = Paths.issuesTsv
    if (new File(path).exists()) Tsv.read(path)
    else Tsv.Table(headers, Nil, List("# SSG Issues Database"))
  }

  private def save(table: Tsv.Table): Unit = {
    Tsv.write(Paths.issuesTsv, table)
  }

  private def printTable(table: Tsv.Table): Unit = {
    if (table.rows.isEmpty) { println("(no results)"); return }
    val display = List("id", "file_path", "category", "status", "severity", "description")
    println(Term.table(display, table.rows.map(r => display.map(h => r.getOrElse(h, "")))))
  }
}
