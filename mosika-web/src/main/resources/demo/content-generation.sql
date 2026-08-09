-- Mosika 内容生成领域演示数据
--
-- 领域对象约定（作为求值 target，即 JavaScript 表达式中的 $）：
-- topic / contentType / channel
-- sourceCount / verifiedSourceCount / sourceFreshnessHours / maxSourceAgeHours
-- claimCount / citedClaimCount / topicRelevance
-- copyrightRisk / sensitiveRisk / factConfidence / qualityScore / originalityScore
-- promptTemplateVersion / modelPolicy / remainingTokenBudget / estimatedTokenCost
-- brandToneReady / campaignActive / campaignBudgetRemaining
-- regulatedTopic / exposureLevel / seoRequired / autoPublishEnabled
-- requiresHumanReview / eventAgeMinutes
--
-- r10001~r10136 和 f20001~f20007 是本演示固定占用的派生 ID。
-- 原子规则只在不存在时插入；规则流由本文件维护，重复导入时升级树结构并重建引用。

BEGIN TRANSACTION;

-- ============================================================
-- 领域 UDF：稳定的可执行能力，与规则生成和流程编排解耦
-- ============================================================

INSERT OR IGNORE INTO udf_definition
    (id, group_name, name, description, source, status, version)
VALUES
    (30001, 'content.generation', 'extractClaims', '从领域对象中提取核心主张阶段结果',
     'function extractClaims(target) { return {stage:"CLAIM_EXTRACTION", status:"completed", claimCount:target.claimCount}; }', 1, 0),
    (30002, 'content.generation', 'bindCitations', '为核心主张绑定引用证据并返回阶段结果',
     'function bindCitations(target) { return {stage:"CITATION_BINDING", status:"completed", citedClaimCount:target.citedClaimCount}; }', 1, 0),
    (30003, 'content.delivery', 'publish', '将通过门禁的内容发布到目标渠道',
     'function publish(target) { return {stage:"PUBLISH", status:"published", channel:target.channel}; }', 1, 0);

-- ============================================================
-- 条件规则：稳定业务语义的最小判断单元
-- ============================================================

INSERT OR IGNORE INTO atomic_rule
    (id, namespace_id, name, description, expression, kind, status, version)
VALUES
    (10001, (SELECT id FROM rule_namespace WHERE code='default'), '创作请求完整', '创作任务具备主题、内容类型和发布渠道', '$.topic != null && $.contentType != null && $.channel != null', 'condition', 1, 0),
    (10002, (SELECT id FROM rule_namespace WHERE code='default'), '素材数量达标', '标准内容生成至少需要三份素材', '$.sourceCount >= 3', 'condition', 1, 0),
    (10003, (SELECT id FROM rule_namespace WHERE code='default'), '可信素材占比达标', '至少两份可信素材且可信素材占比不低于百分之六十', '$.sourceCount > 0 && $.verifiedSourceCount >= 2 && $.verifiedSourceCount / $.sourceCount >= 0.6', 'condition', 1, 0),
    (10004, (SELECT id FROM rule_namespace WHERE code='default'), '主题相关性达标', '素材与创作主题的相关性不低于零点七五', '$.topicRelevance >= 0.75', 'condition', 1, 0),
    (10005, (SELECT id FROM rule_namespace WHERE code='default'), '版权风险可接受', '版权风险评分不高于零点二', '$.copyrightRisk <= 0.2', 'condition', 1, 0),
    (10006, (SELECT id FROM rule_namespace WHERE code='default'), '敏感风险可接受', '敏感内容风险评分不高于零点三', '$.sensitiveRisk <= 0.3', 'condition', 1, 0),
    (10007, (SELECT id FROM rule_namespace WHERE code='default'), '事实置信度达标', '关键事实核验置信度不低于零点八五', '$.factConfidence >= 0.85', 'condition', 1, 0),
    (10008, (SELECT id FROM rule_namespace WHERE code='default'), '内容质量达标', '完整性、连贯性和可读性综合分不低于零点八', '$.qualityScore >= 0.8', 'condition', 1, 0),
    (10009, (SELECT id FROM rule_namespace WHERE code='default'), '品牌语调已配置', '营销内容已经配置目标品牌的语调与禁用词', '$.brandToneReady == true', 'condition', 1, 0),
    (10010, (SELECT id FROM rule_namespace WHERE code='default'), '需要人工复核', '上游指定人工复核或任一关键风险指标未达自动发布要求', '$.requiresHumanReview == true || $.sensitiveRisk > 0.3 || $.factConfidence < 0.85 || $.qualityScore < 0.8', 'condition', 1, 0),
    (10011, (SELECT id FROM rule_namespace WHERE code='default'), '命中快讯时效窗口', '内容类型为快讯且事件发生不超过三十分钟', '$.contentType == "NEWS_FLASH" && $.eventAgeMinutes <= 30', 'condition', 1, 0),
    (10012, (SELECT id FROM rule_namespace WHERE code='default'), '命中深度文章模式', '内容类型为标准深度文章', '$.contentType == "ARTICLE"', 'condition', 1, 0),
    (10013, (SELECT id FROM rule_namespace WHERE code='default'), '命中营销文案模式', '内容类型为营销文案', '$.contentType == "MARKETING"', 'condition', 1, 0),
    (10014, (SELECT id FROM rule_namespace WHERE code='default'), '快讯双源确认', '突发快讯至少拥有两份已验证的独立信源', '$.verifiedSourceCount >= 2', 'condition', 1, 0),
    (10015, (SELECT id FROM rule_namespace WHERE code='default'), '发布渠道受支持', '目标渠道属于当前参考实现支持的发布集合', '["WEB", "APP", "WECHAT"].includes($.channel)', 'condition', 1, 0),
    (10016, (SELECT id FROM rule_namespace WHERE code='default'), '生成配置就绪', '提示词模板和模型策略均已配置', '$.promptTemplateVersion != null && $.modelPolicy != null', 'condition', 1, 0),
    (10017, (SELECT id FROM rule_namespace WHERE code='default'), '生成预算充足', '剩余令牌预算能够覆盖本次预估消耗', '$.remainingTokenBudget >= $.estimatedTokenCost', 'condition', 1, 0),
    (10018, (SELECT id FROM rule_namespace WHERE code='default'), '素材时效达标', '素材更新时间未超过领域允许的最大时效', '$.sourceFreshnessHours <= $.maxSourceAgeHours', 'condition', 1, 0),
    (10019, (SELECT id FROM rule_namespace WHERE code='default'), '证据覆盖达标', '有出处的核心主张占比不低于百分之九十', '$.claimCount > 0 && $.citedClaimCount / $.claimCount >= 0.9', 'condition', 1, 0),
    (10020, (SELECT id FROM rule_namespace WHERE code='default'), '原创性达标', '内容原创性评分不低于零点八', '$.originalityScore >= 0.8', 'condition', 1, 0),
    (10021, (SELECT id FROM rule_namespace WHERE code='default'), '命中监管敏感主题', '内容涉及医疗、金融、法律等强监管主题', '$.regulatedTopic == true', 'condition', 1, 0),
    (10022, (SELECT id FROM rule_namespace WHERE code='default'), '命中高影响发布', '预计曝光量或业务等级要求升级审核', '$.exposureLevel == "HIGH"', 'condition', 1, 0),
    (10023, (SELECT id FROM rule_namespace WHERE code='default'), '需要搜索优化', '目标内容需要执行搜索引擎结构优化', '$.seoRequired == true', 'condition', 1, 0),
    (10024, (SELECT id FROM rule_namespace WHERE code='default'), '营销活动有效', '营销活动有效且仍有可用预算', '$.campaignActive == true && $.campaignBudgetRemaining > 0', 'condition', 1, 0),
    (10025, (SELECT id FROM rule_namespace WHERE code='default'), '允许自动发布', '租户与渠道策略允许质量门禁自动发布', '$.autoPublishEnabled == true', 'condition', 1, 0),
    (10026, (SELECT id FROM rule_namespace WHERE code='default'), '目标渠道为微信', '目标发布渠道为微信公众号', '$.channel == "WECHAT"', 'condition', 1, 0),
    (10027, (SELECT id FROM rule_namespace WHERE code='default'), '目标渠道为客户端', '目标发布渠道为移动客户端', '$.channel == "APP"', 'condition', 1, 0),
    (10028, (SELECT id FROM rule_namespace WHERE code='default'), '目标渠道为网站', '目标发布渠道为 Web 站点', '$.channel == "WEB"', 'condition', 1, 0);

-- ============================================================
-- 动作规则：真实生成流水线中的可替换执行能力
-- ============================================================

DELETE FROM flow_atomic_ref WHERE flow_id BETWEEN 20001 AND 20007;
DELETE FROM flow_flow_ref WHERE flow_id BETWEEN 20001 AND 20007;

INSERT OR IGNORE INTO atomic_rule
    (id, namespace_id, name, description, expression, kind, status, version)
VALUES
    (10101, (SELECT id FROM rule_namespace WHERE code='default'), '素材清洗与归一化', '去重、清洗并统一素材结构', '{stage:"MATERIAL_NORMALIZATION",status:"completed",sourceCount:$.sourceCount}', 'action', 1, 0),
    (10102, (SELECT id FROM rule_namespace WHERE code='default'), '生成内容提纲', '根据主题和素材生成分层内容提纲', '{stage:"OUTLINE_GENERATION",status:"completed",topic:$.topic}', 'action', 1, 0),
    (10103, (SELECT id FROM rule_namespace WHERE code='default'), '生成正文草稿', '依据提纲生成完整正文初稿', '{stage:"DRAFT_GENERATION",status:"completed",contentType:$.contentType}', 'action', 1, 0),
    (10104, (SELECT id FROM rule_namespace WHERE code='default'), '执行事实核验', '抽取关键主张并核对可信信源', '{stage:"FACT_CHECK",status:"completed",confidence:$.factConfidence}', 'action', 1, 0),
    (10105, (SELECT id FROM rule_namespace WHERE code='default'), '执行版权与合规扫描', '识别版权、敏感内容和平台规范风险', '{stage:"COMPLIANCE_SCAN",status:"completed",risk:Math.max($.copyrightRisk,$.sensitiveRisk)}', 'action', 1, 0),
    (10106, (SELECT id FROM rule_namespace WHERE code='default'), '执行内容质量评分', '评估完整性、连贯性、可读性和信息密度', '{stage:"QUALITY_SCORING",status:"completed",score:$.qualityScore}', 'action', 1, 0),
    (10107, (SELECT id FROM rule_namespace WHERE code='default'), '提交人工复核', '将高风险或低置信内容送入人工审核队列', '{stage:"HUMAN_REVIEW",status:"review_required"}', 'action', 1, 0),
    (10108, (SELECT id FROM rule_namespace WHERE code='default'), '发布内容', '将通过质量门禁的内容发布到目标渠道', 'content.delivery.publish($)', 'action', 1, 0),
    (10109, (SELECT id FROM rule_namespace WHERE code='default'), '驳回生成任务', '终止未通过准入或质量门禁的生成任务', '{stage:"REJECT",status:"rejected",reason:"generation_gate_failed"}', 'action', 1, 0),
    (10110, (SELECT id FROM rule_namespace WHERE code='default'), '生成快讯草稿', '基于已确认信源生成短格式突发快讯', '{stage:"FLASH_DRAFT",status:"completed",eventAgeMinutes:$.eventAgeMinutes}', 'action', 1, 0),
    (10111, (SELECT id FROM rule_namespace WHERE code='default'), '应用品牌语调', '依据品牌词典、风格和禁用词重写营销文案', '{stage:"BRAND_TONE_REWRITE",status:"completed"}', 'action', 1, 0),
    (10116, (SELECT id FROM rule_namespace WHERE code='default'), '拒绝不支持的内容类型', '内容类型无法路由时终止任务并返回明确状态', '{stage:"ROUTING",status:"rejected",reason:"unsupported_content_type"}', 'action', 1, 0),
    (10117, (SELECT id FROM rule_namespace WHERE code='default'), '检索补充素材', '从可信知识源召回与主题相关的补充素材', '{stage:"SOURCE_RETRIEVAL",status:"completed",topic:$.topic}', 'action', 1, 0),
    (10118, (SELECT id FROM rule_namespace WHERE code='default'), '提取核心主张', '从素材中提取需要验证的事实主张与关键实体', 'content.generation.extractClaims($)', 'action', 1, 0),
    (10119, (SELECT id FROM rule_namespace WHERE code='default'), '绑定引用证据', '为核心主张绑定可追溯的可信素材引用', 'content.generation.bindCitations($)', 'action', 1, 0),
    (10120, (SELECT id FROM rule_namespace WHERE code='default'), '组装生成上下文', '按模型上下文窗口组织素材、约束和产品指令', '{stage:"CONTEXT_ASSEMBLY",status:"completed",template:$.promptTemplateVersion}', 'action', 1, 0),
    (10121, (SELECT id FROM rule_namespace WHERE code='default'), '选择模型与参数', '根据内容类型、成本和质量要求选择模型策略', '{stage:"MODEL_SELECTION",status:"completed",policy:$.modelPolicy}', 'action', 1, 0),
    (10122, (SELECT id FROM rule_namespace WHERE code='default'), '生成多版本候选', '生成不同结构和表达风格的候选内容', '{stage:"CANDIDATE_GENERATION",status:"completed",candidateCount:3}', 'action', 1, 0),
    (10123, (SELECT id FROM rule_namespace WHERE code='default'), '优选内容候选', '根据事实、质量和风格评分选择最佳候选', '{stage:"CANDIDATE_RANKING",status:"completed"}', 'action', 1, 0),
    (10124, (SELECT id FROM rule_namespace WHERE code='default'), '执行搜索结构优化', '优化标题层级、摘要、关键词和内容结构', '{stage:"SEO_OPTIMIZATION",status:"completed"}', 'action', 1, 0),
    (10125, (SELECT id FROM rule_namespace WHERE code='default'), '适配微信渠道', '生成适合公众号阅读的段落、摘要和封面信息', '{stage:"CHANNEL_ADAPTATION",status:"completed",channel:"WECHAT"}', 'action', 1, 0),
    (10126, (SELECT id FROM rule_namespace WHERE code='default'), '适配客户端渠道', '生成适合移动端信息流的标题、卡片和正文', '{stage:"CHANNEL_ADAPTATION",status:"completed",channel:"APP"}', 'action', 1, 0),
    (10127, (SELECT id FROM rule_namespace WHERE code='default'), '适配网站渠道', '生成适合 Web 站点的正文结构和元数据', '{stage:"CHANNEL_ADAPTATION",status:"completed",channel:"WEB"}', 'action', 1, 0),
    (10128, (SELECT id FROM rule_namespace WHERE code='default'), '记录生成审计', '记录模型、提示词、证据链和规则流版本', '{stage:"GENERATION_AUDIT",status:"recorded"}', 'action', 1, 0),
    (10129, (SELECT id FROM rule_namespace WHERE code='default'), '触发素材补全', '证据覆盖不足但具备生成基础时进入素材补全队列', '{stage:"SOURCE_ENRICHMENT",status:"required"}', 'action', 1, 0),
    (10130, (SELECT id FROM rule_namespace WHERE code='default'), '提交敏感主题专审', '将强监管主题提交给对应领域审核人员', '{stage:"REGULATED_REVIEW",status:"review_required"}', 'action', 1, 0),
    (10131, (SELECT id FROM rule_namespace WHERE code='default'), '提交高影响发布审核', '将高曝光内容提交给高级审核队列', '{stage:"HIGH_IMPACT_REVIEW",status:"review_required"}', 'action', 1, 0),
    (10134, (SELECT id FROM rule_namespace WHERE code='default'), '拒绝不支持的发布渠道', '渠道适配无法识别目标渠道时终止任务', '{stage:"CHANNEL_ADAPTATION",status:"rejected",reason:"unsupported_channel"}', 'action', 1, 0),
    (10135, (SELECT id FROM rule_namespace WHERE code='default'), '生成标题与摘要', '并行生成快讯标题、导语和一句话摘要', '{stage:"TITLE_AND_SUMMARY",status:"completed"}', 'action', 1, 0),
    (10136, (SELECT id FROM rule_namespace WHERE code='default'), '召回品牌知识', '召回品牌术语、产品卖点、风格指南和禁用词', '{stage:"BRAND_KNOWLEDGE",status:"completed"}', 'action', 1, 0);

-- 这三条用于演示“原子规则引用稳定 UDF”；重复导入时同步演示表达式。
UPDATE atomic_rule SET expression='content.delivery.publish($)' WHERE id=10108;
UPDATE atomic_rule SET expression='content.generation.extractClaims($)' WHERE id=10118;
UPDATE atomic_rule SET expression='content.generation.bindCitations($)' WHERE id=10119;

-- ============================================================
-- 规则流：面向产品和业务编排稳定的原子规则
-- ============================================================

-- 20001 内容生成任务智能路由：有序识别快讯、营销文案和深度文章。
INSERT INTO rule_flow (id, namespace_id, name, description, rule_tree, status, version)
VALUES
    (20001, (SELECT id FROM rule_namespace WHERE code='default'), '内容生成任务智能路由', '根据内容类型与时效要求，把创作任务路由到快讯、营销文案或深度文章生成流程',
     '{"type":"T","name":"","next":{"type":"D","name":"","branches":[{"type":"C","name":"","rule":{"type":"B","name":"命中快讯时效窗口","expr":"r10011","negative":false},"next":{"type":"A","name":"","rule":{"type":"R","name":"","expr":"f20003"}}},{"type":"C","name":"","rule":{"type":"B","name":"命中营销文案模式","expr":"r10013","negative":false},"next":{"type":"A","name":"","rule":{"type":"R","name":"","expr":"f20004"}}},{"type":"C","name":"","rule":{"type":"B","name":"命中深度文章模式","expr":"r10012","negative":false},"next":{"type":"A","name":"","rule":{"type":"R","name":"","expr":"f20002"}}}],"defaultBranch":{"type":"A","name":"","rule":{"type":"R","name":"","expr":"r10116"}}}}',
     1, 0)
ON CONFLICT(id) DO UPDATE SET
    name=excluded.name, description=excluded.description, rule_tree=excluded.rule_tree,
    status=excluded.status, version=rule_flow.version + 1, updated_at=datetime('now','localtime')
WHERE rule_flow.name <> excluded.name OR rule_flow.description <> excluded.description
   OR rule_flow.rule_tree <> excluded.rule_tree OR rule_flow.status <> excluded.status;

-- 20002 标准深度文章生成：证据链、上下文、模型、候选生成、SEO、渠道适配和质量门禁串联。
INSERT INTO rule_flow (id, namespace_id, name, description, rule_tree, status, version)
VALUES
    (20002, (SELECT id FROM rule_namespace WHERE code='default'), '标准深度文章生成', '以深层递归树表达准入、证据决策、生成子树、SEO 和发布风险决策的完整内容生产链路',
     '{"type":"T","name":"","next":{"type":"D","name":"","branches":[{"type":"C","name":"","rule":{"type":"L","name":"深度文章生产准入","expr":"&&","negative":false,"rules":[{"type":"L","name":"业务准入","expr":"&&","negative":false,"rules":[{"type":"L","name":"内容模式与请求","expr":"&&","negative":false,"rules":[{"type":"B","name":"命中深度文章模式","expr":"r10012","negative":false},{"type":"B","name":"创作请求完整","expr":"r10001","negative":false}]},{"type":"L","name":"主题与渠道","expr":"&&","negative":false,"rules":[{"type":"B","name":"主题相关性达标","expr":"r10004","negative":false},{"type":"B","name":"发布渠道受支持","expr":"r10015","negative":false}]}]},{"type":"L","name":"运行资源准入","expr":"&&","negative":false,"rules":[{"type":"B","name":"生成配置就绪","expr":"r10016","negative":false},{"type":"B","name":"生成预算充足","expr":"r10017","negative":false}]}]},"next":{"type":"S","name":"","branches":[{"type":"A","name":"","rule":{"type":"R","name":"","expr":"f20006"}},{"type":"D","name":"","branches":[{"type":"C","name":"","rule":{"type":"L","name":"证据链完整","expr":"&&","negative":false,"rules":[{"type":"B","name":"素材时效达标","expr":"r10018","negative":false},{"type":"B","name":"证据覆盖达标","expr":"r10019","negative":false}]},"next":{"type":"S","name":"","branches":[{"type":"A","name":"","rule":{"type":"R","name":"","expr":"r10120"}},{"type":"A","name":"","rule":{"type":"R","name":"","expr":"r10121"}},{"type":"P","name":"","branches":[{"type":"S","name":"","branches":[{"type":"A","name":"","rule":{"type":"R","name":"","expr":"r10102"}},{"type":"A","name":"","rule":{"type":"R","name":"","expr":"r10103"}},{"type":"A","name":"","rule":{"type":"R","name":"","expr":"r10122"}},{"type":"A","name":"","rule":{"type":"R","name":"","expr":"r10123"}}]},{"type":"A","name":"","rule":{"type":"R","name":"","expr":"r10135"}}]},{"type":"C","name":"","rule":{"type":"B","name":"需要搜索优化","expr":"r10023","negative":false},"next":{"type":"S","name":"","branches":[{"type":"A","name":"","rule":{"type":"R","name":"","expr":"r10124"}},{"type":"A","name":"","rule":{"type":"R","name":"","expr":"r10128"}}]}},{"type":"D","name":"","branches":[{"type":"C","name":"","rule":{"type":"B","name":"命中监管敏感主题","expr":"r10021","negative":false},"next":{"type":"S","name":"","branches":[{"type":"A","name":"","rule":{"type":"R","name":"","expr":"r10130"}},{"type":"A","name":"","rule":{"type":"R","name":"","expr":"r10128"}}]}},{"type":"C","name":"","rule":{"type":"B","name":"命中高影响发布","expr":"r10022","negative":false},"next":{"type":"S","name":"","branches":[{"type":"A","name":"","rule":{"type":"R","name":"","expr":"r10131"}},{"type":"A","name":"","rule":{"type":"R","name":"","expr":"r10128"}}]}}],"defaultBranch":{"type":"S","name":"","branches":[{"type":"A","name":"","rule":{"type":"R","name":"","expr":"f20007"}},{"type":"A","name":"","rule":{"type":"R","name":"","expr":"f20005"}}]}}]}},{"type":"C","name":"","rule":{"type":"L","name":"具备素材补全基础","expr":"&&","negative":false,"rules":[{"type":"B","name":"素材数量达标","expr":"r10002","negative":false},{"type":"B","name":"可信素材占比达标","expr":"r10003","negative":false}]},"next":{"type":"S","name":"","branches":[{"type":"A","name":"","rule":{"type":"R","name":"","expr":"r10129"}},{"type":"A","name":"","rule":{"type":"R","name":"","expr":"r10128"}}]}}],"defaultBranch":{"type":"A","name":"","rule":{"type":"R","name":"","expr":"r10109"}}}]}}],"defaultBranch":{"type":"A","name":"","rule":{"type":"R","name":"","expr":"r10109"}}}}',
     1, 0)
ON CONFLICT(id) DO UPDATE SET
    name=excluded.name, description=excluded.description, rule_tree=excluded.rule_tree,
    status=excluded.status, version=rule_flow.version + 1, updated_at=datetime('now','localtime')
WHERE rule_flow.name <> excluded.name OR rule_flow.description <> excluded.description
   OR rule_flow.rule_tree <> excluded.rule_tree OR rule_flow.status <> excluded.status;

-- 20003 突发快讯生成：证据链完成后并行生成正文与标题摘要。
INSERT INTO rule_flow (id, namespace_id, name, description, rule_tree, status, version)
VALUES
    (20003, (SELECT id FROM rule_namespace WHERE code='default'), '突发快讯生成', '以深层递归树表达时效准入、证据决策、双路并行生成和分级发布风险处理',
     '{"type":"T","name":"","next":{"type":"D","name":"","branches":[{"type":"C","name":"","rule":{"type":"L","name":"突发快讯生产准入","expr":"&&","negative":false,"rules":[{"type":"L","name":"业务准入","expr":"&&","negative":false,"rules":[{"type":"L","name":"时效与请求","expr":"&&","negative":false,"rules":[{"type":"B","name":"命中快讯时效窗口","expr":"r10011","negative":false},{"type":"B","name":"创作请求完整","expr":"r10001","negative":false}]},{"type":"L","name":"信源与渠道","expr":"&&","negative":false,"rules":[{"type":"B","name":"快讯双源确认","expr":"r10014","negative":false},{"type":"B","name":"发布渠道受支持","expr":"r10015","negative":false}]}]},{"type":"L","name":"运行资源准入","expr":"&&","negative":false,"rules":[{"type":"B","name":"生成配置就绪","expr":"r10016","negative":false},{"type":"B","name":"生成预算充足","expr":"r10017","negative":false}]}]},"next":{"type":"S","name":"","branches":[{"type":"A","name":"","rule":{"type":"R","name":"","expr":"f20006"}},{"type":"D","name":"","branches":[{"type":"C","name":"","rule":{"type":"L","name":"快讯证据链完整","expr":"&&","negative":false,"rules":[{"type":"B","name":"素材时效达标","expr":"r10018","negative":false},{"type":"B","name":"证据覆盖达标","expr":"r10019","negative":false}]},"next":{"type":"S","name":"","branches":[{"type":"P","name":"","branches":[{"type":"S","name":"","branches":[{"type":"A","name":"","rule":{"type":"R","name":"","expr":"r10120"}},{"type":"A","name":"","rule":{"type":"R","name":"","expr":"r10121"}},{"type":"A","name":"","rule":{"type":"R","name":"","expr":"r10110"}}]},{"type":"S","name":"","branches":[{"type":"A","name":"","rule":{"type":"R","name":"","expr":"r10135"}},{"type":"A","name":"","rule":{"type":"R","name":"","expr":"r10128"}}]}]},{"type":"D","name":"","branches":[{"type":"C","name":"","rule":{"type":"B","name":"命中监管敏感主题","expr":"r10021","negative":false},"next":{"type":"S","name":"","branches":[{"type":"A","name":"","rule":{"type":"R","name":"","expr":"r10130"}},{"type":"A","name":"","rule":{"type":"R","name":"","expr":"r10128"}}]}},{"type":"C","name":"","rule":{"type":"B","name":"命中高影响发布","expr":"r10022","negative":false},"next":{"type":"S","name":"","branches":[{"type":"A","name":"","rule":{"type":"R","name":"","expr":"r10131"}},{"type":"A","name":"","rule":{"type":"R","name":"","expr":"r10128"}}]}}],"defaultBranch":{"type":"S","name":"","branches":[{"type":"A","name":"","rule":{"type":"R","name":"","expr":"f20007"}},{"type":"A","name":"","rule":{"type":"R","name":"","expr":"f20005"}}]}}]}},{"type":"C","name":"","rule":{"type":"L","name":"具备素材补全基础","expr":"&&","negative":false,"rules":[{"type":"B","name":"素材数量达标","expr":"r10002","negative":false},{"type":"B","name":"可信素材占比达标","expr":"r10003","negative":false}]},"next":{"type":"S","name":"","branches":[{"type":"A","name":"","rule":{"type":"R","name":"","expr":"r10129"}},{"type":"A","name":"","rule":{"type":"R","name":"","expr":"r10128"}}]}}],"defaultBranch":{"type":"A","name":"","rule":{"type":"R","name":"","expr":"r10109"}}}]}}],"defaultBranch":{"type":"A","name":"","rule":{"type":"R","name":"","expr":"r10109"}}}}',
     1, 0)
ON CONFLICT(id) DO UPDATE SET
    name=excluded.name, description=excluded.description, rule_tree=excluded.rule_tree,
    status=excluded.status, version=rule_flow.version + 1, updated_at=datetime('now','localtime')
WHERE rule_flow.name <> excluded.name OR rule_flow.description <> excluded.description
   OR rule_flow.rule_tree <> excluded.rule_tree OR rule_flow.status <> excluded.status;

-- 20004 营销文案生成：活动准入、品牌知识、候选优选、渠道适配和高影响审核完整串联。
INSERT INTO rule_flow (id, namespace_id, name, description, rule_tree, status, version)
VALUES
    (20004, (SELECT id FROM rule_namespace WHERE code='default'), '营销文案生成', '以深层递归树表达活动与品牌准入、证据分流、双路准备、候选优选和高影响审核',
     '{"type":"T","name":"","next":{"type":"D","name":"","branches":[{"type":"C","name":"","rule":{"type":"L","name":"营销文案生产准入","expr":"&&","negative":false,"rules":[{"type":"L","name":"业务准入","expr":"&&","negative":false,"rules":[{"type":"L","name":"内容与活动","expr":"&&","negative":false,"rules":[{"type":"B","name":"命中营销文案模式","expr":"r10013","negative":false},{"type":"B","name":"营销活动有效","expr":"r10024","negative":false}]},{"type":"L","name":"品牌与版权","expr":"&&","negative":false,"rules":[{"type":"B","name":"品牌语调已配置","expr":"r10009","negative":false},{"type":"B","name":"版权风险可接受","expr":"r10005","negative":false}]}]},{"type":"L","name":"运行准入","expr":"&&","negative":false,"rules":[{"type":"L","name":"请求与渠道","expr":"&&","negative":false,"rules":[{"type":"B","name":"创作请求完整","expr":"r10001","negative":false},{"type":"B","name":"发布渠道受支持","expr":"r10015","negative":false}]},{"type":"L","name":"配置与预算","expr":"&&","negative":false,"rules":[{"type":"B","name":"生成配置就绪","expr":"r10016","negative":false},{"type":"B","name":"生成预算充足","expr":"r10017","negative":false}]}]}]},"next":{"type":"S","name":"","branches":[{"type":"A","name":"","rule":{"type":"R","name":"","expr":"f20006"}},{"type":"D","name":"","branches":[{"type":"C","name":"","rule":{"type":"L","name":"营销证据链完整","expr":"&&","negative":false,"rules":[{"type":"B","name":"素材时效达标","expr":"r10018","negative":false},{"type":"B","name":"证据覆盖达标","expr":"r10019","negative":false}]},"next":{"type":"S","name":"","branches":[{"type":"P","name":"","branches":[{"type":"S","name":"","branches":[{"type":"A","name":"","rule":{"type":"R","name":"","expr":"r10136"}},{"type":"A","name":"","rule":{"type":"R","name":"","expr":"r10111"}}]},{"type":"S","name":"","branches":[{"type":"A","name":"","rule":{"type":"R","name":"","expr":"r10120"}},{"type":"A","name":"","rule":{"type":"R","name":"","expr":"r10121"}}]}]},{"type":"A","name":"","rule":{"type":"R","name":"","expr":"r10102"}},{"type":"A","name":"","rule":{"type":"R","name":"","expr":"r10122"}},{"type":"A","name":"","rule":{"type":"R","name":"","expr":"r10123"}},{"type":"D","name":"","branches":[{"type":"C","name":"","rule":{"type":"B","name":"命中高影响发布","expr":"r10022","negative":false},"next":{"type":"S","name":"","branches":[{"type":"A","name":"","rule":{"type":"R","name":"","expr":"r10131"}},{"type":"A","name":"","rule":{"type":"R","name":"","expr":"r10128"}}]}}],"defaultBranch":{"type":"S","name":"","branches":[{"type":"A","name":"","rule":{"type":"R","name":"","expr":"f20007"}},{"type":"A","name":"","rule":{"type":"R","name":"","expr":"f20005"}}]}}]}},{"type":"C","name":"","rule":{"type":"L","name":"具备素材补全基础","expr":"&&","negative":false,"rules":[{"type":"B","name":"素材数量达标","expr":"r10002","negative":false},{"type":"B","name":"可信素材占比达标","expr":"r10003","negative":false}]},"next":{"type":"S","name":"","branches":[{"type":"A","name":"","rule":{"type":"R","name":"","expr":"r10129"}},{"type":"A","name":"","rule":{"type":"R","name":"","expr":"r10128"}}]}}],"defaultBranch":{"type":"A","name":"","rule":{"type":"R","name":"","expr":"r10109"}}}]}}],"defaultBranch":{"type":"A","name":"","rule":{"type":"R","name":"","expr":"r10109"}}}}',
     1, 0)
ON CONFLICT(id) DO UPDATE SET
    name=excluded.name, description=excluded.description, rule_tree=excluded.rule_tree,
    status=excluded.status, version=rule_flow.version + 1, updated_at=datetime('now','localtime')
WHERE rule_flow.name <> excluded.name OR rule_flow.description <> excluded.description
   OR rule_flow.rule_tree <> excluded.rule_tree OR rule_flow.status <> excluded.status;

-- 20005 发布前质量门禁：监管、高影响、自动发布、普通复核和驳回有序决策。
INSERT INTO rule_flow (id, namespace_id, name, description, rule_tree, status, version)
VALUES
    (20005, (SELECT id FROM rule_namespace WHERE code='default'), '发布前质量门禁', '以深层递归树表达并行检测、分级审核、嵌套自动发布资格和发布后审计',
     '{"type":"T","name":"","next":{"type":"S","name":"","branches":[{"type":"P","name":"","branches":[{"type":"A","name":"","rule":{"type":"R","name":"","expr":"r10104"}},{"type":"A","name":"","rule":{"type":"R","name":"","expr":"r10105"}},{"type":"A","name":"","rule":{"type":"R","name":"","expr":"r10106"}}]},{"type":"D","name":"","branches":[{"type":"C","name":"","rule":{"type":"B","name":"命中监管敏感主题","expr":"r10021","negative":false},"next":{"type":"S","name":"","branches":[{"type":"A","name":"","rule":{"type":"R","name":"","expr":"r10130"}},{"type":"A","name":"","rule":{"type":"R","name":"","expr":"r10128"}}]}},{"type":"C","name":"","rule":{"type":"B","name":"命中高影响发布","expr":"r10022","negative":false},"next":{"type":"S","name":"","branches":[{"type":"A","name":"","rule":{"type":"R","name":"","expr":"r10131"}},{"type":"A","name":"","rule":{"type":"R","name":"","expr":"r10128"}}]}},{"type":"C","name":"","rule":{"type":"L","name":"自动发布门禁","expr":"&&","negative":false,"rules":[{"type":"L","name":"内容可信","expr":"&&","negative":false,"rules":[{"type":"L","name":"事实与证据","expr":"&&","negative":false,"rules":[{"type":"B","name":"事实置信度达标","expr":"r10007","negative":false},{"type":"B","name":"证据覆盖达标","expr":"r10019","negative":false}]},{"type":"L","name":"版权与敏感","expr":"&&","negative":false,"rules":[{"type":"B","name":"版权风险可接受","expr":"r10005","negative":false},{"type":"B","name":"敏感风险可接受","expr":"r10006","negative":false}]}]},{"type":"L","name":"发布资格","expr":"&&","negative":false,"rules":[{"type":"L","name":"质量与原创","expr":"&&","negative":false,"rules":[{"type":"B","name":"内容质量达标","expr":"r10008","negative":false},{"type":"B","name":"原创性达标","expr":"r10020","negative":false}]},{"type":"L","name":"自动发布策略","expr":"&&","negative":false,"rules":[{"type":"B","name":"允许自动发布","expr":"r10025","negative":false},{"type":"B","name":"无需人工复核","expr":"r10010","negative":true}]}]}]},"next":{"type":"D","name":"","branches":[{"type":"C","name":"","rule":{"type":"B","name":"发布渠道受支持","expr":"r10015","negative":false},"next":{"type":"S","name":"","branches":[{"type":"A","name":"","rule":{"type":"R","name":"","expr":"r10108"}},{"type":"A","name":"","rule":{"type":"R","name":"","expr":"r10128"}}]}}],"defaultBranch":{"type":"A","name":"","rule":{"type":"R","name":"","expr":"r10134"}}}},{"type":"C","name":"","rule":{"type":"L","name":"普通人工复核门禁","expr":"||","negative":false,"rules":[{"type":"B","name":"需要人工复核","expr":"r10010","negative":false},{"type":"L","name":"可信度不足","expr":"||","negative":false,"rules":[{"type":"B","name":"证据覆盖不足","expr":"r10019","negative":true},{"type":"B","name":"原创性不足","expr":"r10020","negative":true}]}]},"next":{"type":"S","name":"","branches":[{"type":"A","name":"","rule":{"type":"R","name":"","expr":"r10107"}},{"type":"A","name":"","rule":{"type":"R","name":"","expr":"r10128"}}]}}],"defaultBranch":{"type":"S","name":"","branches":[{"type":"A","name":"","rule":{"type":"R","name":"","expr":"r10109"}},{"type":"A","name":"","rule":{"type":"R","name":"","expr":"r10128"}}]}}]}}',
     1, 0)
ON CONFLICT(id) DO UPDATE SET
    name=excluded.name, description=excluded.description, rule_tree=excluded.rule_tree,
    status=excluded.status, version=rule_flow.version + 1, updated_at=datetime('now','localtime')
WHERE rule_flow.name <> excluded.name OR rule_flow.description <> excluded.description
   OR rule_flow.rule_tree <> excluded.rule_tree OR rule_flow.status <> excluded.status;

-- 20006 素材证据链构建：清洗、检索、主张提取、引用绑定和证据质量分流。
INSERT INTO rule_flow (id, namespace_id, name, description, rule_tree, status, version)
VALUES
    (20006, (SELECT id FROM rule_namespace WHERE code='default'), '素材证据链构建', '完成素材清洗与检索、核心主张提取、引用绑定，并将证据充分、待补全和不可生成任务分流',
     '{"type":"T","name":"","next":{"type":"S","name":"","branches":[{"type":"P","name":"","branches":[{"type":"A","name":"","rule":{"type":"R","name":"","expr":"r10101"}},{"type":"A","name":"","rule":{"type":"R","name":"","expr":"r10117"}}]},{"type":"A","name":"","rule":{"type":"R","name":"","expr":"r10118"}},{"type":"A","name":"","rule":{"type":"R","name":"","expr":"r10119"}},{"type":"D","name":"","branches":[{"type":"C","name":"","rule":{"type":"L","name":"证据链完整","expr":"&&","negative":false,"rules":[{"type":"B","name":"素材时效达标","expr":"r10018","negative":false},{"type":"B","name":"证据覆盖达标","expr":"r10019","negative":false}]},"next":{"type":"A","name":"","rule":{"type":"R","name":"","expr":"r10128"}}},{"type":"C","name":"","rule":{"type":"L","name":"具备素材补全基础","expr":"&&","negative":false,"rules":[{"type":"B","name":"素材数量达标","expr":"r10002","negative":false},{"type":"B","name":"可信素材占比达标","expr":"r10003","negative":false}]},"next":{"type":"A","name":"","rule":{"type":"R","name":"","expr":"r10129"}}}],"defaultBranch":{"type":"A","name":"","rule":{"type":"R","name":"","expr":"r10109"}}}]}}',
     1, 0)
ON CONFLICT(id) DO UPDATE SET
    name=excluded.name, description=excluded.description, rule_tree=excluded.rule_tree,
    status=excluded.status, version=rule_flow.version + 1, updated_at=datetime('now','localtime')
WHERE rule_flow.name <> excluded.name OR rule_flow.description <> excluded.description
   OR rule_flow.rule_tree <> excluded.rule_tree OR rule_flow.status <> excluded.status;

-- 20007 多渠道内容适配：按渠道执行专属转换，并统一记录生成审计。
INSERT INTO rule_flow (id, namespace_id, name, description, rule_tree, status, version)
VALUES
    (20007, (SELECT id FROM rule_namespace WHERE code='default'), '多渠道内容适配', '按微信、客户端和 Web 三类渠道执行不同内容投影，并记录可追溯的生成审计',
     '{"type":"T","name":"","next":{"type":"D","name":"","branches":[{"type":"C","name":"","rule":{"type":"B","name":"目标渠道为微信","expr":"r10026","negative":false},"next":{"type":"S","name":"","branches":[{"type":"A","name":"","rule":{"type":"R","name":"","expr":"r10125"}},{"type":"A","name":"","rule":{"type":"R","name":"","expr":"r10128"}}]}},{"type":"C","name":"","rule":{"type":"B","name":"目标渠道为客户端","expr":"r10027","negative":false},"next":{"type":"S","name":"","branches":[{"type":"A","name":"","rule":{"type":"R","name":"","expr":"r10126"}},{"type":"A","name":"","rule":{"type":"R","name":"","expr":"r10128"}}]}},{"type":"C","name":"","rule":{"type":"B","name":"目标渠道为网站","expr":"r10028","negative":false},"next":{"type":"S","name":"","branches":[{"type":"A","name":"","rule":{"type":"R","name":"","expr":"r10127"}},{"type":"A","name":"","rule":{"type":"R","name":"","expr":"r10128"}}]}}],"defaultBranch":{"type":"A","name":"","rule":{"type":"R","name":"","expr":"r10134"}}}}',
     1, 0)
ON CONFLICT(id) DO UPDATE SET
    name=excluded.name, description=excluded.description, rule_tree=excluded.rule_tree,
    status=excluded.status, version=rule_flow.version + 1, updated_at=datetime('now','localtime')
WHERE rule_flow.name <> excluded.name OR rule_flow.description <> excluded.description
   OR rule_flow.rule_tree <> excluded.rule_tree OR rule_flow.status <> excluded.status;

-- ============================================================
-- 派生引用：与 RuleFlowService 发布流程时生成的两类引用表保持一致
-- ============================================================

INSERT OR IGNORE INTO flow_atomic_ref (flow_id, rule_id) VALUES
    (20001, 10011), (20001, 10013), (20001, 10012), (20001, 10116),

    (20002, 10012), (20002, 10001), (20002, 10004), (20002, 10015),
    (20002, 10016), (20002, 10017), (20002, 10018),
    (20002, 10019), (20002, 10120), (20002, 10121), (20002, 10102),
    (20002, 10103), (20002, 10122), (20002, 10123), (20002, 10135),
    (20002, 10023), (20002, 10124), (20002, 10128), (20002, 10021),
    (20002, 10130), (20002, 10022), (20002, 10131),
    (20002, 10002), (20002, 10003), (20002, 10129),
    (20002, 10109),

    (20003, 10011), (20003, 10001), (20003, 10014), (20003, 10015),
    (20003, 10016), (20003, 10017), (20003, 10018),
    (20003, 10019), (20003, 10120), (20003, 10121), (20003, 10110),
    (20003, 10135), (20003, 10128), (20003, 10021), (20003, 10130),
    (20003, 10022), (20003, 10131),
    (20003, 10002), (20003, 10003), (20003, 10129), (20003, 10109),

    (20004, 10013), (20004, 10001), (20004, 10009), (20004, 10005),
    (20004, 10015), (20004, 10016), (20004, 10017), (20004, 10024),
    (20004, 10018), (20004, 10019), (20004, 10136),
    (20004, 10111), (20004, 10120), (20004, 10121), (20004, 10102),
    (20004, 10122), (20004, 10123), (20004, 10022), (20004, 10131),
    (20004, 10128), (20004, 10002),
    (20004, 10003), (20004, 10129), (20004, 10109),

    (20005, 10104), (20005, 10105), (20005, 10106), (20005, 10021),
    (20005, 10130), (20005, 10022), (20005, 10131), (20005, 10007),
    (20005, 10005), (20005, 10006), (20005, 10008), (20005, 10019),
    (20005, 10020), (20005, 10025), (20005, 10010), (20005, 10015),
    (20005, 10108), (20005, 10128), (20005, 10134), (20005, 10107),
    (20005, 10109),

    (20006, 10101), (20006, 10117), (20006, 10118), (20006, 10119),
    (20006, 10018), (20006, 10019), (20006, 10128), (20006, 10002),
    (20006, 10003), (20006, 10129), (20006, 10109),

    (20007, 10026), (20007, 10125), (20007, 10128), (20007, 10027),
    (20007, 10126), (20007, 10028), (20007, 10127), (20007, 10134);

INSERT OR IGNORE INTO flow_flow_ref (flow_id, referenced_flow_id) VALUES
    (20001, 20002), (20001, 20003), (20001, 20004),
    (20002, 20005), (20002, 20006), (20002, 20007),
    (20003, 20005), (20003, 20006), (20003, 20007),
    (20004, 20005), (20004, 20006), (20004, 20007);

COMMIT;
