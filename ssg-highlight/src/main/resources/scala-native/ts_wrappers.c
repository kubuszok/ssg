#include <stdint.h>

/* Tree-sitter types (from api.h) */
typedef struct {
  uint32_t context[4];
  const void *id;
  const void *tree;
} TSNode;

typedef struct TSTree TSTree;
typedef struct TSQueryCursor TSQueryCursor;
typedef struct TSQuery TSQuery;

/* Imported from tree-sitter */
extern TSNode ts_tree_root_node(const TSTree *self);
extern void ts_query_cursor_exec(TSQueryCursor *self, const TSQuery *query, TSNode node);
extern uint32_t ts_node_start_byte(TSNode self);
extern uint32_t ts_node_end_byte(TSNode self);

/* Pointer-based wrappers for Scala Native */
void ts_tree_root_node_p(const TSTree *self, TSNode *out) {
  *out = ts_tree_root_node(self);
}

void ts_query_cursor_exec_p(TSQueryCursor *self, const TSQuery *query, const TSNode *node) {
  ts_query_cursor_exec(self, query, *node);
}

uint32_t ts_node_start_byte_p(const TSNode *self) {
  return ts_node_start_byte(*self);
}

uint32_t ts_node_end_byte_p(const TSNode *self) {
  return ts_node_end_byte(*self);
}
