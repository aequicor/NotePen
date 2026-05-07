---
name: knowledge-graph
description: Build and query a semantic knowledge graph from project documentation when @Main needs cross-module understanding that keyword search cannot provide. Use ONLY when simple search fails to connect related concepts across documents — not for single-module lookups.
---

# Knowledge Graph Skill

When @Main asks a question that spans multiple modules or document types, and `knowledge-my-app_search_docs` returns disconnected fragments, this skill builds a semantic layer connecting them.

## When to Use

- @Main asks a cross-module question (e.g. "How does auth in server relate to session management in client?")
- Search returns fragments but not their relationships
- New documents were added and the index is stale
- @PromptEngineer needs to understand document structure before refactoring prompts

**Do NOT activate for:**
- Single-module lookups — use `knowledge-my-app_search_docs` directly
- Keyword searches — use grep or search instead
- When the index is empty and no documents exist yet

## Input

| Field | Required | Description |
|-------|----------|-------------|
| `query` | Yes | The cross-module question or relationship to explore |
| `modules` | No | List of modules to scope the search. Omit for all modules |
| `rebuild` | No | `true` to force full reindex. Default: `false` (incremental) |

## Invocation

```
skill: knowledge-graph
query: "How does authentication in server relate to session management in client?"
modules: [server, client]
rebuild: false
```

## Architecture

```
vault/                           .claude/knowledge-graph/
  concepts/                           index.json         — document metadata
  reference/                          graph.json         — entity relationships
  guidelines/                         embeddings.json    — vector storage (if available)
  how-to/
  tutorials/
```

## Algorithm

### 1. Index documents

```
for each document in vault/:
  1. Extract metadata (title, module, type, date, agent)
  2. Extract entities (classes, functions, APIs, concepts)
  3. Extract relationships (imports, references, "depends on", "implements")
  4. Write to index.json
```

### 2. Build embeddings (optional — requires embedding-capable model)

```
for each document:
  embedding = vectorize(content[:4096])
  store in embeddings.json with doc_id
```

### 3. Query

```json
{
  "query": "How does authentication relate to session management?",
  "top_k": 5,
  "types": ["spec", "guideline"],
  "modules": ["server"]
}
```

Response:

```json
{
  "results": [
    {
      "path": "vault/reference/server/spec/auth.md",
      "excerpt": "Session tokens are issued by AuthService...",
      "entities": ["AuthService", "SessionToken", "AuthConfig"],
      "related": ["vault/concepts/server/requirements/auth.md"]
    }
  ],
  "graph_summary": "AuthService → SessionManager (implements), AuthConfig ← SessionToken (references)"
}
```

### 4. Entity extraction rules

| Language | Entity pattern | Example |
|----------|--------------|---------|
| Kotlin | `class X`, `fun X`, `interface X` | `class SessionManager` |
| TypeScript | `class X`, `function X`, `interface X` | `interface AuthConfig` |
| Python | `class X:`, `def X` | `def create_token` |
| Go | `type X struct`, `func X` | `func (s *Server) Auth` |
| Rust | `struct X`, `fn X`, `trait X` | `struct AuthService` |

### 5. Relationship extraction

| Pattern | Relationship |
|---------|-------------|
| `import X` / `uses X` | depends_on |
| `implements X` / `extends X` | implements |
| `described in vault/X/` | documented_in |
| `tested in tests/X` | tested_by |
| `see ADR-NNN` | references_decision |
| `depends on X module` | module_dependency |

## Tools used

| Tool | Purpose |
|------|---------|
| `knowledge-my-app_search_docs` | Search existing docs |
| `glob` | Find documents to index |
| `read` | Read document content for extraction |
| `knowledge-my-app_write_guideline` | Persist index/graph artifacts |

## Error Handling

- If `vault/` is empty → report `NO DATA: No documents found in vault/. Build requirements and spec first.` Do not build an empty index.
- If no documents match the query modules → report `NO MATCH: No documents found for modules [list]. Available modules: [from index or manifest].`
- If index is stale (documents newer than index.json) → automatically rebuild before querying. Report: `INDEX STALE: Rebuilding index (N new/updated documents).`

## Principles

- **Build incrementally** — index new documents on each feature completion
- **Link everything** — every entity should connect to at least one other entity or document
- **Query before search** — always try graph query before grep
- **Keep index fresh** — rebuild on major spec/guideline changes