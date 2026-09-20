import commandmatrix.extra.*
import kubuszok.sbt._
import kubuszok.sbt.KubuszokPlugin.autoImport._

// Versions
//
// Defined as a top-level object in project/Versions.scala (not an anonymous `new { ... }`
// refinement here) because the sbt-2.0 Scala-3 build dialect drops the structural members of an
// anonymous refinement, breaking `versions.X` field access.
val versions = Versions

// Exclude Baltic Porter generated code from scoverage — coverageAggregate cannot find
// source roots for files under target/balticporter-*/src_managed/ or sourceManaged/balticporter/.
ThisBuild / coverageExcludedFiles := ".*(target/balticporter.*/src_managed/|sourceManaged/balticporter/).*"

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

// Remote cache (BuildBuddy): on only when an API key is available (env BUILDBUDDY_API_KEY, else
// ~/.config/ssg/buildbuddy-api-key); with no key both settings are empty and nothing changes.
Global / remoteCache := RemoteCacheSetup.endpoint
Global / remoteCacheHeaders ++= RemoteCacheSetup.headers

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
      "-Wunused:imports,privates,locals,patvars,nowarn",
      // ISS-1354: raise the kindlings-yaml derivation macro timeout (default 5s) so
      // KindlingsYamlDecoder.fromYamlString[DataView] doesn't intermittently time out on
      // slow CI runners (macos-x86_64). kindlings-yaml reads this from -Xmacro-settings.
      "-Xmacro-settings:yamlDerivation.timeout=30s"
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
    name := "ssg-graphs-commons",
    Compile / sourceGenerators += Def.task {
      val bpRast = (ThisBuild / baseDirectory).value / ".." / "balticporter" / "balticporter" / "frontend-ts" / "src" / "test" / "resources" / "rast"
      BalticPorterGen.generateNonJavaModule(
        "ssg-graphs-commons",
        (ThisBuild / baseDirectory).value / "ssg-graphs-commons" / "reference" / "scala",
        (Compile / sourceManaged).value / "balticporter",
        streams.value.log,
        rastDir = Some(bpRast / "roughjs"))
    }.taskValue
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
    Compile / sourceGenerators += Def.task {
      val bpRast = (ThisBuild / baseDirectory).value / ".." / "balticporter" / "balticporter" / "frontend-ts" / "src" / "test" / "resources" / "rast"
      BalticPorterGen.generateNonJavaModule(
        "ssg-js",
        (ThisBuild / baseDirectory).value / "ssg-js" / "reference" / "scala",
        (Compile / sourceManaged).value / "balticporter",
        streams.value.log,
        rastDir = Some(bpRast / "terser"))
    }.taskValue,
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
    Compile / sourceGenerators += Def.task {
      val bpRast = (ThisBuild / baseDirectory).value / ".." / "balticporter" / "balticporter" / "frontend-ts" / "src" / "test" / "resources" / "rast"
      BalticPorterGen.generateNonJavaModule(
        "ssg-katex",
        (ThisBuild / baseDirectory).value / "ssg-katex" / "reference" / "scala",
        (Compile / sourceManaged).value / "balticporter",
        streams.value.log,
        rastDir = Some(bpRast / "katex"))
    }.taskValue,
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
      "io.github.cquiroz" %% "scala-java-locales" % versions.scalaJavaLocales,
      // Baltic Porter generated code dependencies: the mechanically ported liqp code
      // uses these libraries directly (the hand-port had rewrote them away).
      "com.kubuszok"                    %% "balticporter-runtime"      % "05e187f6b583c4cb71ec6827544b91ce53fde4c9-SNAPSHOT",
      "org.antlr"                        % "antlr4-runtime"            % "4.13.0",
      "com.fasterxml.jackson.core"       % "jackson-core"              % "2.15.0",
      "com.fasterxml.jackson.core"       % "jackson-databind"          % "2.13.4.2",
      "com.fasterxml.jackson.core"       % "jackson-annotations"       % "2.15.0",
      "com.fasterxml.jackson.datatype"   % "jackson-datatype-jsr310"   % "2.15.0",
      "ua.co.k"                          % "strftime4j"                % "1.0.6",
      "com.kubuszok"                    %% "multiarch-serviceloader"   % "0.4.0-12-gc168b2f-SNAPSHOT",
    ),
    resolvers += "Central Portal Snapshots" at "https://central.sonatype.com/repository/maven-snapshots",
    // ANTLR-generated parser class directory: the generated liqp code imports liquid.parser.v4.*
    // which is compiled from the grammar by LiqpClasspath in balticporter.
    // LiqpClasspath.ensure compiles the parser; call it here (not in the sourceGenerator)
    // so the classes exist before sbt evaluates the compile classpath.
    Compile / unmanagedClasspath ++= {
      val bpRoot = (ThisBuild / baseDirectory).value / ".." / "balticporter"
      val parserDir = bpRoot / "out" / "liqp-parser-classes"
      if (java.nio.file.Files.isDirectory(bpRoot.toPath.resolve("balticporter/corpus"))) {
        try { balticporter.corpus.liqp.LiqpClasspath.ensure(bpRoot.toPath) } catch { case _: Exception => () }
      }
      if (parserDir.exists()) {
        val fc = fileConverter.value
        Seq(Attributed.blank(fc.toVirtualFile(parserDir.toPath)))
      } else Nil
    },
    Test / unmanagedClasspath ++= (Compile / unmanagedClasspath).value,
    // Baltic Porter: generate ssg-liquid Scala sources from liqp Java originals.
    Compile / sourceGenerators += Def.task {
      BalticPorterGen.generateLiquid(
        (ThisBuild / baseDirectory).value,
        (Compile / sourceManaged).value / "balticporter",
        streams.value.log)
    }.taskValue,
    scalacOptions += "-Wconf:src=.*/sourceManaged/.*:s,src=.*/ported/.*/src_managed/.*:s"
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
    libraryDependencies ++= Seq(
      "com.kubuszok"       %% "multiarch-resources"   % versions.multiarch,
      "com.kubuszok"       %% "balticporter-runtime"  % "05e187f6b583c4cb71ec6827544b91ce53fde4c9-SNAPSHOT",
      "org.jetbrains"       % "annotations"           % "24.0.1" % Provided,
      "org.nibor.autolink"  % "autolink"              % "0.6.0",
    ),
    // Baltic Porter: generate ssg-md + ssg-md-ext Scala sources from flexmark-java, from the
    // published artifacts alone (no engine checkout). ONE generator, sequential: the ext port
    // needs the base's port map, so the base runs first — BalticPorterGen serialises both.
    Compile / sourceGenerators += Def.task {
      val log  = streams.value.log
      val base = (ThisBuild / baseDirectory).value
      BalticPorterGen.generateFlexmark(base, log) ++ BalticPorterGen.generateFlexmarkExt(base, log)
    }.taskValue,
    // Register the generated source roots so packageSrc uses relative paths, not bare filenames.
    Compile / managedSourceDirectories ++= Seq(
      (ThisBuild / baseDirectory).value / "target" / "balticporter" / "ssg-md" / "src_managed" / "main" / "scala",
      (ThisBuild / baseDirectory).value / "target" / "balticporter" / "ssg-md-ext" / "src_managed" / "main" / "scala"
    ),
    // The port's classpath resources (flexmark's entities.properties) are a SECOND output, and a
    // GENERATOR is what makes the tree exist before anything reads it. The previous wiring was a
    // directory behind `if (resDir.exists())`, evaluated at project LOAD: false on every fresh
    // checkout, so eight suites failed with
    // "Could not initialize class ssg.md.util.sequence.Html5Entities$" and no compile error said why.
    Compile / resourceGenerators += Def.task {
      BalticPorterGen.markdownResources((ThisBuild / baseDirectory).value, streams.value.log)
    }.taskValue,
    Compile / managedResourceDirectories += (ThisBuild / baseDirectory).value / "target" / "balticporter" / "ssg-md" / "src_managed" / "main" / "resources",
    // flexmark's own suites, generated from its java tests: what decides whether the port behaves.
    Test / sourceGenerators += Def.task {
      BalticPorterGen.generateFlexmarkTests((ThisBuild / baseDirectory).value, streams.value.log)
    }.taskValue,
    Test / managedSourceDirectories ++= Seq(
      (ThisBuild / baseDirectory).value / "target" / "balticporter" / "ssg-md" / "src_managed" / "test" / "scala",
      (ThisBuild / baseDirectory).value / "target" / "balticporter" / "ssg-md-ext" / "src_managed" / "test" / "scala"
    ),
    Test / resourceGenerators += Def.task {
      BalticPorterGen.markdownTestResources((ThisBuild / baseDirectory).value, streams.value.log)
    }.taskValue,
    scalacOptions += "-Wconf:src=.*/sourceManaged/.*:s,src=.*/target/balticporter/.*:s",
    Test / scalacOptions += "-language:implicitConversions"
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
    Compile / sourceGenerators += Def.task {
      val bpRast = (ThisBuild / baseDirectory).value / ".." / "balticporter" / "balticporter" / "frontend-ts" / "src" / "test" / "resources" / "rast"
      BalticPorterGen.generateNonJavaModule(
        "ssg-mermaid",
        (ThisBuild / baseDirectory).value / "ssg-mermaid" / "reference" / "scala",
        (Compile / sourceManaged).value / "balticporter",
        streams.value.log,
        rastDir = Some(bpRast / "mermaid"))
    }.taskValue,
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
    name := "ssg-sass",
    Compile / sourceGenerators += Def.task {
      val bpRast = (ThisBuild / baseDirectory).value / ".." / "balticporter" / "balticporter" / "frontend-ts" / "src" / "test" / "resources" / "rast"
      BalticPorterGen.generateNonJavaModule(
        "ssg-sass",
        (ThisBuild / baseDirectory).value / "ssg-sass" / "reference" / "scala",
        (Compile / sourceManaged).value / "balticporter",
        streams.value.log,
        rastDir = Some(bpRast / "dart-sass"))
    }.taskValue
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
    libraryDependencies += "com.kubuszok" %% "kindlings-yaml-derivation" % versions.kindlingsYaml,
    // ISS-1353: the SiteBuildPhase suites each run a full site build (SASS compile + file writes);
    // run them serially so concurrent filesystem access can't race — intermittent IOException on
    // Native-Windows only, where file locking is strict (POSIX tolerates it). Mirrors ssg-js/ssg-katex.
    Test / parallelExecution := false,
    // ANTLR parser classes from ssg-liquid's generated code (unmanagedClasspath is not transitive).
    Test / unmanagedClasspath ++= {
      val bpRoot = (ThisBuild / baseDirectory).value / ".." / "balticporter"
      val parserDir = bpRoot / "out" / "liqp-parser-classes"
      if (parserDir.exists()) {
        val fc = fileConverter.value
        Seq(Attributed.blank(fc.toVirtualFile(parserDir.toPath)))
      } else Nil
    }
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

// The ci aliases open with `clean`, which deletes target/balticporter — the generated markdown
// sources — and forces a regeneration (on CI it throws away the tree the `generatePort` job
// produced). A CI runner starts from an empty target/, so the step buys nothing there; locally
// run `clean` yourself.
def dropClean(command: String): String =
  command.split(";").map(_.trim).filterNot(_ == "clean").mkString(" ; ")

// Records the commit the local gate passed on (target/local-verification); the push hook of the
// Baltic Porter Claude Code plugin reads it. A dirty tree is not a commit, so nothing is recorded.
val markVerified = taskKey[Unit]("Record HEAD as locally verified")
ThisBuild / markVerified := Def.uncached {
  import scala.sys.process.*
  val base  = (ThisBuild / baseDirectory).value
  val log   = streams.value.log
  val dirty = Process(Seq("git", "status", "--porcelain", "--untracked-files=no"), base).!!.trim
  if (dirty.nonEmpty) sys.error("[verifyLocal] the working tree has uncommitted changes — commit first, then verify that commit:\n" + dirty)
  val head = Process(Seq("git", "rev-parse", "HEAD"), base).!!.trim
  IO.write(base / "target" / "local-verification", head)
  log.info(s"[verifyLocal] recorded $head")
}

val jsShards: Map[String, Seq[String]] = Map(
  "md"      -> Seq("ssg-md", "ssg"),
  "engines" -> Seq("ssg-katex", "ssg-mermaid", "ssg-graphviz", "ssg-highlight"),
  "core"    -> Seq("ssg-commons", "ssg-data-commons", "ssg-graphs-commons", "ssg-js", "ssg-sass", "ssg-minify"),
  "site"    -> Seq("ssg-liquid", "ssg-site")
)
val ciModules: Seq[String] = jsShards.values.flatten.toSeq.filterNot(_ == "ssg").sorted

lazy val root = (project in file("."))
  .enablePlugins(KubuszokRootPlugin)
  .settings(
    name := "ssg-root"
  )
  .settings(
    addCommandAlias("ci-jvm-3", dropClean(ciTestFull("JVM", "3"))),
    addCommandAlias("ci-js-3", dropClean(ciTestFull("JS", "3"))),
    // Generated ssg-md/ssg-liquid code uses JVM-only APIs (jackson, Class.getEnumConstants);
    // compile all Native modules but testFull only the ones whose generated code is Native-compatible.
    addCommandAlias("ci-native-3", {
      val allModules = Seq("ssg-commons", "ssg-data-commons", "ssg-graphs-commons", "ssg-graphviz",
        "ssg-highlight", "ssg-js", "ssg-katex", "ssg-liquid", "ssg-md", "ssg-mermaid",
        "ssg-minify", "ssg-sass", "ssg-site")
      val jvmOnly = Set("ssg-md", "ssg-liquid", "ssg-highlight", "ssg-site")
      val compile = allModules.map(m => s"${m}Native/compile").mkString(" ; ")
      val test = allModules.filterNot(jvmOnly).map(m => s"${m}Native/testFull").mkString(" ; ")
      s"$compile ; ssgNative/compile ; $test"
    }),
    // CI compiles each platform once (`testCompile-*`, which fills the remote cache); the test jobs
    // then only link and run. Scala.js is split into four shards whose union is every module.
    addCommandAlias("testCompile-jvm-3", (ciModules :+ "ssg").map(m => s"$m/Test/compile").mkString(" ; ")),
    addCommandAlias("testCompile-js-3", (ciModules :+ "ssg").map(m => s"${m}JS/Test/compile").mkString(" ; ")),
    addCommandAlias("testCompile-native-3", (ciModules :+ "ssg").map(m => s"${m}Native/Test/compile").mkString(" ; ")),
  )
  .settings(
    jsShards.toSeq.flatMap { case (shard, modules) =>
      addCommandAlias(s"ci-js-3-$shard", modules.map(m => s"${m}JS/testFull").mkString(" ; "))
    }
  )
  .settings(
    // generatePort: run the Baltic Porter markdown generation and nothing else (the CI `generate` job)
    addCommandAlias("generatePort", "ssg-md/Compile/managedSources ; ssg-md/Compile/managedResources"),
    // verifyLocal: the gate before a push — every platform's tests, then record the verified commit
    addCommandAlias("verifyLocal", "ci-jvm-3 ; ci-js-3 ; ci-native-3 ; markVerified")
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
