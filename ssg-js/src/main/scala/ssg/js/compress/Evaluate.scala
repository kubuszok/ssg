/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Constant folding: evaluate expressions at compile time.
 *
 * Tries to reduce AST expressions to their constant values when possible.
 * Handles arithmetic, string operations, comparisons, typeof, boolean logic,
 * Math.*, property access on known objects, and method calls on pure natives.
 *
 * Ported from: terser lib/compress/evaluate.js
 * Original: Copyright (c) 2012 Mihai Bazon
 * Original license: BSD-2-Clause
 *
 * Migration notes:
 *   Renames: AST_* -> Ast*, def_eval -> pattern matching in evalNode,
 *     is_constant -> isConstant, _eval -> evalNode
 *   Convention: Object with methods, pattern matching instead of DEFMETHOD
 *   Idiom: boundary/break instead of return, EvalResult ADT instead of
 *     JS union types, Set instead of makePredicate
 *   Gap: ISS-165 to ISS-175 edge cases fixed (hasSideEffects checks, HOP guard,
 *     regexp_source_fix, Object.prototype guard, BigInt **, Array/Number methods,
 *     regexp_is_safe, hasOwnProperty.call edge case)
 */
package ssg
package js
package compress

import scala.collection.mutable
import scala.util.boundary
import scala.util.boundary.break

import ssg.js.ast.*
import ssg.js.ast.AstSize
import ssg.js.compress.NativeObjects.*

/** Sentinel value indicating a nullish short-circuit in optional chains. */
object Nullish

/** Result of attempting to evaluate an expression at compile time.
  *
  *   - `Const(value)` — successfully evaluated to a primitive
  *   - `Unevaluated` — cannot evaluate, keep original node
  */
enum EvalResult {
  case Const(value: Any)
  case Unevaluated
}

/** Wrapper for an evaluated function node (used in unsafe mode).
  *
  * In the original JS, functions can be evaluated to a function object with properties like `.name` and `.length`. We use this wrapper to enable similar functionality.
  */
final case class EvalFunction(node: AstLambda) {
  /** Get the function's name, or empty string if anonymous. */
  def name: String = node.name match {
    case null       => ""
    case sym: AstSymbol => sym.name
    case _ => ""
  }

  /** Get the function's formal parameter count (JavaScript .length semantics). */
  def length: Int = node.lengthProperty
}

/** Constant folding engine.
  *
  * Evaluates compile-time-constant expressions and returns either the evaluated value or the original AST node when evaluation is not possible.
  */
object Evaluate {

  private val unaryPrefixOps:      Set[String] = Set("!", "~", "-", "+", "void")
  private val nonConvertingUnary:  Set[String] = Set("!", "typeof", "void")
  private val nonConvertingBinary: Set[String] = Set("&&", "||", "??", "===", "!==")
  private val identityComparison:  Set[String] = Set("==", "!=", "===", "!==")

  /** Object.prototype methods that must not be overwritten by user object keys. */
  private val objectPrototypeFunctions: Set[String] = Set(
    "toString", "valueOf", "constructor"
  )

  /** Line terminator escape mappings for regexp_source_fix. */
  private val lineTerminatorEscape: Map[Char, String] = Map(
    '\u0000' -> "0",
    '\n' -> "n",
    '\r' -> "r",
    '\u2028' -> "u2028",
    '\u2029' -> "u2029"
  )

  /** Subset of regexps that is not going to cause regexp based DDOS.
    * See: https://owasp.org/www-community/attacks/Regular_expression_Denial_of_Service_-_ReDoS
    * The original JS regex: /^[\\/|\0\s\w\^$.\[\]()]*$/
    * Note: \0 is the NUL character (U+0000)
    */
  private val reSafeRegexp = """^[\\/|\u0000\s\w\^$.\[\]()]*$""".r

  /** Check if the regexp is safe for Terser to create without risking a RegExp DOS. */
  private def regexpIsSafe(source: String): Boolean =
    reSafeRegexp.findFirstIn(source).isDefined

  /** Fix regexp source by escaping line terminators (V8 compatibility).
    * V8 does not escape line terminators in regexp patterns in node 12.
    * Also removes literal \0.
    */
  private def regexpSourceFix(source: String): String = {
    val sb = new StringBuilder
    var i  = 0
    while (i < source.length) {
      val ch = source.charAt(i)
      lineTerminatorEscape.get(ch) match {
        case Some(esc) =>
          // Check if already escaped
          val alreadyEscaped =
            i > 0 && source.charAt(i - 1) == '\\' && {
              // Count preceding backslashes
              var j     = i - 1
              var count = 0
              while (j >= 0 && source.charAt(j) == '\\') {
                count += 1
                j -= 1
              }
              count % 2 == 1 // odd count means already escaped
            }
          if (!alreadyEscaped) sb.append('\\')
          sb.append(esc)
        case None =>
          sb.append(ch)
      }
      i += 1
    }
    sb.toString
  }

  /** Check if an object has its own property (JavaScript HOP equivalent). */
  private def hasOwnProperty(obj: Map[String, Any], key: String): Boolean =
    obj.contains(key)

  // -----------------------------------------------------------------------
  // Public API
  // -----------------------------------------------------------------------

  /** Try to evaluate an expression to a constant value.
    *
    * Returns the original node if evaluation fails or if the evaluated result would be larger than the original expression.
    *
    * @param node
    *   the expression to evaluate
    * @param compressor
    *   the compressor context
    * @return
    *   the constant value (Boolean, Double, String) or the original node
    */
  def evaluate(node: AstNode, compressor: CompressorLike): Any =
    if (!compressor.optionBool("evaluate")) node
    else {
      val result = evalNode(node, compressor, depth = 1)
      result match {
        case null | false => result
        case _: scala.util.matching.Regex => result
        case _ if result.asInstanceOf[AnyRef] eq Nullish => node
        case _: Map[?, ?] => node // object values (Map extends Function1)
        case _: Seq[?]    => node // array values
        case _: EvalFunction => node // evaluated function (used internally for .name/.length)
        case _: Function0[?] | _: Function1[?, ?] => node // function values
        case s: String =>
          // Evaluated strings can be larger than the original expression.
          // Compare the string literal size (length + 2 for quotes) against
          // the original node's estimated output size.
          if (s.length + 2 > AstSize.size(node)) node else s
        case _ => result
      }
    }

  /** Check if a node is a compile-time constant.
    *
    * Accommodates when compress option evaluate=false as well as common constant expressions like !0 and -1.
    */
  def isConstant(node: AstNode): Boolean =
    node match {
      case _:      AstRegExp      => false
      case _:      AstConstant    => true
      case prefix: AstUnaryPrefix =>
        unaryPrefixOps.contains(prefix.operator) && {
          prefix.expression match {
            case null => false
            case expr =>
              expr.nn.isInstanceOf[AstConstant] || isConstant(expr.nn)
          }
        }
      case _ => false
    }

  /** Check if a node is a constant expression (safe to inline across scopes).
    *
    * A constant expression is a literal value or a pure composition of constants. Lambdas, classes, and `this` are NOT constant expressions since they can close over scope.
    */
  def isConstantExpression(node: AstNode): Boolean =
    node match {
      case _: AstConstant => true
      case _: AstLambda | _: AstClass | _: AstThis => false
      case prefix: AstUnaryPrefix =>
        prefix.expression != null && isConstantExpression(prefix.expression.nn)
      case binary: AstBinary =>
        binary.left != null && binary.right != null &&
          isConstantExpression(binary.left.nn) && isConstantExpression(binary.right.nn)
      case seq: AstSequence =>
        seq.expressions.nonEmpty && seq.expressions.forall(isConstantExpression)
      case cond: AstConditional =>
        cond.condition != null && cond.consequent != null && cond.alternative != null &&
          isConstantExpression(cond.condition.nn) && isConstantExpression(cond.consequent.nn) && isConstantExpression(cond.alternative.nn)
      case arr: AstArray =>
        arr.elements.forall(isConstantExpression)
      case _ => false
    }

  // -----------------------------------------------------------------------
  // Internal evaluation (recursive)
  // -----------------------------------------------------------------------

  // Set of AstSymbolRef nodes currently being evaluated (prevents infinite recursion)
  private val reentrantRefEval: mutable.Set[AstSymbolRef] = mutable.Set.empty

  /** Evaluate a node recursively. Returns the JS value or the original node if unevaluable.
    *
    * @param node
    *   the node to evaluate
    * @param compressor
    *   compressor context
    * @param depth
    *   evaluation depth (incremented for type-converting operations)
    * @param astChain
    *   true when evaluating inside an optional chain
    */
  private[compress] def evalNode(
    node:       AstNode,
    compressor: CompressorLike,
    depth:      Int,
    astChain:   Boolean = false
  ): Any =
    node match {
      // Lambda/Class must come before AstStatement since they extend AstScope extends AstBlock extends AstStatement
      case fn: AstFunction if compressor.optionBool("unsafe") =>
        // In unsafe mode, return a wrapper that allows property access (.name, .length)
        EvalFunction(fn)
      case _: AstLambda => node
      case _: AstClass  => node

      // New must come before AstCall since AstNew extends AstCall
      case _: AstNew => node

      // Constants return their value
      case s: AstString    => s.value
      case n: AstNumber    => n.value
      case _: AstTrue      => true
      case _: AstFalse     => false
      case _: AstNull      => null
      case _: AstUndefined => () // represents undefined
      case _: AstNaN       => Double.NaN
      case _: AstInfinity  => Double.PositiveInfinity

      // BigInt
      case bi: AstBigInt =>
        try
          BigInt(bi.value)
        catch {
          case _: NumberFormatException => node
        }

      // RegExp
      case re: AstRegExp =>
        val cached = compressor.evaluatedRegexps.get(re.value)
        cached match {
          case Some(v) => if (v == null) node else v
          case None    =>
            // Only evaluate safe regexps (no ReDoS risk)
            if (regexpIsSafe(re.value.source)) {
              // Store the RegExpValue itself as the evaluated form
              compressor.evaluatedRegexps(re.value) = re.value
              re.value
            } else {
              compressor.evaluatedRegexps(re.value) = null.asInstanceOf[RegExpValue] // @nowarn -- cache miss
              node
            }
        }

      // Template string with single segment
      case ts: AstTemplateString =>
        if (ts.segments.size != 1) node
        else {
          ts.segments(0) match {
            case seg: AstTemplateSegment => seg.value
            case _ => node
          }
        }

      // Unary prefix
      case prefix: AstUnaryPrefix =>
        evalUnaryPrefix(prefix, compressor, depth)

      // Binary
      case binary: AstBinary =>
        evalBinary(binary, compressor, depth)

      // Conditional (ternary)
      case cond: AstConditional =>
        evalConditional(cond, compressor, depth)

      // Symbol reference
      case ref: AstSymbolRef =>
        evalSymbolRef(ref, compressor, depth)

      // Optional chain
      case chain: AstChain =>
        chain.expression match {
          case null => node
          case expr =>
            val evaluated = evalNode(expr.nn, compressor, depth, astChain = true)
            if (evaluated.asInstanceOf[AnyRef] eq Nullish) () // undefined
            else if (evaluated.asInstanceOf[AnyRef] eq expr.nn.asInstanceOf[AnyRef]) node
            else evaluated
        }

      // Property access
      case pa: AstPropAccess =>
        evalPropAccess(pa, compressor, depth, astChain)

      // Call
      case call: AstCall =>
        evalCall(call, compressor, depth, astChain)

      // Array (unsafe mode)
      case arr: AstArray =>
        evalArray(arr, compressor, depth)

      // Object (unsafe mode)
      case obj: AstObject =>
        evalObject(obj, compressor, depth)

      // Statements cannot be evaluated (must come after Lambda/Class which are also Statements)
      case _: AstStatement =>
        throw new IllegalStateException(
          s"Cannot evaluate a statement [${node.start}]"
        )

      // Default: unevaluable
      case _ => node
    }

  // -----------------------------------------------------------------------
  // Unary prefix evaluation
  // -----------------------------------------------------------------------

  private def evalUnaryPrefix(
    prefix:     AstUnaryPrefix,
    compressor: CompressorLike,
    depth:      Int
  ): Any =
    boundary[Any] {
      val e = prefix.expression
      if (e == null) break(prefix)

      // typeof special cases
      if (compressor.optionBool("typeofs") && prefix.operator == "typeof") {
        e.nn match {
          case _: AstLambda => break("function")
          case ref: AstSymbolRef =>
            // Check fixed value for symbol references
            ref.fixedValue() match {
              case _: AstLambda          => break("function")
              case _: AstObject | _: AstArray => break("object")
              case _ => // continue to evaluate below
            }
          case obj: AstObject if !Inference.hasSideEffects(obj, compressor) =>
            break("object")
          case arr: AstArray if !Inference.hasSideEffects(arr, compressor) =>
            break("object")
          case _ =>
        }
      }

      val newDepth  = if (nonConvertingUnary.contains(prefix.operator)) depth else depth + 1
      val evaluated = evalNode(e.nn, compressor, newDepth)
      if (evaluated.asInstanceOf[AnyRef] eq e.nn.asInstanceOf[AnyRef]) break(prefix)

      prefix.operator match {
        case "!" =>
          evaluated match {
            case b: Boolean => !b
            case d: Double  => !(d != 0.0 && !d.isNaN)
            case s: String  => s.isEmpty
            case null => true
            case ()   => true // undefined
            case _    => prefix
          }
        case "typeof" =>
          evaluated match {
            case _: Boolean => "boolean"
            case _: Double  => "number"
            case _: String  => "string"
            case null => "object"
            case ()   => "undefined" // undefined
            case _: BigInt      => "bigint"
            case _: RegExpValue => prefix // RegExp typeof is platform-dependent
            case _ => prefix
          }
        case "void" => () // undefined
        case "~"    =>
          evaluated match {
            case d:  Double => (~d.toInt).toDouble
            case bi: BigInt => ~bi
            case _ => prefix
          }
        case "-" =>
          evaluated match {
            case d:  Double => -d
            case bi: BigInt => -bi
            case _ => prefix
          }
        case "+" =>
          evaluated match {
            case d: Double  => d
            case b: Boolean => if (b) 1.0 else 0.0
            case null => 0.0
            case ()   => Double.NaN // undefined
            case s: String =>
              try s.toDouble
              catch { case _: NumberFormatException => Double.NaN }
            case _ => prefix
          }
        case _ => prefix
      }
    }

  // -----------------------------------------------------------------------
  // Binary evaluation
  // -----------------------------------------------------------------------

  private def evalBinary(
    binary:     AstBinary,
    compressor: CompressorLike,
    depth:      Int
  ): Any =
    boundary[Any] {
      val newDepth = if (nonConvertingBinary.contains(binary.operator)) depth else depth + 1

      if (binary.left == null || binary.right == null) break(binary)

      val left = evalNode(binary.left.nn, compressor, newDepth)
      if (left.asInstanceOf[AnyRef] eq binary.left.nn.asInstanceOf[AnyRef]) break(binary)

      val right = evalNode(binary.right.nn, compressor, newDepth)
      if (right.asInstanceOf[AnyRef] eq binary.right.nn.asInstanceOf[AnyRef]) break(binary)

      // Do not compare objects by reference identity
      if (
        left != null && right != null
        && identityComparison.contains(binary.operator)
        && isObjectLike(left) && isObjectLike(right)
      ) {
        break(binary)
      }

      // BigInt guard: do not mix BigInt and Number; don't use >>> on BigInt or divide by 0n
      val leftIsBigInt  = left.isInstanceOf[BigInt]
      val rightIsBigInt = right.isInstanceOf[BigInt]
      if (leftIsBigInt != rightIsBigInt) break(binary) // mixing BigInt and Number
      if (
        leftIsBigInt && (
          binary.operator == ">>>" ||
          (binary.operator == "/" && right == BigInt(0))
        )
      ) {
        break(binary)
      }

      // Type-safe numeric operations on Doubles
      val result: Any = (left, right) match {
        case (l: Double, r: Double) =>
          binary.operator match {
            case "&&"         => if (l != 0.0 && !l.isNaN) r else l
            case "||"         => if (l != 0.0 && !l.isNaN) l else r
            case "|"          => (l.toInt | r.toInt).toDouble
            case "&"          => (l.toInt & r.toInt).toDouble
            case "^"          => (l.toInt ^ r.toInt).toDouble
            case "+"          => l + r
            case "*"          => l * r
            case "**"         => Math.pow(l, r)
            case "/"          => l / r
            case "%"          => l % r
            case "-"          => l - r
            case "<<"         => (l.toInt << r.toInt).toDouble
            case ">>"         => (l.toInt >> r.toInt).toDouble
            case ">>>"        => (l.toInt >>> r.toInt).toDouble
            case "==" | "===" => l == r
            case "!=" | "!==" => l != r
            case "<"          => l < r
            case "<="         => l <= r
            case ">"          => l > r
            case ">="         => l >= r
            case _            => break(binary)
          }

        case (l: String, r: String) =>
          binary.operator match {
            case "+"          => l + r
            case "==" | "===" => l == r
            case "!=" | "!==" => l != r
            case "<"          => l < r
            case "<="         => l <= r
            case ">"          => l > r
            case ">="         => l >= r
            case _            => break(binary)
          }

        case (l: Boolean, r: Boolean) =>
          binary.operator match {
            case "&&"         => l && r
            case "||"         => l || r
            case "==" | "===" => l == r
            case "!=" | "!==" => l != r
            case _            => break(binary)
          }

        // BigInt operations (already guarded against mixing with Number above)
        case (l: BigInt, r: BigInt) =>
          binary.operator match {
            case "&&" => if (l != BigInt(0)) r else l
            case "||" => if (l != BigInt(0)) l else r
            case "|"  => l | r
            case "&"  => l & r
            case "^"  => l ^ r
            case "+"  => l + r
            case "*"  => l * r
            case "**" => l.pow(r.toInt) // ISS-171: BigInt exponentiation
            case "/"  => l / r // guarded against 0 above
            case "%"  => l % r
            case "-"  => l - r
            case "<<" => l << r.toInt
            case ">>" => l >> r.toInt
            // >>> not supported for BigInt (guarded above)
            case "==" | "===" => l == r
            case "!=" | "!==" => l != r
            case "<"          => l < r
            case "<="         => l <= r
            case ">"          => l > r
            case ">="         => l >= r
            case _            => break(binary)
          }

        // String + non-string (concatenation)
        case (l: String, r) if binary.operator == "+" =>
          l + String.valueOf(r)
        case (l, r: String) if binary.operator == "+" =>
          String.valueOf(l) + r

        // Nullish coalescing
        case (l, r) if binary.operator == "??" =>
          if (l != null && !l.isInstanceOf[Unit]) l else r

        case _ => break(binary)
      }

      // NaN guard: if result is NaN and we're inside a `with` block, don't fold
      // (because `with` can shadow variables and make NaN comparisons behave oddly)
      result match {
        case d: Double if d.isNaN =>
          if (compressor.findParent[AstWith] != null) break(binary)
          result
        case _ => result
      }
    }

  private def isObjectLike(v: Any): Boolean =
    v match {
      case _: Map[?, ?] | _: mutable.Map[?, ?] => true
      case _: Seq[?] | _: mutable.Buffer[?]    => true
      case _: RegExpValue => true
      case _ => false
    }

  // -----------------------------------------------------------------------
  // Conditional evaluation
  // -----------------------------------------------------------------------

  private def evalConditional(
    cond:       AstConditional,
    compressor: CompressorLike,
    depth:      Int
  ): Any =
    if (cond.condition == null) cond
    else {
      val condition = evalNode(cond.condition.nn, compressor, depth)
      if (condition.asInstanceOf[AnyRef] eq cond.condition.nn.asInstanceOf[AnyRef]) cond
      else {
        val branch = if (isTruthy(condition)) cond.consequent else cond.alternative
        if (branch == null) cond
        else {
          val value = evalNode(branch.nn, compressor, depth)
          if (value.asInstanceOf[AnyRef] eq branch.nn.asInstanceOf[AnyRef]) cond
          else value
        }
      }
    }

  // -----------------------------------------------------------------------
  // Symbol reference evaluation
  // -----------------------------------------------------------------------

  /** Evaluate a symbol reference by looking up its fixed value.
    *
    * This handles:
    *   - Infinite recursion guard via reentrantRefEval set
    *   - Fixed value lookup from scope analysis
    *   - Escape depth check for object values
    */
  private def evalSymbolRef(
    ref:        AstSymbolRef,
    compressor: CompressorLike,
    depth:      Int
  ): Any = {
    // Prevent infinite recursion
    if (reentrantRefEval.contains(ref)) return ref // @nowarn — early exit

    // Get the fixed value from scope analysis
    val fixed = ref.fixedValue()
    fixed match {
      case false | null => ref // no fixed value
      case fixedNode: AstNode =>
        // Mark as being evaluated to prevent infinite recursion
        reentrantRefEval.add(ref)
        val value =
          try evalNode(fixedNode, compressor, depth)
          finally reentrantRefEval.remove(ref)

        // If evaluation returned the same node, the ref is unevaluable
        if (value.asInstanceOf[AnyRef] eq fixedNode.asInstanceOf[AnyRef])
          ref
        else if (value != null && isObjectLike(value)) {
          // For object values, check escape depth
          ref.definition() match {
            case d: ssg.js.scope.SymbolDef if d.escaped > 0 && depth > d.escaped =>
              // Value escaped at a shallower depth, don't propagate
              ref
            case _ => value
          }
        } else {
          value
        }
      case _ => ref // unexpected type
    }
  }

  // -----------------------------------------------------------------------
  // Property access evaluation
  // -----------------------------------------------------------------------

  private def evalPropAccess(
    pa:         AstPropAccess,
    compressor: CompressorLike,
    depth:      Int,
    astChain:   Boolean
  ): Any =
    boundary[Any] {
      if (pa.expression == null) break(pa)

      val propName = pa.property match {
        case s: String  => s
        case n: AstNode =>
          if (!compressor.optionBool("unsafe") && !astChain) break(pa)
          val evaluated = evalNode(n, compressor, depth)
          evaluated match {
            case s: String => s
            case d: Double => d.toString
            case _ => break(pa)
          }
      }

      // Evaluate expression (only if safe)
      val shouldEval = astChain || propName == "length" || compressor.optionBool("unsafe")
      if (!shouldEval) break(pa)

      val obj = evalNode(pa.expression.nn, compressor, depth + 1, astChain)

      // Optional chaining nullish check
      if (astChain) {
        if (obj.asInstanceOf[AnyRef] eq Nullish) break(Nullish)
        pa match {
          case dotNode: AstDot if dotNode.optional && (obj == null || obj.isInstanceOf[Unit]) =>
            break(Nullish)
          case subNode: AstSub if subNode.optional && (obj == null || obj.isInstanceOf[Unit]) =>
            break(Nullish)
          case _ =>
        }
      }

      // .length on strings and arrays (always safe)
      if (propName == "length") {
        obj match {
          case s:   String   => break(s.length.toDouble)
          case arr: AstArray =>
            // Check if array has no spreads and all elements have no side effects
            val noSpreads = arr.elements.forall(!_.isInstanceOf[AstExpansion])
            if (noSpreads && arr.elements.forall(el => !Inference.hasSideEffects(el, compressor))) {
              break(arr.elements.size.toDouble)
            }
          case seq: Seq[?] =>
            // Evaluated array from unsafe mode
            break(seq.size.toDouble)
          case _ =>
        }
      }

      // Unsafe property access on evaluated objects
      if (compressor.optionBool("unsafe")) {
        // Check for undeclared ref to global object (Math.PI, Number.MAX_VALUE, etc.)
        pa.expression.nn match {
          case ref: AstSymbolRef if Inference.isUndeclaredRef(ref) =>
            // Handle hasOwnProperty.call edge case (ISS-167)
            if (ref.name == "hasOwnProperty" && propName == "call") {
              // Get the call parent and check first arg
              val parent = compressor.parent()
              parent match {
                case call: AstCall if call.args.nonEmpty =>
                  val firstArg = evaluate(call.args(0), compressor)
                  firstArg match {
                    case dot: AstDot => // first_arg = first_arg instanceof AST_Dot ? first_arg.expression : first_arg
                      val expr = dot.expression
                      if (expr == null) break(pa) // unevaluable
                      val evalExpr = evalNode(expr.nn, compressor, depth)
                      if (evalExpr == null || (evalExpr.isInstanceOf[AstSymbolRef] &&
                          evalExpr.asInstanceOf[AstSymbolRef].definition() != null &&
                          evalExpr.asInstanceOf[AstSymbolRef].definition().nn.undeclared)) {
                        break(pa) // unevaluable
                      }
                    case _ if firstArg == null => break(pa) // unevaluable
                    case ref2: AstSymbolRef if ref2.definition() != null && ref2.definition().nn.undeclared =>
                      break(pa) // unevaluable
                    case _ => // continue
                  }
                case _ =>
              }
            }
            // Check if this is a pure native value (Math.PI, Number.MAX_VALUE, etc.)
            if (isPureNativeValue(ref.name, propName)) {
              // Return the actual value for known constants
              (ref.name, propName) match {
                case ("Math", "E")              => break(Math.E)
                case ("Math", "LN10")           => break(Math.log(10.0))
                case ("Math", "LN2")            => break(Math.log(2.0))
                case ("Math", "LOG2E")          => break(1.0 / Math.log(2.0))
                case ("Math", "LOG10E")         => break(1.0 / Math.log(10.0))
                case ("Math", "PI")             => break(Math.PI)
                case ("Math", "SQRT1_2")        => break(Math.sqrt(0.5))
                case ("Math", "SQRT2")          => break(Math.sqrt(2.0))
                case ("Number", "MAX_VALUE")    => break(Double.MaxValue)
                case ("Number", "MIN_VALUE")    => break(Double.MinPositiveValue)
                case ("Number", "NaN")          => break(Double.NaN)
                case ("Number", "NEGATIVE_INFINITY") => break(Double.NegativeInfinity)
                case ("Number", "POSITIVE_INFINITY") => break(Double.PositiveInfinity)
                case _ => break(pa)
              }
            }
            break(pa)
          case _ =>
        }

        obj match {
          case s: String =>
            propName match {
              case "length" => break(s.length.toDouble)
              case _        => break(pa)
            }
          case rv: RegExpValue =>
            // RegExp property access (ISS-169: use regexp_source_fix for source)
            propName match {
              case "source"     => break(regexpSourceFix(rv.source))
              case "flags"      => break(rv.flags)
              case "global"     => break(rv.flags.contains('g'))
              case "ignoreCase" => break(rv.flags.contains('i'))
              case "multiline"  => break(rv.flags.contains('m'))
              case "dotAll"     => break(rv.flags.contains('s'))
              case "unicode"    => break(rv.flags.contains('u'))
              case "sticky"     => break(rv.flags.contains('y'))
              case _ => break(pa)
            }
          case ef: EvalFunction =>
            // Function property access (.name, .length)
            propName match {
              case "name"   => break(ef.name)
              case "length" => break(ef.length.toDouble)
              case _ => break(pa)
            }
          case m: Map[?, ?] =>
            // ISS-168: HOP check - only access if obj has own property
            val mapObj = m.asInstanceOf[Map[String, Any]]
            if (hasOwnProperty(mapObj, propName))
              break(mapObj(propName))
            else break(pa)
          case _ =>
            // For other evaluated objects, we can't access properties safely
            // because we don't have HOP check semantics for Scala objects
            break(pa)
        }
      }

      pa
    }

  // -----------------------------------------------------------------------
  // Call evaluation
  // -----------------------------------------------------------------------

  private def evalCall(
    call:       AstCall,
    compressor: CompressorLike,
    depth:      Int,
    astChain:   Boolean
  ): Any = {
    boundary[Any] {
      if (call.expression == null) break(call)

      // Optional chain check
      if (astChain) {
        val callee = evalNode(call.expression.nn, compressor, depth, astChain)
        if (callee.asInstanceOf[AnyRef] eq Nullish) break(Nullish)
        if (call.optional && (callee == null || callee.isInstanceOf[Unit])) break(Nullish)
      }

      // Only proceed in unsafe mode with property access calls
      if (!compressor.optionBool("unsafe")) break(call)

      call.expression match {
        case pa: AstPropAccess =>
          val key = pa.property match {
            case s: String  => s
            case n: AstNode =>
              val k = evalNode(n, compressor, depth)
              k match {
                case s: String => s
                case d: Double => d.toString
                case _ => break(call)
              }
          }

          if (pa.expression == null) break(call)

          // Check for pure native function calls on global objects
          pa.expression.nn match {
            case ref: AstSymbolRef if Inference.isUndeclaredRef(ref) =>
              // ISS-172: Handle hasOwnProperty.call edge case
              if (ref.name == "hasOwnProperty" && key == "call" && call.args.nonEmpty) {
                var firstArg: Any = evaluate(call.args(0), compressor)
                // first_arg = first_arg instanceof AST_Dot ? first_arg.expression : first_arg
                firstArg = firstArg match {
                  case dot: AstDot => if (dot.expression != null) dot.expression.nn else firstArg
                  case _           => firstArg
                }
                // If first_arg is null or has undeclared thedef, return unevaluable
                firstArg match {
                  case null => break(call) // unevaluable
                  case ref2: AstSymbolRef if ref2.definition() != null && ref2.definition().nn.undeclared =>
                    break(call) // unevaluable
                  case _ => // continue
                }
              }
              if (!isPureNativeFn(ref.name, key)) break(call)

              // Evaluate arguments for global function calls
              val globalArgs = mutable.ArrayBuffer.empty[Any]
              var gi         = 0
              while (gi < call.args.size) {
                val arg = call.args(gi)
                if (arg.isInstanceOf[AstLambda]) break(call)
                val value = evalNode(arg, compressor, depth)
                if (value.asInstanceOf[AnyRef] eq arg.asInstanceOf[AnyRef]) break(call)
                globalArgs.addOne(value)
                gi += 1
              }

              // Execute Math methods at compile time
              if (ref.name == "Math") {
                try
                  key match {
                    case "abs"   => globalArgs.headOption match {
                      case Some(d: Double) => break(Math.abs(d))
                      case _ => break(call)
                    }
                    case "acos"  => globalArgs.headOption match {
                      case Some(d: Double) => break(Math.acos(d))
                      case _ => break(call)
                    }
                    case "asin"  => globalArgs.headOption match {
                      case Some(d: Double) => break(Math.asin(d))
                      case _ => break(call)
                    }
                    case "atan"  => globalArgs.headOption match {
                      case Some(d: Double) => break(Math.atan(d))
                      case _ => break(call)
                    }
                    case "atan2" =>
                      if (globalArgs.size >= 2) {
                        (globalArgs(0), globalArgs(1)) match {
                          case (y: Double, x: Double) => break(Math.atan2(y, x))
                          case _ => break(call)
                        }
                      } else break(call)
                    case "ceil"  => globalArgs.headOption match {
                      case Some(d: Double) => break(Math.ceil(d))
                      case _ => break(call)
                    }
                    case "cos"   => globalArgs.headOption match {
                      case Some(d: Double) => break(Math.cos(d))
                      case _ => break(call)
                    }
                    case "exp"   => globalArgs.headOption match {
                      case Some(d: Double) => break(Math.exp(d))
                      case _ => break(call)
                    }
                    case "floor" => globalArgs.headOption match {
                      case Some(d: Double) => break(Math.floor(d))
                      case _ => break(call)
                    }
                    case "log"   => globalArgs.headOption match {
                      case Some(d: Double) => break(Math.log(d))
                      case _ => break(call)
                    }
                    case "max"   =>
                      val nums = globalArgs.collect { case d: Double => d }
                      if (nums.size == globalArgs.size && nums.nonEmpty)
                        break(nums.max)
                      else break(call)
                    case "min"   =>
                      val nums = globalArgs.collect { case d: Double => d }
                      if (nums.size == globalArgs.size && nums.nonEmpty)
                        break(nums.min)
                      else break(call)
                    case "pow"   =>
                      if (globalArgs.size >= 2) {
                        (globalArgs(0), globalArgs(1)) match {
                          case (base: Double, exp: Double) => break(Math.pow(base, exp))
                          case _ => break(call)
                        }
                      } else break(call)
                    case "round" => globalArgs.headOption match {
                      case Some(d: Double) => break(Math.round(d).toDouble)
                      case _ => break(call)
                    }
                    case "sin"   => globalArgs.headOption match {
                      case Some(d: Double) => break(Math.sin(d))
                      case _ => break(call)
                    }
                    case "sqrt"  => globalArgs.headOption match {
                      case Some(d: Double) => break(Math.sqrt(d))
                      case _ => break(call)
                    }
                    case "tan"   => globalArgs.headOption match {
                      case Some(d: Double) => break(Math.tan(d))
                      case _ => break(call)
                    }
                    case _ => break(call)
                  }
                catch {
                  case _: Exception => break(call)
                }
              }

              // Execute Number methods at compile time
              if (ref.name == "Number") {
                try
                  key match {
                    case "isFinite" => globalArgs.headOption match {
                      case Some(d: Double) => break(d.isFinite)
                      case _ => break(call)
                    }
                    case "isNaN" => globalArgs.headOption match {
                      case Some(d: Double) => break(d.isNaN)
                      case _ => break(call)
                    }
                    case _ => break(call)
                  }
                catch {
                  case _: Exception => break(call)
                }
              }

              // Execute Array methods at compile time
              if (ref.name == "Array") {
                try
                  key match {
                    case "isArray" => globalArgs.headOption match {
                      case Some(_: Seq[?]) => break(true)
                      case Some(_)         => break(false)
                      case None            => break(call)
                    }
                    case _ => break(call)
                  }
                catch {
                  case _: Exception => break(call)
                }
              }

              // Execute String static methods at compile time
              if (ref.name == "String") {
                try
                  key match {
                    case "fromCharCode" =>
                      val codes = globalArgs.collect { case d: Double => d.toInt.toChar }
                      if (codes.size == globalArgs.size)
                        break(codes.mkString)
                      else break(call)
                    case _ => break(call)
                  }
                catch {
                  case _: Exception => break(call)
                }
              }

              break(call) // Unknown global function
            case _ =>
          }

          // Evaluate the object
          val obj = evalNode(pa.expression.nn, compressor, depth + 1)
          if (obj.asInstanceOf[AnyRef] eq pa.expression.nn.asInstanceOf[AnyRef]) break(call)
          if (obj == null) break(call)

          // Evaluate arguments
          val args = mutable.ArrayBuffer.empty[Any]
          var i    = 0
          while (i < call.args.size) {
            val arg = call.args(i)
            if (arg.isInstanceOf[AstLambda]) break(call)
            val value = evalNode(arg, compressor, depth)
            if (value.asInstanceOf[AnyRef] eq arg.asInstanceOf[AnyRef]) break(call)
            args.addOne(value)
            i += 1
          }

          // Execute pure methods at compile time
          obj match {
            case s: String =>
              if (isPureNativeMethod("String", key)) {
                try
                  key match {
                    case "charAt" =>
                      args.headOption match {
                        case Some(d: Double) =>
                          val idx = d.toInt
                          if (idx >= 0 && idx < s.length) break(s.charAt(idx).toString)
                          else break("")
                        case _ => break(call)
                      }
                    case "charCodeAt" =>
                      args.headOption match {
                        case Some(d: Double) =>
                          val idx = d.toInt
                          if (idx >= 0 && idx < s.length) break(s.charAt(idx).toDouble)
                          else break(Double.NaN)
                        case _ => break(call)
                      }
                    case "indexOf" =>
                      args.headOption match {
                        case Some(sub: String) =>
                          val from = if (args.size > 1) args(1).asInstanceOf[Double].toInt else 0
                          break(s.indexOf(sub, from).toDouble)
                        case _ => break(call)
                      }
                    case "slice" =>
                      args.headOption match {
                        case Some(d: Double) =>
                          val start = d.toInt
                          val end   = if (args.size > 1) args(1).asInstanceOf[Double].toInt else s.length
                          break(
                            s.slice(
                              if (start < 0) Math.max(s.length + start, 0) else start,
                              if (end < 0) Math.max(s.length + end, 0) else end
                            )
                          )
                        case _ => break(call)
                      }
                    case "toLowerCase"          => break(s.toLowerCase)
                    case "toUpperCase"          => break(s.toUpperCase)
                    case "trim"                 => break(s.trim)
                    case "toString" | "valueOf" => break(s)
                    case _                      => break(call)
                  }
                catch {
                  case _: Exception => break(call)
                }
              }

            // ISS-173: Array instance methods
            case arr: Seq[?] =>
              if (isPureNativeMethod("Array", key)) {
                try
                  key match {
                    case "at" =>
                      args.headOption match {
                        case Some(d: Double) =>
                          val idx = d.toInt
                          val normalizedIdx = if (idx < 0) arr.size + idx else idx
                          if (normalizedIdx >= 0 && normalizedIdx < arr.size) break(arr(normalizedIdx))
                          else break(()) // undefined
                        case _ => break(call)
                      }
                    case "flat" =>
                      // Flatten one level (default depth=1)
                      val depth = args.headOption.collect { case d: Double => d.toInt }.getOrElse(1)
                      if (depth == 0) break(arr)
                      val flattened = arr.flatMap {
                        case inner: Seq[?] => inner
                        case other         => Seq(other)
                      }
                      break(flattened)
                    case "includes" =>
                      args.headOption match {
                        case Some(searchElement) =>
                          val fromIndex = if (args.size > 1) args(1).asInstanceOf[Double].toInt else 0
                          break(arr.drop(fromIndex).contains(searchElement))
                        case None => break(call)
                      }
                    case "indexOf" =>
                      args.headOption match {
                        case Some(searchElement) =>
                          val fromIndex = if (args.size > 1) args(1).asInstanceOf[Double].toInt else 0
                          val idx = arr.drop(fromIndex).indexOf(searchElement)
                          break(if (idx >= 0) (idx + fromIndex).toDouble else -1.0)
                        case None => break(call)
                      }
                    case "join" =>
                      val sep = args.headOption.collect { case s: String => s }.getOrElse(",")
                      break(arr.map(String.valueOf).mkString(sep))
                    case "lastIndexOf" =>
                      args.headOption match {
                        case Some(searchElement) =>
                          val fromIndex = if (args.size > 1) args(1).asInstanceOf[Double].toInt else arr.size - 1
                          val idx = arr.take(fromIndex + 1).lastIndexOf(searchElement)
                          break(idx.toDouble)
                        case None => break(call)
                      }
                    case "slice" =>
                      val start = args.headOption.collect { case d: Double => d.toInt }.getOrElse(0)
                      val end = if (args.size > 1) args(1).asInstanceOf[Double].toInt else arr.size
                      val normalizedStart = if (start < 0) Math.max(arr.size + start, 0) else start
                      val normalizedEnd = if (end < 0) Math.max(arr.size + end, 0) else end
                      break(arr.slice(normalizedStart, normalizedEnd))
                    case "toString" | "valueOf" =>
                      break(arr.map(String.valueOf).mkString(","))
                    case _ => break(call)
                  }
                catch {
                  case _: Exception => break(call)
                }
              }

            // ISS-174: Number instance methods
            case d: Double =>
              if (isPureNativeMethod("Number", key)) {
                try
                  key match {
                    case "toExponential" =>
                      args.headOption match {
                        case Some(digits: Double) =>
                          val fractionDigits = digits.toInt
                          if (fractionDigits < 0 || fractionDigits > 100) break(call) // RangeError
                          break(s"%%.${fractionDigits}e".format(d))
                        case None => break(s"%.6e".format(d)) // default 6 digits
                        case _    => break(call)
                      }
                    case "toFixed" =>
                      args.headOption match {
                        case Some(digits: Double) =>
                          val fractionDigits = digits.toInt
                          if (fractionDigits < 0 || fractionDigits > 100) break(call) // RangeError
                          break(s"%%.${fractionDigits}f".format(d))
                        case None => break(s"%.0f".format(d)) // default 0 digits
                        case _    => break(call)
                      }
                    case "toPrecision" =>
                      args.headOption match {
                        case Some(precision: Double) =>
                          val prec = precision.toInt
                          if (prec < 1 || prec > 100) break(call) // RangeError
                          break(s"%%.${prec}g".format(d))
                        case None => break(d.toString) // no precision = toString
                        case _    => break(call)
                      }
                    case "toString" | "valueOf" => break(d)
                    case _ => break(call)
                  }
                catch {
                  case _: Exception => break(call)
                }
              }

            case _ =>
          }

          call

        case _ => call
      }
    }
  }

  // -----------------------------------------------------------------------
  // Array/Object evaluation (unsafe mode)
  // -----------------------------------------------------------------------

  private def evalArray(
    arr:        AstArray,
    compressor: CompressorLike,
    depth:      Int
  ): Any =
    if (!compressor.optionBool("unsafe")) arr
    else {
      boundary[Any] {
        val elements = mutable.ArrayBuffer.empty[Any]
        var i        = 0
        while (i < arr.elements.size) {
          val element = arr.elements(i)
          val value   = evalNode(element, compressor, depth)
          if (value.asInstanceOf[AnyRef] eq element.asInstanceOf[AnyRef]) break(arr)
          elements.addOne(value)
          i += 1
        }
        elements.toSeq
      }
    }

  private def evalObject(
    obj:        AstObject,
    compressor: CompressorLike,
    depth:      Int
  ): Any =
    if (!compressor.optionBool("unsafe")) obj
    else {
      boundary[Any] {
        val result = mutable.Map.empty[String, Any]
        var i      = 0
        while (i < obj.properties.size) {
          obj.properties(i) match {
            case _:  AstExpansion    => break(obj)
            case kv: AstObjectKeyVal =>
              val key: String = kv.key match {
                case s:   String    => s
                case sym: AstSymbol => sym.name
                case n:   AstNode   =>
                  val k = evalNode(n, compressor, depth)
                  if (k.asInstanceOf[AnyRef] eq n.asInstanceOf[AnyRef]) break(obj)
                  k.toString
              }
              // ISS-170: Guard against Object.prototype functions (toString, valueOf, constructor)
              if (objectPrototypeFunctions.contains(key)) break(obj)
              // Skip function values
              kv.value match {
                case _: AstFunction | null => // skip
                case v                     =>
                  val value = evalNode(v.nn, compressor, depth)
                  if (value.asInstanceOf[AnyRef] eq v.nn.asInstanceOf[AnyRef]) break(obj)
                  result(key) = value
              }
            case _ => break(obj) // getter/setter/etc
          }
          i += 1
        }
        result.toMap
      }
    }

  // -----------------------------------------------------------------------
  // Truthiness helper
  // -----------------------------------------------------------------------

  /** Check if a value is truthy in JavaScript semantics. */
  private def isTruthy(v: Any): Boolean =
    v match {
      case null       => false
      case ()         => false // undefined
      case false      => false
      case 0.0 | -0.0 => false
      case d: Double if d.isNaN => false
      case "" => false
      case _  => true
    }
}
