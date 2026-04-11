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

/** Constant folding engine.
  *
  * Evaluates compile-time-constant expressions and returns either the evaluated value or the original AST node when evaluation is not possible.
  */
object Evaluate {

  private val unaryPrefixOps:      Set[String] = Set("!", "~", "-", "+", "void")
  private val nonConvertingUnary:  Set[String] = Set("!", "typeof", "void")
  private val nonConvertingBinary: Set[String] = Set("&&", "||", "??", "===", "!==")
  private val identityComparison:  Set[String] = Set("==", "!=", "===", "!==")

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
            // Store the RegExpValue itself as the evaluated form
            compressor.evaluatedRegexps(re.value) = re.value
            re.value
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
          case _: AstLambda    => break("function")
          case _: AstSymbolRef =>
            // Check fixed value
            // TODO: fixedValue lookup when scope analysis is complete
            break(prefix)
          case _: AstObject | _: AstArray =>
            // hasSideEffects check would go here
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

      // Type-safe numeric operations on Doubles
      (left, right) match {
        case (l: Double, r: Double) =>
          val result: Any = binary.operator match {
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
          result

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

        // String + non-string (concatenation)
        case (l: String, r) if binary.operator == "+" =>
          l + String.valueOf(r)
        case (l, r: String) if binary.operator == "+" =>
          String.valueOf(l) + r

        // Nullish coalescing
        case (l, r) if binary.operator == "??" =>
          if (l != null && !l.isInstanceOf[Unit]) l else r

        case _ => binary
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

  private def evalSymbolRef(
    ref:        AstSymbolRef,
    compressor: CompressorLike,
    depth:      Int
  ): Any =
    if (reentrantRefEval.contains(ref)) ref
    else {
      // TODO: implement fixed_value() lookup when scope analysis is complete
      // For now, symbol refs are unevaluable
      ref
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

      // .length on strings
      if (propName == "length") {
        obj match {
          case s:   String   => break(s.length.toDouble)
          case arr: AstArray =>
            // Check if array has no spreads and no side effects
            val noSpreads = arr.elements.forall(!_.isInstanceOf[AstExpansion])
            if (noSpreads) {
              // In a full implementation we'd also check side effects
              break(arr.elements.size.toDouble)
            }
          case _ =>
        }
      }

      // Unsafe property access on evaluated objects
      if (compressor.optionBool("unsafe")) {
        obj match {
          case s: String =>
            propName match {
              case "length" => break(s.length.toDouble)
              case _        => break(pa)
            }
          case m: Map[?, ?] =>
            if (m.asInstanceOf[Map[String, Any]].contains(propName))
              break(m.asInstanceOf[Map[String, Any]](propName))
            else break(pa)
          case _ =>
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

          // Check for pure native function calls
          pa.expression.nn match {
            case ref: AstSymbolRef if Inference.isUndeclaredRef(ref) =>
              if (!isPureNativeFn(ref.name, key)) break(call)
              // We can't actually execute global functions at compile time
              break(call)
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

          // Execute pure string methods at compile time
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
