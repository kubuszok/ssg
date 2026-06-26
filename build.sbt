import commandmatrix.extra.*
import kubuszok.sbt._
import kubuszok.sbt.KubuszokPlugin.autoImport._

// Versions
//
// Defined as a top-level object in project/Versions.scala (not an anonymous `new { ... }`
// refinement here) because the sbt-2.0 Scala-3 build dialect drops the structural members of an
// anonymous refinement, breaking `versions.X` field access.
val versions = Versions

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
    `ssg-sass`,
    `ssg-site`
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
      "org.scalameta"     %% "munit"             % versions.munit % Test,
      "org.scalameta"     %% "munit-scalacheck"  % versions.munitScalacheck % Test
    ),
    resolvers += Resolver.mavenLocal,
    // Sonatype Central Portal snapshots — hearth/kindlings sbt-2.0 dev snapshots
    // (the incoming hearth 0.4.0 / kindlings 0.3.0 breaking-change line) live here,
    // published by their CI; not yet released to Central.
    resolvers += "Central Portal Snapshots" at "https://central.sonatype.com/repository/maven-snapshots/",
    testFrameworks += new TestFramework("munit.Framework")
  )),
  MatrixAction.ForPlatforms(VirtualAxis.jvm).Configure(_.settings(
    fork := true,
    // Enable native access for the Foreign Function & Memory API (JEP 454),
    // used by NativeMathPlatform to call the native C pow() for exact
    // floating-point parity with dart-sass / JavaScript Math.pow.
    javaOptions += "--enable-native-access=ALL-UNNAMED",
    // scoverage's runtime Invoker (forked test JVM) appends measurement files to
    // crossTarget/scoverage-data, but under sbt-2.0's target/out layout scoverage
    // does not create that dir at instrumentation time, so the first write throws
    // FileNotFoundException mid-test (only when `coverage` is on, i.e. ci-jvm-3).
    // Pre-create it before the test tasks (a no-op empty dir when coverage is off).
    // Handoff recipe: "scoverage Test/compile dir pre-create" → Def.uncached.
    // Only testFull needs it: coverage runs via ci-jvm-3 → testFull (sbt-2.0's
    // bare `test` is an InputTask and is not used by CI).
    Test / testFull := (Test / testFull).dependsOn(Def.uncached(Def.task {
      IO.createDirectory(crossTarget.value / "scoverage-data")
    })).value
  )),
  MatrixAction.ForPlatforms(VirtualAxis.native).Configure(_.settings(
    scalanative.sbtplugin.ScalaNativePlugin.autoImport.nativeConfig ~= {
      _.withEmbedResources(true).withMultithreading(false) // Single-threaded: avoids thread stack limits, uses main stack
    },
    // scalacheck 1.19 pulls test-interface 0.5.8 while scala-native 0.5.12 selects 0.5.8's successor
    // 0.5.12 (strict): the two are compatible at the test-interface level, so downgrade the eviction
    // conflict from an error to a warning on the native axis.
    evictionErrorLevel := Level.Warn
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
      "com.kubuszok"  %% "lls" % versions.lls,
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
      "com.kubuszok"      %% "hearth"            % versions.hearth,
      "io.github.cquiroz" %% "scala-java-time"   % versions.scalaJavaTime
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
      // sbt 2.0 result caching has no sjsonnew.HashWriter for JSEnv → opt out with Def.uncached.
      Test / jsEnv := Def.uncached(new org.scalajs.jsenv.nodejs.NodeJSEnv(
        org.scalajs.jsenv.nodejs.NodeJSEnv.Config()
          .withEnv(Map("TREE_SITTER_WASM_DIR" -> sys.env.getOrElse("TREE_SITTER_WASM_DIR", "/tmp/ts-wasm")))
      ))
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
    name := "ssg-js",
    // The Terser port's name mangler keeps process-global mutable state (object Base54's char/frequency
    // table — terser's lib/scope.js Base54 is a single module-level singleton, reset per minify call).
    // sbt 2.0 runs test suites in parallel within one forked JVM by default, so concurrent minify calls
    // race on that shared state and produce nondeterministic mangled names. Run ssg-js tests serially to
    // preserve the single-threaded contract (matches sbt 1.x behavior).
    Test / parallelExecution := false
  )
  .settings(publishSettings)
  .settings(mimaSettings)
  .dependsOn(`ssg-commons`)

// --- Math typesetting (KaTeX port) ---

lazy val `ssg-katex` = (projectMatrix in file("ssg-katex"))
  .defaultAxes(VirtualAxis.jvm, VirtualAxis.scalaABIVersion(versions.scala3))
  .someVariations(versions.scalas, versions.platforms)((commonSettings ++ dev.only1VersionInIDE) *)
  .settings(
    name := "ssg-katex",
    // ISS-1348: The KaTeX port's macro registry (Macros.registerAll) populates a process-global
    // mutable map. Parallel test suites race on that shared state. Run ssg-katex tests serially
    // to preserve the single-threaded contract (same pattern as ssg-js / Base54).
    Test / parallelExecution := false
  )
  .settings(publishSettings)
  .settings(mimaSettings)
  .dependsOn(`ssg-commons`)

// --- Liquid template engine (liqp port) ---

lazy val `ssg-liquid` = (projectMatrix in file("ssg-liquid"))
  .defaultAxes(VirtualAxis.jvm, VirtualAxis.scalaABIVersion(versions.scala3))
  .someVariations(versions.scalas, versions.platforms)((commonSettings ++ dev.only1VersionInIDE ++ Seq(
    MatrixAction.ForPlatforms(VirtualAxis.js).Configure(_.settings(
      libraryDependencies += "io.github.cquiroz" %% "scala-java-time-tzdb" % versions.scalaJavaTime
    )),
    MatrixAction.ForPlatforms(VirtualAxis.native).Configure(_.settings(
      libraryDependencies += "io.github.cquiroz" %% "scala-java-time-tzdb" % versions.scalaJavaTime
    ))
  )) *)
  .settings(
    name := "ssg-liquid",
    libraryDependencies ++= Seq(
      "io.github.cquiroz" %% "scala-java-time"    % versions.scalaJavaTime,
      "io.github.cquiroz" %% "scala-java-locales" % versions.scalaJavaLocales
    )
  )
  .settings(publishSettings)
  .settings(mimaSettings)
  .dependsOn(`ssg-commons`, `ssg-data-commons`)

// --- Markdown engine (flexmark-java port) ---

// Scala.js has no classpath, so Class.getResourceAsStream cannot resolve runtime resources. The shared
// multiarch-resources mechanism embeds ssg-md's main resources at build time into a self-registering
// generated object (ssg.md.util.misc.GeneratedEmbeddedResources) which the runtime
// multiarch.resources.PlatformResourcesImpl (scalajs) consults first, falling back to a Node fs lookup
// for in-repo development. ssg.md.util.misc.PlatformResources is a thin boundary shim that delegates to
// the shared API and converts Option -> Nullable (ISS-979).
lazy val `ssg-md` = (projectMatrix in file("ssg-md"))
  .defaultAxes(VirtualAxis.jvm, VirtualAxis.scalaABIVersion(versions.scala3))
  .someVariations(versions.scalas, versions.platforms)((commonSettings ++ dev.only1VersionInIDE ++ Seq(
    MatrixAction.ForPlatforms(VirtualAxis.js).Configure(_.settings(
      _root_.multiarch.sbt.MultiArchResourcesPlugin.embeddedResourcesSettings(
        objectName = "ssg.md.util.misc.GeneratedEmbeddedResources"
      )
    ))
  )) *)
  .settings(
    name := "ssg-md",
    libraryDependencies += "com.kubuszok" %% "multiarch-resources" % versions.multiarch
  )
  .settings(publishSettings)
  .settings(mimaSettings)
  .dependsOn(`ssg-commons`)

// --- Diagramming engine (Mermaid port) ---

lazy val `ssg-mermaid` = (projectMatrix in file("ssg-mermaid"))
  .defaultAxes(VirtualAxis.jvm, VirtualAxis.scalaABIVersion(versions.scala3))
  .someVariations(versions.scalas, versions.platforms)((commonSettings ++ dev.only1VersionInIDE ++ Seq(
    MatrixAction.ForPlatforms(VirtualAxis.js).Configure(_.settings(
      libraryDependencies += "io.github.cquiroz" %% "scala-java-time-tzdb" % versions.scalaJavaTime
    )),
    MatrixAction.ForPlatforms(VirtualAxis.native).Configure(_.settings(
      libraryDependencies += "io.github.cquiroz" %% "scala-java-time-tzdb" % versions.scalaJavaTime
    ))
  )) *)
  .settings(
    name := "ssg-mermaid",
    libraryDependencies ++= Seq(
      "io.github.cquiroz" %% "scala-java-time"          % versions.scalaJavaTime,
      "com.kubuszok"      %% "kindlings-yaml-derivation" % versions.kindlingsYaml
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

// --- Site pipeline (SSG-native glue) ---

lazy val `ssg-site` = (projectMatrix in file("ssg-site"))
  .defaultAxes(VirtualAxis.jvm, VirtualAxis.scalaABIVersion(versions.scala3))
  .someVariations(versions.scalas, versions.platforms)((commonSettings ++ dev.only1VersionInIDE) *)
  .settings(
    name := "ssg-site",
    libraryDependencies += "com.kubuszok" %% "kindlings-yaml-derivation" % versions.kindlingsYaml
  )
  .settings(publishSettings)
  .settings(mimaSettings)
  .dependsOn(`ssg-commons`, `ssg-data-commons`, `ssg-js`, `ssg-liquid`, `ssg-md`, `ssg-minify`, `ssg-sass`)

// --- Aggregator module ---

lazy val ssg = (projectMatrix in file("ssg"))
  .defaultAxes(VirtualAxis.jvm, VirtualAxis.scalaABIVersion(versions.scala3))
  .someVariations(versions.scalas, versions.platforms)((commonSettings ++ dev.only1VersionInIDE) *)
  .settings(
    name := "ssg"
  )
  .settings(publishSettings)
  .settings(mimaSettings)
  .dependsOn(`ssg-commons`, `ssg-data-commons`, `ssg-graphs-commons`, `ssg-graphviz`, `ssg-highlight`, `ssg-js`, `ssg-katex`, `ssg-liquid`, `ssg-md`, `ssg-mermaid`, `ssg-minify`, `ssg-sass`, `ssg-site`)

// ── Root project (aggregation + CI aliases) ──────────────────────────
//
// sbt-welcome has no sbt-2.0 build and is no longer bundled by sbt-kubuszok, so the `logo` /
// `usefulTasks` wiring (which also registered the ci-*/test-* aliases on the sbt-1.x axis) is gone.
// We register the CI aliases ourselves via addCommandAlias, reusing Aliases.ci(...) to assemble each
// pipeline's task list. On sbt 2.0 the bare `test` task is incrementally cached and runs 0 suites on a
// fresh checkout (silent false-green), so we rewrite every `<id>/test` task to `<id>/testFull`.
def ciTestFull(platform: String, scalaBinary: String): String =
  al.ci(platform, scalaBinary).replaceAll("""/test(?=( ; )|$)""", "/testFull")

lazy val root = (project in file("."))
  .enablePlugins(KubuszokRootPlugin)
  .settings(
    name := "ssg-root"
  )
  .settings(
    addCommandAlias("ci-jvm-3", ciTestFull("JVM", "3")),
    addCommandAlias("ci-js-3", ciTestFull("JS", "3")),
    addCommandAlias("ci-native-3", ciTestFull("Native", "3"))
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
  .aggregate(`ssg-site`.projectRefs *)
  .aggregate(ssg.projectRefs *)
  .settings(noPublishSettings)
  .settings(mimaSettings)
