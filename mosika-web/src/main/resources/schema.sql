-- ============================================================
-- mosika-web 持久化 schema（SQLite）
-- rule_definition：原子规则叶子（原子/决策表）
-- rule_flow      ：命名规则编排，rule_tree 存 UI AST JSON
-- flow_rule_ref  ：flow 引用 rule 的派生边表，保存 flow 时重建
-- ============================================================

CREATE TABLE IF NOT EXISTS rule_definition (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    name        TEXT    NOT NULL,
    description TEXT    NOT NULL DEFAULT '',
    expression  TEXT    NOT NULL,
    use_type    INTEGER NOT NULL DEFAULT 0,
    rule_kind   TEXT    NOT NULL DEFAULT 'condition',  -- condition 条件规则 / action 动作规则
    status      INTEGER NOT NULL DEFAULT 1,
    version     INTEGER NOT NULL DEFAULT 0,
    created_at  TEXT    NOT NULL DEFAULT (datetime('now','localtime')),
    updated_at  TEXT    NOT NULL DEFAULT (datetime('now','localtime'))
);

CREATE INDEX IF NOT EXISTS idx_rule_definition_status   ON rule_definition(status);
CREATE INDEX IF NOT EXISTS idx_rule_definition_use_type ON rule_definition(use_type);
-- 注意：rule_kind 列在存量库由 DbMigrator 在启动时补齐，其索引也在 DbMigrator 内创建，
-- 不能放在 schema.sql（它先于所有 bean 执行，对老库会因缺列而报错）。

CREATE TABLE IF NOT EXISTS rule_flow (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    name        TEXT    NOT NULL,
    description TEXT    NOT NULL DEFAULT '',
    rule_tree   TEXT    NOT NULL,
    -- 0=draft 草稿（不校验、不进运行态）/ 1=published 已生效（编译发布进 RuleSuite）/ 2=disabled 已停用
    -- 新建流程默认草稿；老库 status=0 原语义为“停用”，由 DbMigrator 依 PRAGMA user_version 一次性迁移为 2。
    status      INTEGER NOT NULL DEFAULT 0,
    version     INTEGER NOT NULL DEFAULT 0,
    created_at  TEXT    NOT NULL DEFAULT (datetime('now','localtime')),
    updated_at  TEXT    NOT NULL DEFAULT (datetime('now','localtime'))
);

CREATE INDEX IF NOT EXISTS idx_rule_flow_status ON rule_flow(status);

CREATE TABLE IF NOT EXISTS flow_rule_ref (
    flow_id INTEGER NOT NULL,
    rule_id INTEGER NOT NULL,
    PRIMARY KEY (flow_id, rule_id)
);

CREATE INDEX IF NOT EXISTS idx_flow_rule_ref_rule ON flow_rule_ref(rule_id);
