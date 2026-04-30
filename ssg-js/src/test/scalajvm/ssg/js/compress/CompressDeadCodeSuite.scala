/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Compression tests for dead code elimination.
 * Ported from: terser/test/compress/dead-code.js (41 test cases)
 *
 * Uses CompressTestHelper with false_by_default mode. */
package ssg
package js
package compress

import CompressTestHelper.{ AllOff, assertCompresses }

final class CompressDeadCodeSuite extends munit.FunSuite {

  override val munitTimeout = scala.concurrent.duration.Duration(120, "s")

  // =========================================================================
  // dead_code_1
  // =========================================================================
  test("dead_code_1") {
    assertCompresses(
      input = """function f() {
                |    a();
                |    b();
                |    x = 10;
                |    return;
                |    if (x) {
                |        y();
                |    }
                |}""".stripMargin,
      expected = """function f() {
                   |    a();
                   |    b();
                   |    x = 10;
                   |    return;
                   |}""".stripMargin,
      options = AllOff.copy(deadCode = true)
    )
  }

  // =========================================================================
  // dead_code_2_should_warn
  // =========================================================================
  test("dead_code_2_should_warn") {
    assertCompresses(
      input = """function f() {
                |    g();
                |    x = 10;
                |    throw new Error("foo");
                |    if (x) {
                |        y();
                |        var x;
                |        function g(){};
                |        (function(){
                |            var q;
                |            function y(){};
                |        })();
                |    }
                |}
                |f();""".stripMargin,
      expected = """function f() {
                   |    g();
                   |    x = 10;
                   |    throw new Error("foo");
                   |    var x;
                   |    var g;
                   |}
                   |f();""".stripMargin,
      options = AllOff.copy(deadCode = true)
    )
  }

  // =========================================================================
  // dead_code_2_should_warn_strict
  // Fails: strict mode block-scoped function extraction not yet implemented
  // =========================================================================
  test("dead_code_2_should_warn_strict".fail) {
    assertCompresses(
      input = """"use strict";
                |function f() {
                |    g();
                |    x = 10;
                |    throw new Error("foo");
                |    if (x) {
                |        y();
                |        var x;
                |        function g(){};
                |        (function(){
                |            var q;
                |            function y(){};
                |        })();
                |    }
                |}
                |f();""".stripMargin,
      expected = """"use strict";
                   |function f() {
                   |    g();
                   |    x = 10;
                   |    throw new Error("foo");
                   |    var x;
                   |}
                   |f();""".stripMargin,
      options = AllOff.copy(deadCode = true)
    )
  }

  // =========================================================================
  // dead_code_constant_boolean_should_warn_more
  // Fails: combined booleans+conditionals+loops+side_effects optimization incomplete
  // =========================================================================
  test("dead_code_constant_boolean_should_warn_more".fail) {
    assertCompresses(
      input = """while (!((foo && bar) || (x + "0"))) {
                |    console.log("unreachable");
                |    var foo;
                |    function bar() {}
                |}
                |for (var x = 10, y; x && (y || x) && (!typeof x); ++x) {
                |    asdf();
                |    foo();
                |    var moo;
                |}
                |bar();""".stripMargin,
      expected = """var foo;
                   |var bar;
                   |var moo;
                   |var x = 10, y;
                   |bar();""".stripMargin,
      options = AllOff.copy(
        booleans = true,
        conditionals = true,
        deadCode = true,
        evaluate = true,
        loops = true,
        sideEffects = true
      )
    )
  }

  // =========================================================================
  // dead_code_constant_boolean_should_warn_more_strict
  // Fails: combined booleans+conditionals+loops+side_effects optimization incomplete
  // =========================================================================
  test("dead_code_constant_boolean_should_warn_more_strict".fail) {
    assertCompresses(
      input = """"use strict";
                |while (!(foo || (x + "0"))) {
                |    console.log("unreachable");
                |    var foo;
                |}
                |for (var x = 10, y; x && (y || x) && (!typeof x); ++x) {
                |    asdf();
                |    foo();
                |    var moo;
                |}
                |bar();""".stripMargin,
      expected = """"use strict";
                   |var foo;
                   |var moo;
                   |var x = 10, y;
                   |bar();""".stripMargin,
      options = AllOff.copy(
        deadCode = true,
        loops = true,
        booleans = true,
        conditionals = true,
        evaluate = true,
        sideEffects = true
      )
    )
  }

  // =========================================================================
  // dead_code_block_decls_die
  // Fails: class declaration in dead code not yet handled
  // =========================================================================
  test("dead_code_block_decls_die".fail) {
    assertCompresses(
      input = """if (0) {
                |    let foo = 6;
                |    const bar = 12;
                |    class Baz {};
                |    var qux;
                |}
                |console.log(foo, bar, Baz);""".stripMargin,
      expected = """var qux;
                   |console.log(foo, bar, Baz);""".stripMargin,
      options = AllOff.copy(
        booleans = true,
        conditionals = true,
        deadCode = true,
        evaluate = true,
        sideEffects = true
      )
    )
  }

  // =========================================================================
  // dead_code_const_declaration
  // =========================================================================
  test("dead_code_const_declaration") {
    assertCompresses(
      input = """var unused;
                |const CONST_FOO = false;
                |if (CONST_FOO) {
                |    console.log("unreachable");
                |    var moo;
                |    function bar() {}
                |}""".stripMargin,
      expected = """var unused;
                   |const CONST_FOO = !1;
                   |var moo;
                   |var bar;""".stripMargin,
      options = AllOff.copy(
        booleans = true,
        conditionals = true,
        deadCode = true,
        evaluate = true,
        loops = true,
        reduceFuncs = true,
        reduceVars = true,
        sideEffects = true
      )
    )
  }

  // =========================================================================
  // dead_code_const_annotation
  // Fails: @const annotation + toplevel reduce_vars interaction
  // =========================================================================
  test("dead_code_const_annotation".fail) {
    assertCompresses(
      input = """var unused;
                |/** @const */ var CONST_FOO_ANN = false;
                |if (CONST_FOO_ANN) {
                |    console.log("unreachable");
                |    var moo;
                |    function bar() {}
                |}""".stripMargin,
      expected = """var unused;
                   |var CONST_FOO_ANN = !1;
                   |var moo;
                   |var bar;""".stripMargin,
      options = AllOff.copy(
        booleans = true,
        conditionals = true,
        deadCode = true,
        evaluate = true,
        loops = true,
        reduceFuncs = true,
        reduceVars = true,
        sideEffects = true,
        toplevel = ToplevelConfig(funcs = true, vars = true)
      )
    )
  }

  // =========================================================================
  // dead_code_const_annotation_regex
  // =========================================================================
  test("dead_code_const_annotation_regex") {
    assertCompresses(
      input = """var unused;
                |// @constraint this shouldn't be a constant
                |var CONST_FOO_ANN = false;
                |if (CONST_FOO_ANN) {
                |    console.log("reachable");
                |}""".stripMargin,
      expected = """var unused;
                   |var CONST_FOO_ANN = !1;
                   |CONST_FOO_ANN && console.log('reachable');""".stripMargin,
      options = AllOff.copy(
        deadCode = true,
        loops = true,
        booleans = true,
        conditionals = true,
        evaluate = true
      )
    )
  }

  // =========================================================================
  // dead_code_const_annotation_complex_scope
  // Fails: complex reduce_vars + sequences + toplevel interaction
  // =========================================================================
  test("dead_code_const_annotation_complex_scope".fail) {
    assertCompresses(
      input = """var unused_var;
                |/** @const */ var test = 'test';
                |// @const
                |var CONST_FOO_ANN = false;
                |var unused_var_2;
                |if (CONST_FOO_ANN) {
                |    console.log("unreachable");
                |    var moo;
                |    function bar() {}
                |}
                |if (test === 'test') {
                |    var beef = 'good';
                |    /** @const */ var meat = 'beef';
                |    var pork = 'bad';
                |    if (meat === 'pork') {
                |        console.log('also unreachable');
                |    } else if (pork === 'good') {
                |        console.log('reached, not const');
                |    }
                |}""".stripMargin,
      expected = """var unused_var;
                   |var test = 'test';
                   |var CONST_FOO_ANN = !1;
                   |var unused_var_2;
                   |var moo;
                   |var bar;
                   |var beef = 'good';
                   |var meat = 'beef';
                   |var pork = 'bad';""".stripMargin,
      options = AllOff.copy(
        booleans = true,
        conditionals = true,
        deadCode = true,
        evaluate = true,
        loops = true,
        reduceFuncs = true,
        reduceVars = true,
        sequencesLimit = 200,
        sideEffects = true,
        toplevel = ToplevelConfig(funcs = true, vars = true)
      )
    )
  }

  // =========================================================================
  // try_catch_finally
  // Fails: ClassCastException — AstSymbolVar cannot be cast to AstSymbolRef
  // =========================================================================
  test("try_catch_finally".fail) {
    assertCompresses(
      input = """var a = 1;
                |!function() {
                |    try {
                |        if (false) throw x;
                |    } catch (a) {
                |        var a = 2;
                |        console.log("FAIL");
                |    } finally {
                |        a = 3;
                |        console.log("PASS");
                |    }
                |}();
                |try {
                |    console.log(a);
                |} finally {
                |}""".stripMargin,
      expected = """var a = 1;
                   |!function() {
                   |    var a;
                   |    a = 3;
                   |    console.log("PASS");
                   |}();
                   |try {
                   |    console.log(a);
                   |} finally {
                   |}""".stripMargin,
      options = AllOff.copy(
        conditionals = true,
        deadCode = true,
        evaluate = true,
        passes = 2,
        sideEffects = true
      )
    )
  }

  // =========================================================================
  // accessor
  // Fails: parser does not support getter/setter shorthand in object literals
  // =========================================================================
  test("accessor".fail) {
    assertCompresses(
      input = """({
                |    get a() {},
                |    set a(v){
                |        this.b = 2;
                |    },
                |    b: 1
                |});""".stripMargin,
      expected = "",
      options = AllOff.copy(sideEffects = true)
    )
  }

  // =========================================================================
  // issue_2233_1
  // Fails: side_effects+unsafe global symbol dropping incomplete
  // =========================================================================
  test("issue_2233_1".fail) {
    assertCompresses(
      input = """Array.isArray;
                |Boolean;
                |console.log;
                |Date;
                |decodeURI;
                |decodeURIComponent;
                |encodeURI;
                |encodeURIComponent;
                |Error.name;
                |escape;
                |eval;
                |EvalError;
                |Function.length;
                |isFinite;
                |isNaN;
                |JSON;
                |Math.random;
                |Number.isNaN;
                |parseFloat;
                |parseInt;
                |RegExp;
                |Object.defineProperty;
                |String.fromCharCode;
                |RangeError;
                |ReferenceError;
                |SyntaxError;
                |TypeError;
                |unescape;
                |URIError;""".stripMargin,
      expected = "",
      options = AllOff.copy(
        pureGetters = "strict",
        sideEffects = true,
        unsafe = true
      )
    )
  }

  // =========================================================================
  // global_timeout_and_interval_symbols
  // Fails: side_effects+unsafe global symbol dropping incomplete
  // =========================================================================
  test("global_timeout_and_interval_symbols".fail) {
    assertCompresses(
      input = """clearInterval;
                |clearTimeout;
                |setInterval;
                |setTimeout;""".stripMargin,
      expected = "",
      options = AllOff.copy(
        pureGetters = "strict",
        sideEffects = true,
        unsafe = true
      )
    )
  }

  // =========================================================================
  // issue_2233_2
  // Fails: side_effects+unsafe+unused global symbol interaction
  // =========================================================================
  test("issue_2233_2".fail) {
    assertCompresses(
      input = """var RegExp;
                |Array.isArray;
                |RegExp;
                |UndeclaredGlobal;
                |function foo() {
                |    var Number;
                |    AnotherUndeclaredGlobal;
                |    Math.sin;
                |    Number.isNaN;
                |}""".stripMargin,
      expected = """var RegExp;
                   |UndeclaredGlobal;
                   |function foo() {
                   |    var Number;
                   |    AnotherUndeclaredGlobal;
                   |    Number.isNaN;
                   |}""".stripMargin,
      options = AllOff.copy(
        pureGetters = "strict",
        reduceFuncs = true,
        reduceVars = true,
        sideEffects = true,
        unsafe = true,
        unused = true
      )
    )
  }

  // =========================================================================
  // issue_2233_3
  // Fails: side_effects+unsafe+unused+toplevel interaction
  // =========================================================================
  test("issue_2233_3".fail) {
    assertCompresses(
      input = """var RegExp;
                |Array.isArray;
                |RegExp;
                |UndeclaredGlobal;
                |function foo() {
                |    var Number;
                |    AnotherUndeclaredGlobal;
                |    Math.sin;
                |    Number.isNaN;
                |}""".stripMargin,
      expected = "UndeclaredGlobal;",
      options = AllOff.copy(
        pureGetters = "strict",
        reduceFuncs = true,
        reduceVars = true,
        sideEffects = true,
        toplevel = ToplevelConfig(funcs = true, vars = true),
        unsafe = true,
        unused = true
      )
    )
  }

  // =========================================================================
  // global_fns
  // Fails: side_effects+unsafe pure global function call dropping incomplete
  // =========================================================================
  test("global_fns".fail) {
    assertCompresses(
      input = """Boolean(1, 2);
                |decodeURI(1, 2);
                |decodeURIComponent(1, 2);
                |Date(1, 2);
                |encodeURI(1, 2);
                |encodeURIComponent(1, 2);
                |Error(1, 2);
                |escape(1, 2);
                |EvalError(1, 2);
                |isFinite(1, 2);
                |isNaN(1, 2);
                |Number(1, 2);
                |Object(1, 2);
                |parseFloat(1, 2);
                |parseInt(1, 2);
                |RangeError(1, 2);
                |ReferenceError(1, 2);
                |String(1, 2);
                |SyntaxError(1, 2);
                |TypeError(1, 2);
                |unescape(1, 2);
                |URIError(1, 2);
                |try {
                |    Function(1, 2);
                |} catch (e) {
                |    console.log(e.name);
                |}
                |try {
                |    RegExp(1, 2);
                |} catch (e) {
                |    console.log(e.name);
                |}
                |try {
                |    Array(NaN);
                |} catch (e) {
                |    console.log(e.name);
                |}""".stripMargin,
      expected = """try {
                   |    Function(1, 2);
                   |} catch (e) {
                   |    console.log(e.name);
                   |}
                   |try {
                   |    RegExp(1, 2);
                   |} catch (e) {
                   |    console.log(e.name);
                   |}
                   |try {
                   |    Array(NaN);
                   |} catch (e) {
                   |    console.log(e.name);
                   |}""".stripMargin,
      options = AllOff.copy(
        sideEffects = true,
        unsafe = true
      )
    )
  }

  // =========================================================================
  // issue_2383_1
  // =========================================================================
  test("issue_2383_1") {
    assertCompresses(
      input = """if (0) {
                |    var {x, y} = foo();
                |}""".stripMargin,
      expected = "var x, y;",
      options = AllOff.copy(
        conditionals = true,
        deadCode = true,
        evaluate = true,
        sideEffects = true
      )
    )
  }

  // =========================================================================
  // issue_2383_2
  // Fails: parser does not support destructuring default values in var declarations
  // =========================================================================
  test("issue_2383_2".fail) {
    assertCompresses(
      input = """if (0) {
                |    var {
                |        x = 0,
                |        y: [ w, , { z, p: q = 7 } ] = [ 1, 2, { z: 3 } ]
                |    } = {};
                |}
                |console.log(x, q, w, z);""".stripMargin,
      expected = """var x, w, z, q;
                   |console.log(x, q, w, z);""".stripMargin,
      options = AllOff.copy(
        conditionals = true,
        deadCode = true,
        evaluate = true,
        sideEffects = true
      )
    )
  }

  // =========================================================================
  // issue_2383_3
  // Fails: destructuring array in dead code var extraction
  // =========================================================================
  test("issue_2383_3".fail) {
    assertCompresses(
      input = """var b = 7, y = 8;
                |if (0) {
                |    var a = 1, [ x, y, z ] = [ 2, 3, 4 ], b = 5;
                |}
                |console.log(a, x, y, z, b);""".stripMargin,
      expected = """var b = 7, y = 8;
                   |var a, x, y, z, b;
                   |console.log(a, x, y, z, b);""".stripMargin,
      options = AllOff.copy(
        conditionals = true,
        deadCode = true,
        evaluate = true,
        sideEffects = true
      )
    )
  }

  // =========================================================================
  // collapse_vars_assignment
  // Fails: collapse_vars pass not yet fully implemented
  // =========================================================================
  test("collapse_vars_assignment".fail) {
    assertCompresses(
      input = """function f0(c) {
                |    var a = 3 / c;
                |    return a = a;
                |}""".stripMargin,
      expected = """function f0(c) {
                   |    return 3 / c;
                   |}""".stripMargin,
      options = AllOff.copy(
        collapseVars = true,
        deadCode = true,
        passes = 2,
        unused = true
      )
    )
  }

  // =========================================================================
  // collapse_vars_lvalues_drop_assign
  // Fails: collapse_vars pass not yet fully implemented
  // =========================================================================
  test("collapse_vars_lvalues_drop_assign".fail) {
    assertCompresses(
      input = """function f0(x) { var i = ++x; return x += i; }
                |function f1(x) { var a = (x -= 3); return x += a; }
                |function f2(x) { var z = x, a = ++z; return z += a; }""".stripMargin,
      expected = """function f0(x) { var i = ++x; return x + i; }
                   |function f1(x) { var a = (x -= 3); return x + a; }
                   |function f2(x) { var z = x, a = ++z; return z + a; }""".stripMargin,
      options = AllOff.copy(
        collapseVars = true,
        deadCode = true,
        unused = true
      )
    )
  }

  // =========================================================================
  // collapse_vars_misc1
  // Fails: collapse_vars pass not yet fully implemented
  // =========================================================================
  test("collapse_vars_misc1".fail) {
    assertCompresses(
      input = """function f10(x) { var a = 5, b = 3; return a += b; }
                |function f11(x) { var a = 5, b = 3; return a += --b; }""".stripMargin,
      expected = """function f10(x) { return 5 + 3; }
                   |function f11(x) { var b = 3; return 5 + --b; }""".stripMargin,
      options = AllOff.copy(
        collapseVars = true,
        deadCode = true,
        unused = true
      )
    )
  }

  // =========================================================================
  // return_assignment
  // Fails: dead_code+unused return assignment optimization incomplete
  // =========================================================================
  test("return_assignment".fail) {
    assertCompresses(
      input = """function f1(a, b, c) {
                |    return a = x(), b = y(), b = a && (c >>= 5);
                |}
                |function f2() {
                |    return e = x();
                |}
                |function f3(e) {
                |    return e = x();
                |}
                |function f4() {
                |    var e;
                |    return e = x();
                |}
                |function f5(a) {
                |    try {
                |        return a = x();
                |    } catch (b) {
                |        console.log(a);
                |    }
                |}
                |function f6(a) {
                |    try {
                |        return a = x();
                |    } finally {
                |        console.log(a);
                |    }
                |}
                |function y() {
                |    console.log("y");
                |}
                |function test(inc) {
                |    var counter = 0;
                |    x = function() {
                |        counter += inc;
                |        if (inc < 0) throw counter;
                |        return counter;
                |    };
                |    [ f1, f2, f3, f4, f5, f6 ].forEach(function(f, i) {
                |        e = null;
                |        try {
                |            i += 1;
                |            console.log("result " + f(10 * i, 100 * i, 1000 * i));
                |        } catch (x) {
                |            console.log("caught " + x);
                |        }
                |        if (null !== e) console.log("e: " + e);
                |    });
                |}
                |var x, e;
                |test(1);
                |test(-1);""".stripMargin,
      expected = """function f1(a, b, c) {
                   |    return a = x(), y(), a && (c >> 5);
                   |}
                   |function f2() {
                   |    return e = x();
                   |}
                   |function f3(e) {
                   |    return x();
                   |}
                   |function f4() {
                   |    return x();
                   |}
                   |function f5(a) {
                   |    try {
                   |        return x();
                   |    } catch (b) {
                   |        console.log(a);
                   |    }
                   |}
                   |function f6(a) {
                   |    try {
                   |        return a = x();
                   |    } finally {
                   |        console.log(a);
                   |    }
                   |}
                   |function y() {
                   |    console.log("y");
                   |}
                   |function test(inc) {
                   |    var counter = 0;
                   |    x = function() {
                   |        counter += inc;
                   |        if (inc < 0) throw counter;
                   |        return counter;
                   |    };
                   |    [ f1, f2, f3, f4, f5, f6 ].forEach(function(f, i) {
                   |        e = null;
                   |        try {
                   |            i += 1;
                   |            console.log("result " + f(10 * i, 100 * i, 1000 * i));
                   |        } catch (x) {
                   |            console.log("caught " + x);
                   |        }
                   |        if (null !== e) console.log("e: " + e);
                   |    });
                   |}
                   |var x, e;
                   |test(1);
                   |test(-1);""".stripMargin,
      options = AllOff.copy(
        deadCode = true,
        unused = true
      )
    )
  }

  // =========================================================================
  // throw_assignment
  // Fails: dead_code+unused throw assignment optimization incomplete
  // =========================================================================
  test("throw_assignment".fail) {
    assertCompresses(
      input = """function f1() {
                |    throw a = x();
                |}
                |function f2(a) {
                |    throw a = x();
                |}
                |function f3() {
                |    var a;
                |    throw a = x();
                |}
                |function f4() {
                |    try {
                |        throw a = x();
                |    } catch (b) {
                |        console.log(a);
                |    }
                |}
                |function f5(a) {
                |    try {
                |        throw a = x();
                |    } catch (b) {
                |        console.log(a);
                |    }
                |}
                |function f6() {
                |    var a;
                |    try {
                |        throw a = x();
                |    } catch (b) {
                |        console.log(a);
                |    }
                |}
                |function f7() {
                |    try {
                |        throw a = x();
                |    } finally {
                |        console.log(a);
                |    }
                |}
                |function f8(a) {
                |    try {
                |        throw a = x();
                |    } finally {
                |        console.log(a);
                |    }
                |}
                |function f9() {
                |    var a;
                |    try {
                |        throw a = x();
                |    } finally {
                |        console.log(a);
                |    }
                |}
                |function test(inc) {
                |    var counter = 0;
                |    x = function() {
                |        counter += inc;
                |        if (inc < 0) throw counter;
                |        return counter;
                |    };
                |    [ f1, f2, f3, f4, f5, f6, f7, f8, f9 ].forEach(function(f, i) {
                |        a = null;
                |        try {
                |            f(10 * (1 + i));
                |        } catch (x) {
                |            console.log("caught " + x);
                |        }
                |        if (null !== a) console.log("a: " + a);
                |    });
                |}
                |var x, a;
                |test(1);
                |test(-1);""".stripMargin,
      expected = """function f1() {
                   |    throw a = x();
                   |}
                   |function f2(a) {
                   |    throw x();
                   |}
                   |function f3() {
                   |    throw x();
                   |}
                   |function f4() {
                   |    try {
                   |        throw a = x();
                   |    } catch (b) {
                   |        console.log(a);
                   |    }
                   |}
                   |function f5(a) {
                   |    try {
                   |        throw a = x();
                   |    } catch (b) {
                   |        console.log(a);
                   |    }
                   |}
                   |function f6() {
                   |    var a;
                   |    try {
                   |        throw a = x();
                   |    } catch (b) {
                   |        console.log(a);
                   |    }
                   |}
                   |function f7() {
                   |    try {
                   |        throw a = x();
                   |    } finally {
                   |        console.log(a);
                   |    }
                   |}
                   |function f8(a) {
                   |    try {
                   |        throw a = x();
                   |    } finally {
                   |        console.log(a);
                   |    }
                   |}
                   |function f9() {
                   |    var a;
                   |    try {
                   |        throw a = x();
                   |    } finally {
                   |        console.log(a);
                   |    }
                   |}
                   |function test(inc) {
                   |    var counter = 0;
                   |    x = function() {
                   |        counter += inc;
                   |        if (inc < 0) throw counter;
                   |        return counter;
                   |    };
                   |    [ f1, f2, f3, f4, f5, f6, f7, f8, f9 ].forEach(function(f, i) {
                   |        a = null;
                   |        try {
                   |            f(10 * (1 + i));
                   |        } catch (x) {
                   |            console.log("caught " + x);
                   |        }
                   |        if (null !== a) console.log("a: " + a);
                   |    });
                   |}
                   |var x, a;
                   |test(1);
                   |test(-1);""".stripMargin,
      options = AllOff.copy(
        deadCode = true,
        unused = true
      )
    )
  }

  // =========================================================================
  // issue_2597
  // =========================================================================
  test("issue_2597") {
    assertCompresses(
      input = """function f(b) {
                |    try {
                |        try {
                |            throw "foo";
                |        } catch (e) {
                |            return b = true;
                |        }
                |    } finally {
                |        b && (a = "PASS");
                |    }
                |}
                |var a = "FAIL";
                |f();
                |console.log(a);""".stripMargin,
      expected = """function f(b) {
                   |    try {
                   |        try {
                   |            throw "foo";
                   |        } catch (e) {
                   |            return b = true;
                   |        }
                   |    } finally {
                   |        b && (a = "PASS");
                   |    }
                   |}
                   |var a = "FAIL";
                   |f();
                   |console.log(a);""".stripMargin,
      options = AllOff.copy(deadCode = true)
    )
  }

  // =========================================================================
  // issue_2666
  // =========================================================================
  test("issue_2666") {
    assertCompresses(
      input = """function f(a) {
                |    return a = {
                |        p: function() {
                |            return a;
                |        }
                |    };
                |}
                |console.log(typeof f().p());""".stripMargin,
      expected = """function f(a) {
                   |    return a = {
                   |        p: function() {
                   |            return a;
                   |        }
                   |    };
                   |}
                   |console.log(typeof f().p());""".stripMargin,
      options = AllOff.copy(deadCode = true)
    )
  }

  // =========================================================================
  // issue_2692
  // =========================================================================
  test("issue_2692") {
    assertCompresses(
      input = """function f(a) {
                |    return a = g;
                |    function g() {
                |        return a;
                |    }
                |}
                |console.log(typeof f()());""".stripMargin,
      expected = """function f(a) {
                   |    return a = g;
                   |    function g() {
                   |        return a;
                   |    }
                   |}
                   |console.log(typeof f()());""".stripMargin,
      options = AllOff.copy(
        deadCode = true,
        reduceVars = false
      )
    )
  }

  // =========================================================================
  // issue_2701
  // =========================================================================
  test("issue_2701") {
    assertCompresses(
      input = """function f(a) {
                |    return a = function() {
                |        return function() {
                |            return a;
                |        };
                |    }();
                |}
                |console.log(typeof f()());""".stripMargin,
      expected = """function f(a) {
                   |    return a = function() {
                   |        return function() {
                   |            return a;
                   |        };
                   |    }();
                   |}
                   |console.log(typeof f()());""".stripMargin,
      options = AllOff.copy(
        deadCode = true,
        inline = InlineLevel.InlineDisabled
      )
    )
  }

  // =========================================================================
  // issue_2749
  // Fails: ClassCastException — AstSymbolVar cannot be cast to AstSymbolRef
  // =========================================================================
  test("issue_2749".fail) {
    assertCompresses(
      input = """var a = 2, c = "PASS";
                |while (a--)
                |    (function() {
                |        return b ? c = "FAIL" : b = 1;
                |        try {
                |        } catch (b) {
                |            var b;
                |        }
                |    })();
                |console.log(c);""".stripMargin,
      expected = """var a = 2, c = "PASS";
                   |while (a--)
                   |    (function() {
                   |        return b ? c = "FAIL" : b = 1;
                   |        var b;
                   |    })();
                   |console.log(c);""".stripMargin,
      options = AllOff.copy(
        deadCode = true,
        inline = InlineLevel.InlineFull,
        toplevel = ToplevelConfig(funcs = true, vars = true),
        unused = true
      )
    )
  }

  // =========================================================================
  // unsafe_builtin
  // Fails: side_effects+unsafe builtin method call optimization incomplete
  // =========================================================================
  test("unsafe_builtin".fail) {
    assertCompresses(
      input = """(!w).constructor(x);
                |Math.abs(y);
                |[ 1, 2, z ].valueOf();""".stripMargin,
      expected = """w, x;
                   |y;
                   |z;""".stripMargin,
      options = AllOff.copy(
        sideEffects = true,
        unsafe = true
      )
    )
  }

  // =========================================================================
  // issue_2860_1
  // Fails: compound assignment to return value not simplified (a ^= 1 -> a ^ 1)
  // =========================================================================
  test("issue_2860_1".fail) {
    assertCompresses(
      input = """console.log(function(a) {
                |    return a ^= 1;
                |}());""".stripMargin,
      expected = """console.log(function(a) {
                   |    return a ^ 1;
                   |}());""".stripMargin,
      options = AllOff.copy(
        deadCode = true,
        evaluate = true,
        reduceVars = true
      )
    )
  }

  // =========================================================================
  // issue_2860_2
  // Fails: compound assignment + inline + multi-pass interaction
  // =========================================================================
  test("issue_2860_2".fail) {
    assertCompresses(
      input = """console.log(function(a) {
                |    return a ^= 1;
                |}());""".stripMargin,
      expected = "console.log(1);",
      options = AllOff.copy(
        deadCode = true,
        evaluate = true,
        inline = InlineLevel.InlineFull,
        passes = 2,
        reduceVars = true
      )
    )
  }

  // =========================================================================
  // issue_2929
  // =========================================================================
  test("issue_2929") {
    assertCompresses(
      input = """console.log(function(a) {
                |    try {
                |        return null.p = a = 1;
                |    } catch (e) {
                |        return a ? "PASS" : "FAIL";
                |    }
                |}());""".stripMargin,
      expected = """console.log(function(a) {
                   |    try {
                   |        return null.p = a = 1;
                   |    } catch (e) {
                   |        return a ? "PASS" : "FAIL";
                   |    }
                   |}());""".stripMargin,
      options = AllOff.copy(deadCode = true)
    )
  }

  // =========================================================================
  // issue_718
  // =========================================================================
  test("issue_718") {
    assertCompresses(
      input = """throw 'error'
                |
                |import 'x'
                |export {y}""".stripMargin,
      expected = """throw 'error'
                   |
                   |import 'x'
                   |export {y}""".stripMargin,
      options = AllOff.copy(deadCode = true)
    )
  }

  // =========================================================================
  // issue_1029_1
  // =========================================================================
  test("issue_1029_1") {
    assertCompresses(
      input = """function asyncFn() {
                |  let promise;
                |  return promise = (async () => {
                |    await true;
                |    console.log(promise);
                |  })()
                |}
                |asyncFn({});""".stripMargin,
      expected = """function asyncFn() {
                   |    let promise;
                   |    return promise = (async () => {
                   |        await true;
                   |        console.log(promise);
                   |    })();
                   |}
                   |asyncFn({});""".stripMargin,
      options = AllOff.copy(deadCode = true)
    )
  }

  // =========================================================================
  // issue_1029_2
  // =========================================================================
  test("issue_1029_2") {
    assertCompresses(
      input = """function asyncFn() {
                |  let promise;
                |  return promise = (async () => {
                |    console.log(promise);
                |  })()
                |}
                |asyncFn({});""".stripMargin,
      expected = """function asyncFn() {
                   |    let promise;
                   |    return promise = (async () => {
                   |        console.log(promise);
                   |    })();
                   |}
                   |asyncFn({});""".stripMargin,
      options = AllOff.copy(deadCode = true)
    )
  }

  // =========================================================================
  // issue_1029_3
  // =========================================================================
  test("issue_1029_3") {
    assertCompresses(
      input = """function genFn() {
                |    let gen;
                |    return gen = function*() {
                |        console.log(gen);
                |    }();
                |}
                |genFn({}).next();""".stripMargin,
      expected = """function genFn() {
                   |    let gen;
                   |    return gen = function* () {
                   |        console.log(gen);
                   |    }();
                   |}
                   |genFn({}).next();""".stripMargin,
      options = AllOff.copy(deadCode = true)
    )
  }

  // =========================================================================
  // issue_1029_4
  // Fails: return assignment to let not dropped when val is synchronously used
  // =========================================================================
  test("issue_1029_4".fail) {
    assertCompresses(
      input = """function fn() {
                |    let val
                |    return val = function() {
                |        console.log(val);
                |        return {};
                |    }();
                |}
                |fn();""".stripMargin,
      expected = """function fn() {
                   |    let val
                   |    return function() {
                   |        console.log(val);
                   |        return {};
                   |    }();
                   |}
                   |fn();""".stripMargin,
      options = AllOff.copy(deadCode = true)
    )
  }

  // =========================================================================
  // issue_1029_5
  // =========================================================================
  test("issue_1029_5") {
    assertCompresses(
      input = """function fn() {
                |    let val
                |    return val = function() {
                |        setTimeout(() => console.log(val));
                |        return {};
                |    }();
                |}
                |fn();""".stripMargin,
      expected = """function fn() {
                   |    let val
                   |    return val = function() {
                   |        setTimeout(() => console.log(val));
                   |        return {};
                   |    }();
                   |}
                   |fn();""".stripMargin,
      options = AllOff.copy(deadCode = true)
    )
  }

  // =========================================================================
  // issue_1029_6
  // =========================================================================
  test("issue_1029_6") {
    assertCompresses(
      input = """function fn() {
                |    let val
                |    return val = function() {
                |        setTimeout(() => {
                |            (() => console.log(val))();
                |        })
                |        return {};
                |    }();
                |}
                |fn();""".stripMargin,
      expected = """function fn() {
                   |    let val
                   |    return val = function() {
                   |        setTimeout(() => {
                   |            (() => console.log(val))();
                   |        })
                   |        return {};
                   |    }();
                   |}
                   |fn();""".stripMargin,
      options = AllOff.copy(deadCode = true)
    )
  }
}
