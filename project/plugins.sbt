// kubuszok plugin (bundles: sbt-scalafmt, sbt-scoverage, sbt-projectmatrix [merged into sbt 2.0], sbt-scalajs, sbt-scala-native, sbt-commandmatrix, and more)
addSbtPlugin("com.kubuszok" % "sbt-kubuszok" % "0.2.3")
// native library providers (auto-configures Scala Native linker from sn-provider.json) + shared multiarch resources
addSbtPlugin("com.kubuszok" % "sbt-multiarch-scala" % "0.3.0")
ThisBuild / libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always
