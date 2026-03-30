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
      "org.scalameta" %%% "munit"            % "1.2.3" % Test,
      "org.scalameta" %%% "munit-scalacheck" % "1.0.0" % Test
    )
  )
  .jvmPlatform(scalaVersions = Seq(SsgSettings.scalaVersion), settings = SsgSettings.jvmSettings)
  .jsPlatform(scalaVersions = Seq(SsgSettings.scalaVersion), settings = SsgSettings.jsSettings)
  .nativePlatform(scalaVersions = Seq(SsgSettings.scalaVersion), settings = SsgSettings.nativeSettings)

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
  .jvmPlatform(scalaVersions = Seq(SsgSettings.scalaVersion), settings = SsgSettings.jvmSettings)
  .jsPlatform(scalaVersions = Seq(SsgSettings.scalaVersion), settings = SsgSettings.jsSettings)
  .nativePlatform(scalaVersions = Seq(SsgSettings.scalaVersion), settings = SsgSettings.nativeSettings)

// --- HTML/JS minification (jekyll-minifier port) ---

val `ssg-html` = (projectMatrix in file("ssg-html"))
  .defaultAxes(VirtualAxis.jvm, VirtualAxis.scalaABIVersion(SsgSettings.scalaVersion))
  .settings(SsgSettings.commonSettings *)
  .settings(
    name := "ssg-html",
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
  .dependsOn(`ssg-md`, `ssg-liquid`, `ssg-sass`, `ssg-html`)
  .jvmPlatform(scalaVersions = Seq(SsgSettings.scalaVersion), settings = SsgSettings.jvmSettings)
  .jsPlatform(scalaVersions = Seq(SsgSettings.scalaVersion), settings = SsgSettings.jsSettings)
  .nativePlatform(scalaVersions = Seq(SsgSettings.scalaVersion), settings = SsgSettings.nativeSettings)
