package ssg.sass

import java.util.concurrent.{Executors, TimeUnit, TimeoutException}

object DeclWiringTest {
  def main(args: Array[String]): Unit = {
    // Grab all spec test inputs from a representative sample
    val tests = List(
      // Basic patterns
      "a { b: c; }",
      "a { b: url(http://example.com/x); }",
      // Nested rules
      "a { b { c: d; } }",
      // Multiple selectors
      "a, b { c: d; }",
      // @import
      """@import "foo";""",
      """@import url("foo.css");""",
      // @use
      // Empty rule
      "a { }",
      // Comment before declaration
      "a { /* comment */ b: c; }",
      // Comment as value
      "a { b: c /* d */ e; }",
      // Semicolonless (last decl)
      "a { b: c }",
      // CSS custom property
      "a { --b: c d e; }",
      // Nested @media
      "@media screen { a { b: c; } }",
      // @keyframes
      "@keyframes x { from { a: b; } to { a: c; } }",
      // Multiline value
      "a {\n  b: c\n    d;\n}",
      // Calc
      "a { b: calc(1px + 2px); }",
      // Var
      "a { b: var(--c); }",
      // Progid (IE)
      "a { b: progid:DXImageTransform.Microsoft.gradient(startColorstr='#550000FF', endColorstr='#55FFFF00'); }",
      // Unterminated comment (ISS-252 candidate)
      "a {\n  b: c /* d\n}",
      // Splat args (number-format regression)
      "@function foo($a, $b, $c, $d) {\n  @return \"a: #{$a}, b: #{$b}, c: #{$c}, d: #{$d}\";\n}\n$list: 2, 3, 4;\n.foo {val: foo(1, $list...)}",
    )
    val exec = Executors.newSingleThreadExecutor()
    tests.foreach { t =>
      print(s"${t.take(60).padTo(60, ' ')} => ")
      val future = exec.submit(new java.util.concurrent.Callable[String] {
        def call(): String = {
          val r = Compile.compileString(t)
          s"OK: ${r.css.take(40).replace("\n", "\\n")}"
        }
      })
      try {
        val result = future.get(5, TimeUnit.SECONDS)
        println(result)
      } catch {
        case _: TimeoutException =>
          future.cancel(true)
          println("TIMEOUT (5s) — INFINITE LOOP!")
        case e: java.util.concurrent.ExecutionException =>
          println(s"${e.getCause.getClass.getSimpleName}: ${Option(e.getCause.getMessage).getOrElse("").take(60)}")
          if (e.getCause.isInstanceOf[NumberFormatException]) {
            e.getCause.printStackTrace(System.out)
          }
      }
    }
    exec.shutdownNow()
  }
}
