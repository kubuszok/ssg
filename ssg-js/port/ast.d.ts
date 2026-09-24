// Auto-generated from terser lib/ast.js DEFNODE hierarchy.
// Provides type declarations so the TS exporter can resolve field accesses.

export declare class AST_Token {
  type: string;
  value: string;
  line: number;
  col: number;
  pos: number;
  nlb: boolean;
  quote: string;
  comments_before: AST_Token[];
  comments_after: AST_Token[];
  file: string;
}

export declare class SymbolDef {
  name: string;
  orig: AST_Symbol[];
  init: AST_Node | null;
  scope: AST_Scope;
  references: AST_Symbol[];
  global: boolean;
  export: number;
  mangled_name: string | null;
  undeclared: boolean;
  id: number;
  chained: boolean;
  direct_access: boolean;
  escaped: number;
  recursive_refs: number;
  assignments: number;
  replaced: number;
  single_use: any;
  fixed: any;
  eliminated: number;
  should_replace: any;
}

export declare class AST_Node {
  TYPE: string;
  start: AST_Token | null;
  end: AST_Token | null;
}

export declare class AST_Statement extends AST_Node {
}

export declare class AST_Debugger extends AST_Statement {
}

export declare class AST_Directive extends AST_Statement {
  value: string;
  quote: string;
}

export declare class AST_SimpleStatement extends AST_Statement {
  body: AST_Node | null;
}

export declare class AST_Block extends AST_Statement {
  body: AST_Node[];
  block_scope: AST_Scope | null;
}

export declare class AST_BlockStatement extends AST_Block {
}

export declare class AST_EmptyStatement extends AST_Statement {
}

export declare class AST_StatementWithBody extends AST_Statement {
  body: AST_Node | null;
}

export declare class AST_LabeledStatement extends AST_StatementWithBody {
  label: AST_Node | null;
}

export declare class AST_IterationStatement extends AST_StatementWithBody {
  block_scope: AST_Scope | null;
}

export declare class AST_DWLoop extends AST_IterationStatement {
  condition: AST_Node | null;
}

export declare class AST_Do extends AST_DWLoop {
}

export declare class AST_While extends AST_DWLoop {
}

export declare class AST_For extends AST_IterationStatement {
  init: AST_Node | null;
  condition: AST_Node | null;
  step: AST_Node | null;
}

export declare class AST_ForIn extends AST_IterationStatement {
  init: AST_Node | null;
  object: AST_Node | null;
}

export declare class AST_ForOf extends AST_ForIn {
  await: boolean;
}

export declare class AST_With extends AST_StatementWithBody {
  expression: AST_Node | null;
}

export declare class AST_Scope extends AST_Block {
  variables: Map<string, SymbolDef>;
  uses_with: boolean;
  uses_eval: boolean;
  parent_scope: AST_Scope | null;
  enclosed: SymbolDef[];
  cname: any;
}

export declare class AST_Toplevel extends AST_Scope {
  globals: Map<string, SymbolDef>;
}

export declare class AST_Expansion extends AST_Node {
  expression: AST_Node | null;
}

export declare class AST_Lambda extends AST_Scope {
  name: AST_Node | null;
  argnames: AST_Node[];
  uses_arguments: boolean;
  is_generator: boolean;
  async: boolean;
}

export declare class AST_Accessor extends AST_Lambda {
}

export declare class AST_Function extends AST_Lambda {
}

export declare class AST_Arrow extends AST_Lambda {
}

export declare class AST_Defun extends AST_Lambda {
}

export declare class AST_Destructuring extends AST_Node {
  names: AST_Node[];
  is_array: boolean;
}

export declare class AST_PrefixedTemplateString extends AST_Node {
  template_string: any;
  prefix: any;
}

export declare class AST_TemplateString extends AST_Node {
  segments: AST_Node[];
}

export declare class AST_TemplateSegment extends AST_Node {
  value: string;
  raw: string;
}

export declare class AST_Jump extends AST_Statement {
}

export declare class AST_Exit extends AST_Jump {
  value: any;
}

export declare class AST_Return extends AST_Exit {
}

export declare class AST_Throw extends AST_Exit {
}

export declare class AST_LoopControl extends AST_Jump {
  label: AST_Node | null;
}

export declare class AST_Break extends AST_LoopControl {
}

export declare class AST_Continue extends AST_LoopControl {
}

export declare class AST_Await extends AST_Node {
  expression: AST_Node | null;
}

export declare class AST_Yield extends AST_Node {
  expression: AST_Node | null;
  is_star: boolean;
}

export declare class AST_If extends AST_StatementWithBody {
  condition: AST_Node | null;
  alternative: AST_Node | null;
}

export declare class AST_Switch extends AST_Block {
  expression: AST_Node | null;
}

export declare class AST_SwitchBranch extends AST_Block {
}

export declare class AST_Default extends AST_SwitchBranch {
}

export declare class AST_Case extends AST_SwitchBranch {
  expression: AST_Node | null;
}

export declare class AST_Try extends AST_Statement {
  body: AST_Node | null;
  bcatch: AST_Node | null;
  bfinally: AST_Node | null;
}

export declare class AST_TryBlock extends AST_Block {
}

export declare class AST_Catch extends AST_Block {
  argname: AST_Node | null;
}

export declare class AST_Finally extends AST_Block {
}

export declare class AST_DefinitionsLike extends AST_Statement {
  definitions: AST_Node[];
}

export declare class AST_Definitions extends AST_DefinitionsLike {
}

export declare class AST_Var extends AST_Definitions {
}

export declare class AST_Let extends AST_Definitions {
}

export declare class AST_Const extends AST_Definitions {
}

export declare class AST_Using extends AST_DefinitionsLike {
  await: boolean;
}

export declare class AST_VarDefLike extends AST_Node {
  name: AST_Node | null;
  value: any;
}

export declare class AST_VarDef extends AST_VarDefLike {
}

export declare class AST_UsingDef extends AST_VarDefLike {
}

export declare class AST_NameMapping extends AST_Node {
  foreign_name: AST_Node | null;
  name: AST_Node | null;
}

export declare class AST_Import extends AST_Node {
  imported_name: any;
  imported_names: AST_Node[] | null;
  module_name: AST_Node | null;
  attributes: any;
}

export declare class AST_ImportMeta extends AST_Node {
}

export declare class AST_Export extends AST_Statement {
  exported_definition: any;
  exported_value: any;
  is_default: boolean;
  exported_names: AST_Node[] | null;
  module_name: AST_Node | null;
  attributes: any;
}

export declare class AST_Call extends AST_Node {
  expression: AST_Node | null;
  args: AST_Node[];
  optional: boolean;
  _annotations: any;
}

export declare class AST_New extends AST_Call {
}

export declare class AST_Sequence extends AST_Node {
  expressions: AST_Node[];
}

export declare class AST_PropAccess extends AST_Node {
  expression: AST_Node | null;
  property: string | AST_Node;
  optional: boolean;
}

export declare class AST_Dot extends AST_PropAccess {
  quote: string;
}

export declare class AST_DotHash extends AST_PropAccess {
}

export declare class AST_Sub extends AST_PropAccess {
}

export declare class AST_Chain extends AST_Node {
  expression: AST_Node | null;
}

export declare class AST_Unary extends AST_Node {
  operator: string;
  expression: AST_Node | null;
}

export declare class AST_UnaryPrefix extends AST_Unary {
}

export declare class AST_UnaryPostfix extends AST_Unary {
}

export declare class AST_Binary extends AST_Node {
  operator: string;
  left: any;
  right: any;
}

export declare class AST_Conditional extends AST_Node {
  condition: AST_Node | null;
  consequent: any;
  alternative: AST_Node | null;
}

export declare class AST_Assign extends AST_Binary {
  logical: boolean;
}

export declare class AST_DefaultAssign extends AST_Binary {
}

export declare class AST_Array extends AST_Node {
  elements: AST_Node[];
}

export declare class AST_Object extends AST_Node {
  properties: AST_Node[];
}

export declare class AST_ObjectProperty extends AST_Node {
  key: string | AST_Node;
  value: any;
}

export declare class AST_ObjectKeyVal extends AST_ObjectProperty {
  quote: string;
}

export declare class AST_PrivateSetter extends AST_ObjectProperty {
  static: boolean;
}

export declare class AST_PrivateGetter extends AST_ObjectProperty {
  static: boolean;
}

export declare class AST_ObjectSetter extends AST_ObjectProperty {
  quote: string;
  static: boolean;
}

export declare class AST_ObjectGetter extends AST_ObjectProperty {
  quote: string;
  static: boolean;
}

export declare class AST_ConciseMethod extends AST_ObjectProperty {
  quote: string;
  static: boolean;
}

export declare class AST_PrivateMethod extends AST_ObjectProperty {
  static: boolean;
}

export declare class AST_Class extends AST_Node {
  name: AST_Node | null;
  extends: any;
  properties: AST_Node[];
}

export declare class AST_ClassProperty extends AST_ObjectProperty {
  static: boolean;
  quote: string;
}

export declare class AST_ClassPrivateProperty extends AST_ObjectProperty {
}

export declare class AST_PrivateIn extends AST_Node {
  key: AST_Node;
  value: any;
}

export declare class AST_DefClass extends AST_Class {
}

export declare class AST_ClassStaticBlock extends AST_Scope {
  body: AST_Node | null;
  block_scope: AST_Scope | null;
}

export declare class AST_ClassExpression extends AST_Class {
}

export declare class AST_Symbol extends AST_Node {
  scope: AST_Scope | null;
  name: string;
  thedef: SymbolDef | null;
}

export declare class AST_NewTarget extends AST_Node {
}

export declare class AST_SymbolDeclaration extends AST_Symbol {
  init: AST_Node | null;
}

export declare class AST_SymbolVar extends AST_SymbolDeclaration {
}

export declare class AST_SymbolBlockDeclaration extends AST_SymbolDeclaration {
}

export declare class AST_SymbolConst extends AST_SymbolBlockDeclaration {
}

export declare class AST_SymbolUsing extends AST_SymbolBlockDeclaration {
}

export declare class AST_SymbolLet extends AST_SymbolBlockDeclaration {
}

export declare class AST_SymbolFunarg extends AST_SymbolVar {
}

export declare class AST_SymbolDefun extends AST_SymbolDeclaration {
}

export declare class AST_SymbolMethod extends AST_Symbol {
}

export declare class AST_SymbolClassProperty extends AST_Symbol {
}

export declare class AST_SymbolLambda extends AST_SymbolDeclaration {
}

export declare class AST_SymbolDefClass extends AST_SymbolBlockDeclaration {
}

export declare class AST_SymbolClass extends AST_SymbolDeclaration {
}

export declare class AST_SymbolCatch extends AST_SymbolBlockDeclaration {
}

export declare class AST_SymbolImport extends AST_SymbolBlockDeclaration {
}

export declare class AST_SymbolImportForeign extends AST_Symbol {
  quote: string;
}

export declare class AST_Label extends AST_Symbol {
  references: AST_Node[];
}

export declare class AST_SymbolRef extends AST_Symbol {
}

export declare class AST_SymbolExport extends AST_SymbolRef {
  quote: string;
}

export declare class AST_SymbolExportForeign extends AST_Symbol {
  quote: string;
}

export declare class AST_LabelRef extends AST_Symbol {
}

export declare class AST_SymbolPrivateProperty extends AST_Symbol {
}

export declare class AST_This extends AST_Symbol {
}

export declare class AST_Super extends AST_This {
}

export declare class AST_Constant extends AST_Node {
}

export declare class AST_String extends AST_Constant {
  value: string;
  quote: string;
}

export declare class AST_Number extends AST_Constant {
  value: string;
  raw: string;
}

export declare class AST_BigInt extends AST_Constant {
  value: string;
  raw: string;
}

export declare class AST_RegExp extends AST_Constant {
  value: any;
}

export declare class AST_Atom extends AST_Constant {
}

export declare class AST_Null extends AST_Atom {
}

export declare class AST_NaN extends AST_Atom {
}

export declare class AST_Undefined extends AST_Atom {
}

export declare class AST_Hole extends AST_Atom {
}

export declare class AST_Infinity extends AST_Atom {
}

export declare class AST_Boolean extends AST_Atom {
}

export declare class AST_False extends AST_Boolean {
}

export declare class AST_True extends AST_Boolean {
}

export declare class TreeWalker {
  constructor(callback: (node: AST_Node, descend: () => void) => any);
  find_parent(type: any): AST_Node | undefined;
  parent(n?: number): AST_Node | undefined;
  push(node: AST_Node): void;
  pop(): void;
  self(): AST_Node;
  stack: AST_Node[];
}

export declare class TreeTransformer extends TreeWalker {
  constructor(
    before: (node: AST_Node, descend: (node: AST_Node, tw: TreeTransformer) => void, in_list: boolean) => AST_Node | undefined,
    after?: (node: AST_Node, in_list: boolean) => AST_Node | undefined,
  );
  before: any;
  after: any;
}

export declare function walk(node: AST_Node, visitor: (node: AST_Node) => any): void;
export declare function walk_abort(node: AST_Node, visitor: (node: AST_Node) => any): boolean;
export declare function walk_body(node: AST_Node, visitor: TreeWalker): void;
export declare function walk_parent(node: AST_Node, cb: (node: AST_Node, info: any) => any, initial_stack?: AST_Node[]): void;

export declare const _INLINE: number;
export declare const _NOINLINE: number;
export declare const _PURE: number;
export declare const _KEY: number;
export declare const _MANGLEPROP: number;

