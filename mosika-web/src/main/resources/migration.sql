BEGIN TRANSACTION;

DROP TABLE IF EXISTS udf_definition_new;

CREATE TABLE udf_definition_new (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    namespace_id INTEGER NOT NULL,
    group_name   TEXT    NOT NULL DEFAULT '',
    name         TEXT    NOT NULL,
    description  TEXT    NOT NULL DEFAULT '',
    source       TEXT    NOT NULL,
    status       INTEGER NOT NULL DEFAULT 1,
    version      INTEGER NOT NULL DEFAULT 0,
    created_at   TEXT    NOT NULL DEFAULT (datetime('now','localtime')),
    updated_at   TEXT    NOT NULL DEFAULT (datetime('now','localtime')),
    UNIQUE (namespace_id, group_name, name),
    FOREIGN KEY (namespace_id) REFERENCES rule_namespace(id)
);

INSERT OR IGNORE INTO udf_definition_new
    (id, namespace_id, group_name, name, description, source, status, version, created_at, updated_at)
SELECT id,
       (SELECT id FROM rule_namespace WHERE code='default'),
       group_name,
       name,
       description,
       source,
       status,
       version,
       created_at,
       updated_at
FROM udf_definition;

DROP TABLE udf_definition;
ALTER TABLE udf_definition_new RENAME TO udf_definition;

CREATE INDEX IF NOT EXISTS idx_udf_definition_namespace ON udf_definition(namespace_id);
CREATE INDEX IF NOT EXISTS idx_udf_definition_status ON udf_definition(status);

COMMIT;
