/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Main Compressor class: the orchestrator for all JS optimization passes.
 *
 * Implements the multi-pass optimization loop that walks the AST and
 * dispatches to per-node-type optimizers. Each node type has a dedicated
 * optimization function registered via the OPT pattern (Terser's
 * `def_optimize` macro).
 *
 * The compressor extends TreeWalker (not TreeTransformer) — the `before`
 * callback performs descent + optimization, returning the optimized node
 * to replace the original in the parent.
 *
 * Ported from: terser lib/compress/index.js
 * Original: Copyright (c) 2012 Mihai Bazon
 * Original license: BSD-2-Clause
 *
 * Migration notes:
 *   Renames: Compressor -> Compressor (same), def_optimize -> optimizeNode
 *     pattern match, OPT() macro -> optimizeNode() method dispatch,
 *     option() -> option(), compress() -> compress(), before() -> before()
 *   Convention: Class with CompressorLike trait, pattern matching dispatch
 *   Idiom: boundary/break instead of return, match/case instead of
 *     DEFMETHOD + instanceof chains
 *   Gap: 1067 LOC vs upstream 4129 LOC (~26%). Multi-pass convergence loop
 *     stubbed at lines 1021-1033 — TerserSuite compression tests are disabled
 *     because the loop hangs. Single-pass orchestration only. Pure-call elision
 *     (lines 107, 116) and global hoisting (line 286) gated on SymbolDef
 *     integration. See ISS-031, ISS-032. docs/architecture/terser-port.md.
 *   Audited: 2026-04-07 (major_issues)
 */
package ssg
package js
package compress

import scala.collection.mutable.ArrayBuffer
import scala.util.boundary
import scala.util.boundary.break

import ssg.js.ast.*
import ssg.js.compress.CompressorFlags.*
import ssg.js.compress.Common.*
import ssg.js.compress.Inference.*
import ssg.js.compress.TightenBody.{ extractFromUnreachableCode, tightenBody }
import ssg.js.compress.Inline.inlineIntoSymbolRef

/** The main JavaScript compressor.
  *
  * Performs multi-pass AST transformation to minimize JavaScript code size. Each pass walks the entire AST, applying per-node optimizations in a bottom-up fashion (children are optimized before
  * parents).
  *
  * @param options
  *   the compressor configuration
  */
class Compressor(val options: CompressorOptions) extends TreeWalker(null) with CompressorLike {

  // -----------------------------------------------------------------------
  // CompressorLike implementation
  //
  // parent(), findParent(), and hasDirective() are inherited from TreeWalker
  // which provides compatible implementations.
  // -----------------------------------------------------------------------

  override def option(name: String): Any = options.get(name)

  override def inBooleanContext(): Boolean =
    if (!optionBool("booleans")) false
    else {
      boundary[Boolean] {
        var current: AstNode = this.self()
        var i = 0
        var p: AstNode | Null = parent(i)
        while (p != null) {
          val pn = p.nn
          pn match {
            case _:       AstSimpleStatement                                                                => break(true)
            case cond:    AstConditional if cond.condition.nn eq current                                    => break(true)
            case dw:      AstDWLoop if dw.condition.nn eq current                                           => break(true)
            case forNode: AstFor if forNode.condition != null && (forNode.condition.nn eq current)          => break(true)
            case ifNode:  AstIf if ifNode.condition.nn eq current                                           => break(true)
            case up:      AstUnaryPrefix if up.operator == "!" && (up.expression.nn eq current)             => break(true)
            case bin:     AstBinary if bin.operator == "&&" || bin.operator == "||" || bin.operator == "??" =>
              current = pn
            case _: AstConditional =>
              current = pn
            case _ =>
              break(false)
          }
          i += 1
          p = parent(i)
        }
        false
      }
    }

  override def in32BitContext(): Boolean =
    if (!optionBool("evaluate")) false
    else {
      val p = parent(0)
      if (p == null) false
      else {
        p.nn match {
          case bin: AstBinary if bitwiseBinop.contains(bin.operator) => true
          case up:  AstUnaryPrefix if up.operator == "~"             => true
          case _ => false
        }
      }
    }

  override def exposed(theDef: Any): Boolean = {
    val d = theDef.asInstanceOf[ssg.js.scope.SymbolDef]
    d.exportFlag != 0 ||
      (d.undeclared && d.global) ||
      (d.global && {
        val dropsFuncs = toplevel.funcs
        val dropsVars  = toplevel.vars
        if (d.orig.nonEmpty && d.orig(0).isInstanceOf[AstSymbolDefun]) !dropsFuncs
        else if (d.orig.nonEmpty && d.orig(0).isInstanceOf[AstSymbolVar]) !dropsVars
        else !dropsFuncs || !dropsVars
      })
  }

  override def pureFuncs(call: AstCall): Boolean =
    // Returns true if the call is NOT pure (i.e., has side effects)
    options.pureFuncs match {
      case Nil => false // no pure_funcs specified — all calls may have side effects
      case _   =>
        // TODO: check call.expression.print_to_string() against pureFuncs
        true
    }

  // -----------------------------------------------------------------------
  // State
  //
  // `directives` is inherited from TreeWalker (Map[String, AstNode]).
  // -----------------------------------------------------------------------

  /** The current toplevel node being compressed. */
  private var toplevelNode: Option[AstToplevel] = None

  /** Sequences limit for the sequenceize pass. */
  val sequencesLimit: Int = {
    val seq = options.sequencesLimit
    if (seq == 1) 800 else seq
  }

  /** Bitwise binary operators. */
  private val bitwiseBinop: Set[String] = Set("&", "|", "^", "<<", ">>", ">>>")

  // Initialize module mode — set "use strict" directive
  if (options.module) {
    // TreeWalker.directives maps String -> AstNode, but we need a
    // sentinel node here. Use a synthetic directive.
    val strictDirective = new AstDirective
    strictDirective.value = "use strict"
    directives("use strict") = strictDirective
  }

  // -----------------------------------------------------------------------
  // Public API
  // -----------------------------------------------------------------------

  /** Get the current top-level AST being compressed. */
  def getToplevel: AstToplevel | Null = toplevelNode.getOrElse(null)

  /** Compress the given top-level AST.
    *
    * Runs multiple optimization passes over the AST. Each pass:
    *   1. Resolves global definitions (constant substitution)
    *   2. Figures out scope (variable resolution)
    *   3. Resets per-pass optimization flags
    *   4. Transforms the AST via the `before` callback
    *   5. Counts nodes to detect convergence (for multi-pass)
    *
    * @param ast
    *   the top-level AST to compress
    * @return
    *   the compressed AST
    */
  def compress(ast: AstToplevel): AstToplevel = {
    var toplevel = ast
    toplevelNode = Some(toplevel)

    // Resolve global definitions (e.g., DEBUG: false → replace all DEBUG refs)
    toplevel = GlobalDefs.resolveDefs(toplevel, options.globalDefs)

    val passes   = options.passes.max(1)
    var minCount = Int.MaxValue
    var stopping = false

    // Create a TreeTransformer that delegates to the Compressor's before() callback
    val compressor = this
    val transformer = new TreeTransformer(
      before = (node, descend) => {
        compressor.before(node, (n, _) => descend())
      }
    )

    var pass = 0
    while (pass < passes) {
      // Run scope analysis before each pass
      ssg.js.scope.ScopeAnalysis.figureOutScope(toplevel)

      if (pass == 0 && options.dropConsole != DropConsoleConfig.Disabled) {
        // must be run before reduce_vars and compress pass
        toplevel = dropConsole(toplevel)
      }

      // Reset per-pass flags and run data-flow analysis
      resetOptFlags(toplevel)
      if (optionBool("reduce_vars")) {
        ReduceVars.reduceVars(toplevel, this)
      }

      // Transform pass — walks the AST, optimizing each node
      toplevelNode = Some(toplevel)
      toplevel = toplevel.transform(transformer).asInstanceOf[AstToplevel]

      // Multi-pass convergence: count AST nodes, stop when size stops shrinking
      if (passes > 1) {
        var count = 0
        ssg.js.ast.walk(toplevel, (_, _) => { count += 1; null })
        if (count < minCount) {
          minCount = count
          stopping = false
        } else if (stopping) {
          pass = passes // break
        } else {
          stopping = true
        }
      }

      pass += 1
    }

    toplevelNode = None
    toplevel
  }

  /** Optimize each element of an ArrayBuffer in place. */
  private def optimizeList(list: ArrayBuffer[AstNode]): Unit = {
    var i = 0
    while (i < list.size) {
      val child = list(i)
      val opt   = optimizeTree(child)
      if (!(opt eq child)) list(i) = opt
      i += 1
    }
  }

  /** Walk the AST bottom-up, applying optimizations to each node. Returns the optimized tree.
    */
  private def optimizeTree(node: AstNode): AstNode = {
    // Bottom-up: optimize children first, then the node itself
    node match {
      case scope: AstScope if scope.body.nonEmpty =>
        var i = 0
        while (i < scope.body.size) {
          val child = scope.body(i)
          val opt   = optimizeTree(child)
          if (!(opt eq child)) {
            scope.body(i) = opt
          }
          i += 1
        }
        // Tighten the body (dead code elimination, var joining, etc.)
        if (optionBool("dead_code") || optionBool("join_vars")) {
          tightenBody(scope.body, this)
        }
      case block: AstBlock if block.body.nonEmpty =>
        var i = 0
        while (i < block.body.size) {
          val child = block.body(i)
          val opt   = optimizeTree(child)
          if (!(opt eq child)) {
            block.body(i) = opt
          }
          i += 1
        }
      case simple: AstSimpleStatement =>
        if (simple.body != null) simple.body = optimizeTree(simple.body.nn)
      case ifNode: AstIf =>
        if (ifNode.condition != null) ifNode.condition = optimizeTree(ifNode.condition.nn)
        if (ifNode.body != null) ifNode.body = optimizeTree(ifNode.body.nn)
        if (ifNode.alternative != null) ifNode.alternative = optimizeTree(ifNode.alternative.nn)

      // Loops
      case forNode: AstFor =>
        if (forNode.init != null) forNode.init = optimizeTree(forNode.init.nn)
        if (forNode.condition != null) forNode.condition = optimizeTree(forNode.condition.nn)
        if (forNode.step != null) forNode.step = optimizeTree(forNode.step.nn)
        if (forNode.body != null) forNode.body = optimizeTree(forNode.body.nn)
      case forIn: AstForIn =>
        if (forIn.init != null) forIn.init = optimizeTree(forIn.init.nn)
        if (forIn.obj != null) forIn.obj = optimizeTree(forIn.obj.nn)
        if (forIn.body != null) forIn.body = optimizeTree(forIn.body.nn)
      case whileNode: AstWhile =>
        if (whileNode.condition != null) whileNode.condition = optimizeTree(whileNode.condition.nn)
        if (whileNode.body != null) whileNode.body = optimizeTree(whileNode.body.nn)
      case doNode: AstDo =>
        if (doNode.body != null) doNode.body = optimizeTree(doNode.body.nn)
        if (doNode.condition != null) doNode.condition = optimizeTree(doNode.condition.nn)

      // Switch
      case switchNode: AstSwitch =>
        if (switchNode.expression != null) switchNode.expression = optimizeTree(switchNode.expression.nn)
        optimizeList(switchNode.body)
      case caseNode: AstCase =>
        if (caseNode.expression != null) caseNode.expression = optimizeTree(caseNode.expression.nn)
        optimizeList(caseNode.body)
      case defaultNode: AstDefault =>
        optimizeList(defaultNode.body)

      // Try/Catch/Finally
      case tryNode: AstTry =>
        if (tryNode.body != null) tryNode.body = optimizeTree(tryNode.body.nn).asInstanceOf[AstTryBlock]
        if (tryNode.bcatch != null) tryNode.bcatch = optimizeTree(tryNode.bcatch.nn).asInstanceOf[AstCatch]
        if (tryNode.bfinally != null) tryNode.bfinally = optimizeTree(tryNode.bfinally.nn).asInstanceOf[AstFinally]
      case tryBlock: AstTryBlock =>
        optimizeList(tryBlock.body)
      case catchNode: AstCatch =>
        if (catchNode.argname != null) catchNode.argname = optimizeTree(catchNode.argname.nn)
        optimizeList(catchNode.body)
      case finallyNode: AstFinally =>
        optimizeList(finallyNode.body)

      // Exit statements
      case ret: AstReturn if ret.value != null =>
        ret.value = optimizeTree(ret.value.nn)
      case throwNode: AstThrow if throwNode.value != null =>
        throwNode.value = optimizeTree(throwNode.value.nn)

      // Labeled statement
      case labeled: AstLabeledStatement =>
        if (labeled.body != null) labeled.body = optimizeTree(labeled.body.nn)

      // With statement
      case withNode: AstWith =>
        if (withNode.expression != null) withNode.expression = optimizeTree(withNode.expression.nn)
        if (withNode.body != null) withNode.body = optimizeTree(withNode.body.nn)

      // Expressions
      case assign: AstAssign =>
        if (assign.left != null) assign.left = optimizeTree(assign.left.nn)
        if (assign.right != null) assign.right = optimizeTree(assign.right.nn)
      case binary: AstBinary =>
        if (binary.left != null) binary.left = optimizeTree(binary.left.nn)
        if (binary.right != null) binary.right = optimizeTree(binary.right.nn)
      case unary: AstUnary =>
        if (unary.expression != null) unary.expression = optimizeTree(unary.expression.nn)
      case call: AstCall =>
        if (call.expression != null) call.expression = optimizeTree(call.expression.nn)
        optimizeList(call.args)
      case cond: AstConditional =>
        if (cond.condition != null) cond.condition = optimizeTree(cond.condition.nn)
        if (cond.consequent != null) cond.consequent = optimizeTree(cond.consequent.nn)
        if (cond.alternative != null) cond.alternative = optimizeTree(cond.alternative.nn)
      case seq: AstSequence if seq.expressions.nonEmpty =>
        optimizeList(seq.expressions)

      // Classes
      case cls: AstClass =>
        if (cls.name != null) cls.name = optimizeTree(cls.name.nn)
        if (cls.superClass != null) cls.superClass = optimizeTree(cls.superClass.nn)
        optimizeList(cls.properties)
      case staticBlock: AstClassStaticBlock =>
        optimizeList(staticBlock.body)

      // Object/Array literals
      case arr: AstArray =>
        optimizeList(arr.elements)
      case obj: AstObject =>
        optimizeList(obj.properties)
      case prop: AstObjectProperty =>
        prop.key match { case k: AstNode => prop.key = optimizeTree(k); case _ => }
        if (prop.value != null) prop.value = optimizeTree(prop.value.nn)

      // Variable definitions
      case defs: AstDefinitionsLike =>
        optimizeList(defs.definitions)
      case varDef: AstVarDef =>
        if (varDef.name != null) varDef.name = optimizeTree(varDef.name.nn)
        if (varDef.value != null) varDef.value = optimizeTree(varDef.value.nn)

      // Lambda (function/arrow/accessor)
      case lambda: AstLambda =>
        if (lambda.name != null) lambda.name = optimizeTree(lambda.name.nn)
        optimizeList(lambda.argnames)
        optimizeList(lambda.body)

      // Destructuring
      case dest: AstDestructuring =>
        optimizeList(dest.names)

      // Expansion (spread)
      case exp: AstExpansion =>
        if (exp.expression != null) exp.expression = optimizeTree(exp.expression.nn)

      // Template strings
      case tmpl: AstTemplateString =>
        optimizeList(tmpl.segments)
      case ptmpl: AstPrefixedTemplateString =>
        if (ptmpl.prefix != null) ptmpl.prefix = optimizeTree(ptmpl.prefix.nn)
        if (ptmpl.templateString != null) ptmpl.templateString = optimizeTree(ptmpl.templateString.nn).asInstanceOf[AstTemplateString]

      // PropAccess
      case sub: AstSub =>
        if (sub.expression != null) sub.expression = optimizeTree(sub.expression.nn)
        sub.property match { case p: AstNode => sub.property = optimizeTree(p); case _ => }
      case dot: AstDot =>
        if (dot.expression != null) dot.expression = optimizeTree(dot.expression.nn)
      case chain: AstChain =>
        if (chain.expression != null) chain.expression = optimizeTree(chain.expression.nn)

      // Await/Yield
      case aw: AstAwait =>
        if (aw.expression != null) aw.expression = optimizeTree(aw.expression.nn)
      case yld: AstYield =>
        if (yld.expression != null) yld.expression = optimizeTree(yld.expression.nn)

      // PrivateIn
      case pi: AstPrivateIn =>
        if (pi.key != null) pi.key = optimizeTree(pi.key.nn)
        if (pi.value != null) pi.value = optimizeTree(pi.value.nn)

      // Import/Export
      case imp: AstImport =>
        if (imp.importedName != null) imp.importedName = optimizeTree(imp.importedName.nn)
        if (imp.moduleName != null) imp.moduleName = optimizeTree(imp.moduleName.nn)
      case exp: AstExport =>
        if (exp.exportedDefinition != null) exp.exportedDefinition = optimizeTree(exp.exportedDefinition.nn)
        if (exp.exportedValue != null) exp.exportedValue = optimizeTree(exp.exportedValue.nn)
        if (exp.moduleName != null) exp.moduleName = optimizeTree(exp.moduleName.nn)

      case _ => // leaf node — no children to recurse into
    }

    // Now optimize this node
    optimizeNode(node)
  }

  // -----------------------------------------------------------------------
  // TreeWalker before() callback — the core of the compressor
  // -----------------------------------------------------------------------

  /** Called before descending into each node during tree transformation.
    *
    *   1. Skip already-squeezed nodes
    *   2. For scope nodes: hoist declarations/properties
    *   3. Descend twice (matches Terser's behavior for convergence)
    *   4. Run the per-node optimizer
    *   5. For scope nodes: drop unused, descend again
    *
    * Returns the optimized node to replace the original.
    */
  def before(node: AstNode, descend: (AstNode, TreeWalker) => Unit): AstNode =
    if (hasFlag(node, SQUEEZED)) {
      node
    } else {
      val current  = node
      val wasScope = current.isInstanceOf[AstScope]
      // TODO: if scope, hoist properties and declarations
      // current = current.hoistProperties(this)
      // current = current.hoistDeclarations(this)

      // Descend twice for convergence (matches Terser behavior)
      descend(current, this)
      descend(current, this)

      // Per-node optimization dispatch
      val opt = optimizeNode(current)

      if (wasScope && opt.isInstanceOf[AstScope]) {
        // TODO: opt.dropUnused(this)
        descend(opt, this)
      }

      if (opt eq current) {
        setFlag(opt, SQUEEZED)
      }
      opt
    }

  // -----------------------------------------------------------------------
  // Per-node optimization dispatch (replaces Terser's def_optimize / OPT)
  // -----------------------------------------------------------------------

  /** Dispatch to the appropriate optimizer for the given node type.
    *
    * This is the Scala equivalent of Terser's `def_optimize` macro and the `AST_Node.optimize()` method. Each node type gets a dedicated optimization branch.
    *
    * @param node
    *   the node to optimize
    * @return
    *   the optimized node (may be same instance, a replacement, or a simplified form)
    */
  private def optimizeNode(node: AstNode): AstNode = {
    if (hasFlag(node, OPTIMIZED)) {
      node
    } else if (hasDirective("use asm") != null) {
      setFlag(node, OPTIMIZED)
      node
    } else {
      val opt = node match {
        // ---- Debugger ----
        case self: AstDebugger => optimizeDebugger(self)

        // ---- Directives ----
        case self: AstDirective => optimizeDirective(self)

        // ---- Debugger ----
        // (already matched above)

        // ---- Labeled statements ----
        case self: AstLabeledStatement => optimizeLabeledStatement(self)

        // ---- Simple statements ----
        case self: AstSimpleStatement => optimizeSimpleStatement(self)

        // ---- Loops ----
        case self: AstWhile => optimizeWhile(self)
        case self: AstDo    => optimizeDo(self)
        case self: AstFor   => optimizeFor(self)

        // ---- Conditionals ----
        case self: AstIf => optimizeIf(self)

        // ---- Try/Catch ----
        case self: AstTry => optimizeTry(self)

        // ---- Return ----
        case self: AstReturn => optimizeReturn(self)

        // ---- Import ----
        case self: AstImport => self

        // ---- Yield ----
        case self: AstYield => optimizeYield(self)

        // ---- Calls ----
        case self: AstCall => optimizeCall(self)

        // ---- Definitions (VarDef before Definitions) ----
        case self: AstVarDef                                  => optimizeVarDef(self)
        case self: AstDefinitions if self.definitions.isEmpty =>
          val empty = new AstEmptyStatement
          empty.start = self.start
          empty.end = self.end
          empty

        // ---- Assignment / DefaultAssign before Binary ----
        // (AstAssign extends AstBinary, AstDefaultAssign extends AstBinary)
        case self: AstDefaultAssign => optimizeDefaultAssign(self)
        case self: AstAssign        => optimizeAssign(self)

        // ---- Binary operations ----
        case self: AstBinary => optimizeBinary(self)

        // ---- Conditional expression ----
        case self: AstConditional => optimizeConditional(self)

        // ---- Property access ----
        case self: AstDot => optimizeDot(self)
        case self: AstSub => optimizeSub(self)

        // ---- Chain ----
        case self: AstChain => optimizeChain(self)

        // ---- Symbol references (Export before Ref) ----
        // (AstSymbolExport extends AstSymbolRef)
        case self: AstSymbolExport => self
        case self: AstSymbolRef    => optimizeSymbolRef(self)

        // ---- Constants and special values ----
        case self: AstUndefined => optimizeUndefined(self)
        case self: AstInfinity  => optimizeInfinity(self)
        case self: AstNaN       => optimizeNaN(self)
        case self: AstBoolean   => optimizeBoolean(self)

        // ---- Literals ----
        case self: AstArray  => optimizeArray(self)
        case self: AstObject => optimizeObject(self)
        case self: AstRegExp => literalsInBooleanContext(self)

        // ---- Class (before Scope/Block since AstClass extends AstScope) ----
        case self: AstClass => optimizeClass(self)

        // ---- Lambdas (Function/Arrow before Lambda) ----
        // (AstFunction extends AstLambda, AstArrow extends AstLambda)
        case self: AstFunction => optimizeFunction(self)
        case self: AstArrow    => optimizeLambda(self)
        case self: AstLambda   => optimizeLambda(self)

        // ---- Switch (before Block since AstSwitch extends AstBlock) ----
        case self: AstSwitch => optimizeSwitch(self)

        // ---- Blocks (BlockStatement before Block) ----
        case self: AstBlockStatement => optimizeBlockStatement(self)
        case self: AstBlock          => optimizeBlock(self)

        // ---- Catch-all: no optimization ----
        case self => self
      }
      setFlag(opt, OPTIMIZED)
      opt
    }
  }

  // -----------------------------------------------------------------------
  // Individual optimizers
  // -----------------------------------------------------------------------

  /** `debugger;` -> `` (empty) when drop_debugger is enabled. */
  private def optimizeDebugger(self: AstDebugger): AstNode =
    if (optionBool("drop_debugger")) {
      val empty = new AstEmptyStatement
      empty.start = self.start
      empty.end = self.end
      empty
    } else {
      self
    }

  /** Remove redundant or non-standard directives. */
  private def optimizeDirective(self: AstDirective): AstNode = {
    val validDirectives = Set("use asm", "use strict")
    if (
      optionBool("directives")
      && (!validDirectives.contains(self.value)
        || hasDirective(self.value) == null
        || (hasDirective(self.value) ne self))
    ) {
      val empty = new AstEmptyStatement
      empty.start = self.start
      empty.end = self.end
      empty
    } else {
      self
    }
  }

  /** Optimize labeled statements — remove if label is unused or body is break. */
  private def optimizeLabeledStatement(self: AstLabeledStatement): AstNode = {
    // If label has no references, remove the label wrapper
    if (self.label != null) {
      self.label.nn match {
        case lbl: AstLabel if lbl.references.isEmpty =>
          // Label is unreferenced — just return the body
          return self.body // @nowarn
        case _ =>
      }
    }
    self
  }

  /** Tighten a generic block. */
  private def optimizeBlock(self: AstBlock): AstNode = {
    tightenBody(self.body, this)
    self
  }

  /** Tighten a block statement, and try to unwrap single-statement blocks. */
  private def optimizeBlockStatement(self: AstBlockStatement): AstNode = {
    tightenBody(self.body, this)
    self.body.size match {
      case 1 =>
        val stmt = self.body(0)
        // Can unwrap if parent is an if-block and content is extractable,
        // or if the content can be evicted from a block
        if (canBeEvictedFromBlock(stmt)) {
          stmt
        } else {
          self
        }
      case 0 =>
        val empty = new AstEmptyStatement
        empty.start = self.start
        empty.end = self.end
        empty
      case _ =>
        self
    }
  }

  /** Optimize a lambda body — tighten body and remove "use strict" if sole statement. */
  private def optimizeLambda(self: AstLambda): AstNode = {
    tightenBody(self.body, this)
    if (optionBool("side_effects") && self.body.size == 1) {
      val directive = hasDirective("use strict")
      if (directive != null && (self.body(0) eq directive.nn)) {
        self.body.clear()
      }
    }
    self
  }

  /** Optimize a function expression — try to convert to arrow when safe. */
  private def optimizeFunction(self: AstFunction): AstNode = {
    val base = optimizeLambda(self)
    // TODO: unsafe_arrows conversion to AstArrow
    base
  }

  /** Optimize simple statement — drop side-effect-free expressions. */
  private def optimizeSimpleStatement(self: AstSimpleStatement): AstNode = {
    if (optionBool("side_effects") && self.body != null) {
      // TODO: val node = self.body.dropSideEffectFree(this, true)
      // if (node == null) return AstEmptyStatement
      // if (node ne self.body) return AstSimpleStatement(node)
    }
    self
  }

  /** `while (x) { ... }` -> `for (; x; ) { ... }` when loops optimization is on. */
  private def optimizeWhile(self: AstWhile): AstNode =
    if (optionBool("loops")) {
      val forNode = new AstFor
      forNode.start = self.start
      forNode.end = self.end
      forNode.condition = self.condition
      forNode.body = self.body
      forNode.init = null
      forNode.step = null
      optimizeFor(forNode)
    } else {
      self
    }

  /** Optimize do-while loops. */
  private def optimizeDo(self: AstDo): AstNode =
    if (!optionBool("loops")) self
    else {
      // TODO: evaluate condition, potentially convert to for or block
      self
    }

  /** Optimize for loops — evaluate constant conditions, dead code in body. */
  private def optimizeFor(self: AstFor): AstNode =
    if (!optionBool("loops")) self
    else {
      // Drop side-effect-free init
      if (optionBool("side_effects") && self.init != null) {
        // TODO: self.init = self.init.dropSideEffectFree(this)
      }

      // Evaluate constant condition
      if (self.condition != null) {
        // TODO: evaluate and handle dead-code
      }

      self
    }

  /** Optimize if-statements: dead branch elimination, ternary conversion, etc. */
  private def optimizeIf(self: AstIf): AstNode = {
    // Remove empty alternative
    if (self.alternative != null && isEmpty(self.alternative)) {
      self.alternative = null
    }

    if (!optionBool("conditionals")) {
      self
    } else {
      // TODO: Evaluate condition for dead branch elimination
      // TODO: Convert if/else with simple bodies to ternary
      // TODO: Merge nested if without alternative: if(a) if(b) x -> if(a&&b) x
      // TODO: Handle aborts() for branch extraction

      // Convert if(x) expr; to x && expr; when no alternative
      if (self.alternative == null) {
        self.body match {
          case ss: AstSimpleStatement if isEmpty(self.alternative) =>
            val binary = new AstBinary
            binary.start = self.start
            binary.end = self.end
            binary.operator = "&&"
            binary.left = self.condition.nn
            binary.right = ss.body.nn

            val result = new AstSimpleStatement
            result.start = self.start
            result.end = self.end
            result.body = binary
            result
          case _ => self
        }
      } else {
        // Both branches are simple statements -> ternary
        (self.body, self.alternative.nn) match {
          case (thenSS: AstSimpleStatement, elseSS: AstSimpleStatement) =>
            val cond = new AstConditional
            cond.start = self.start
            cond.end = self.end
            cond.condition = self.condition.nn
            cond.consequent = thenSS.body.nn
            cond.alternative = elseSS.body.nn

            val result = new AstSimpleStatement
            result.start = self.start
            result.end = self.end
            result.body = cond
            result
          case _ => self
        }
      }
    }
  }

  /** Optimize conditional expressions (ternary operator). */
  private def optimizeConditional(self: AstConditional): AstNode =
    if (!optionBool("conditionals")) self
    else {
      // Lift sequences from condition
      self.condition.nn match {
        case seq: AstSequence =>
          val exprs    = ArrayBuffer.from(seq.expressions)
          val lastExpr = exprs.remove(exprs.size - 1)
          self.condition = lastExpr
          exprs.addOne(self)
          return makeSequence(self, exprs)
        case _ =>
      }

      // TODO: Evaluate constant condition
      // TODO: x?x:y -> x||y
      // TODO: Boolean simplification (c?true:false -> !!c)
      // TODO: Merge common branches
      // TODO: x?y:y -> (x,y)
      self
    }

  /** Optimize switch statements — constant case elimination, branch merging. */
  private def optimizeSwitch(self: AstSwitch): AstNode =
    if (!optionBool("switches")) self
    else {
      // TODO: Evaluate expression for constant dispatch
      // TODO: Dead branch elimination
      // TODO: Branch merging
      self
    }

  /** Optimize try-catch-finally. */
  private def optimizeTry(self: AstTry): AstNode = {
    // Remove empty finally
    if (self.bcatch != null && self.bfinally != null) {
      if (self.bfinally.nn.body.forall(isEmpty)) {
        self.bfinally = null
      }
    }

    // Remove try with empty body
    if (optionBool("dead_code") && self.body.body.forall(isEmpty)) {
      val body = ArrayBuffer.empty[AstNode]
      if (self.bcatch != null) {
        extractFromUnreachableCode(this, self.bcatch.nn, body)
      }
      if (self.bfinally != null) {
        body.addAll(self.bfinally.nn.body)
      }
      val block = new AstBlockStatement
      block.start = self.start
      block.end = self.end
      block.body = body
      block
    } else {
      self
    }
  }

  /** Optimize var definitions — remove undefined initializers for `let`. */
  private def optimizeVarDef(self: AstVarDef): AstNode = {
    if (
      self.name.isInstanceOf[AstSymbolLet]
      && self.value != null
      && isUndefined(self.value.nn, this)
    ) {
      self.value = null
    }
    self
  }

  /** Optimize return statements — remove `return undefined`. */
  private def optimizeReturn(self: AstReturn): AstNode = {
    if (self.value != null && isUndefined(self.value.nn, this)) {
      self.value = null
    }
    self
  }

  /** Optimize function calls.
    *
    * Handles unused argument trimming, built-in constructor simplification (Array, Object, String, Number, Boolean), method call optimization (toString, join, charAt, apply, call), and function
    * inlining.
    */
  private def optimizeCall(self: AstCall): AstNode =
    if (self.expression == null) {
      self
    } else {
      // TODO: resolve SymbolRef to fixed value for inlining:
      // val exp = self.expression.nn
      // val fn = if (optionBool("reduce_vars") && exp.isInstanceOf[AstSymbolRef])
      //   exp.asInstanceOf[AstSymbolRef].fixedValue() else exp

      // TODO: Trim unused arguments (when UNUSED flag checking is available)
      // TODO: inlineIntoCall(self, this)
      // TODO: self.evaluate(this)

      self
    }

  /** Optimize binary expressions.
    *
    * Handles:
    *   - Constant folding (evaluate both sides)
    *   - Comparison simplification (=== to ==, typeof checks)
    *   - Commutative operator reordering (constant to LHS)
    *   - Algebraic simplifications (x + 0, x * 1, etc.)
    *   - Bitwise optimizations (De Morgan, shift by 0, identity)
    *   - String concatenation folding
    *   - Boolean context optimizations
    *   - Associativity flattening (a && (b && c) -> a && b && c)
    */
  private def optimizeBinary(self: AstBinary): AstNode = {
    // Lift sequences from operands
    // TODO: self = self.liftSequences(this)

    // Commutative operator: move constant to left
    val commutativeOps = Set("==", "===", "!=", "!==", "*", "&", "|", "^")
    if (optionBool("lhs_constants") && commutativeOps.contains(self.operator)) {
      if (self.right != null && self.left != null) {
        val rightConst = self.right.nn.isInstanceOf[AstConstant]
        val leftConst  = self.left.nn.isInstanceOf[AstConstant]
        if (rightConst && !leftConst) {
          // Swap left and right
          val tmp = self.left
          self.left = self.right
          self.right = tmp
        }
      }
    }

    // Comparison optimizations
    if (optionBool("comparisons")) {
      self.operator match {
        case "===" | "!==" =>
        // Strict comparison can be relaxed if both sides are same type
        // TODO: check is_string, is_number, is_boolean
        case _ =>
      }
    }

    // Constant folding
    // TODO: val ev = self.evaluate(this)
    // if (ev ne self) return bestOf(this, makeNodeFromConstant(ev, self), self)

    self
  }

  /** Optimize assignment expressions. */
  private def optimizeAssign(self: AstAssign): AstNode = {
    if (self.logical) {
      // TODO: self.liftSequences(this)
      return self
    }

    // x = x -> x (self-assignment)
    if (
      self.operator == "="
      && self.left != null && self.right != null
      && self.left.nn.isInstanceOf[AstSymbolRef]
      && self.right.nn.isInstanceOf[AstSymbolRef]
    ) {
      val leftRef  = self.left.nn.asInstanceOf[AstSymbolRef]
      val rightRef = self.right.nn.asInstanceOf[AstSymbolRef]
      if (leftRef.name == rightRef.name && leftRef.name != "arguments") {
        val d = leftRef.definition()
        if (d == null || !d.nn.undeclared) return self.right.nn // @nowarn — safe self-assignment removal
      }
    }

    // Compound assignment: x = x + y -> x += y
    val assignOps = Set("+", "-", "/", "*", "%", ">>", "<<", ">>>", "|", "^", "&")
    if (
      self.operator == "="
      && self.left != null && self.right != null
      && self.left.nn.isInstanceOf[AstSymbolRef]
      && self.right.nn.isInstanceOf[AstBinary]
    ) {
      val leftRef  = self.left.nn.asInstanceOf[AstSymbolRef]
      val binRight = self.right.nn.asInstanceOf[AstBinary]
      if (
        binRight.left != null
        && binRight.left.nn.isInstanceOf[AstSymbolRef]
        && binRight.left.nn.asInstanceOf[AstSymbolRef].name == leftRef.name
        && assignOps.contains(binRight.operator)
      ) {
        // x = x OP y -> x OP= y
        self.operator = binRight.operator + "="
        self.right = binRight.right
      }
    }

    self
  }

  /** Optimize property dot access. */
  private def optimizeDot(self: AstDot): AstNode =
    // TODO: flatten_object, evaluate
    self

  /** Optimize computed property access (bracket notation). */
  private def optimizeSub(self: AstSub): AstNode =
    // TODO: Convert computed access to dot when key is a valid identifier
    // TODO: flatten_object, evaluate
    self

  /** Optimize optional chain expressions. */
  private def optimizeChain(self: AstChain): AstNode =
    // TODO: Optimize nullish chains
    self

  /** Optimize symbol references — inline or replace with constants. */
  private def optimizeSymbolRef(self: AstSymbolRef): AstNode = {
    // Replace undeclared references to well-known globals
    if (isUndeclaredRef(self)) {
      self.name match {
        case "undefined" =>
          return optimizeUndefined(new AstUndefined)
        case "NaN" =>
          val nan = new AstNaN
          nan.start = self.start
          nan.end = self.end
          return optimizeNaN(nan)
        case "Infinity" =>
          val inf = new AstInfinity
          inf.start = self.start
          inf.end = self.end
          return optimizeInfinity(inf)
        case _ =>
      }
    }

    // Inline
    if (optionBool("reduce_vars")) {
      inlineIntoSymbolRef(self, this)
    } else {
      self
    }
  }

  /** Optimize `undefined` -> `void 0`. */
  private def optimizeUndefined(self: AstUndefined): AstNode =
    makeVoid0(self)

  /** Optimize `Infinity` -> `1/0` (unless keep_infinity). */
  private def optimizeInfinity(self: AstInfinity): AstNode =
    if (optionBool("keep_infinity")) {
      self
    } else {
      val one = new AstNumber
      one.start = self.start
      one.end = self.end
      one.value = 1.0

      val zero = new AstNumber
      zero.start = self.start
      zero.end = self.end
      zero.value = 0.0

      val div = new AstBinary
      div.start = self.start
      div.end = self.end
      div.operator = "/"
      div.left = one
      div.right = zero
      div
    }

  /** Optimize `NaN` -> `0/0`. */
  private def optimizeNaN(self: AstNaN): AstNode = {
    val zero1 = new AstNumber
    zero1.start = self.start
    zero1.end = self.end
    zero1.value = 0.0

    val zero2 = new AstNumber
    zero2.start = self.start
    zero2.end = self.end
    zero2.value = 0.0

    val div = new AstBinary
    div.start = self.start
    div.end = self.end
    div.operator = "/"
    div.left = zero1
    div.right = zero2
    div
  }

  /** Optimize boolean literals in boolean context. */
  private def optimizeBoolean(self: AstBoolean): AstNode =
    if (inBooleanContext()) {
      val num = new AstNumber
      num.start = self.start
      num.end = self.end
      num.value = if (self.isInstanceOf[AstTrue]) 1.0 else 0.0
      num
    } else if (optionBool("booleans")) {
      // true -> !0, false -> !1
      val inner = new AstNumber
      inner.start = self.start
      inner.end = self.end
      inner.value = if (self.isInstanceOf[AstTrue]) 0.0 else 1.0

      val not = new AstUnaryPrefix
      not.start = self.start
      not.end = self.end
      not.operator = "!"
      not.expression = inner
      not
    } else {
      self
    }

  /** Optimize default assignment — remove `= undefined`. */
  private def optimizeDefaultAssign(self: AstDefaultAssign): AstNode =
    if (!optionBool("evaluate")) self
    else {
      // TODO: evaluate right side, check for undefined
      self
    }

  /** Optimize array literals in boolean context. */
  private def optimizeArray(self: AstArray): AstNode =
    // TODO: inline_array_like_spread
    literalsInBooleanContext(self)

  /** Optimize object literals in boolean context. */
  private def optimizeObject(self: AstObject): AstNode =
    // TODO: inline_object_prop_spread
    literalsInBooleanContext(self)

  /** Optimize yield expressions — remove `yield undefined`. */
  private def optimizeYield(self: AstYield): AstNode = {
    if (self.expression != null && !self.isStar && isUndefined(self.expression.nn, this)) {
      self.expression = null
    }
    self
  }

  /** Optimize class — remove empty static blocks. */
  private def optimizeClass(self: AstClass): AstNode =
    // TODO: remove empty AstClassStaticBlock entries from properties
    self

  // -----------------------------------------------------------------------
  // Shared helpers
  // -----------------------------------------------------------------------

  /** In boolean context, `[1,2,3]` or `{a:1}` can be `[1,2,3], true`. */
  private def literalsInBooleanContext(self: AstNode): AstNode =
    if (inBooleanContext()) {
      val trueNode = new AstTrue
      trueNode.start = self.start
      trueNode.end = self.end
      makeSequence(self, ArrayBuffer(self, trueNode))
      // TODO: bestOf(this, self, optimized)
    } else {
      self
    }

  /** Create `void 0` from a node, preserving source position. */
  private def makeVoid0(orig: AstNode): AstNode = {
    val zero = new AstNumber
    zero.start = orig.start
    zero.end = orig.end
    zero.value = 0.0

    val prefix = new AstUnaryPrefix
    prefix.start = orig.start
    prefix.end = orig.end
    prefix.operator = "void"
    prefix.expression = zero
    prefix
  }

  // self() is inherited from TreeWalker

  // findScope() is inherited from TreeWalker

  /** Reset optimization flags before each pass. */
  private def resetOptFlags(toplevel: AstToplevel): Unit = {
    val tw = new TreeWalker((node, _) => {
      clearFlag(node, CLEAR_BETWEEN_PASSES)

      // TODO: Set TOP flag for retain_top_func
      // TODO: Call node.reduceVars() for data-flow analysis

      null // continue walking
    })
    toplevel.walk(tw)
  }

  /** Drop console.* calls from the AST.
    *
    * Replaces `console.method(...)` calls with `void 0`. When `dropConsole` is `Methods(names)`, only drops calls to those specific methods.
    */
  private def dropConsole(toplevel: AstToplevel): AstToplevel = {
    val methodFilter: Option[Set[String]] = options.dropConsole match {
      case DropConsoleConfig.Methods(names) => Some(names)
      case _                               => None
    }

    val tt = new TreeTransformer(
      before = (self, _) => {
        self match {
          case call: AstCall =>
            call.expression match {
              case pa: AstPropAccess =>
                // Walk up property chains: console.log.bind(console) etc.
                var nameNode: AstNode = pa.expression.nn
                var property: String | AstNode = pa.property
                var keepWalking = true
                while (keepWalking) {
                  nameNode match {
                    case inner: AstPropAccess =>
                      property = inner.property
                      nameNode = inner.expression.nn
                    case _ =>
                      keepWalking = false
                  }
                }

                // Check if filtering by method name
                val propertyName = property match {
                  case s: String => s
                  case _         => null
                }
                if (methodFilter.isDefined && propertyName != null && !methodFilter.get.contains(propertyName)) {
                  null // not a filtered method, keep it
                } else if (Inference.isUndeclaredRef(nameNode) && nameNode.isInstanceOf[AstSymbolRef] && nameNode.asInstanceOf[AstSymbolRef].name == "console") {
                  // Replace with void 0
                  makeVoid0(self)
                } else {
                  null // not a console reference
                }

              case _ => null // not a property access call
            }

          case _ => null // not a call node
        }
      }
    )
    toplevel.transform(tt).asInstanceOf[AstToplevel]
  }

  /** Walk a node tree, calling visitor for each node. TODO: used in multi-pass convergence check. */
  @scala.annotation.nowarn("msg=unused private member")
  private def walk(
    node:    AstNode,
    visitor: (AstNode, ArrayBuffer[AstNode]) => Any
  ): Boolean = {
    val parents = ArrayBuffer.empty[AstNode]
    var aborted = false
    val tw      = new TreeWalker((n, _) =>
      if (aborted) {
        true
      } else {
        visitor(n, parents) match {
          case WalkAbort =>
            aborted = true
            true
          case true => true
          case _    => null
        }
      }
    )
    node.walk(tw)
    aborted
  }
}

object Compressor {

  /** Create a Compressor with default options. */
  def apply(): Compressor = new Compressor(CompressorOptions())

  /** Create a Compressor with custom options. */
  def apply(options: CompressorOptions): Compressor = new Compressor(options)
}
