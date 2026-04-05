package ssgdev

import java.io.File

/** Project path resolution. */
object Paths {

  /** Find the project root by walking up to find build.sbt + ssg-md/. */
  lazy val projectRoot: String = {
    var dir = new File(System.getProperty("user.dir"))
    var found: String = System.getProperty("user.dir")
    var searching = true
    while (dir != null && searching) {
      val buildSbt = new File(dir, "build.sbt")
      val ssgMd = new File(dir, "ssg-md")
      if (buildSbt.exists() && ssgMd.exists()) {
        found = dir.getAbsolutePath
        searching = false
      } else {
        dir = dir.getParentFile
      }
    }
    found
  }

  // Original source trees
  def flexmarkSrc: String = s"$projectRoot/original-src/flexmark-java"
  def liqpSrc: String = s"$projectRoot/original-src/liqp"
  def dartSassSrc: String = s"$projectRoot/original-src/dart-sass"
  def jekyllMinifierSrc: String = s"$projectRoot/original-src/jekyll-minifier"

  // SSG module sources
  def ssgMdSrc: String = s"$projectRoot/ssg-md/src/main/scala/ssg/md"
  def ssgLiquidSrc: String = s"$projectRoot/ssg-liquid/src/main/scala/ssg/liquid"
  def ssgSassSrc: String = s"$projectRoot/ssg-sass/src/main/scala/ssg/sass"
  def ssgMinifySrc: String = s"$projectRoot/ssg-minify/src/main/scala/ssg/minify"
  def ssgJsSrc: String = s"$projectRoot/ssg-js/src/main/scala/ssg/js"

  /** All SSG module source directories. */
  def allSsgSrcDirs: List[String] = List(ssgMdSrc, ssgLiquidSrc, ssgSassSrc, ssgMinifySrc, ssgJsSrc)

  /** Resolve original source root by library name. */
  def originalSrc(lib: String): String = lib match {
    case "flexmark" => flexmarkSrc
    case "liqp" => liqpSrc
    case "dart-sass" => dartSassSrc
    case "jekyll-minifier" => jekyllMinifierSrc
    case "terser" => s"$projectRoot/original-src/terser"
    case other => throw new IllegalArgumentException(s"Unknown library: $other")
  }

  /** Resolve SSG module source root by module name. */
  def moduleSrc(module: String): String = module match {
    case "ssg-md" => ssgMdSrc
    case "ssg-liquid" => ssgLiquidSrc
    case "ssg-sass" => ssgSassSrc
    case "ssg-minify" => ssgMinifySrc
    case "ssg-js" => ssgJsSrc
    case other => throw new IllegalArgumentException(s"Unknown module: $other")
  }

  def dataDir: String = s"$projectRoot/scripts/data"
  def migrationTsv: String = s"$dataDir/migration.tsv"
  def issuesTsv: String = s"$dataDir/issues.tsv"
  def auditTsv: String = s"$dataDir/audit.tsv"
  def scriptsDir: String = s"$projectRoot/scripts"
  def docsDir: String = s"$projectRoot/docs"
}
