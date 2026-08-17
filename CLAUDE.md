# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Mosika is a JDK 21 Maven multi-module visual rule-flow language and execution kernel. The UI AST (its JSON) is the single source of truth for editing, persistence, compilation, execution, tracing and replay — the model is never reconstructed from the execution tree.

`AGENTS.md` (Chinese) is the detailed collaboration guide: read it before changing DSL parsing, node semantics, the UI tree, or the result contract. This file is the quick orientation; `AGENTS.md` and `docs/` hold the full invariants.

## Build & Test

```bash
mvn clean test                 # regenerate ANTLR sources + run all tests
mvn -pl mosika-core test       # kernel only
mvn -pl mosika-ui -am test     # UI tree + its kernel deps
mvn -pl mosika-web -am test    # Web/API + its deps
mvn clean package              # test + build per-module JARs
mvn -pl mosika-core test -Dtest=SomeTest              # single test class
mvn -pl mosika-core test -Dtest=SomeTest#someMethod   # single test method
```

Tests are JUnit 5, named `*Test`. Do not edit generated code under `target/generated-sources/` (ANTLR output from `mosika-core/src/main/antlr4/Rule.g4`). Changes to parsing, execution, concurrent context, or tree conversion require targeted regression tests.

## Run the reference web server

```bash
./scripts/mosika.sh start      # build + start Spring Boot fat jar
./scripts/mosika.sh dev        # dev mode
./scripts/mosika.sh status | logs | restart | stop
```

Serves at `http://127.0.0.1:8080/` (rule flows), `/rules` (atomic rules), `/udfs` (JS UDF registry), `/flow/{id}` (canvas). Runtime state, logs and the SQLite DB must NOT be written into the repo — the script places them under a runtime dir configured via `MOSIKA_RUN_DIR` / `MOSIKA_DB_PATH` / `MOSIKA_PORT` env vars.

## Module boundaries (strict — do not violate)

| Module | Owns | Must NOT contain |
| --- | --- | --- |
| `mosika-core` | DSL, rule tree, exec context, evaluation, UDF, named atomic/composite rules | pages, persistence, Flow product models, business-scenario mapping |
| `mosika-ui` | serializable UI AST + one-way compile to `RuleNode` | page layout, web deps, reverse-recovering UI from the exec tree |
| `mosika-web` | optional reference control plane: REST, SQLite, rule management + visual editor | rule semantics, execution kernel, replacing Core/UI as libraries |

The core only knows `RuleDefinition` and `UdfDefinition`. It does **not** define `RuleFlow`/`RuleFlowDefinition` or any Flow-specific API. Product layers (like web) compile their orchestration one-way into `useType=2` `RuleDefinition`s and hand them to Core — never push Flow types, config, or APIs back into `mosika-core`.

## Core execution model (`mosika-core`)

- `RuleDefinition.useType=0` = JavaScript atomic rule; `useType=2` = rule-ID DSL composite rule. Both compile by `ruleId` into a `RuleNode`; a composite rule's runtime root is `CompositeNode`.
- `RuleEngine` is the single source of truth for rule registration — it registers built-ins + all `RuleDefinition`s and precompiles `useType=0` JavaScript. `NodeGenerator.create(compositeRules)` receives only the full composite-rule table, recursively expands `CompositeNode`, reuses compiled results, and detects composite-rule cycles. It does not keep a second registered-rule set. A `ruleId` not in the composite table becomes an `ExprNode`, resolved at eval time via `RuleEngine.evalRule()`.
- A `RuleSuite` assembles `RuleEngine` + `NodeBuilder`. Build the whole suite by first collecting ALL composite rules, then recursively compiling — this allows a composite rule to reference a later-defined composite rule.
- DSL operators: `->` serial, `=>` parallel, `?:` condition, `hits(min,max,...)` threshold; all recursively nestable. `_` = unbounded interval side (e.g. `hits(2,_,a,b,c)`). `∅` is a structural placeholder only (never business logic).
- **Result contract** — keep responsibilities separate: `EvalResult.matched` = match/control state; `EvalResult.result` = only the node's meaningful business return. Do not stuff diagnostics/state into `result`; do not add fields to `EvalResult`/`RuleResult`/`NodeResult` or change serialization without maintainer alignment.
- Logical nodes (`AndNode`, `OrNode`, `HitsNode`) interpret children's matches and short-circuit. Execution nodes (`SerNode`, `ParNode`) run children (serial / parallel-then-await) and, on normal completion, return `result=null, matched=true` — they never abort/aggregate on children's `matched`. A rule not matching is not an execution failure; exceptions propagate normally.
- **Template params**: an atomic rule may declare `$args`; bind per-node JSON via `ruleId("""{...}""")`. Runtime exposes exactly `$` (input), `$$` (shared mutable context), `$args` (this node's params). ANTLR only recognizes the triple-quoted argument block's safe boundary; `RuleArguments` parses it once at `ExprNode` construction into canonical JSON. Cache key = `ruleId + canonical JSON` (object keys recursively sorted, array order preserved); identical param combos evaluate once per top-level run.

## UI tree model (`mosika-ui`)

Two recursive domains: **Flow** (host) and **Rule** (embedded sub-language), connected one-way and only through `JNode` (left = Rule, right = Flow). Rule cannot host/expand a Flow subtree.

```text
Flow = A(next?) | S(Flow+) | P(Flow+) | C(rule, Flow?) | J(Rule, Flow?) | D((C|J)+, Flow?)
Rule = R | And(Rule, Rule+) | Or(Rule, Rule+) | Hits(bounds, Rule+)
```

- Structure is explicit, not inferred from layout/coordinates/edge direction. `S(P(a,b),P(c,d))` directly means "finish first parallel group, then run second". Keep explicit serial nodes — do not encode semantics via "vertical=serial, horizontal=parallel" layout conventions, which would force merge points and degrade the tree into a DAG.
- `SNode`/`PNode`/`DNode`/`JNode` are first-class virtual roots of their subtrees; any same-kind subtree must be substitutable for another (any `FlowNode` as a branch, any `RNode` as a sub-rule).
- `RNode.expr` holds a stable `RuleDefinition.ruleId` reference; `RNode.name` is the node's editable UI name (initial value may come from `RuleDefinition.desc`). Rule names are UI metadata on the rule node (`RNode.name`, inherited by `LNode`/`HNode`), never on the outer `JNode`, and don't participate in `ruleExpr()`/evaluation.
- `UINodeAdapter` keeps only `toRule()` — a one-way compile of UI AST to `RuleNode` that drops `label`/`name` and cannot reverse-recover the UI tree. Legacy `CNode` remains a valid model and must keep JSON round-trip + `toRule()` compatibility.
- When editing any node, keep consistent: `toRule()` compilation, all recursive edges in `TreeNode.visit()`, `NodeTypeResolver` JSON type mapping, and "UI → JSON → UI → Rule" structural/semantic equivalence. Do not reintroduce the removed `tree2`.

## Web layer (`mosika-web`)

`AtomicRule` and `RuleFlow` are stored in separate tables with independent PKs. On entering Core's unified `ruleId` namespace, IDs get stable prefixes (reference impl: `r<id>` for atomic, `f<id>` for flow) — never assume the two tables' numeric IDs won't collide. `RuleTreeCompiler` validates a UI tree (`MAX_TREE_DEPTH=128`, `MAX_TOTAL_NODES=2000`) then one-way-compiles to `RuleNode` + canonical JSON + DSL; `RuleSuiteManager` assembles/hot-refreshes the single global `RuleSuite`. UDFs are in-process executable code, not untrusted config; the `sys` namespace is reserved for built-ins.

## Conventions

Java, 4-space indent, package under `com.nianien.mosika`. Types UpperCamelCase, methods lowerCamelCase, constants UPPER_SNAKE. Do not infer semantics from method names — e.g. `ParNode.next()` is a covariant override that appends a parallel node, not a serial one. Commit messages: short Chinese subject, one concern per commit; PRs state affected modules, semantic changes, and verification commands (attach representative DSL/JSON/structure for UI-tree changes).
