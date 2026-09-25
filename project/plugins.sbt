// sbt 2's Bazel-compatible remote cache client. Inert unless `Global / remoteCache` names an
// endpoint — project/RemoteCacheSetup.scala (on only when a BuildBuddy API key is available).
addRemoteCachePlugin

// kubuszok plugin (bundles: sbt-scalafmt, sbt-scoverage, sbt-projectmatrix [merged into sbt 2.0], sbt-scalajs, sbt-scala-native, sbt-commandmatrix, and more)
addSbtPlugin("com.kubuszok" % "sbt-kubuszok" % "0.2.3")
// native library providers (auto-configures Scala Native linker from sn-provider.json) + shared multiarch resources
addSbtPlugin("com.kubuszok" % "sbt-multiarch-scala" % "0.4.0")
ThisBuild / libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always

// Baltic Porter: the generic porting ENGINE only. How liqp and flexmark are ported is ssg's own —
// the configurations and injected sources under `ssg-liquid/port` and `ssg-md/port`, run by
// project/BalticPorterGen.scala. The frontend-ts module provides the non-Java emitters.
resolvers += "Central Portal Snapshots" at "https://central.sonatype.com/repository/maven-snapshots"
libraryDependencies += "com.kubuszok" %% "balticporter-engine" % "61c1deb04ae429cf0cd8ce600a9676e0ab810694-SNAPSHOT"
libraryDependencies += "com.kubuszok" %% "balticporter-frontend-ts" % "61c1deb04ae429cf0cd8ce600a9676e0ab810694-SNAPSHOT"
