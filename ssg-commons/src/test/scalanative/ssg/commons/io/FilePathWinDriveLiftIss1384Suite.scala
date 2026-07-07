/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package commons
package io

/** ISS-1384 [R0610]: cross-platform parity follow-up to ISS-1383 — the Native FilePathPlatform.of/renderPath must lift a native Windows drive-absolute input ("C:\\a" or "C:/a", with NO leading slash)
  * into the documented model form "/C:/a", exactly as the merged ISS-1383 JVM fix does (scalajvm/ssg/commons/io/FilePathPlatform.scala: isNativeDriveAbsolute + the renderPath lift).
  *
  * This is a MODEL-LEVEL string defect, reproducible on ANY OS: the Native FilePathPlatform.isAbsolute only checks startsWith("/"), so FilePath.of("C:\\a").isAbsolute is false everywhere, and
  * .toAbsolute then string-joins the process cwd onto a path that is already drive-absolute. ISS-1383 fixed this on the JVM only; the Native (and JS) platform files never received the lift — the
  * parity gap ISS-1383's auditor flagged as ISS-1384.
  *
  * Expected values follow java.nio.file semantics on Windows combined with the model's POSIX rendering, as already documented by FilePathWin32ParityIss1128Suite and the JVM FilePathPlatform: the
  * model form of a Windows drive-absolute path is "/C:/x" (leading '/' makes it POSIX-absolute; the drive colon is preserved; native backslash separators render as forward slashes). On Windows,
  * Paths.get("C:\\a\\b").toString == "C:\\a\\b" and Paths.get("C:\\a\\b").isAbsolute == true; POSIX-rendering that drive-absolute path yields "/C:/a/b". These are the same values the merged JVM
  * FilePathWinDriveLiftIss1383Suite pins — this suite brings the Native platform to parity (anti-cheat C11: every expected value is cited from java.nio semantics + the documented /C:/x model form).
  *
  * Red until ISS-1384 ports the lift into the scalanative FilePathPlatform.
  */
final class FilePathWinDriveLiftIss1384Suite extends munit.FunSuite {

  // (a) backslash drive-absolute input lifts into the model form "/C:/a/b".
  // Windows: Paths.get("C:\\a\\b").toString == "C:\\a\\b", isAbsolute true; POSIX-rendered model form == "/C:/a/b".
  // The literal "C:\\a\\b" is the 6-char string 'C' ':' '\\' 'a' '\\' 'b'.
  test("ISS-1384: of(\"C:\\\\a\\\\b\").pathString lifts to \"/C:/a/b\"") {
    assertEquals(FilePath.of("C:\\a\\b").pathString, "/C:/a/b")
  }

  // (b) forward-slash drive-absolute input lifts into the same model form "/C:/a/b".
  // Windows: Paths.get("C:/a/b").toString == "C:\\a\\b", isAbsolute true; POSIX-rendered model form == "/C:/a/b".
  test("ISS-1384: of(\"C:/a/b\").pathString lifts to \"/C:/a/b\"") {
    assertEquals(FilePath.of("C:/a/b").pathString, "/C:/a/b")
  }

  // (c) a drive-absolute input is absolute in the model, everywhere.
  // Windows: Paths.get("C:\\a").isAbsolute == true; the lifted model form "/C:/a" is POSIX-absolute.
  test("ISS-1384: of(\"C:\\\\a\").isAbsolute is true") {
    assert(FilePath.of("C:\\a").isAbsolute)
  }

  // (d) toAbsolute leaves a drive-absolute input unchanged — no cwd join.
  // Because the input is already absolute, java.nio's toAbsolutePath is a no-op; the model must agree.
  // The result must equal of("C:/a") and carry exactly one drive colon segment (a cwd join would still
  // carry that single colon but would NOT equal of("C:/a") — the equality is what the defect breaks).
  test("ISS-1384: of(\"C:/a\").toAbsolute equals of(\"C:/a\") with one drive colon and no cwd join") {
    val absolute = FilePath.of("C:/a").toAbsolute
    assertEquals(absolute, FilePath.of("C:/a"))
    assertEquals(absolute.pathString.count(_ == ':'), 1)
  }
}
