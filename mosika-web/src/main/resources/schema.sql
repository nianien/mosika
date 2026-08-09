-- ============================================================
-- mosika-web 持久化 schema（SQLite）
-- rule_namespace ：规则与规则流的业务引用范围
-- atomic_rule    ：原子规则定义
-- udf_definition ：用户注册的 JavaScript UDF
-- rule_flow      ：规则流，rule_tree 保存 UI AST JSON
-- flow_atomic_ref：规则流到原子规则的派生引用
-- flow_flow_ref  ：规则流之间的派生引用
-- ============================================================

CREATE TABLE IF NOT EXISTS rule_namespace (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    code        TEXT    NOT NULL UNIQUE,
    name        TEXT    NOT NULL,
    description TEXT    NOT NULL DEFAULT '',
    status      INTEGER NOT NULL DEFAULT 1,
    created_at  TEXT    NOT NULL DEFAULT (datetime('now','localtime')),
    updated_at  TEXT    NOT NULL DEFAULT (datetime('now','localtime'))
);

INSERT OR IGNORE INTO rule_namespace (code, name, description, status)
VALUES ('default', '默认命名空间', '系统默认规则引用范围', 1);

CREATE TABLE IF NOT EXISTS atomic_rule (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    namespace_id INTEGER NOT NULL,
    name         TEXT    NOT NULL,
    description  TEXT    NOT NULL DEFAULT '',
    expression   TEXT    NOT NULL,
    params       TEXT    NOT NULL DEFAULT '[]',
    kind         TEXT    NOT NULL DEFAULT 'condition' CHECK (kind IN ('condition', 'action')),
    status       INTEGER NOT NULL DEFAULT 1,
    version      INTEGER NOT NULL DEFAULT 0,
    created_at   TEXT    NOT NULL DEFAULT (datetime('now','localtime')),
    updated_at   TEXT    NOT NULL DEFAULT (datetime('now','localtime')),
    FOREIGN KEY (namespace_id) REFERENCES rule_namespace(id)
);

CREATE INDEX IF NOT EXISTS idx_atomic_rule_namespace ON atomic_rule(namespace_id);
CREATE INDEX IF NOT EXISTS idx_atomic_rule_status ON atomic_rule(status);
CREATE INDEX IF NOT EXISTS idx_atomic_rule_kind ON atomic_rule(kind);

CREATE TABLE IF NOT EXISTS udf_definition (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    group_name  TEXT    NOT NULL DEFAULT '',
    name        TEXT    NOT NULL,
    description TEXT    NOT NULL DEFAULT '',
    source      TEXT    NOT NULL,
    status      INTEGER NOT NULL DEFAULT 1,
    version     INTEGER NOT NULL DEFAULT 0,
    created_at  TEXT    NOT NULL DEFAULT (datetime('now','localtime')),
    updated_at  TEXT    NOT NULL DEFAULT (datetime('now','localtime')),
    UNIQUE (group_name, name)
);

CREATE INDEX IF NOT EXISTS idx_udf_definition_status ON udf_definition(status);

CREATE TABLE IF NOT EXISTS rule_flow (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    namespace_id INTEGER NOT NULL,
    name         TEXT    NOT NULL,
    description  TEXT    NOT NULL DEFAULT '',
    rule_tree    TEXT    NOT NULL,
    -- 0=draft 草稿 / 1=published 已生效 / 2=disabled 已停用
    status       INTEGER NOT NULL DEFAULT 0,
    version      INTEGER NOT NULL DEFAULT 0,
    created_at   TEXT    NOT NULL DEFAULT (datetime('now','localtime')),
    updated_at   TEXT    NOT NULL DEFAULT (datetime('now','localtime')),
    FOREIGN KEY (namespace_id) REFERENCES rule_namespace(id)
);

CREATE INDEX IF NOT EXISTS idx_rule_flow_status ON rule_flow(status);
CREATE INDEX IF NOT EXISTS idx_rule_flow_namespace ON rule_flow(namespace_id);

CREATE TABLE IF NOT EXISTS flow_atomic_ref (
    flow_id INTEGER NOT NULL,
    rule_id INTEGER NOT NULL,
    PRIMARY KEY (flow_id, rule_id),
    FOREIGN KEY (flow_id) REFERENCES rule_flow(id),
    FOREIGN KEY (rule_id) REFERENCES atomic_rule(id)
);

CREATE INDEX IF NOT EXISTS idx_flow_atomic_ref_rule ON flow_atomic_ref(rule_id);

CREATE TABLE IF NOT EXISTS flow_flow_ref (
    flow_id            INTEGER NOT NULL,
    referenced_flow_id INTEGER NOT NULL,
    PRIMARY KEY (flow_id, referenced_flow_id),
    FOREIGN KEY (flow_id) REFERENCES rule_flow(id),
    FOREIGN KEY (referenced_flow_id) REFERENCES rule_flow(id)
);

CREATE INDEX IF NOT EXISTS idx_flow_flow_ref_target ON flow_flow_ref(referenced_flow_id);
