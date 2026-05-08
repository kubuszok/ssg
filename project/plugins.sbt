// linters
addSbtPlugin("org.scalameta"    % "sbt-scalafmt"       % "2.5.4")
addSbtPlugin("ch.epfl.scala"    % "sbt-scalafix"       % "0.14.6")
// coverage
addSbtPlugin("org.scoverage"    % "sbt-scoverage"      % "2.4.4")
// cross-compilation
addSbtPlugin("com.eed3si9n"     % "sbt-projectmatrix"  % "0.11.0")
addSbtPlugin("org.scala-js"     % "sbt-scalajs"        % "1.21.0")
addSbtPlugin("org.scala-native" % "sbt-scala-native"   % "0.5.10")
// native library providers (auto-configures Scala Native linker from sn-provider.json)
addSbtPlugin("com.kubuszok"     % "sbt-multiarch-scala" % "1.0-0.1.2-3-gba7ffe4-SNAPSHOT")
ThisBuild / libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always
// Sonatype snapshots for sbt-multiarch-scala
ThisBuild / resolvers += "Maven Central Snapshots" at "https://central.sonatype.com/repository/maven-snapshots"
