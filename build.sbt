ThisBuild / organization := "dev.ssg"
ThisBuild / version      := "0.1.0-SNAPSHOT"

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
  .jvmPlatform(scalaVersions = Seq(SsgSettings.scalaVersion), settings = SsgSettings.jvmSettings)
  .jsPlatform(scalaVersions = Seq(SsgSettings.scalaVersion), settings = SsgSettings.jsSettings)
  .nativePlatform(scalaVersions = Seq(SsgSettings.scalaVersion), settings = SsgSettings.nativeSettings)

// --- SASS/SCSS compiler (dart-sass port) ---

// ssg-sass needs JVM-only source directories for FilesystemImporter
// (requires java.nio.file which isn't available on Scala.js).
val sassJvmSettings: Seq[Setting[?]] = SsgSettings.jvmSettings ++ Seq(
  Compile / unmanagedSourceDirectories += (ThisBuild / baseDirectory).value / "ssg-sass" / "src" / "main" / "scala-jvm",
  Test    / unmanagedSourceDirectories += (ThisBuild / baseDirectory).value / "ssg-sass" / "src" / "test" / "scala-jvm"
)

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
  .jvmPlatform(scalaVersions = Seq(SsgSettings.scalaVersion), settings = sassJvmSettings)
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
  .jvmPlatform(scalaVersions = Seq(SsgSettings.scalaVersion), settings = SsgSettings.jvmSettings)
  .jsPlatform(scalaVersions = Seq(SsgSettings.scalaVersion), settings = SsgSettings.jsSettings)
  .nativePlatform(scalaVersions = Seq(SsgSettings.scalaVersion), settings = SsgSettings.nativeSettings)

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
  .dependsOn(`ssg-md`, `ssg-liquid`, `ssg-sass`, `ssg-minify`, `ssg-js`)
  .jvmPlatform(scalaVersions = Seq(SsgSettings.scalaVersion), settings = SsgSettings.jvmSettings)
  .jsPlatform(scalaVersions = Seq(SsgSettings.scalaVersion), settings = SsgSettings.jsSettings)
  .nativePlatform(scalaVersions = Seq(SsgSettings.scalaVersion), settings = SsgSettings.nativeSettings)
