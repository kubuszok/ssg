// kubuszok plugin (bundles: sbt-scalafmt, sbt-scoverage, sbt-projectmatrix, sbt-scalajs, sbt-scala-native, and more)
addSbtPlugin("com.kubuszok" % "sbt-kubuszok" % "0.2.0")
// linters
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.14.6")
// native library providers (auto-configures Scala Native linker from sn-provider.json)
addSbtPlugin("com.kubuszok" % "sbt-multiarch-scala" % "0.2.0")
ThisBuild / libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always
