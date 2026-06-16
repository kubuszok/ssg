import sbtwelcome.UsefulTask
import commandmatrix.extra.*
import kubuszok.sbt._
import kubuszok.sbt.KubuszokPlugin.autoImport._

// Versions

val versions = new {
  // Versions we are publishing for.
  val scala3 = "3.8.4"

  // Which versions should be cross-compiled for publishing.
  val scalas = List(scala3)
  val platforms = List(VirtualAxis.jvm, VirtualAxis.js, VirtualAxis.native)

  // Dependencies
  val hearth              = "0.3.0-49-g68e1781-SNAPSHOT"
  val kindlingsYaml       = "0.2.0"
  val lls                 = "0.1.0"
  val scalaJavaLocales    = "1.5.4"
  val scalaJavaTime       = "2.6.0"

  // Multiarch
  val multiarch           = "0.2.0"
  val treeSitterProviders = "0.1.0"

  // Tests
  val munit           = "1.3.2"
  val munitScalacheck = "1.3.0"
}

val dev = new DevProperties(
  scala213 = None,
  scala3 = Some(versions.scala3),
  platforms = versions.platforms
)

lazy val al = new Aliases(
  published = Seq(
    `ssg-commons`,
    `ssg-data-commons`,
    `ssg-graphs-commons`,
    `ssg-graphviz`,
    `ssg-highlight`,
    `ssg-js`,
    `ssg-katex`,
    `ssg-liquid`,
    `ssg-md`,
    `ssg-mermaid`,
    `ssg-minify`,
    `ssg-sass`
  ),
  compileOnly = Seq(
    ssg
  )
)

val commonSettings = Seq(
  MatrixAction.ForAll.Configure(_.settings(
    scalacOptions ++= Seq(
      "-deprecation",
      "-feature",
      "-no-indent",
      "-Werror",
      "-Wimplausible-patterns",
      "-Wrecurse-with-default",
      "-Wenum-comment-discard",
      "-Wunused:imports,privates,locals,patvars,nowarn"
    ),
    libraryDependencies ++= Seq(
      "org.scalameta"     %%% "munit"             % versions.munit % Test,
      "org.scalameta"     %%% "munit-scalacheck"  % versions.munitScalacheck % Test
    ),
    resolvers += Resolver.mavenLocal,
    testFrameworks += new TestFramework("munit.Framework")
  )),
  MatrixAction.ForPlatforms(VirtualAxis.jvm).Configure(_.settings(
    fork := true,
    // Enable native access for the Foreign Function & Memory API (JEP 454),
    // used by NativeMathPlatform to call the native C pow() for exact
    // floating-point parity with dart-sass / JavaScript Math.pow.
    javaOptions += "--enable-native-access=ALL-UNNAMED"
  )),
  MatrixAction.ForPlatforms(VirtualAxis.native).Configure(_.settings(
    scalanative.sbtplugin.ScalaNativePlugin.autoImport.nativeConfig ~= {
      _.withEmbedResources(true).withMultithreading(false) // Single-threaded: avoids thread stack limits, uses main stack
    }
  ))
)

val publishSettings = Seq(
  organization := "com.kubuszok",
  homepage := Some(url("https://github.com/kubuszok/ssg")),
  organizationHomepage := Some(url("https://kubuszok.com")),
  licenses := Seq("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0")),
  scmInfo := Some(
    ScmInfo(
      url("https://github.com/kubuszok/ssg/"),
      "scm:git:git@github.com:kubuszok/ssg.git"
    )
  ),
  startYear := Some(2026),
  developers := List(
    Developer("MateuszKubuszok", "Mateusz Kubuszok", "", url("https://kubuszok.com"))
  ),
  pomExtra := (
    <issueManagement>
      <system>GitHub issues</system>
      <url>https://github.com/kubuszok/ssg/issues</url>
    </issueManagement>
  ),
  projectType := ProjectType.ScalaLibrary
)

val noPublishSettings =
  Seq(projectType := ProjectType.NonPublished)

val mimaSettings = Seq(
  mimaPreviousArtifacts := Set(),
  mimaFailOnNoPrevious := false
)

// --- Common utilities (cross-platform abstractions) ---

lazy val `ssg-commons` = (projectMatrix in file("ssg-commons"))
  .defaultAxes(VirtualAxis.jvm, VirtualAxis.scalaABIVersion(versions.scala3))
  .someVariations(versions.scalas, versions.platforms)((commonSettings ++ dev.only1VersionInIDE) *)
  .settings(
    name := "ssg-commons",
    libraryDependencies ++= Seq(
      "com.kubuszok"  %%% "lls" % versions.lls,
    )
  )
  .settings(publishSettings)
  .settings(mimaSettings)

// --- Data view abstractions (shared) ---

lazy val `ssg-data-commons` = (projectMatrix in file("ssg-data-commons"))
  .defaultAxes(VirtualAxis.jvm, VirtualAxis.scalaABIVersion(versions.scala3))
  .someVariations(versions.scalas, versions.platforms)((commonSettings ++ dev.only1VersionInIDE) *)
  .settings(
    name := "ssg-data-commons",
    libraryDependencies ++= Seq(
      "com.kubuszok"      %%% "hearth"            % versions.hearth,
      "io.github.cquiroz" %%% "scala-java-time"   % versions.scalaJavaTime
    ),
    libraryDependencies += compilerPlugin("com.kubuszok" %% "hearth-cross-quotes" % versions.hearth)
  )
  .settings(publishSettings)
  .settings(mimaSettings)
  .dependsOn(`ssg-commons`)

// --- Graph layout and SVG infrastructure (shared) ---

lazy val `ssg-graphs-commons` = (projectMatrix in file("ssg-graphs-commons"))
  .defaultAxes(VirtualAxis.jvm, VirtualAxis.scalaABIVersion(versions.scala3))
  .someVariations(versions.scalas, versions.platforms)((commonSettings ++ dev.only1VersionInIDE) *)
  .settings(
    name := "ssg-graphs-commons"
  )
  .settings(publishSettings)
  .settings(mimaSettings)
  .dependsOn(`ssg-commons`)

// --- Graphviz DOT renderer ---

lazy val `ssg-graphviz` = (projectMatrix in file("ssg-graphviz"))
  .defaultAxes(VirtualAxis.jvm, VirtualAxis.scalaABIVersion(versions.scala3))
  .someVariations(versions.scalas, versions.platforms)((commonSettings ++ dev.only1VersionInIDE) *)
  .settings(
    name := "ssg-graphviz"
  )
  .settings(publishSettings)
  .settings(mimaSettings)
  .dependsOn(`ssg-commons`, `ssg-graphs-commons`)

// --- Syntax highlighting (tree-sitter) ---

lazy val `ssg-highlight` = (projectMatrix in file("ssg-highlight"))
  .defaultAxes(VirtualAxis.jvm, VirtualAxis.scalaABIVersion(versions.scala3))
  .someVariations(versions.scalas, versions.platforms)((commonSettings ++ dev.only1VersionInIDE ++ Seq(
    MatrixAction.ForPlatforms(VirtualAxis.jvm).Configure(_.settings(
      libraryDependencies ++= Seq(
        "com.kubuszok" % "pnm-provider-tree-sitter-desktop" % versions.treeSitterProviders,
        "com.kubuszok" %% "multiarch-core"                  % versions.multiarch
      )
    )),
    MatrixAction.ForPlatforms(VirtualAxis.js).Configure(_.settings(
      libraryDependencies += "com.kubuszok" % "wasm-provider-tree-sitter" % versions.treeSitterProviders,
      scalaJSLinkerConfig ~= { _.withModuleKind(org.scalajs.linker.interface.ModuleKind.CommonJSModule) },
      Test / jsEnv := new org.scalajs.jsenv.nodejs.NodeJSEnv(
        org.scalajs.jsenv.nodejs.NodeJSEnv.Config()
          .withEnv(Map("TREE_SITTER_WASM_DIR" -> sys.env.getOrElse("TREE_SITTER_WASM_DIR", "/tmp/ts-wasm")))
      )
    )),
    // TODO: check if _root_.multiarch.sbt.NativeProviderPlugin.projectSettings is necessary for this to work
    MatrixAction.ForPlatforms(VirtualAxis.native).Configure(_.settings(
      (_root_.multiarch.sbt.NativeProviderPlugin.projectSettings ++ Seq(
        libraryDependencies += "com.kubuszok" % "sn-provider-tree-sitter" % versions.treeSitterProviders,
        scalanative.sbtplugin.ScalaNativePlugin.autoImport.nativeConfig ~= {
          _.withResourceIncludePatterns(Seq("**.scm"))
        }
      )) *
    ))
  )) *)
  .settings(
    name := "ssg-highlight",
    libraryDependencies ++= Seq(
      "com.kubuszok" % "tree-sitter-queries" % versions.treeSitterProviders
    )
  )
  .settings(publishSettings)
  .settings(mimaSettings)
  .dependsOn(`ssg-commons`, `ssg-md`)

// --- JavaScript compiler/minifier (Terser port) ---

lazy val `ssg-js` = (projectMatrix in file("ssg-js"))
  .defaultAxes(VirtualAxis.jvm, VirtualAxis.scalaABIVersion(versions.scala3))
  .someVariations(versions.scalas, versions.platforms)((commonSettings ++ dev.only1VersionInIDE) *)
  .settings(
    name := "ssg-js"
  )
  .settings(publishSettings)
  .settings(mimaSettings)
  .dependsOn(`ssg-commons`)

// --- Math typesetting (KaTeX port) ---

lazy val `ssg-katex` = (projectMatrix in file("ssg-katex"))
  .defaultAxes(VirtualAxis.jvm, VirtualAxis.scalaABIVersion(versions.scala3))
  .someVariations(versions.scalas, versions.platforms)((commonSettings ++ dev.only1VersionInIDE) *)
  .settings(
    name := "ssg-katex"
  )
  .settings(publishSettings)
  .settings(mimaSettings)
  .dependsOn(`ssg-commons`)

// --- Liquid template engine (liqp port) ---

lazy val `ssg-liquid` = (projectMatrix in file("ssg-liquid"))
  .defaultAxes(VirtualAxis.jvm, VirtualAxis.scalaABIVersion(versions.scala3))
  .someVariations(versions.scalas, versions.platforms)((commonSettings ++ dev.only1VersionInIDE ++ Seq(
    MatrixAction.ForPlatforms(VirtualAxis.js).Configure(_.settings(
      libraryDependencies += "io.github.cquiroz" %%% "scala-java-time-tzdb" % versions.scalaJavaTime
    )),
    MatrixAction.ForPlatforms(VirtualAxis.native).Configure(_.settings(
      libraryDependencies += "io.github.cquiroz" %%% "scala-java-time-tzdb" % versions.scalaJavaTime
    ))
  )) *)
  .settings(
    name := "ssg-liquid",
    libraryDependencies ++= Seq(
      "io.github.cquiroz" %%% "scala-java-time"    % versions.scalaJavaTime,
      "io.github.cquiroz" %%% "scala-java-locales" % versions.scalaJavaLocales
    )
  )
  .settings(publishSettings)
  .settings(mimaSettings)
  .dependsOn(`ssg-commons`, `ssg-data-commons`)

// --- Markdown engine (flexmark-java port) ---

// Generates ssg.md.util.misc.EmbeddedResources for the Scala.js platform (see project/EmbeddedResourcesGen.scala).
// Class.getResourceAsStream is not available on Scala.js, so PlatformResourcesImpl (scalajs) loads runtime
// resources from this build-time-embedded map first, falling back to Node fs only for development/in-repo lookups.
lazy val `ssg-md` = (projectMatrix in file("ssg-md"))
  .defaultAxes(VirtualAxis.jvm, VirtualAxis.scalaABIVersion(versions.scala3))
  .someVariations(versions.scalas, versions.platforms)((commonSettings ++ dev.only1VersionInIDE ++ Seq(
    MatrixAction.ForPlatforms(VirtualAxis.js).Configure(_.settings(
      Compile / sourceGenerators += Def.task {
        val resourceDir = (Compile / sourceDirectory).value / "resources"
        val outFile     = (Compile / sourceManaged).value / "ssg" / "md" / "util" / "misc" / "EmbeddedResources.scala"
        val log         = streams.value.log
        EmbeddedResourcesGen.generate(resourceDir, outFile, log)
        Seq(outFile)
      }.taskValue
    ))
  )) *)
  .settings(
    name := "ssg-md"
  )
  .settings(publishSettings)
  .settings(mimaSettings)
  .dependsOn(`ssg-commons`)

// --- Diagramming engine (Mermaid port) ---

lazy val `ssg-mermaid` = (projectMatrix in file("ssg-mermaid"))
  .defaultAxes(VirtualAxis.jvm, VirtualAxis.scalaABIVersion(versions.scala3))
  .someVariations(versions.scalas, versions.platforms)((commonSettings ++ dev.only1VersionInIDE ++ Seq(
    MatrixAction.ForPlatforms(VirtualAxis.js).Configure(_.settings(
      libraryDependencies += "io.github.cquiroz" %%% "scala-java-time-tzdb" % versions.scalaJavaTime
    )),
    MatrixAction.ForPlatforms(VirtualAxis.native).Configure(_.settings(
      libraryDependencies += "io.github.cquiroz" %%% "scala-java-time-tzdb" % versions.scalaJavaTime
    ))
  )) *)
  .settings(
    name := "ssg-mermaid",
    libraryDependencies ++= Seq(
      "io.github.cquiroz" %%% "scala-java-time"          % versions.scalaJavaTime,
      "com.kubuszok"      %%% "kindlings-yaml-derivation" % versions.kindlingsYaml
    )
  )
  .settings(publishSettings)
  .settings(mimaSettings)
  .dependsOn(`ssg-commons`, `ssg-data-commons`, `ssg-graphs-commons`)

// --- Web asset minification (jekyll-minifier port) ---

lazy val `ssg-minify` = (projectMatrix in file("ssg-minify"))
  .defaultAxes(VirtualAxis.jvm, VirtualAxis.scalaABIVersion(versions.scala3))
  .someVariations(versions.scalas, versions.platforms)((commonSettings ++ dev.only1VersionInIDE) *)
  .settings(
    name := "ssg-minify"
  )
  .settings(publishSettings)
  .settings(mimaSettings)
  .dependsOn(`ssg-commons`)

// --- SASS/SCSS compiler (dart-sass port) ---

lazy val `ssg-sass` = (projectMatrix in file("ssg-sass"))
  .defaultAxes(VirtualAxis.jvm, VirtualAxis.scalaABIVersion(versions.scala3))
  .someVariations(versions.scalas, versions.platforms)((commonSettings ++ dev.only1VersionInIDE) *)
  .settings(
    name := "ssg-sass"
  )
  .settings(publishSettings)
  .settings(mimaSettings)
  .dependsOn(`ssg-commons`)

// --- Aggregator module ---

lazy val ssg = (projectMatrix in file("ssg"))
  .defaultAxes(VirtualAxis.jvm, VirtualAxis.scalaABIVersion(versions.scala3))
  .someVariations(versions.scalas, versions.platforms)((commonSettings ++ dev.only1VersionInIDE) *)
  .settings(
    name := "ssg"
  )
  .settings(publishSettings)
  .settings(mimaSettings)
  .dependsOn(`ssg-commons`, `ssg-data-commons`, `ssg-graphs-commons`, `ssg-graphviz`, `ssg-highlight`, `ssg-js`, `ssg-katex`, `ssg-liquid`, `ssg-md`, `ssg-mermaid`, `ssg-minify`, `ssg-sass`)

// ── Root project (welcome + aggregation) ─────────────────────────────

lazy val root = (project in file("."))
  .enablePlugins(KubuszokRootPlugin)
  .settings(
    name := "ssg-root",
    logo :=
      s"""SSG ${version.value} for Scala ${versions.scala3} x (Scala JVM, Scala.js $scalaJSVersion, Scala Native $nativeVersion)
         |
         |This build uses sbt-projectmatrix:
         | - Scala JVM adds no suffix to a project name seen in build.sbt
         | - Scala.js adds the "JS" suffix to a project name seen in build.sbt
         | - Scala Native adds the "Native" suffix to a project name seen in build.sbt
         |
         |When working with IntelliJ or Scala Metals, edit dev.properties to control which platform you're currently working with.
         |
         |Library depends on artifacts developed in:
         | - https://github.com/kubuszok/lls
         | - https://github.com/kubuszok/ssg-native-providers
         |When working with them, it might be necessary to create PRs and test the SNAPSHOTs published before merging all changes.
         |""".stripMargin,
    usefulTasks := al.usefulTasks(extra = Seq(
      UsefulTask("scalafmtAll", "Format all sources").noAlias
    ))
  )
  .aggregate(`ssg-commons`.projectRefs *)
  .aggregate(`ssg-data-commons`.projectRefs *)
  .aggregate(`ssg-graphs-commons`.projectRefs *)
  .aggregate(`ssg-graphviz`.projectRefs *)
  .aggregate(`ssg-highlight`.projectRefs *)
  .aggregate(`ssg-js`.projectRefs *)
  .aggregate(`ssg-katex`.projectRefs *)
  .aggregate(`ssg-liquid`.projectRefs *)
  .aggregate(`ssg-md`.projectRefs *)
  .aggregate(`ssg-mermaid`.projectRefs *)
  .aggregate(`ssg-minify`.projectRefs *)
  .aggregate(`ssg-sass`.projectRefs *)
  .aggregate(ssg.projectRefs *)
  .settings(noPublishSettings)
  .settings(mimaSettings)
