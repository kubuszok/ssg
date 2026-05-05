ThisBuild / organization := "dev.ssg"
ThisBuild / version      := "0.1.0-SNAPSHOT"

val treeSitterProvidersVersion = "d4426012b81520b936ef611663424f1b5936f19d-SNAPSHOT"
val multiarchCoreVersion       = "0.1.2"
ThisBuild / resolvers += "Maven Central Snapshots" at "https://central.sonatype.com/repository/maven-snapshots/"

// --- Common utilities (cross-platform abstractions) ---

val `ssg-commons` = (projectMatrix in file("ssg-commons"))
  .defaultAxes(VirtualAxis.jvm, VirtualAxis.scalaABIVersion(SsgSettings.scalaVersion))
  .settings(SsgSettings.commonSettings *)
  .settings(
    name := "ssg-commons",
    libraryDependencies ++= Seq(
      "org.scalameta" %%% "munit"            % "1.2.3" % Test,
      "org.scalameta" %%% "munit-scalacheck" % "1.0.0" % Test
    )
  )
  .jvmPlatform(scalaVersions = Seq(SsgSettings.scalaVersion), settings = SsgSettings.jvmSettings)
  .jsPlatform(scalaVersions = Seq(SsgSettings.scalaVersion), settings = SsgSettings.jsSettings)
  .nativePlatform(scalaVersions = Seq(SsgSettings.scalaVersion), settings = SsgSettings.nativeSettings)

// --- Markdown engine (flexmark-java port) ---

val `ssg-md` = (projectMatrix in file("ssg-md"))
  .defaultAxes(VirtualAxis.jvm, VirtualAxis.scalaABIVersion(SsgSettings.scalaVersion))
  .settings(SsgSettings.commonSettings *)
  .settings(
    name := "ssg-md",
    libraryDependencies ++= Seq(
      "org.scalameta" %%% "munit"            % "1.2.3" % Test,
      "org.scalameta" %%% "munit-scalacheck" % "1.0.0" % Test
    )
  )
  .dependsOn(`ssg-commons`)
  .jvmPlatform(scalaVersions = Seq(SsgSettings.scalaVersion), settings = SsgSettings.jvmSettings)
  .jsPlatform(scalaVersions = Seq(SsgSettings.scalaVersion), settings = SsgSettings.jsSettings)
  .nativePlatform(scalaVersions = Seq(SsgSettings.scalaVersion), settings = SsgSettings.nativeSettings)

// --- Liquid template engine (liqp port) ---

val `ssg-liquid` = (projectMatrix in file("ssg-liquid"))
  .defaultAxes(VirtualAxis.jvm, VirtualAxis.scalaABIVersion(SsgSettings.scalaVersion))
  .settings(SsgSettings.commonSettings *)
  .settings(
    name := "ssg-liquid",
    libraryDependencies ++= Seq(
      "io.github.cquiroz" %%% "scala-java-time"    % "2.6.0",
      "io.github.cquiroz" %%% "scala-java-locales" % "1.5.4",
      "org.scalameta"      %%% "munit"              % "1.2.3" % Test,
      "org.scalameta"      %%% "munit-scalacheck"   % "1.0.0" % Test
    )
  )
  .dependsOn(`ssg-commons`)
  .jvmPlatform(scalaVersions = Seq(SsgSettings.scalaVersion), settings = SsgSettings.jvmSettings)
  .jsPlatform(scalaVersions = Seq(SsgSettings.scalaVersion), settings = SsgSettings.jsSettings ++ Seq(
    libraryDependencies += "io.github.cquiroz" %%% "scala-java-time-tzdb" % "2.6.0"
  ))
  .nativePlatform(scalaVersions = Seq(SsgSettings.scalaVersion), settings = SsgSettings.nativeSettings ++ Seq(
    libraryDependencies += "io.github.cquiroz" %%% "scala-java-time-tzdb" % "2.6.0"
  ))

// --- SASS/SCSS compiler (dart-sass port) ---

val `ssg-sass` = (projectMatrix in file("ssg-sass"))
  .defaultAxes(VirtualAxis.jvm, VirtualAxis.scalaABIVersion(SsgSettings.scalaVersion))
  .settings(SsgSettings.commonSettings *)
  .settings(
    name := "ssg-sass",
    libraryDependencies ++= Seq(
      "org.scalameta" %%% "munit"            % "1.2.3" % Test,
      "org.scalameta" %%% "munit-scalacheck" % "1.0.0" % Test
    )
  )
  .dependsOn(`ssg-commons`)
  .jvmPlatform(scalaVersions = Seq(SsgSettings.scalaVersion), settings = SsgSettings.jvmSettings)
  .jsPlatform(scalaVersions = Seq(SsgSettings.scalaVersion), settings = SsgSettings.jsSettings)
  .nativePlatform(scalaVersions = Seq(SsgSettings.scalaVersion), settings = SsgSettings.nativeSettings)

// --- Web asset minification (jekyll-minifier port) ---

val `ssg-minify` = (projectMatrix in file("ssg-minify"))
  .defaultAxes(VirtualAxis.jvm, VirtualAxis.scalaABIVersion(SsgSettings.scalaVersion))
  .settings(SsgSettings.commonSettings *)
  .settings(
    name := "ssg-minify",
    libraryDependencies ++= Seq(
      "org.scalameta" %%% "munit"            % "1.2.3" % Test,
      "org.scalameta" %%% "munit-scalacheck" % "1.0.0" % Test
    )
  )
  .dependsOn(`ssg-commons`)
  .jvmPlatform(scalaVersions = Seq(SsgSettings.scalaVersion), settings = SsgSettings.jvmSettings)
  .jsPlatform(scalaVersions = Seq(SsgSettings.scalaVersion), settings = SsgSettings.jsSettings)
  .nativePlatform(scalaVersions = Seq(SsgSettings.scalaVersion), settings = SsgSettings.nativeSettings)

// --- JavaScript compiler/minifier (Terser port) ---

val `ssg-js` = (projectMatrix in file("ssg-js"))
  .defaultAxes(VirtualAxis.jvm, VirtualAxis.scalaABIVersion(SsgSettings.scalaVersion))
  .settings(SsgSettings.commonSettings *)
  .settings(
    name := "ssg-js",
    libraryDependencies ++= Seq(
      "org.scalameta" %%% "munit"            % "1.2.3" % Test,
      "org.scalameta" %%% "munit-scalacheck" % "1.0.0" % Test
    )
  )
  .dependsOn(`ssg-commons`)
  .jvmPlatform(scalaVersions = Seq(SsgSettings.scalaVersion), settings = SsgSettings.jvmSettings)
  .jsPlatform(scalaVersions = Seq(SsgSettings.scalaVersion), settings = SsgSettings.jsSettings)
  .nativePlatform(scalaVersions = Seq(SsgSettings.scalaVersion), settings = SsgSettings.nativeSettings)

// --- Syntax highlighting (tree-sitter) ---

val `ssg-highlight` = (projectMatrix in file("ssg-highlight"))
  .defaultAxes(VirtualAxis.jvm, VirtualAxis.scalaABIVersion(SsgSettings.scalaVersion))
  .settings(SsgSettings.commonSettings *)
  .settings(
    name := "ssg-highlight",
    libraryDependencies ++= Seq(
      "com.kubuszok" % "tree-sitter-queries" % treeSitterProvidersVersion,
      "org.scalameta" %%% "munit"            % "1.2.3" % Test,
      "org.scalameta" %%% "munit-scalacheck" % "1.0.0" % Test
    )
  )
  .dependsOn(`ssg-commons`, `ssg-md`)
  .jvmPlatform(scalaVersions = Seq(SsgSettings.scalaVersion), settings = SsgSettings.jvmSettings ++ Seq(
    libraryDependencies ++= Seq(
      "com.kubuszok" % "pnm-provider-tree-sitter-desktop" % treeSitterProvidersVersion,
      "com.kubuszok" %% "multiarch-core" % multiarchCoreVersion
    )
  ))
  .jsPlatform(scalaVersions = Seq(SsgSettings.scalaVersion), settings = SsgSettings.jsSettings ++ Seq(
    libraryDependencies += "com.kubuszok" % "wasm-provider-tree-sitter" % treeSitterProvidersVersion,
    scalaJSLinkerConfig ~= { _.withModuleKind(org.scalajs.linker.interface.ModuleKind.CommonJSModule) },
    Test / jsEnv := new org.scalajs.jsenv.nodejs.NodeJSEnv(
      org.scalajs.jsenv.nodejs.NodeJSEnv.Config()
        .withEnv(Map("TREE_SITTER_WASM_DIR" -> sys.env.getOrElse("TREE_SITTER_WASM_DIR", "/tmp/ts-wasm")))
    )
  ))
  .nativePlatform(scalaVersions = Seq(SsgSettings.scalaVersion), settings = SsgSettings.nativeSettings ++
    _root_.multiarch.sbt.NativeProviderPlugin.projectSettings ++ Seq(
    libraryDependencies += "com.kubuszok" % "sn-provider-tree-sitter" % treeSitterProvidersVersion,
    scalanative.sbtplugin.ScalaNativePlugin.autoImport.nativeConfig := {
      val c = scalanative.sbtplugin.ScalaNativePlugin.autoImport.nativeConfig.value
      val cppLib = if (System.getProperty("os.name", "").toLowerCase.contains("mac")) "-lc++" else "-lstdc++"
      c.withEmbedResources(true)
        .withResourceIncludePatterns(Seq("**.scm"))
        .withLinkingOptions(c.linkingOptions ++ Seq(cppLib))
    }
  ))

// --- Aggregator module ---

val ssg = (projectMatrix in file("ssg"))
  .defaultAxes(VirtualAxis.jvm, VirtualAxis.scalaABIVersion(SsgSettings.scalaVersion))
  .settings(SsgSettings.commonSettings *)
  .settings(
    name := "ssg",
    libraryDependencies ++= Seq(
      "org.scalameta" %%% "munit"            % "1.2.3" % Test,
      "org.scalameta" %%% "munit-scalacheck" % "1.0.0" % Test
    )
  )
  .dependsOn(`ssg-commons`, `ssg-md`, `ssg-liquid`, `ssg-sass`, `ssg-minify`, `ssg-js`, `ssg-highlight`)
  .jvmPlatform(scalaVersions = Seq(SsgSettings.scalaVersion), settings = SsgSettings.jvmSettings)
  .jsPlatform(scalaVersions = Seq(SsgSettings.scalaVersion), settings = SsgSettings.jsSettings)
  .nativePlatform(scalaVersions = Seq(SsgSettings.scalaVersion), settings = SsgSettings.nativeSettings)

// ── Test aggregation aliases ─────────────────────────────────────────

addCommandAlias("test-jvm",
  List(
    "ssg-md/test", "ssg-liquid/test", "ssg-sass/test",
    "ssg-minify/test", "ssg-js/test", "ssg-highlight/test"
  ).mkString("; ")
)

addCommandAlias("test-js",
  List(
    "ssg-mdJS/test", "ssg-liquidJS/test", "ssg-sassJS/test",
    "ssg-minifyJS/test", "ssg-jsJS/test", "ssg-highlightJS/test"
  ).mkString("; ")
)

addCommandAlias("test-native",
  List(
    "ssg-mdNative/test", "ssg-liquidNative/test", "ssg-sassNative/test",
    "ssg-minifyNative/test", "ssg-jsNative/test", "ssg-highlightNative/test"
  ).mkString("; ")
)

// ── Coverage alias (JVM-only) ────────────────────────────────────────
// Scala 3 coverage instrumentation is incompatible with JS/Native runtimes
// (java.io.FileWriter references). Only run on JVM projects via test-jvm.
// Strips -Werror to avoid false-positive warnings from instrumented code.

addCommandAlias("test-coverage",
  List(
    "coverage",
    """set ThisBuild / scalacOptions -= "-Werror"""",
    "test-jvm",
    "coverageReport",
    "coverageAggregate",
    "coverageOff"
  ).mkString("; ")
)
