import sbt.*
import sbt.Keys.*

object SsgSettings {

  val scalaVersion = "3.8.2"

  object versions {
    val munit           = "1.2.3"
    val munitScalacheck = "1.0.0"
  }

  val commonSettings: Seq[Setting[?]] = Seq(
    Keys.scalaVersion := scalaVersion,
    scalacOptions ++= Seq(
      "-deprecation",
      "-feature",
      "-no-indent",
      "-Werror",
      "-Wimplausible-patterns",
      "-Wrecurse-with-default",
      "-Wenum-comment-discard",
      "-Wunused:imports,privates,locals,patvars,nowarn",
      "-Wconf:cat=deprecation:info"
    ),
    testFrameworks += new TestFramework("munit.Framework")
  )

  val jvmSettings: Seq[Setting[?]] = Seq(
    fork := true
  )

  val jsSettings: Seq[Setting[?]] = Seq.empty

  val nativeSettings: Seq[Setting[?]] = Seq(
    scalanative.sbtplugin.ScalaNativePlugin.autoImport.nativeConfig ~= {
      _.withEmbedResources(true).withMultithreading(false) // Single-threaded: avoids thread stack limits, uses main stack
    }
  )
}
