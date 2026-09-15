// kubuszok plugin (bundles: sbt-scalafmt, sbt-scoverage, sbt-projectmatrix [merged into sbt 2.0], sbt-scalajs, sbt-scala-native, sbt-commandmatrix, and more)
addSbtPlugin("com.kubuszok" % "sbt-kubuszok" % "0.2.3")
// native library providers (auto-configures Scala Native linker from sn-provider.json) + shared multiarch resources
addSbtPlugin("com.kubuszok" % "sbt-multiarch-scala" % "0.4.0")
ThisBuild / libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always

// Baltic Porter: the corpus module provides the migrators (LiqpMigrate, FlexmarkMigrate)
// that the sourceGenerator calls to produce ssg-liquid and ssg-md Scala sources from the
// upstream Java originals. The frontend-ts module provides the non-Java emitters
// (ParityDerive, TerserEmitter, KaTeXEmitter, MermaidEmitter, DartSassEmitter, roughjs emitters).
resolvers += "Central Portal Snapshots" at "https://central.sonatype.com/repository/maven-snapshots"
libraryDependencies += "com.kubuszok" %% "balticporter-corpus" % "9fb82906bd9ee9a30ebdb4fbdc39aea0f447ccd9-SNAPSHOT"
libraryDependencies += "com.kubuszok" %% "balticporter-frontend-ts" % "9fb82906bd9ee9a30ebdb4fbdc39aea0f447ccd9-SNAPSHOT"
