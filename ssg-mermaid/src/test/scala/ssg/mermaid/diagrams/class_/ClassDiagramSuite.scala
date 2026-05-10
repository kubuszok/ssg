/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Tests ported from:
 *   mermaid/packages/mermaid/src/diagrams/class/classDiagram.spec.ts
 *   mermaid/packages/mermaid/src/diagrams/class/classTypes.spec.ts
 */
package ssg
package mermaid
package diagrams
package class_

import munit.FunSuite

final class ClassDiagramSuite extends FunSuite {

  private val staticCssStyle   = "text-decoration:underline;"
  private val abstractCssStyle = "font-style:italic;"

  // ────────────────────────────────────────────────────────────────────────────
  // Detection tests
  // ────────────────────────────────────────────────────────────────────────────

  test("detect: classDiagram") {
    assert(ClassDiagram.detect("classDiagram\n    class Animal"))
  }

  test("detect: classDiagram-v2") {
    assert(ClassDiagram.detect("classDiagram-v2\n    class Animal"))
  }

  test("detect: not a class diagram") {
    assert(!ClassDiagram.detect("sequenceDiagram\n    Alice->>Bob: Hello"))
  }

  // ────────────────────────────────────────────────────────────────────────────
  // classDiagram.spec.ts: "given a basic class diagram" / "when parsing class definition"
  // ────────────────────────────────────────────────────────────────────────────

  test("should handle accTitle and accDescr") {
    val str = "classDiagram\n    accTitle: My Title\n    accDescr: My Description"
    val db  = ClassParser.parse(str)
    assertEquals(db.accTitle, "My Title")
    assertEquals(db.accDescription, "My Description")
  }

  test("should handle accTitle and multiline accDescr") {
    val str =
      """classDiagram
        |    accTitle: My Title
        |    accDescr {
        |      This is my multi
        |      line description
        |    }""".stripMargin
    val db = ClassParser.parse(str)
    assertEquals(db.accTitle, "My Title")
    assertEquals(db.accDescription, "This is my multi\nline description")
  }

  test("should handle backquoted class names") {
    val str = "classDiagram\nclass `Car`"
    val db  = ClassParser.parse(str) // verify no error
    assert(db.classes.contains("Car"), str)
  }

  test("should handle class names with dash") {
    val str    = "classDiagram\nclass Ca-r"
    val db     = ClassParser.parse(str)
    val actual = db.getClass("Ca-r")
    assertEquals(actual.label, "Ca-r")
  }

  test("should handle class names with underscore") {
    val str = "classDiagram\nclass `A_Car`"
    ClassParser.parse(str) // Just verify it parses without error
  }

  test("should handle parsing of separators") {
    val str =
      "classDiagram\n" +
        "class Foo1 {\n" +
        "  You can use\n" +
        "  several lines\n" +
        "..\n" +
        "as you want\n" +
        "and group\n" +
        "==\n" +
        "things together.\n" +
        "__\n" +
        "You can have as many groups\n" +
        "as you want\n" +
        "--\n" +
        "End of class\n" +
        "}\n" +
        "\n" +
        "class User {\n" +
        ".. Simple Getter ..\n" +
        "+ getName()\n" +
        "+ getAddress()\n" +
        ".. Some setter ..\n" +
        "+ setName()\n" +
        "__ private data __\n" +
        "int age\n" +
        "-- encrypted --\n" +
        "String password\n" +
        "}"
    ClassParser.parse(str) // Just verify it parses without error
  }

  test("should parse a class with a text label") {
    val db = ClassParser.parse("classDiagram\nclass C1[\"Class 1 with text label\"]")
    val c1 = db.getClass("C1")
    assertEquals(c1.label, "Class 1 with text label")
  }

  test("should parse two classes with text labels") {
    val db = ClassParser.parse(
      "classDiagram\n" +
        "class C1[\"Class 1 with text label\"]\n" +
        "class C2[\"Class 2 with chars @?\"]\n"
    )
    assertEquals(db.getClass("C1").label, "Class 1 with text label")
    assertEquals(db.getClass("C2").label, "Class 2 with chars @?")
  }

  test("should parse a class with a text label and member") {
    val db = ClassParser.parse(
      "classDiagram\nclass C1[\"Class 1 with text label\"]\nC1: member1"
    )
    val c1 = db.getClass("C1")
    assertEquals(c1.label, "Class 1 with text label")
    assertEquals(c1.members.length, 1)
    assertEquals(c1.members(0).displayDetails._1, "member1")
  }

  test("should parse a class with a text label, member and annotation") {
    val db = ClassParser.parse(
      "classDiagram\n" +
        "class C1[\"Class 1 with text label\"]\n" +
        "<<interface>> C1\n" +
        "C1 : int member1"
    )
    val c1 = db.getClass("C1")
    assertEquals(c1.label, "Class 1 with text label")
    assertEquals(c1.members.length, 1)
    assertEquals(c1.members(0).displayDetails._1, "int member1")
    assertEquals(c1.annotations.length, 1)
    assertEquals(c1.annotations(0), "interface")
  }

  test("should parse a class with text label and css class shorthand") {
    val db = ClassParser.parse(
      "classDiagram\nclass C1[\"Class 1 with text label\"]:::styleClass"
    )
    val c1 = db.getClass("C1")
    assertEquals(c1.label, "Class 1 with text label")
    assertEquals(c1.cssClasses(0), "styleClass")
  }

  test("should parse a class with text label and css class") {
    val db = ClassParser.parse(
      "classDiagram\n" +
        "class C1[\"Class 1 with text label\"]\n" +
        "C1 : int member1\n" +
        "cssClass \"C1\" styleClass"
    )
    val c1 = db.getClass("C1")
    assertEquals(c1.label, "Class 1 with text label")
    assertEquals(c1.members(0).displayDetails._1, "int member1")
    assertEquals(c1.cssClasses(0), "styleClass")
  }

  test("should parse two classes with text labels and css classes") {
    val db = ClassParser.parse(
      "classDiagram\n" +
        "class C1[\"Class 1 with text label\"]\n" +
        "C1 : int member1\n" +
        "class C2[\"Long long long long long long long long long long label\"]\n" +
        "cssClass \"C1,C2\" styleClass"
    )
    val c1 = db.getClass("C1")
    assertEquals(c1.label, "Class 1 with text label")
    assertEquals(c1.cssClasses(0), "styleClass")
    val c2 = db.getClass("C2")
    assertEquals(c2.label, "Long long long long long long long long long long label")
    assertEquals(c2.cssClasses(0), "styleClass")
  }

  test("should parse two classes with text labels and css class shorthands") {
    val db = ClassParser.parse(
      "classDiagram\n" +
        "class C1[\"Class 1 with text label\"]:::styleClass1\n" +
        "class C2[\"Class 2 !@#$%^&*() label\"]:::styleClass2"
    )
    val c1 = db.getClass("C1")
    assertEquals(c1.label, "Class 1 with text label")
    assertEquals(c1.cssClasses(0), "styleClass1")
    val c2 = db.getClass("C2")
    assertEquals(c2.label, "Class 2 !@#$%^&*() label")
    assertEquals(c2.cssClasses(0), "styleClass2")
  }

  test("should parse multiple classes with same text labels") {
    val db = ClassParser.parse(
      "classDiagram\n" +
        "class C1[\"Class with text label\"]\n" +
        "class C2[\"Class with text label\"]\n" +
        "class C3[\"Class with text label\"]"
    )
    assertEquals(db.getClass("C1").label, "Class with text label")
    assertEquals(db.getClass("C2").label, "Class with text label")
    assertEquals(db.getClass("C3").label, "Class with text label")
  }

  test("should parse classes with different text labels") {
    val db = ClassParser.parse(
      """classDiagram
        |class C1["OneWord"]
        |class C2["With, Comma"]
        |class C3["With (Brackets)"]
        |class C4["With [Brackets]"]
        |class C5["With {Brackets}"]
        |class C6[" "]
        |class C7["With 1 number"]
        |class C8["With . period..."]
        |class C9["With - dash"]
        |class C10["With _ underscore"]
        |class C11["With ' single quote"]
        |class C12["With ~!@#$%^&*()_+=-/?"]
        |class C13["With foreign language"]""".stripMargin
    )
    assertEquals(db.getClass("C1").label, "OneWord")
    assertEquals(db.getClass("C2").label, "With, Comma")
    assertEquals(db.getClass("C3").label, "With (Brackets)")
    assertEquals(db.getClass("C4").label, "With [Brackets]")
    assertEquals(db.getClass("C5").label, "With {Brackets}")
    assertEquals(db.getClass("C6").label, " ")
    assertEquals(db.getClass("C7").label, "With 1 number")
    assertEquals(db.getClass("C8").label, "With . period...")
    assertEquals(db.getClass("C9").label, "With - dash")
    assertEquals(db.getClass("C10").label, "With _ underscore")
    assertEquals(db.getClass("C11").label, "With ' single quote")
    assertEquals(db.getClass("C12").label, "With ~!@#$%^&*()_+=-/?")
    assertEquals(db.getClass("C13").label, "With foreign language")
  }

  test("should handle 'note for'") {
    val str = "classDiagram\nClass11 <|.. Class12\nnote for Class11 \"test\"\n"
    val db  = ClassParser.parse(str)
    assert(db.notes.nonEmpty)
  }

  test("should handle 'note'") {
    val str = "classDiagram\nnote \"test\"\n"
    val db  = ClassParser.parse(str)
    assert(db.notes.nonEmpty)
    assert(db.notes(0).text.contains("test"))
  }

  test("should parse diagram with direction") {
    val db = ClassParser.parse(
      """classDiagram
        |    direction TB
        |    class Student {
        |        -idCard : IdCard
        |    }
        |    class IdCard{
        |        -id : int
        |        -name : string
        |    }
        |    class Bike{
        |        -id : int
        |        -name : string
        |    }
        |    Student "1" --o "1" IdCard : carries
        |    Student "1" --o "1" Bike : rides""".stripMargin
    )
    assertEquals(db.classes.size, 3)
    val student = db.getClass("Student")
    assertEquals(student.id, "Student")
    assertEquals(student.label, "Student")
    assertEquals(student.members.length, 1)
    assertEquals(student.members(0).visibility, "-")
    assertEquals(student.methods.length, 0)
    assertEquals(db.relations.length, 2)
  }

  test("should revert direction to default once direction is removed") {
    val db1 = ClassParser.parse("classDiagram\n    direction RL\n    class A")
    assertEquals(db1.direction, "RL")
    val db2 = ClassParser.parse("classDiagram\n    class B")
    assertEquals(db2.direction, "TB")
  }

  // ────────────────────────────────────────────────────────────────────────────
  // "when parsing class defined in brackets"
  // ────────────────────────────────────────────────────────────────────────────

  test("should handle member definitions in brackets") {
    val db = ClassParser.parse("classDiagram\nclass Car{\n+int wheels\n}")
    assert(db.classes.contains("Car"))
  }

  test("should handle method definitions in brackets") {
    val db = ClassParser.parse("classDiagram\nclass Car{\n+size()\n}")
    assert(db.classes.contains("Car"))
  }

  test("should handle a mix of members defined in and outside of brackets") {
    val db = ClassParser.parse(
      "classDiagram\nclass Car{\n+int wheels\n}\nCar : +ArrayList size()\n"
    )
    assert(db.classes.contains("Car"))
  }

  test("should handle member and method definitions") {
    val db = ClassParser.parse(
      "classDiagram\nclass Dummy_Class {\nString data\nvoid methods()\n}"
    )
    assert(db.classes.contains("Dummy_Class"))
  }

  test("should handle return types on methods") {
    val db = ClassParser.parse(
      "classDiagram\nclass Flight {\nint flightNumber\ndatetime departureTime\ngetDepartureTime() datetime\n}"
    )
    assert(db.classes.contains("Flight"))
  }

  test("should add bracket members in right order") {
    val db = ClassParser.parse(
      "classDiagram\n" +
        "class Class1 {\n" +
        "int testMember\n" +
        "test()\n" +
        "string fooMember\n" +
        "foo()\n" +
        "}"
    )
    val actual = db.getClass("Class1")
    assertEquals(actual.members.length, 2)
    assertEquals(actual.methods.length, 2)
    assertEquals(actual.members(0).displayDetails._1, "int testMember")
    assertEquals(actual.members(1).displayDetails._1, "string fooMember")
    assertEquals(actual.methods(0).displayDetails._1, "test()")
    assertEquals(actual.methods(1).displayDetails._1, "foo()")
  }

  test("should parse a class with a text label and members in brackets") {
    val db = ClassParser.parse(
      "classDiagram\nclass C1[\"Class 1 with text label\"] {\n+member1\n}"
    )
    val c1 = db.getClass("C1")
    assertEquals(c1.label, "Class 1 with text label")
    assertEquals(c1.members.length, 1)
    assertEquals(c1.members(0).displayDetails._1, "+member1")
  }

  test("should parse a class with a text label, members and annotation in brackets") {
    val db = ClassParser.parse(
      "classDiagram\n" +
        "class C1[\"Class 1 with text label\"] {\n" +
        "<<interface>>\n" +
        "+member1\n" +
        "}"
    )
    val c1 = db.getClass("C1")
    assertEquals(c1.label, "Class 1 with text label")
    assertEquals(c1.members.length, 1)
    assertEquals(c1.members(0).displayDetails._1, "+member1")
    assertEquals(c1.annotations.length, 1)
    assertEquals(c1.annotations(0), "interface")
  }

  // ────────────────────────────────────────────────────────────────────────────
  // "when parsing comments"
  // ────────────────────────────────────────────────────────────────────────────

  test("should handle comments at the start") {
    val str =
      "%% Comment\nclassDiagram\nclass Class1 {\nint : test\nstring : foo\ntest()\nfoo()\n}"
    val db = ClassParser.parse(str)
    assert(db.classes.contains("Class1"))
  }

  test("should handle comments at the end") {
    val str =
      "classDiagram\nclass Class1 {\nint : test\nstring : foo\ntest()\nfoo()\n\n}\n%% Comment\n"
    val db = ClassParser.parse(str)
    assert(db.classes.contains("Class1"))
  }

  test("should handle comments at the end no trailing newline") {
    val str = "classDiagram\nclass Class1 {\nint : test\nstring : foo\ntest()\nfoo()\n}\n%% Comment"
    val db  = ClassParser.parse(str)
    assert(db.classes.contains("Class1"))
  }

  test("should handle a comment with multiple line feeds") {
    val str =
      "classDiagram\n\n\n%% Comment\n\nclass Class1 {\nint : test\nstring : foo\ntest()\nfoo()\n}"
    val db = ClassParser.parse(str)
    assert(db.classes.contains("Class1"))
  }

  test("should handle a comment with mermaid class diagram code in them") {
    val str =
      "classDiagram\n%% Comment Class1 <|-- Class02\nclass Class1 {\nint : test\nstring : foo\ntest()\nfoo()\n}"
    val db = ClassParser.parse(str)
    assert(db.classes.contains("Class1"))
  }

  test("should handle a comment inside brackets") {
    val str =
      "classDiagram\n" +
        "class Class1 {\n" +
        "%% Comment Class1 <|-- Class02\n" +
        "int : test\n" +
        "string : foo\n" +
        "test()\n" +
        "foo()\n" +
        "}"
    val db = ClassParser.parse(str)
    assert(db.classes.contains("Class1"))
  }

  // ────────────────────────────────────────────────────────────────────────────
  // "when parsing click statements"
  // ────────────────────────────────────────────────────────────────────────────

  test("should handle href link") {
    val db = ClassParser.parse(
      "classDiagram\nclass Class1 \nclick Class1 href \"google.com\" "
    )
    val actual = db.getClass("Class1")
    assertEquals(actual.link.get, "google.com")
    assert(actual.cssClasses.contains("clickable"))
  }

  test("should handle href link with tooltip") {
    val db = ClassParser.parse(
      "classDiagram\nclass Class1 \nclick Class1 href \"google.com\" \"A Tooltip\" "
    )
    val actual = db.getClass("Class1")
    assertEquals(actual.link.get, "google.com")
    assertEquals(actual.tooltip.get, "A Tooltip")
    assert(actual.cssClasses.contains("clickable"))
  }

  test("should handle href link with tooltip and target") {
    val db = ClassParser.parse(
      "classDiagram\nclass Class1\nClass1 : someMethod()\nclick Class1 href \"google.com\" \"A tooltip\" _self"
    )
    val actual = db.getClass("Class1")
    assertEquals(actual.link.get, "google.com")
    assertEquals(actual.tooltip.get, "A tooltip")
    assert(actual.cssClasses.contains("clickable"))
  }

  test("should handle function call") {
    val db = ClassParser.parse(
      "classDiagram\nclass Class1 \nclick Class1 call functionCall() "
    )
    val actual = db.getClass("Class1")
    assert(actual.haveCallback)
  }

  test("should handle function call with tooltip") {
    val db = ClassParser.parse(
      "classDiagram\nclass Class1 \nclick Class1 call functionCall() \"A Tooltip\" "
    )
    val actual = db.getClass("Class1")
    assert(actual.haveCallback)
    assertEquals(actual.tooltip.get, "A Tooltip")
  }

  test("should handle function call with an arbitrary number of args") {
    val db = ClassParser.parse(
      "classDiagram\nclass Class1\nClass1 : someMethod()\nclick Class1 call functionCall(test, test1, test2)"
    )
    val actual = db.getClass("Class1")
    assert(actual.haveCallback)
  }

  test("should handle function call with an arbitrary number of args and tooltip") {
    val db = ClassParser.parse(
      "classDiagram\nclass Class1\nClass1 : someMethod()\nclick Class1 call functionCall(\"test0\", test1, test2) \"A Tooltip\""
    )
    val actual = db.getClass("Class1")
    assert(actual.haveCallback)
    assertEquals(actual.tooltip.get, "A Tooltip")
  }

  // ────────────────────────────────────────────────────────────────────────────
  // "when parsing annotations"
  // ────────────────────────────────────────────────────────────────────────────

  test("should handle class annotations") {
    val db     = ClassParser.parse("classDiagram\nclass Class1\n<<interface>> Class1")
    val actual = db.getClass("Class1")
    assertEquals(actual.annotations.length, 1)
    assertEquals(actual.members.length, 0)
    assertEquals(actual.methods.length, 0)
    assertEquals(actual.annotations(0), "interface")
  }

  test("should handle class annotations with members and methods") {
    val db = ClassParser.parse(
      "classDiagram\nclass Class1\nClass1 : int test\nClass1 : test()\n<<interface>> Class1"
    )
    val actual = db.getClass("Class1")
    assertEquals(actual.annotations.length, 1)
    assertEquals(actual.members.length, 1)
    assertEquals(actual.methods.length, 1)
    assertEquals(actual.annotations(0), "interface")
  }

  test("should handle class annotations in brackets") {
    val db     = ClassParser.parse("classDiagram\nclass Class1 {\n<<interface>>\n}")
    val actual = db.getClass("Class1")
    assertEquals(actual.annotations.length, 1)
    assertEquals(actual.members.length, 0)
    assertEquals(actual.methods.length, 0)
    assertEquals(actual.annotations(0), "interface")
  }

  test("should handle class annotations in brackets with members and methods") {
    val db = ClassParser.parse(
      "classDiagram\nclass Class1 {\n<<interface>>\nint : test\ntest()\n}"
    )
    val actual = db.getClass("Class1")
    assertEquals(actual.annotations.length, 1)
    assertEquals(actual.members.length, 1)
    assertEquals(actual.methods.length, 1)
    assertEquals(actual.annotations(0), "interface")
  }

  // ────────────────────────────────────────────────────────────────────────────
  // "given a class diagram with members and methods" / "when parsing members"
  // ────────────────────────────────────────────────────────────────────────────

  test("should handle simple member declaration") {
    val db = ClassParser.parse("classDiagram\nclass Car\nCar : wheels")
    assert(db.classes.contains("Car"))
  }

  test("should handle direct member declaration") {
    val db  = ClassParser.parse("classDiagram\nCar : wheels")
    val car = db.getClass("Car")
    assertEquals(car.members.length, 1)
    assertEquals(car.members(0).id, "wheels")
  }

  test("should handle direct member declaration with type") {
    val db  = ClassParser.parse("classDiagram\nCar : int wheels")
    val car = db.getClass("Car")
    assertEquals(car.members.length, 1)
    assertEquals(car.members(0).id, "int wheels")
  }

  test("should handle simple member declaration with type") {
    val db = ClassParser.parse("classDiagram\nclass Car\nCar : int wheels")
    assert(db.classes.contains("Car"))
  }

  test("should handle member visibility") {
    val db = ClassParser.parse(
      "classDiagram\n" +
        "class actual\n" +
        "actual : -int privateMember\n" +
        "actual : +int publicMember\n" +
        "actual : #int protectedMember\n" +
        "actual : ~int privatePackage"
    )
    val actual = db.getClass("actual")
    assertEquals(actual.members.length, 4)
    assertEquals(actual.methods.length, 0)
    assertEquals(actual.members(0).displayDetails._1, "-int privateMember")
    assertEquals(actual.members(1).displayDetails._1, "+int publicMember")
    assertEquals(actual.members(2).displayDetails._1, "#int protectedMember")
    assertEquals(actual.members(3).displayDetails._1, "~int privatePackage")
  }

  test("should handle generic types in member declaration") {
    val db = ClassParser.parse("classDiagram\nclass Car\nCar : -List~Wheel~ wheels")
    assert(db.classes.contains("Car"))
  }

  // ────────────────────────────────────────────────────────────────────────────
  // "when parsing method definition"
  // ────────────────────────────────────────────────────────────────────────────

  test("should handle method definition") {
    val db = ClassParser.parse("classDiagram\nclass Car\nCar : GetSize()")
    assert(db.classes.contains("Car"))
  }

  test("should handle simple return types") {
    val db = ClassParser.parse("classDiagram\nclass Object\nObject : getObject() Object")
    assert(db.classes.contains("Object"))
  }

  test("should handle return types as array") {
    val db = ClassParser.parse("classDiagram\nclass Object\nObject : getObjects() Object[]")
    assert(db.classes.contains("Object"))
  }

  test("should handle method visibility") {
    val db = ClassParser.parse(
      "classDiagram\nclass actual\n" +
        "actual : -privateMethod()\n" +
        "actual : +publicMethod()\n" +
        "actual : #protectedMethod()\n"
    )
    val actual = db.getClass("actual")
    assertEquals(actual.methods.length, 3)
  }

  test("should handle abstract methods via colon syntax") {
    val db     = ClassParser.parse("classDiagram\nclass Class1\nClass1 : someMethod()*")
    val actual = db.getClass("Class1")
    assertEquals(actual.annotations.length, 0)
    assertEquals(actual.members.length, 0)
    assertEquals(actual.methods.length, 1)
    val method = actual.methods(0)
    assertEquals(method.displayDetails._1, "someMethod()")
    assertEquals(method.displayDetails._2, abstractCssStyle)
  }

  test("should handle static methods via colon syntax") {
    val db     = ClassParser.parse("classDiagram\nclass Class1\nClass1 : someMethod()$")
    val actual = db.getClass("Class1")
    assertEquals(actual.annotations.length, 0)
    assertEquals(actual.members.length, 0)
    assertEquals(actual.methods.length, 1)
    val method = actual.methods(0)
    assertEquals(method.displayDetails._1, "someMethod()")
    assertEquals(method.displayDetails._2, staticCssStyle)
  }

  test("should handle generic types in arguments") {
    val db = ClassParser.parse("classDiagram\nclass Car\nCar : +setWheels(List~Wheel~ wheels)")
    assert(db.classes.contains("Car"))
  }

  test("should handle generic return types") {
    val db = ClassParser.parse("classDiagram\nclass Car\nCar : +getWheels() List~Wheel~")
    assert(db.classes.contains("Car"))
  }

  test("should handle generic types in members in class with brackets") {
    val db = ClassParser.parse(
      "classDiagram\n" +
        "class Car {\n" +
        "List~Wheel~ wheels\n" +
        "setWheels(List~Wheel~ wheels)\n" +
        "+getWheels() List~Wheel~\n" +
        "}"
    )
    assert(db.classes.contains("Car"))
  }

  // ────────────────────────────────────────────────────────────────────────────
  // "given a class diagram with generics"
  // ────────────────────────────────────────────────────────────────────────────

  test("should handle generic class") {
    val db = ClassParser.parse("classDiagram\nclass Car~T~")
    assert(db.classes.contains("Car"))
  }

  test("should handle generic class with relationships") {
    val db = ClassParser.parse(
      "classDiagram\n" +
        "class Car~T~\n" +
        "Driver -- Car : drives >\n" +
        "Car *-- Wheel : have 4 >\n" +
        "Car -- Person : < owns"
    )
    assert(db.classes.contains("Car"))
    assertEquals(db.relations.length, 3)
  }

  test("should handle generic class with a literal name") {
    val db = ClassParser.parse(
      "classDiagram\n" +
        "class `Car`~T~\n" +
        "Driver -- `Car` : drives >\n" +
        "`Car` *-- Wheel : have 4 >\n" +
        "`Car` -- Person : < owns"
    )
    assert(db.classes.contains("Car"))
  }

  test("should handle generic class with brackets") {
    val db = ClassParser.parse(
      "classDiagram\n" +
        "class Dummy_Class~T~ {\n" +
        "String data\n" +
        "void methods()\n" +
        "}\n" +
        "\n" +
        "class Flight {\n" +
        "Integer flightNumber\n" +
        "Date departureTime\n" +
        "}"
    )
    assert(db.classes.contains("Dummy_Class"))
    assert(db.classes.contains("Flight"))
  }

  test("should handle generic class with brackets and a literal name") {
    val db = ClassParser.parse(
      "classDiagram\n" +
        "class `Dummy_Class`~T~ {\n" +
        "String data\n" +
        "  void methods()\n" +
        "}\n" +
        "\n" +
        "class Flight {\n" +
        "   flightNumber : Integer\n" +
        "   departureTime : Date\n" +
        "}"
    )
    assert(db.classes.contains("Dummy_Class"))
    assert(db.classes.contains("Flight"))
  }

  test("should handle namespace") {
    val db = ClassParser.parse(
      """classDiagram
        |namespace Namespace1 { class Class1 }
        |namespace Namespace2 { class Class1
        |}
        |namespace Namespace3 {
        |class Class1 {
        |int : test
        |string : foo
        |test()
        |foo()
        |}
        |}
        |namespace Namespace4 {
        |class Class1 {
        |int : test
        |string : foo
        |test()
        |foo()
        |}
        |class Class2 {
        |int : test
        |string : foo
        |test()
        |foo()
        |}
        |}""".stripMargin
    )
    assert(db.namespaces.contains("Namespace1"))
    assert(db.namespaces.contains("Namespace2"))
    assert(db.namespaces.contains("Namespace3"))
    assert(db.namespaces.contains("Namespace4"))
  }

  test("should handle namespace with generic types") {
    val db = ClassParser.parse(
      """classDiagram
        |
        |namespace space {
        |    class Square~Shape~{
        |        int id
        |        List~int~ position
        |        setPoints(List~int~ points)
        |        getPoints() List~int~
        |    }
        |}""".stripMargin
    )
    assert(db.classes.contains("Square"))
  }

  // ────────────────────────────────────────────────────────────────────────────
  // "given a class diagram with relationships" / "when parsing basic relationships"
  // ────────────────────────────────────────────────────────────────────────────

  test("should handle all basic relationships") {
    val db = ClassParser.parse(
      "classDiagram\n" +
        "Class1 <|-- Class02\n" +
        "Class03 *-- Class04\n" +
        "Class05 o-- Class06\n" +
        "Class07 .. Class08\n" +
        "Class09 -- Class1"
    )
    assertEquals(db.relations.length, 5)
  }

  test("should handle backquoted class name in relationships") {
    val db = ClassParser.parse(
      "classDiagram\n" +
        "`Class1` <|-- Class02\n" +
        "Class03 *-- Class04\n" +
        "Class05 o-- Class06\n" +
        "Class07 .. Class08\n" +
        "Class09 -- Class1"
    )
    assertEquals(db.relations.length, 5)
  }

  test("should handle generics in relationships") {
    val db        = ClassParser.parse("classDiagram\nClass1~T~ <|-- Class02")
    val relations = db.relations
    assertEquals(db.getClass("Class1").id, "Class1")
    assertEquals(db.getClass("Class1").classType, "T")
    assertEquals(db.getClass("Class02").id, "Class02")
    assertEquals(relations(0).relationType.type1, ClassRelationType.Extension)
    assertEquals(relations(0).relationType.type2, ClassRelationType.None)
    assertEquals(relations(0).relationType.lineType, ClassLineType.Line)
  }

  test("should handle relationships with labels") {
    val db = ClassParser.parse(
      "classDiagram\n" +
        "class Car\n" +
        "Driver -- Car : drives >\n" +
        "Car *-- Wheel : have 4 >\n" +
        "Car -- Person : < owns"
    )
    assertEquals(db.relations.length, 3)
  }

  test("should handle relation definitions EXTENSION") {
    val db        = ClassParser.parse("classDiagram\nClass1 <|-- Class02")
    val relations = db.relations
    assertEquals(db.getClass("Class1").id, "Class1")
    assertEquals(db.getClass("Class02").id, "Class02")
    assertEquals(relations(0).relationType.type1, ClassRelationType.Extension)
    assertEquals(relations(0).relationType.type2, ClassRelationType.None)
    assertEquals(relations(0).relationType.lineType, ClassLineType.Line)
  }

  test("should handle relation definition of different types and directions") {
    val db = ClassParser.parse(
      "classDiagram\n" +
        "Class11 <|.. Class12\n" +
        "Class13 --> Class14\n" +
        "Class15 ..> Class16\n" +
        "Class17 ..|> Class18\n" +
        "Class19 <--* Class20"
    )
    assertEquals(db.relations.length, 5)
  }

  test("should handle cardinality and labels") {
    val db = ClassParser.parse(
      "classDiagram\n" +
        "Class1 \"1\" *-- \"many\" Class02 : contains\n" +
        "Class03 o-- Class04 : aggregation\n" +
        "Class05 --> \"1\" Class06"
    )
    assertEquals(db.relations.length, 3)
  }

  test("should handle dashed relation definition of different types and directions") {
    val db = ClassParser.parse(
      "classDiagram\n" +
        "Class11 <|.. Class12\n" +
        "Class13 <.. Class14\n" +
        "Class15 ..|> Class16\n" +
        "Class17 ..> Class18\n" +
        "Class19 .. Class20"
    )
    assertEquals(db.relations.length, 5)
  }

  test("should handle relation definitions AGGREGATION and dotted line") {
    val db        = ClassParser.parse("classDiagram\nClass1 o.. Class02")
    val relations = db.relations
    assertEquals(db.getClass("Class1").id, "Class1")
    assertEquals(db.getClass("Class02").id, "Class02")
    assertEquals(relations(0).relationType.type1, ClassRelationType.Aggregation)
    assertEquals(relations(0).relationType.type2, ClassRelationType.None)
    assertEquals(relations(0).relationType.lineType, ClassLineType.DottedLine)
  }

  test("should handle relation definitions COMPOSITION on both sides") {
    val db        = ClassParser.parse("classDiagram\nClass1 *--* Class02")
    val relations = db.relations
    assertEquals(db.getClass("Class1").id, "Class1")
    assertEquals(db.getClass("Class02").id, "Class02")
    assertEquals(relations(0).relationType.type1, ClassRelationType.Composition)
    assertEquals(relations(0).relationType.type2, ClassRelationType.Composition)
    assertEquals(relations(0).relationType.lineType, ClassLineType.Line)
  }

  test("should handle relation definitions with no types") {
    val db        = ClassParser.parse("classDiagram\nClass1 -- Class02")
    val relations = db.relations
    assertEquals(db.getClass("Class1").id, "Class1")
    assertEquals(db.getClass("Class02").id, "Class02")
    assertEquals(relations(0).relationType.type1, ClassRelationType.None)
    assertEquals(relations(0).relationType.type2, ClassRelationType.None)
    assertEquals(relations(0).relationType.lineType, ClassLineType.Line)
  }

  test("should handle relation definitions with type only on right side") {
    val db        = ClassParser.parse("classDiagram\nClass1 --|> Class02")
    val relations = db.relations
    assertEquals(db.getClass("Class1").id, "Class1")
    assertEquals(db.getClass("Class02").id, "Class02")
    assertEquals(relations(0).relationType.type1, ClassRelationType.None)
    assertEquals(relations(0).relationType.type2, ClassRelationType.Extension)
    assertEquals(relations(0).relationType.lineType, ClassLineType.Line)
  }

  test("should handle multiple classes and relation definitions") {
    val db = ClassParser.parse(
      "classDiagram\n" +
        "Class1 <|-- Class02\n" +
        "Class03 *-- Class04\n" +
        "Class05 o-- Class06\n" +
        "Class07 .. Class08\n" +
        "Class09 -- Class10"
    )
    val relations = db.relations
    assertEquals(db.getClass("Class1").id, "Class1")
    assertEquals(db.getClass("Class10").id, "Class10")
    assertEquals(relations.length, 5)
    assertEquals(relations(0).relationType.type1, ClassRelationType.Extension)
    assertEquals(relations(0).relationType.type2, ClassRelationType.None)
    assertEquals(relations(0).relationType.lineType, ClassLineType.Line)
    assertEquals(relations(3).relationType.type1, ClassRelationType.None)
    assertEquals(relations(3).relationType.type2, ClassRelationType.None)
    assertEquals(relations(3).relationType.lineType, ClassLineType.DottedLine)
  }

  test("should handle generic class with relation definitions") {
    val db        = ClassParser.parse("classDiagram\nClass01~T~ <|-- Class02")
    val relations = db.relations
    assertEquals(db.getClass("Class01").id, "Class01")
    assertEquals(db.getClass("Class01").classType, "T")
    assertEquals(db.getClass("Class02").id, "Class02")
    assertEquals(relations(0).relationType.type1, ClassRelationType.Extension)
    assertEquals(relations(0).relationType.type2, ClassRelationType.None)
    assertEquals(relations(0).relationType.lineType, ClassLineType.Line)
  }

  test("should handle class annotations in relationships section") {
    val db        = ClassParser.parse("classDiagram\nclass Class1\n<<interface>> Class1")
    val testClass = db.getClass("Class1")
    assertEquals(testClass.annotations.length, 1)
    assertEquals(testClass.members.length, 0)
    assertEquals(testClass.methods.length, 0)
    assertEquals(testClass.annotations(0), "interface")
  }

  test("should handle class annotations with members and methods in relationships section") {
    val db = ClassParser.parse(
      "classDiagram\nclass Class1\nClass1 : int test\nClass1 : test()\n<<interface>> Class1"
    )
    val testClass = db.getClass("Class1")
    assertEquals(testClass.annotations.length, 1)
    assertEquals(testClass.members.length, 1)
    assertEquals(testClass.methods.length, 1)
    assertEquals(testClass.annotations(0), "interface")
  }

  test("should handle class annotations in brackets in relationships section") {
    val db        = ClassParser.parse("classDiagram\nclass Class1 {\n<<interface>>\n}")
    val testClass = db.getClass("Class1")
    assertEquals(testClass.annotations.length, 1)
    assertEquals(testClass.members.length, 0)
    assertEquals(testClass.methods.length, 0)
    assertEquals(testClass.annotations(0), "interface")
  }

  test("should handle class annotations in brackets with members and methods in relationships section") {
    val db = ClassParser.parse(
      "classDiagram\nclass Class1 {\n<<interface>>\nint : test\ntest()\n}"
    )
    val testClass = db.getClass("Class1")
    assertEquals(testClass.annotations.length, 1)
    assertEquals(testClass.members.length, 1)
    assertEquals(testClass.methods.length, 1)
    assertEquals(testClass.annotations(0), "interface")
  }

  test("should add bracket members in right order in relationships section") {
    val db = ClassParser.parse(
      "classDiagram\nclass Class1 {\nint : test\nstring : foo\ntest()\nfoo()\n}"
    )
    val testClass = db.getClass("Class1")
    assertEquals(testClass.members.length, 2)
    assertEquals(testClass.methods.length, 2)
    assertEquals(testClass.members(0).displayDetails._1, "int : test")
    assertEquals(testClass.members(1).displayDetails._1, "string : foo")
    assertEquals(testClass.methods(0).displayDetails._1, "test()")
    assertEquals(testClass.methods(1).displayDetails._1, "foo()")
  }

  test("should handle abstract methods in relationships section") {
    val db        = ClassParser.parse("classDiagram\nclass Class1\nClass1 : someMethod()*")
    val testClass = db.getClass("Class1")
    assertEquals(testClass.annotations.length, 0)
    assertEquals(testClass.members.length, 0)
    assertEquals(testClass.methods.length, 1)
    val method = testClass.methods(0)
    assertEquals(method.displayDetails._1, "someMethod()")
    assertEquals(method.displayDetails._2, abstractCssStyle)
  }

  test("should handle static methods in relationships section") {
    val db        = ClassParser.parse("classDiagram\nclass Class1\nClass1 : someMethod()$")
    val testClass = db.getClass("Class1")
    assertEquals(testClass.annotations.length, 0)
    assertEquals(testClass.members.length, 0)
    assertEquals(testClass.methods.length, 1)
    val method = testClass.methods(0)
    assertEquals(method.displayDetails._1, "someMethod()")
    assertEquals(method.displayDetails._2, staticCssStyle)
  }

  test("should associate link and css appropriately") {
    val db = ClassParser.parse(
      "classDiagram\nclass Class1\nClass1 : someMethod()\nlink Class1 \"google.com\""
    )
    val testClass = db.getClass("Class1")
    assertEquals(testClass.link.get, "google.com")
    assertEquals(testClass.cssClasses.length, 1)
    assertEquals(testClass.cssClasses(0), "clickable")
  }

  test("should associate click and href link and css appropriately") {
    val db = ClassParser.parse(
      "classDiagram\nclass Class1\nClass1 : someMethod()\nclick Class1 href \"google.com\""
    )
    val testClass = db.getClass("Class1")
    assertEquals(testClass.link.get, "google.com")
    assertEquals(testClass.cssClasses.length, 1)
    assertEquals(testClass.cssClasses(0), "clickable")
  }

  test("should associate link with tooltip") {
    val db = ClassParser.parse(
      "classDiagram\nclass Class1\nClass1 : someMethod()\nlink Class1 \"google.com\" \"A tooltip\""
    )
    val testClass = db.getClass("Class1")
    assertEquals(testClass.link.get, "google.com")
    assertEquals(testClass.tooltip.get, "A tooltip")
    assertEquals(testClass.cssClasses.length, 1)
    assertEquals(testClass.cssClasses(0), "clickable")
  }

  test("should associate click and href link with tooltip") {
    val db = ClassParser.parse(
      "classDiagram\nclass Class1\nClass1 : someMethod()\nclick Class1 href \"google.com\" \"A tooltip\""
    )
    val testClass = db.getClass("Class1")
    assertEquals(testClass.link.get, "google.com")
    assertEquals(testClass.tooltip.get, "A tooltip")
    assertEquals(testClass.cssClasses.length, 1)
    assertEquals(testClass.cssClasses(0), "clickable")
  }

  test("should associate callback appropriately") {
    val db = ClassParser.parse(
      "classDiagram\nclass Class1\nClass1 : someMethod()\ncallback Class1 \"functionCall\""
    )
    val testClass = db.getClass("Class1")
    assert(testClass.haveCallback)
  }

  test("should associate click and call callback appropriately") {
    val db = ClassParser.parse(
      "classDiagram\nclass Class1\nClass1 : someMethod()\nclick Class1 call functionCall()"
    )
    val testClass = db.getClass("Class1")
    assert(testClass.haveCallback)
  }

  test("should associate callback with tooltip") {
    val db = ClassParser.parse(
      "classDiagram\nclass Class1\nClass1 : someMethod()\nclick Class1 call functionCall() \"A tooltip\""
    )
    val testClass = db.getClass("Class1")
    assert(testClass.haveCallback)
    assertEquals(testClass.tooltip.get, "A tooltip")
  }

  test("should add classes to namespaces") {
    val db = ClassParser.parse(
      """classDiagram
        |namespace Namespace1 {
        |class Class1 {
        |int : test
        |string : foo
        |test()
        |foo()
        |}
        |class Class2
        |}""".stripMargin
    )
    val testNs = db.namespaces("Namespace1")
    assertEquals(testNs.classes.size, 2)
    assertEquals(testNs.classes.get("Class1").map(_.id), Some("Class1"))
    assertEquals(db.classes.size, 2)
  }

  test("should add relations between classes of different namespaces") {
    val db = ClassParser.parse(
      """classDiagram
        |      A1 --> B1
        |      namespace A {
        |        class A1 {
        |          +foo : string
        |        }
        |        class A2 {
        |          +bar : int
        |        }
        |      }
        |      namespace B {
        |        class B1 {
        |          +foo : bool
        |        }
        |        class B2 {
        |          +bar : float
        |        }
        |      }
        |      A2 --> B2""".stripMargin
    )
    val testNsA = db.namespaces("A")
    val testNsB = db.namespaces("B")
    assertEquals(testNsA.classes.size, 2)
    assertEquals(testNsB.classes.size, 2)
    assertEquals(db.classes.size, 4)
    assertEquals(db.classes("A1").parent.get, "A")
    assertEquals(db.classes("A2").parent.get, "A")
    assertEquals(db.classes("B1").parent.get, "B")
    assertEquals(db.classes("B2").parent.get, "B")
    assertEquals(db.relations(0).id1, "A1")
    assertEquals(db.relations(0).id2, "B1")
    assertEquals(db.relations(1).id1, "A2")
    assertEquals(db.relations(1).id2, "B2")
  }

  // ────────────────────────────────────────────────────────────────────────────
  // "when parsing classDiagram with text labels" (relationships section)
  // ────────────────────────────────────────────────────────────────────────────

  test("text labels: should parse a class with a text label and relation") {
    val db = ClassParser.parse(
      "classDiagram\nclass C1[\"Class 1 with text label\"]\nC1 -->  C2\n"
    )
    assertEquals(db.getClass("C1").label, "Class 1 with text label")
    assertEquals(db.getClass("C2").label, "C2")
  }

  test("text labels: should parse two classes with text labels and relation") {
    val db = ClassParser.parse(
      "classDiagram\nclass C1[\"Class 1 with text label\"]\nclass C2[\"Class 2 with chars @?\"]\nC1 -->  C2\n"
    )
    assertEquals(db.getClass("C1").label, "Class 1 with text label")
    assertEquals(db.getClass("C2").label, "Class 2 with chars @?")
  }

  test("text labels: should parse a class with a text label and members and relation") {
    val db = ClassParser.parse(
      "classDiagram\nclass C1[\"Class 1 with text label\"] {\n+member1\n}\nC1 -->  C2\n"
    )
    val c1 = db.getClass("C1")
    assertEquals(c1.label, "Class 1 with text label")
    assertEquals(c1.members.length, 1)
    assertEquals(c1.members(0).displayDetails._1, "+member1")
    assertEquals(db.getClass("C2").label, "C2")
  }

  test("text labels: should parse a class with text label, members and annotation and relation") {
    val db = ClassParser.parse(
      "classDiagram\nclass C1[\"Class 1 with text label\"] {\n<<interface>>\n+member1\n}\nC1 -->  C2\n"
    )
    val c1 = db.getClass("C1")
    assertEquals(c1.label, "Class 1 with text label")
    assertEquals(c1.members.length, 1)
    assertEquals(c1.annotations.length, 1)
    assertEquals(c1.annotations(0), "interface")
    assertEquals(c1.members(0).displayDetails._1, "+member1")
    assertEquals(db.getClass("C2").label, "C2")
  }

  test("text labels: should parse a class with text label and css class shorthand and members") {
    val db = ClassParser.parse(
      "classDiagram\nclass C1[\"Class 1 with text label\"]:::styleClass {\n+member1\n}\nC1 -->  C2\n"
    )
    val c1 = db.getClass("C1")
    assertEquals(c1.label, "Class 1 with text label")
    assertEquals(c1.cssClasses.length, 1)
    assertEquals(c1.cssClasses(0), "styleClass")
    assertEquals(c1.members(0).displayDetails._1, "+member1")
  }

  test("text labels: should parse a class with text label and css class and members") {
    val db = ClassParser.parse(
      "classDiagram\n" +
        "class C1[\"Class 1 with text label\"] {\n+member1\n}\n" +
        "C1 --> C2\n" +
        "cssClass \"C1\" styleClass\n"
    )
    val c1 = db.getClass("C1")
    assertEquals(c1.label, "Class 1 with text label")
    assertEquals(c1.cssClasses.length, 1)
    assertEquals(c1.cssClasses(0), "styleClass")
    assertEquals(c1.members(0).displayDetails._1, "+member1")
  }

  test("text labels: should parse two classes with text labels and css classes") {
    val db = ClassParser.parse(
      "classDiagram\n" +
        "class C1[\"Class 1 with text label\"] {\n+member1\n}\n" +
        "class C2[\"Long long long long long long long long long long label\"]\n" +
        "C1 --> C2\n" +
        "cssClass \"C1,C2\" styleClass\n"
    )
    val c1 = db.getClass("C1")
    assertEquals(c1.label, "Class 1 with text label")
    assertEquals(c1.cssClasses.length, 1)
    assertEquals(c1.cssClasses(0), "styleClass")

    val c2 = db.getClass("C2")
    assertEquals(c2.label, "Long long long long long long long long long long label")
    assertEquals(c2.cssClasses.length, 1)
    assertEquals(c2.cssClasses(0), "styleClass")
  }

  test("text labels: should parse two classes with text labels and css class shorthands") {
    val db = ClassParser.parse(
      "classDiagram\n" +
        "class C1[\"Class 1 with text label\"]:::styleClass1 {\n+member1\n}\n" +
        "class C2[\"Class 2 !@#$%^&*() label\"]:::styleClass2\n" +
        "C1 --> C2\n"
    )
    val c1 = db.getClass("C1")
    assertEquals(c1.label, "Class 1 with text label")
    assertEquals(c1.cssClasses.length, 1)
    assertEquals(c1.cssClasses(0), "styleClass1")

    val c2 = db.getClass("C2")
    assertEquals(c2.label, "Class 2 !@#$%^&*() label")
    assertEquals(c2.cssClasses.length, 1)
    assertEquals(c2.cssClasses(0), "styleClass2")
  }

  test("text labels: should parse multiple classes with same text labels and relations") {
    val db = ClassParser.parse(
      "classDiagram\n" +
        "class C1[\"Class with text label\"]\n" +
        "class C2[\"Class with text label\"]\n" +
        "class C3[\"Class with text label\"]\n" +
        "C1 --> C2\n" +
        "C3 ..> C2\n"
    )
    assertEquals(db.getClass("C1").label, "Class with text label")
    assertEquals(db.getClass("C2").label, "Class with text label")
    assertEquals(db.getClass("C3").label, "Class with text label")
  }

  test("text labels: should parse classes with different text labels and relations") {
    val db = ClassParser.parse(
      """classDiagram
        |class C1["OneWord"]
        |class C2["With, Comma"]
        |class C3["With (Brackets)"]
        |class C4["With [Brackets]"]
        |class C5["With {Brackets}"]
        |class C6[" "]
        |class C7["With 1 number"]
        |class C8["With . period..."]
        |class C9["With - dash"]
        |class C10["With _ underscore"]
        |class C11["With ' single quote"]
        |class C12["With ~!@#$%^&*()_+=-/?"]
        |class C13["With foreign language"]""".stripMargin
    )
    assertEquals(db.getClass("C1").label, "OneWord")
    assertEquals(db.getClass("C2").label, "With, Comma")
    assertEquals(db.getClass("C3").label, "With (Brackets)")
    assertEquals(db.getClass("C4").label, "With [Brackets]")
    assertEquals(db.getClass("C5").label, "With {Brackets}")
    assertEquals(db.getClass("C6").label, " ")
    assertEquals(db.getClass("C7").label, "With 1 number")
    assertEquals(db.getClass("C8").label, "With . period...")
    assertEquals(db.getClass("C9").label, "With - dash")
    assertEquals(db.getClass("C10").label, "With _ underscore")
    assertEquals(db.getClass("C11").label, "With ' single quote")
    assertEquals(db.getClass("C12").label, "With ~!@#$%^&*()_+=-/?")
    assertEquals(db.getClass("C13").label, "With foreign language")
  }

  // ────────────────────────────────────────────────────────────────────────────
  // ClassDb unit tests
  // ────────────────────────────────────────────────────────────────────────────

  test("ClassDb.clear resets all state") {
    val db = new ClassDb
    db.addClass("Animal")
    db.addClass("Duck")
    db.addRelation(ClassRelation("Animal", "Duck"))
    db.clear()
    assert(db.classes.isEmpty)
    assert(db.relations.isEmpty)
  }

  test("ClassDb.setDirection") {
    val db = new ClassDb
    db.setDirection("LR")
    assertEquals(db.direction, "LR")
  }

  test("ClassDb.addMembers reverses order") {
    val db = new ClassDb
    db.addClass("MyClass")
    db.addMembers("MyClass", Array("+method1()", "+method2()"))
    // Original reverses, so method2 should be added before method1
    assertEquals(db.classes("MyClass").methods.length, 2)
  }

  // ────────────────────────────────────────────────────────────────────────────
  // More parser tests from the original
  // ────────────────────────────────────────────────────────────────────────────

  test("parse: basic class declaration") {
    val db = ClassParser.parse(
      """classDiagram
        |    class Animal""".stripMargin
    )
    assert(db.classes.contains("Animal"), "Animal class should exist")
  }

  test("parse: class with members and methods") {
    val db = ClassParser.parse(
      """classDiagram
        |    class Duck {
        |        +String beakColor
        |        +swim()
        |        +quack()
        |    }""".stripMargin
    )
    assert(db.classes.contains("Duck"), "Duck class should exist")
    val duck = db.classes("Duck")
    assert(duck.members.nonEmpty, "Duck should have members")
    assert(duck.methods.nonEmpty, "Duck should have methods")
  }

  test("parse: inheritance relation") {
    val db = ClassParser.parse(
      """classDiagram
        |    Animal <|-- Duck""".stripMargin
    )
    assert(db.relations.nonEmpty, "Should have at least one relation")
    val rel = db.relations.head
    assertEquals(rel.id1, "Animal")
    assertEquals(rel.id2, "Duck")
  }

  test("parse: composition relation") {
    val db = ClassParser.parse(
      """classDiagram
        |    Car *-- Engine""".stripMargin
    )
    assert(db.relations.nonEmpty)
    val rel = db.relations.head
    assert(
      rel.relationType.type1 == ClassRelationType.Composition ||
        rel.relationType.type2 == ClassRelationType.Composition,
      "Should have composition relation type"
    )
  }

  test("parse: aggregation relation") {
    val db = ClassParser.parse(
      """classDiagram
        |    Library o-- Book""".stripMargin
    )
    assert(db.relations.nonEmpty)
    val rel = db.relations.head
    assert(
      rel.relationType.type1 == ClassRelationType.Aggregation ||
        rel.relationType.type2 == ClassRelationType.Aggregation,
      "Should have aggregation relation type"
    )
  }

  test("parse: relation with label") {
    val db = ClassParser.parse(
      """classDiagram
        |    Animal <|-- Duck : can fly""".stripMargin
    )
    assert(db.relations.nonEmpty)
    assert(db.relations.head.title.contains("can fly"), "Relation should have label 'can fly'")
  }

  test("parse: annotation") {
    val db = ClassParser.parse(
      """classDiagram
        |    class Shape
        |    <<interface>> Shape""".stripMargin
    )
    val shape = db.classes("Shape")
    assert(shape.annotations.contains("interface"), "Shape should have <<interface>> annotation")
  }

  test("parse: note for class") {
    val db = ClassParser.parse(
      """classDiagram
        |    class Animal
        |    note for Animal "This is an animal" """.stripMargin
    )
    assert(db.notes.nonEmpty, "Should have at least one note")
    assertEquals(db.notes.head.className, "Animal")
    assert(db.notes.head.text.contains("This is an animal"))
  }

  test("parse: direction") {
    val db = ClassParser.parse(
      """classDiagram
        |    direction LR
        |    class Animal""".stripMargin
    )
    assertEquals(db.direction, "LR")
  }

  test("parse: namespace") {
    val db = ClassParser.parse(
      """classDiagram
        |    namespace Animals {
        |        class Duck
        |        class Fish
        |    }""".stripMargin
    )
    assert(db.namespaces.contains("Animals"), "Should have Animals namespace")
    assert(db.classes.contains("Duck"), "Duck class should exist")
    assert(db.classes.contains("Fish"), "Fish class should exist")
  }

  test("parse: member visibility via ClassMember.parse") {
    val member1 = ClassMember.parse("+publicMethod()", "method")
    assertEquals(member1.visibility, "+")

    val member2 = ClassMember.parse("-privateField", "attribute")
    assertEquals(member2.visibility, "-")

    val member3 = ClassMember.parse("#protectedMethod()", "method")
    assertEquals(member3.visibility, "#")
  }

  test("parse: member classifier via ClassMember.parse") {
    val staticMethod = ClassMember.parse("+getName()$", "method")
    assertEquals(staticMethod.classifier, "$")

    val abstractMethod = ClassMember.parse("+area()*", "method")
    assertEquals(abstractMethod.classifier, "*")
  }

  test("ClassMember.displayDetails") {
    val method    = ClassMember.parse("+swim(speed)", "method")
    val (text, _) = method.displayDetails
    assert(text.contains("+"))
    assert(text.contains("swim"))
    assert(text.contains("speed"))
  }

  test("parse: dotted relation") {
    val db = ClassParser.parse(
      """classDiagram
        |    classA ..> classB""".stripMargin
    )
    assert(db.relations.nonEmpty, "Should have a dotted relation")
    assertEquals(db.relations.head.relationType.lineType, ClassLineType.DottedLine)
  }

  test("parse: class with label") {
    val db = ClassParser.parse(
      """classDiagram
        |    class Animal["An Animal"]""".stripMargin
    )
    val animal = db.classes("Animal")
    assertEquals(animal.label, "An Animal")
  }

  test("parse: member statement with colon") {
    val db = ClassParser.parse(
      """classDiagram
        |    class Animal
        |    Animal : +name""".stripMargin
    )
    val animal = db.classes("Animal")
    assert(animal.members.nonEmpty || animal.methods.nonEmpty, "Animal should have a member")
  }

  test("render: basic class diagram produces valid SVG") {
    val svg = ClassDiagram.render(
      """classDiagram
        |    class Animal
        |    class Duck
        |    Animal <|-- Duck""".stripMargin
    )
    assert(svg.contains("<svg"), "SVG should contain <svg tag")
    assert(svg.contains("viewBox"), "SVG should have viewBox")
    assert(svg.contains("Animal"), "SVG should contain class name")
  }

  test("render: via Mermaid entry point") {
    val svg = Mermaid.render("classDiagram\n    class Animal")
    assert(svg.contains("<svg"), "Should produce SVG via Mermaid.render")
    assert(!svg.startsWith("<!--"), "Should not be an unsupported type comment")
  }

  // ────────────────────────────────────────────────────────────────────────────
  // classTypes.spec.ts: ClassMember parsing tests
  // ────────────────────────────────────────────────────────────────────────────

  // --- Method with no parameters ---

  test("ClassMember method no params: should parse correctly") {
    val m = ClassMember.parse("getTime()", "method")
    assertEquals(m.displayDetails._1, "getTime()")
  }

  test("ClassMember method no params: public visibility") {
    val m = ClassMember.parse("+getTime()", "method")
    assertEquals(m.displayDetails._1, "+getTime()")
  }

  test("ClassMember method no params: private visibility") {
    val m = ClassMember.parse("-getTime()", "method")
    assertEquals(m.displayDetails._1, "-getTime()")
  }

  test("ClassMember method no params: protected visibility") {
    val m = ClassMember.parse("#getTime()", "method")
    assertEquals(m.displayDetails._1, "#getTime()")
  }

  test("ClassMember method no params: internal visibility") {
    val m = ClassMember.parse("~getTime()", "method")
    assertEquals(m.displayDetails._1, "~getTime()")
  }

  test("ClassMember method no params: static classifier css") {
    val m = ClassMember.parse("getTime()$", "method")
    assertEquals(m.displayDetails._1, "getTime()")
    assertEquals(m.displayDetails._2, staticCssStyle)
  }

  test("ClassMember method no params: abstract classifier css") {
    val m = ClassMember.parse("getTime()*", "method")
    assertEquals(m.displayDetails._1, "getTime()")
    assertEquals(m.displayDetails._2, abstractCssStyle)
  }

  // --- Method with single parameter value ---

  test("ClassMember method single param value: should parse correctly") {
    val m = ClassMember.parse("getTime(int)", "method")
    assertEquals(m.displayDetails._1, "getTime(int)")
  }

  test("ClassMember method single param value: public visibility") {
    val m = ClassMember.parse("+getTime(int)", "method")
    assertEquals(m.displayDetails._1, "+getTime(int)")
  }

  test("ClassMember method single param value: private visibility") {
    val m = ClassMember.parse("-getTime(int)", "method")
    assertEquals(m.displayDetails._1, "-getTime(int)")
  }

  test("ClassMember method single param value: protected visibility") {
    val m = ClassMember.parse("#getTime(int)", "method")
    assertEquals(m.displayDetails._1, "#getTime(int)")
  }

  test("ClassMember method single param value: internal visibility") {
    val m = ClassMember.parse("~getTime(int)", "method")
    assertEquals(m.displayDetails._1, "~getTime(int)")
  }

  test("ClassMember method single param value: static classifier css") {
    val m = ClassMember.parse("getTime(int)$", "method")
    assertEquals(m.displayDetails._1, "getTime(int)")
    assertEquals(m.displayDetails._2, staticCssStyle)
  }

  test("ClassMember method single param value: abstract classifier css") {
    val m = ClassMember.parse("getTime(int)*", "method")
    assertEquals(m.displayDetails._1, "getTime(int)")
    assertEquals(m.displayDetails._2, abstractCssStyle)
  }

  // --- Method with single parameter type and name (type first) ---

  test("ClassMember method type first: should parse correctly") {
    val m = ClassMember.parse("getTime(int count)", "method")
    assertEquals(m.displayDetails._1, "getTime(int count)")
  }

  test("ClassMember method type first: public visibility") {
    val m = ClassMember.parse("+getTime(int count)", "method")
    assertEquals(m.displayDetails._1, "+getTime(int count)")
  }

  test("ClassMember method type first: private visibility") {
    val m = ClassMember.parse("-getTime(int count)", "method")
    assertEquals(m.displayDetails._1, "-getTime(int count)")
  }

  test("ClassMember method type first: protected visibility") {
    val m = ClassMember.parse("#getTime(int count)", "method")
    assertEquals(m.displayDetails._1, "#getTime(int count)")
  }

  test("ClassMember method type first: internal visibility") {
    val m = ClassMember.parse("~getTime(int count)", "method")
    assertEquals(m.displayDetails._1, "~getTime(int count)")
  }

  test("ClassMember method type first: static classifier css") {
    val m = ClassMember.parse("getTime(int count)$", "method")
    assertEquals(m.displayDetails._1, "getTime(int count)")
    assertEquals(m.displayDetails._2, staticCssStyle)
  }

  test("ClassMember method type first: abstract classifier css") {
    val m = ClassMember.parse("getTime(int count)*", "method")
    assertEquals(m.displayDetails._1, "getTime(int count)")
    assertEquals(m.displayDetails._2, abstractCssStyle)
  }

  // --- Method with single parameter (name first) ---

  test("ClassMember method name first: should parse correctly") {
    val m = ClassMember.parse("getTime(count int)", "method")
    assertEquals(m.displayDetails._1, "getTime(count int)")
  }

  test("ClassMember method name first: public visibility") {
    val m = ClassMember.parse("+getTime(count int)", "method")
    assertEquals(m.displayDetails._1, "+getTime(count int)")
  }

  test("ClassMember method name first: private visibility") {
    val m = ClassMember.parse("-getTime(count int)", "method")
    assertEquals(m.displayDetails._1, "-getTime(count int)")
  }

  test("ClassMember method name first: protected visibility") {
    val m = ClassMember.parse("#getTime(count int)", "method")
    assertEquals(m.displayDetails._1, "#getTime(count int)")
  }

  test("ClassMember method name first: internal visibility") {
    val m = ClassMember.parse("~getTime(count int)", "method")
    assertEquals(m.displayDetails._1, "~getTime(count int)")
  }

  test("ClassMember method name first: static classifier css") {
    val m = ClassMember.parse("getTime(count int)$", "method")
    assertEquals(m.displayDetails._1, "getTime(count int)")
    assertEquals(m.displayDetails._2, staticCssStyle)
  }

  test("ClassMember method name first: abstract classifier css") {
    val m = ClassMember.parse("getTime(count int)*", "method")
    assertEquals(m.displayDetails._1, "getTime(count int)")
    assertEquals(m.displayDetails._2, abstractCssStyle)
  }

  // --- Method with multiple parameters ---

  test("ClassMember method multiple params: should parse correctly") {
    val m = ClassMember.parse("getTime(string text, int count)", "method")
    assertEquals(m.displayDetails._1, "getTime(string text, int count)")
  }

  test("ClassMember method multiple params: public visibility") {
    val m = ClassMember.parse("+getTime(string text, int count)", "method")
    assertEquals(m.displayDetails._1, "+getTime(string text, int count)")
  }

  test("ClassMember method multiple params: private visibility") {
    val m = ClassMember.parse("-getTime(string text, int count)", "method")
    assertEquals(m.displayDetails._1, "-getTime(string text, int count)")
  }

  test("ClassMember method multiple params: protected visibility") {
    val m = ClassMember.parse("#getTime(string text, int count)", "method")
    assertEquals(m.displayDetails._1, "#getTime(string text, int count)")
  }

  test("ClassMember method multiple params: internal visibility") {
    val m = ClassMember.parse("~getTime(string text, int count)", "method")
    assertEquals(m.displayDetails._1, "~getTime(string text, int count)")
  }

  test("ClassMember method multiple params: static classifier css") {
    val m = ClassMember.parse("getTime(string text, int count)$", "method")
    assertEquals(m.displayDetails._1, "getTime(string text, int count)")
    assertEquals(m.displayDetails._2, staticCssStyle)
  }

  test("ClassMember method multiple params: abstract classifier css") {
    val m = ClassMember.parse("getTime(string text, int count)*", "method")
    assertEquals(m.displayDetails._1, "getTime(string text, int count)")
    assertEquals(m.displayDetails._2, abstractCssStyle)
  }

  // --- Method with return type ---

  test("ClassMember method return type: should parse correctly") {
    val m = ClassMember.parse("getTime() DateTime", "method")
    assertEquals(m.displayDetails._1, "getTime() : DateTime")
  }

  test("ClassMember method return type: public visibility") {
    val m = ClassMember.parse("+getTime() DateTime", "method")
    assertEquals(m.displayDetails._1, "+getTime() : DateTime")
  }

  test("ClassMember method return type: private visibility") {
    val m = ClassMember.parse("-getTime() DateTime", "method")
    assertEquals(m.displayDetails._1, "-getTime() : DateTime")
  }

  test("ClassMember method return type: protected visibility") {
    val m = ClassMember.parse("#getTime() DateTime", "method")
    assertEquals(m.displayDetails._1, "#getTime() : DateTime")
  }

  test("ClassMember method return type: internal visibility") {
    val m = ClassMember.parse("~getTime() DateTime", "method")
    assertEquals(m.displayDetails._1, "~getTime() : DateTime")
  }

  test("ClassMember method return type: static classifier css") {
    val m = ClassMember.parse("getTime() DateTime$", "method")
    assertEquals(m.displayDetails._1, "getTime() : DateTime")
    assertEquals(m.displayDetails._2, staticCssStyle)
  }

  test("ClassMember method return type: abstract classifier css") {
    val m = ClassMember.parse("getTime()  DateTime*", "method")
    assertEquals(m.displayDetails._1, "getTime() : DateTime")
    assertEquals(m.displayDetails._2, abstractCssStyle)
  }

  // --- Method parameter is generic ---

  test("ClassMember method generic param: should parse correctly") {
    val m = ClassMember.parse("getTimes(List~T~)", "method")
    assertEquals(m.displayDetails._1, "getTimes(List<T>)")
  }

  test("ClassMember method generic param: public visibility") {
    val m = ClassMember.parse("+getTimes(List~T~)", "method")
    assertEquals(m.displayDetails._1, "+getTimes(List<T>)")
  }

  test("ClassMember method generic param: private visibility") {
    val m = ClassMember.parse("-getTimes(List~T~)", "method")
    assertEquals(m.displayDetails._1, "-getTimes(List<T>)")
  }

  test("ClassMember method generic param: protected visibility") {
    val m = ClassMember.parse("#getTimes(List~T~)", "method")
    assertEquals(m.displayDetails._1, "#getTimes(List<T>)")
  }

  test("ClassMember method generic param: internal visibility") {
    val m = ClassMember.parse("~getTimes(List~T~)", "method")
    assertEquals(m.displayDetails._1, "~getTimes(List<T>)")
  }

  test("ClassMember method generic param: static classifier css") {
    val m = ClassMember.parse("getTimes(List~T~)$", "method")
    assertEquals(m.displayDetails._1, "getTimes(List<T>)")
    assertEquals(m.displayDetails._2, staticCssStyle)
  }

  test("ClassMember method generic param: abstract classifier css") {
    val m = ClassMember.parse("getTimes(List~T~)*", "method")
    assertEquals(m.displayDetails._1, "getTimes(List<T>)")
    assertEquals(m.displayDetails._2, abstractCssStyle)
  }

  // --- Method parameter contains two generics ---

  test("ClassMember method two generic params: should parse correctly") {
    val m = ClassMember.parse("getTimes(List~T~, List~OT~)", "method")
    assertEquals(m.displayDetails._1, "getTimes(List<T>, List<OT>)")
  }

  test("ClassMember method two generic params: public visibility") {
    val m = ClassMember.parse("+getTimes(List~T~, List~OT~)", "method")
    assertEquals(m.displayDetails._1, "+getTimes(List<T>, List<OT>)")
  }

  test("ClassMember method two generic params: private visibility") {
    val m = ClassMember.parse("-getTimes(List~T~, List~OT~)", "method")
    assertEquals(m.displayDetails._1, "-getTimes(List<T>, List<OT>)")
  }

  test("ClassMember method two generic params: protected visibility") {
    val m = ClassMember.parse("#getTimes(List~T~, List~OT~)", "method")
    assertEquals(m.displayDetails._1, "#getTimes(List<T>, List<OT>)")
  }

  test("ClassMember method two generic params: internal visibility") {
    val m = ClassMember.parse("~getTimes(List~T~, List~OT~)", "method")
    assertEquals(m.displayDetails._1, "~getTimes(List<T>, List<OT>)")
  }

  test("ClassMember method two generic params: static classifier css") {
    val m = ClassMember.parse("getTimes(List~T~, List~OT~)$", "method")
    assertEquals(m.displayDetails._1, "getTimes(List<T>, List<OT>)")
    assertEquals(m.displayDetails._2, staticCssStyle)
  }

  test("ClassMember method two generic params: abstract classifier css") {
    val m = ClassMember.parse("getTimes(List~T~, List~OT~)*", "method")
    assertEquals(m.displayDetails._1, "getTimes(List<T>, List<OT>)")
    assertEquals(m.displayDetails._2, abstractCssStyle)
  }

  // --- Method parameter is a nested generic ---

  test("ClassMember method nested generic param: should parse correctly") {
    val m = ClassMember.parse("getTimetableList(List~List~T~~)", "method")
    assertEquals(m.displayDetails._1, "getTimetableList(List<List<T>>)")
  }

  test("ClassMember method nested generic param: public visibility") {
    val m = ClassMember.parse("+getTimetableList(List~List~T~~)", "method")
    assertEquals(m.displayDetails._1, "+getTimetableList(List<List<T>>)")
  }

  test("ClassMember method nested generic param: private visibility") {
    val m = ClassMember.parse("-getTimetableList(List~List~T~~)", "method")
    assertEquals(m.displayDetails._1, "-getTimetableList(List<List<T>>)")
  }

  test("ClassMember method nested generic param: protected visibility") {
    val m = ClassMember.parse("#getTimetableList(List~List~T~~)", "method")
    assertEquals(m.displayDetails._1, "#getTimetableList(List<List<T>>)")
  }

  test("ClassMember method nested generic param: internal visibility") {
    val m = ClassMember.parse("~getTimetableList(List~List~T~~)", "method")
    assertEquals(m.displayDetails._1, "~getTimetableList(List<List<T>>)")
  }

  test("ClassMember method nested generic param: static classifier css") {
    val m = ClassMember.parse("getTimetableList(List~List~T~~)$", "method")
    assertEquals(m.displayDetails._1, "getTimetableList(List<List<T>>)")
    assertEquals(m.displayDetails._2, staticCssStyle)
  }

  test("ClassMember method nested generic param: abstract classifier css") {
    val m = ClassMember.parse("getTimetableList(List~List~T~~)*", "method")
    assertEquals(m.displayDetails._1, "getTimetableList(List<List<T>>)")
    assertEquals(m.displayDetails._2, abstractCssStyle)
  }

  // --- Method parameter is a composite generic (List~K, V~) ---

  test("ClassMember method composite generic: should parse correctly") {
    val m = ClassMember.parse("getTimes(List~K, V~)", "method")
    assertEquals(m.displayDetails._1, "getTimes(List<K, V>)")
  }

  test("ClassMember method composite generic: public visibility") {
    val m = ClassMember.parse("+getTimes(List~K, V~)", "method")
    assertEquals(m.displayDetails._1, "+getTimes(List<K, V>)")
  }

  test("ClassMember method composite generic: private visibility") {
    val m = ClassMember.parse("-getTimes(List~K, V~)", "method")
    assertEquals(m.displayDetails._1, "-getTimes(List<K, V>)")
  }

  test("ClassMember method composite generic: protected visibility") {
    val m = ClassMember.parse("#getTimes(List~K, V~)", "method")
    assertEquals(m.displayDetails._1, "#getTimes(List<K, V>)")
  }

  test("ClassMember method composite generic: internal visibility") {
    val m = ClassMember.parse("~getTimes(List~K, V~)", "method")
    assertEquals(m.displayDetails._1, "~getTimes(List<K, V>)")
  }

  test("ClassMember method composite generic: static classifier css") {
    val m = ClassMember.parse("getTimes(List~K, V~)$", "method")
    assertEquals(m.displayDetails._1, "getTimes(List<K, V>)")
    assertEquals(m.displayDetails._2, staticCssStyle)
  }

  test("ClassMember method composite generic: abstract classifier css") {
    val m = ClassMember.parse("getTimes(List~K, V~)*", "method")
    assertEquals(m.displayDetails._1, "getTimes(List<K, V>)")
    assertEquals(m.displayDetails._2, abstractCssStyle)
  }

  // --- Method return type is generic ---

  test("ClassMember method generic return: should parse correctly") {
    val m = ClassMember.parse("getTimes() List~T~", "method")
    assertEquals(m.displayDetails._1, "getTimes() : List<T>")
  }

  test("ClassMember method generic return: public visibility") {
    val m = ClassMember.parse("+getTimes() List~T~", "method")
    assertEquals(m.displayDetails._1, "+getTimes() : List<T>")
  }

  test("ClassMember method generic return: private visibility") {
    val m = ClassMember.parse("-getTimes() List~T~", "method")
    assertEquals(m.displayDetails._1, "-getTimes() : List<T>")
  }

  test("ClassMember method generic return: protected visibility") {
    val m = ClassMember.parse("#getTimes() List~T~", "method")
    assertEquals(m.displayDetails._1, "#getTimes() : List<T>")
  }

  test("ClassMember method generic return: internal visibility") {
    val m = ClassMember.parse("~getTimes() List~T~", "method")
    assertEquals(m.displayDetails._1, "~getTimes() : List<T>")
  }

  test("ClassMember method generic return: static classifier css") {
    val m = ClassMember.parse("getTimes() List~T~$", "method")
    assertEquals(m.displayDetails._1, "getTimes() : List<T>")
    assertEquals(m.displayDetails._2, staticCssStyle)
  }

  test("ClassMember method generic return: abstract classifier css") {
    val m = ClassMember.parse("getTimes() List~T~*", "method")
    assertEquals(m.displayDetails._1, "getTimes() : List<T>")
    assertEquals(m.displayDetails._2, abstractCssStyle)
  }

  // --- Method return type is nested generic ---

  test("ClassMember method nested generic return: should parse correctly") {
    val m = ClassMember.parse("getTimetableList() List~List~T~~", "method")
    assertEquals(m.displayDetails._1, "getTimetableList() : List<List<T>>")
  }

  test("ClassMember method nested generic return: public visibility") {
    val m = ClassMember.parse("+getTimetableList() List~List~T~~", "method")
    assertEquals(m.displayDetails._1, "+getTimetableList() : List<List<T>>")
  }

  test("ClassMember method nested generic return: private visibility") {
    val m = ClassMember.parse("-getTimetableList() List~List~T~~", "method")
    assertEquals(m.displayDetails._1, "-getTimetableList() : List<List<T>>")
  }

  test("ClassMember method nested generic return: protected visibility") {
    val m = ClassMember.parse("#getTimetableList() List~List~T~~", "method")
    assertEquals(m.displayDetails._1, "#getTimetableList() : List<List<T>>")
  }

  test("ClassMember method nested generic return: internal visibility") {
    val m = ClassMember.parse("~getTimetableList() List~List~T~~", "method")
    assertEquals(m.displayDetails._1, "~getTimetableList() : List<List<T>>")
  }

  test("ClassMember method nested generic return: static classifier css") {
    val m = ClassMember.parse("getTimetableList() List~List~T~~$", "method")
    assertEquals(m.displayDetails._1, "getTimetableList() : List<List<T>>")
    assertEquals(m.displayDetails._2, staticCssStyle)
  }

  test("ClassMember method nested generic return: abstract classifier css") {
    val m = ClassMember.parse("getTimetableList() List~List~T~~*", "method")
    assertEquals(m.displayDetails._1, "getTimetableList() : List<List<T>>")
    assertEquals(m.displayDetails._2, abstractCssStyle)
  }

  // --- Uncategorized ClassMember tests ---

  test("ClassMember: member name should handle double colons") {
    val m = ClassMember.parse("std::map ~int,string~ pMap;", "attribute")
    assertEquals(m.displayDetails._1, "std::map <int,string> pMap;")
  }

  test("ClassMember: member name should handle generic type on method") {
    val m = ClassMember.parse("getTime~T~(this T, int seconds)$ DateTime", "method")
    assertEquals(m.displayDetails._1, "getTime<T>(this T, int seconds) : DateTime")
    assertEquals(m.displayDetails._2, staticCssStyle)
  }

  // --- Attribute tests from classTypes.spec.ts ---

  test("ClassMember attribute: no modifiers") {
    val dd = ClassMember.parse("name String", "attribute").displayDetails
    assertEquals(dd._1, "name String")
    assertEquals(dd._2, "")
  }

  test("ClassMember attribute: public + modifier") {
    val dd = ClassMember.parse("+name String", "attribute").displayDetails
    assertEquals(dd._1, "+name String")
    assertEquals(dd._2, "")
  }

  test("ClassMember attribute: protected # modifier") {
    val dd = ClassMember.parse("#name String", "attribute").displayDetails
    assertEquals(dd._1, "#name String")
    assertEquals(dd._2, "")
  }

  test("ClassMember attribute: private - modifier") {
    val dd = ClassMember.parse("-name String", "attribute").displayDetails
    assertEquals(dd._1, "-name String")
    assertEquals(dd._2, "")
  }

  test("ClassMember attribute: internal ~ modifier") {
    val dd = ClassMember.parse("~name String", "attribute").displayDetails
    assertEquals(dd._1, "~name String")
    assertEquals(dd._2, "")
  }

  test("ClassMember attribute: static $ modifier") {
    val dd = ClassMember.parse("name String$", "attribute").displayDetails
    assertEquals(dd._1, "name String")
    assertEquals(dd._2, staticCssStyle)
  }

  test("ClassMember attribute: abstract * modifier") {
    val dd = ClassMember.parse("name String*", "attribute").displayDetails
    assertEquals(dd._1, "name String")
    assertEquals(dd._2, abstractCssStyle)
  }
}
