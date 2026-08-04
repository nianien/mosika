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
-- 10001~10136 和 20001~20007 是本演示固定占用的高位 ID。
-- 原子规则只在不存在时插入；规则流由本文件维护，重复导入时升级树结构并重建引用。

BEGIN TRANSACTION;

-- ============================================================
-- 条件规则：稳定业务语义的最小判断单元
-- ============================================================

INSERT OR IGNORE INTO rule_definition
    (id, name, description, expression, use_type, rule_kind, status, version)
VALUES
    (10001, '创作请求完整', '创作任务具备主题、内容类型和发布渠道', '$.topic != null && $.contentType != null && $.channel != null', 0, 'condition', 1, 0),
    (10002, '素材数量达标', '标准内容生成至少需要三份素材', '$.sourceCount >= 3', 0, 'condition', 1, 0),
    (10003, '可信素材占比达标', '至少两份可信素材且可信素材占比不低于百分之六十', '$.sourceCount > 0 && $.verifiedSourceCount >= 2 && $.verifiedSourceCount / $.sourceCount >= 0.6', 0, 'condition', 1, 0),
    (10004, '主题相关性达标', '素材与创作主题的相关性不低于零点七五', '$.topicRelevance >= 0.75', 0, 'condition', 1, 0),
    (10005, '版权风险可接受', '版权风险评分不高于零点二', '$.copyrightRisk <= 0.2', 0, 'condition', 1, 0),
    (10006, '敏感风险可接受', '敏感内容风险评分不高于零点三', '$.sensitiveRisk <= 0.3', 0, 'condition', 1, 0),
    (10007, '事实置信度达标', '关键事实核验置信度不低于零点八五', '$.factConfidence >= 0.85', 0, 'condition', 1, 0),
    (10008, '内容质量达标', '完整性、连贯性和可读性综合分不低于零点八', '$.qualityScore >= 0.8', 0, 'condition', 1, 0),
    (10009, '品牌语调已配置', '营销内容已经配置目标品牌的语调与禁用词', '$.brandToneReady == true', 0, 'condition', 1, 0),
    (10010, '需要人工复核', '上游指定人工复核或任一关键风险指标未达自动发布要求', '$.requiresHumanReview == true || $.sensitiveRisk > 0.3 || $.factConfidence < 0.85 || $.qualityScore < 0.8', 0, 'condition', 1, 0),
    (10011, '命中快讯时效窗口', '内容类型为快讯且事件发生不超过三十分钟', '$.contentType == "NEWS_FLASH" && $.eventAgeMinutes <= 30', 0, 'condition', 1, 0),
    (10012, '命中深度文章模式', '内容类型为标准深度文章', '$.contentType == "ARTICLE"', 0, 'condition', 1, 0),
    (10013, '命中营销文案模式', '内容类型为营销文案', '$.contentType == "MARKETING"', 0, 'condition', 1, 0),
    (10014, '快讯双源确认', '突发快讯至少拥有两份已验证的独立信源', '$.verifiedSourceCount >= 2', 0, 'condition', 1, 0),
    (10015, '发布渠道受支持', '目标渠道属于当前参考实现支持的发布集合', '["WEB", "APP", "WECHAT"].includes($.channel)', 0, 'condition', 1, 0),
    (10016, '生成配置就绪', '提示词模板和模型策略均已配置', '$.promptTemplateVersion != null && $.modelPolicy != null', 0, 'condition', 1, 0),
    (10017, '生成预算充足', '剩余令牌预算能够覆盖本次预估消耗', '$.remainingTokenBudget >= $.estimatedTokenCost', 0, 'condition', 1, 0),
    (10018, '素材时效达标', '素材更新时间未超过领域允许的最大时效', '$.sourceFreshnessHours <= $.maxSourceAgeHours', 0, 'condition', 1, 0),
    (10019, '证据覆盖达标', '有出处的核心主张占比不低于百分之九十', '$.claimCount > 0 && $.citedClaimCount / $.claimCount >= 0.9', 0, 'condition', 1, 0),
    (10020, '原创性达标', '内容原创性评分不低于零点八', '$.originalityScore >= 0.8', 0, 'condition', 1, 0),
    (10021, '命中监管敏感主题', '内容涉及医疗、金融、法律等强监管主题', '$.regulatedTopic == true', 0, 'condition', 1, 0),
    (10022, '命中高影响发布', '预计曝光量或业务等级要求升级审核', '$.exposureLevel == "HIGH"', 0, 'condition', 1, 0),
    (10023, '需要搜索优化', '目标内容需要执行搜索引擎结构优化', '$.seoRequired == true', 0, 'condition', 1, 0),
    (10024, '营销活动有效', '营销活动有效且仍有可用预算', '$.campaignActive == true && $.campaignBudgetRemaining > 0', 0, 'condition', 1, 0),
    (10025, '允许自动发布', '租户与渠道策略允许质量门禁自动发布', '$.autoPublishEnabled == true', 0, 'condition', 1, 0),
    (10026, '目标渠道为微信', '目标发布渠道为微信公众号', '$.channel == "WECHAT"', 0, 'condition', 1, 0),
    (10027, '目标渠道为客户端', '目标发布渠道为移动客户端', '$.channel == "APP"', 0, 'condition', 1, 0),
    (10028, '目标渠道为网站', '目标发布渠道为 Web 站点', '$.channel == "WEB"', 0, 'condition', 1, 0);

-- ============================================================
-- 动作规则：真实生成流水线中的可替换执行能力
-- ============================================================

INSERT OR IGNORE INTO rule_definition
    (id, name, description, expression, use_type, rule_kind, status, version)
VALUES
    (10101, '素材清洗与归一化', '去重、清洗并统一素材结构', '{stage:"MATERIAL_NORMALIZATION",status:"completed",sourceCount:$.sourceCount}', 0, 'action', 1, 0),
    (10102, '生成内容提纲', '根据主题和素材生成分层内容提纲', '{stage:"OUTLINE_GENERATION",status:"completed",topic:$.topic}', 0, 'action', 1, 0),
    (10103, '生成正文草稿', '依据提纲生成完整正文初稿', '{stage:"DRAFT_GENERATION",status:"completed",contentType:$.contentType}', 0, 'action', 1, 0),
    (10104, '执行事实核验', '抽取关键主张并核对可信信源', '{stage:"FACT_CHECK",status:"completed",confidence:$.factConfidence}', 0, 'action', 1, 0),
    (10105, '执行版权与合规扫描', '识别版权、敏感内容和平台规范风险', '{stage:"COMPLIANCE_SCAN",status:"completed",risk:Math.max($.copyrightRisk,$.sensitiveRisk)}', 0, 'action', 1, 0),
    (10106, '执行内容质量评分', '评估完整性、连贯性、可读性和信息密度', '{stage:"QUALITY_SCORING",status:"completed",score:$.qualityScore}', 0, 'action', 1, 0),
    (10107, '提交人工复核', '将高风险或低置信内容送入人工审核队列', '{stage:"HUMAN_REVIEW",status:"review_required"}', 0, 'action', 1, 0),
    (10108, '发布内容', '将通过质量门禁的内容发布到目标渠道', '{stage:"PUBLISH",status:"published",channel:$.channel}', 0, 'action', 1, 0),
    (10109, '驳回生成任务', '终止未通过准入或质量门禁的生成任务', '{stage:"REJECT",status:"rejected",reason:"generation_gate_failed"}', 0, 'action', 1, 0),
    (10110, '生成快讯草稿', '基于已确认信源生成短格式突发快讯', '{stage:"FLASH_DRAFT",status:"completed",eventAgeMinutes:$.eventAgeMinutes}', 0, 'action', 1, 0),
    (10111, '应用品牌语调', '依据品牌词典、风格和禁用词重写营销文案', '{stage:"BRAND_TONE_REWRITE",status:"completed"}', 0, 'action', 1, 0),
    (10112, '调用深度文章生成流程', '将任务路由到标准深度文章生成流程', 'sys.flow.eval("20002",$,$$), {stage:"ROUTE",status:"article_completed"}', 0, 'action', 1, 0),
    (10113, '调用突发快讯生成流程', '将任务路由到突发快讯生成流程', 'sys.flow.eval("20003",$,$$), {stage:"ROUTE",status:"flash_completed"}', 0, 'action', 1, 0),
    (10114, '调用营销文案生成流程', '将任务路由到营销文案生成流程', 'sys.flow.eval("20004",$,$$), {stage:"ROUTE",status:"marketing_completed"}', 0, 'action', 1, 0),
    (10115, '调用发布前质量门禁', '复用统一的事实、合规和质量门禁子流程', 'sys.flow.eval("20005",$,$$), {stage:"QUALITY_GATE",status:"completed"}', 0, 'action', 1, 0),
    (10116, '拒绝不支持的内容类型', '内容类型无法路由时终止任务并返回明确状态', '{stage:"ROUTING",status:"rejected",reason:"unsupported_content_type"}', 0, 'action', 1, 0),
    (10117, '检索补充素材', '从可信知识源召回与主题相关的补充素材', '{stage:"SOURCE_RETRIEVAL",status:"completed",topic:$.topic}', 0, 'action', 1, 0),
    (10118, '提取核心主张', '从素材中提取需要验证的事实主张与关键实体', '{stage:"CLAIM_EXTRACTION",status:"completed",claimCount:$.claimCount}', 0, 'action', 1, 0),
    (10119, '绑定引用证据', '为核心主张绑定可追溯的可信素材引用', '{stage:"CITATION_BINDING",status:"completed",citedClaimCount:$.citedClaimCount}', 0, 'action', 1, 0),
    (10120, '组装生成上下文', '按模型上下文窗口组织素材、约束和产品指令', '{stage:"CONTEXT_ASSEMBLY",status:"completed",template:$.promptTemplateVersion}', 0, 'action', 1, 0),
    (10121, '选择模型与参数', '根据内容类型、成本和质量要求选择模型策略', '{stage:"MODEL_SELECTION",status:"completed",policy:$.modelPolicy}', 0, 'action', 1, 0),
    (10122, '生成多版本候选', '生成不同结构和表达风格的候选内容', '{stage:"CANDIDATE_GENERATION",status:"completed",candidateCount:3}', 0, 'action', 1, 0),
    (10123, '优选内容候选', '根据事实、质量和风格评分选择最佳候选', '{stage:"CANDIDATE_RANKING",status:"completed"}', 0, 'action', 1, 0),
    (10124, '执行搜索结构优化', '优化标题层级、摘要、关键词和内容结构', '{stage:"SEO_OPTIMIZATION",status:"completed"}', 0, 'action', 1, 0),
    (10125, '适配微信渠道', '生成适合公众号阅读的段落、摘要和封面信息', '{stage:"CHANNEL_ADAPTATION",status:"completed",channel:"WECHAT"}', 0, 'action', 1, 0),
    (10126, '适配客户端渠道', '生成适合移动端信息流的标题、卡片和正文', '{stage:"CHANNEL_ADAPTATION",status:"completed",channel:"APP"}', 0, 'action', 1, 0),
    (10127, '适配网站渠道', '生成适合 Web 站点的正文结构和元数据', '{stage:"CHANNEL_ADAPTATION",status:"completed",channel:"WEB"}', 0, 'action', 1, 0),
    (10128, '记录生成审计', '记录模型、提示词、证据链和规则流版本', '{stage:"GENERATION_AUDIT",status:"recorded"}', 0, 'action', 1, 0),
    (10129, '触发素材补全', '证据覆盖不足但具备生成基础时进入素材补全队列', '{stage:"SOURCE_ENRICHMENT",status:"required"}', 0, 'action', 1, 0),
    (10130, '提交敏感主题专审', '将强监管主题提交给对应领域审核人员', '{stage:"REGULATED_REVIEW",status:"review_required"}', 0, 'action', 1, 0),
    (10131, '提交高影响发布审核', '将高曝光内容提交给高级审核队列', '{stage:"HIGH_IMPACT_REVIEW",status:"review_required"}', 0, 'action', 1, 0),
    (10132, '调用素材证据链构建流程', '复用素材检索、主张提取和引用绑定子流程', 'sys.flow.eval("20006",$,$$), {stage:"EVIDENCE_PIPELINE",status:"completed"}', 0, 'action', 1, 0),
    (10133, '调用多渠道适配流程', '复用微信、客户端和网站渠道适配子流程', 'sys.flow.eval("20007",$,$$), {stage:"CHANNEL_PIPELINE",status:"completed"}', 0, 'action', 1, 0),
    (10134, '拒绝不支持的发布渠道', '渠道适配无法识别目标渠道时终止任务', '{stage:"CHANNEL_ADAPTATION",status:"rejected",reason:"unsupported_channel"}', 0, 'action', 1, 0),
    (10135, '生成标题与摘要', '并行生成快讯标题、导语和一句话摘要', '{stage:"TITLE_AND_SUMMARY",status:"completed"}', 0, 'action', 1, 0),
    (10136, '召回品牌知识', '召回品牌术语、产品卖点、风格指南和禁用词', '{stage:"BRAND_KNOWLEDGE",status:"completed"}', 0, 'action', 1, 0);

-- ============================================================
-- 规则流：面向产品和业务编排稳定的原子规则
-- ============================================================

DELETE FROM flow_rule_ref WHERE flow_id BETWEEN 20001 AND 20007;

-- 20001 内容生成任务智能路由：有序识别快讯、营销文案和深度文章。
INSERT INTO rule_flow (id, name, description, rule_tree, status, version)
VALUES
    (20001, '内容生成任务智能路由', '根据内容类型与时效要求，把创作任务路由到快讯、营销文案或深度文章生成流程',
     '{"type":"T","expr":"","next":{"type":"D","expr":"D","branches":[{"type":"J","expr":"J","rule":{"type":"R","expr":"10011","name":"命中快讯时效窗口","negative":false},"action":{"type":"A","expr":"10113"}},{"type":"J","expr":"J","rule":{"type":"R","expr":"10013","name":"命中营销文案模式","negative":false},"action":{"type":"A","expr":"10114"}},{"type":"J","expr":"J","rule":{"type":"R","expr":"10012","name":"命中深度文章模式","negative":false},"action":{"type":"A","expr":"10112"}}],"action":{"type":"A","expr":"10116"}}}',
     1, 0)
ON CONFLICT(id) DO UPDATE SET
    name=excluded.name, description=excluded.description, rule_tree=excluded.rule_tree,
    status=excluded.status, version=rule_flow.version + 1, updated_at=datetime('now','localtime')
WHERE rule_flow.name <> excluded.name OR rule_flow.description <> excluded.description
   OR rule_flow.rule_tree <> excluded.rule_tree OR rule_flow.status <> excluded.status;

-- 20002 标准深度文章生成：证据链、上下文、模型、候选生成、SEO、渠道适配和质量门禁串联。
INSERT INTO rule_flow (id, name, description, rule_tree, status, version)
VALUES
    (20002, '标准深度文章生成', '以深层递归树表达准入、证据决策、生成子树、SEO 和发布风险决策的完整内容生产链路',
     '{"type":"T","expr":"","next":{"type":"D","expr":"D","branches":[{"type":"J","expr":"J","rule":{"type":"L","expr":"&&","name":"深度文章生产准入","negative":false,"rules":[{"type":"L","expr":"&&","name":"业务准入","negative":false,"rules":[{"type":"L","expr":"&&","name":"内容模式与请求","negative":false,"rules":[{"type":"R","expr":"10012","name":"命中深度文章模式","negative":false},{"type":"R","expr":"10001","name":"创作请求完整","negative":false}]},{"type":"L","expr":"&&","name":"主题与渠道","negative":false,"rules":[{"type":"R","expr":"10004","name":"主题相关性达标","negative":false},{"type":"R","expr":"10015","name":"发布渠道受支持","negative":false}]}]},{"type":"L","expr":"&&","name":"运行资源准入","negative":false,"rules":[{"type":"R","expr":"10016","name":"生成配置就绪","negative":false},{"type":"R","expr":"10017","name":"生成预算充足","negative":false}]}]},"action":{"type":"S","expr":"S","branches":[{"type":"A","expr":"10132"},{"type":"D","expr":"D","branches":[{"type":"J","expr":"J","rule":{"type":"L","expr":"&&","name":"证据链完整","negative":false,"rules":[{"type":"R","expr":"10018","name":"素材时效达标","negative":false},{"type":"R","expr":"10019","name":"证据覆盖达标","negative":false}]},"action":{"type":"S","expr":"S","branches":[{"type":"A","expr":"10120"},{"type":"A","expr":"10121"},{"type":"P","expr":"P","branches":[{"type":"S","expr":"S","branches":[{"type":"A","expr":"10102"},{"type":"A","expr":"10103"},{"type":"A","expr":"10122"},{"type":"A","expr":"10123"}]},{"type":"A","expr":"10135"}]},{"type":"J","expr":"J","rule":{"type":"R","expr":"10023","name":"需要搜索优化","negative":false},"action":{"type":"S","expr":"S","branches":[{"type":"A","expr":"10124"},{"type":"A","expr":"10128"}]}},{"type":"D","expr":"D","branches":[{"type":"J","expr":"J","rule":{"type":"R","expr":"10021","name":"命中监管敏感主题","negative":false},"action":{"type":"S","expr":"S","branches":[{"type":"A","expr":"10130"},{"type":"A","expr":"10128"}]}},{"type":"J","expr":"J","rule":{"type":"R","expr":"10022","name":"命中高影响发布","negative":false},"action":{"type":"S","expr":"S","branches":[{"type":"A","expr":"10131"},{"type":"A","expr":"10128"}]}}],"action":{"type":"S","expr":"S","branches":[{"type":"A","expr":"10133"},{"type":"A","expr":"10115"}]}}]}},{"type":"J","expr":"J","rule":{"type":"L","expr":"&&","name":"具备素材补全基础","negative":false,"rules":[{"type":"R","expr":"10002","name":"素材数量达标","negative":false},{"type":"R","expr":"10003","name":"可信素材占比达标","negative":false}]},"action":{"type":"S","expr":"S","branches":[{"type":"A","expr":"10129"},{"type":"A","expr":"10128"}]}}],"action":{"type":"A","expr":"10109"}}]}}],"action":{"type":"A","expr":"10109"}}}',
     1, 0)
ON CONFLICT(id) DO UPDATE SET
    name=excluded.name, description=excluded.description, rule_tree=excluded.rule_tree,
    status=excluded.status, version=rule_flow.version + 1, updated_at=datetime('now','localtime')
WHERE rule_flow.name <> excluded.name OR rule_flow.description <> excluded.description
   OR rule_flow.rule_tree <> excluded.rule_tree OR rule_flow.status <> excluded.status;

-- 20003 突发快讯生成：证据链完成后并行生成正文与标题摘要。
INSERT INTO rule_flow (id, name, description, rule_tree, status, version)
VALUES
    (20003, '突发快讯生成', '以深层递归树表达时效准入、证据决策、双路并行生成和分级发布风险处理',
     '{"type":"T","expr":"","next":{"type":"D","expr":"D","branches":[{"type":"J","expr":"J","rule":{"type":"L","expr":"&&","name":"突发快讯生产准入","negative":false,"rules":[{"type":"L","expr":"&&","name":"业务准入","negative":false,"rules":[{"type":"L","expr":"&&","name":"时效与请求","negative":false,"rules":[{"type":"R","expr":"10011","name":"命中快讯时效窗口","negative":false},{"type":"R","expr":"10001","name":"创作请求完整","negative":false}]},{"type":"L","expr":"&&","name":"信源与渠道","negative":false,"rules":[{"type":"R","expr":"10014","name":"快讯双源确认","negative":false},{"type":"R","expr":"10015","name":"发布渠道受支持","negative":false}]}]},{"type":"L","expr":"&&","name":"运行资源准入","negative":false,"rules":[{"type":"R","expr":"10016","name":"生成配置就绪","negative":false},{"type":"R","expr":"10017","name":"生成预算充足","negative":false}]}]},"action":{"type":"S","expr":"S","branches":[{"type":"A","expr":"10132"},{"type":"D","expr":"D","branches":[{"type":"J","expr":"J","rule":{"type":"L","expr":"&&","name":"快讯证据链完整","negative":false,"rules":[{"type":"R","expr":"10018","name":"素材时效达标","negative":false},{"type":"R","expr":"10019","name":"证据覆盖达标","negative":false}]},"action":{"type":"S","expr":"S","branches":[{"type":"P","expr":"P","branches":[{"type":"S","expr":"S","branches":[{"type":"A","expr":"10120"},{"type":"A","expr":"10121"},{"type":"A","expr":"10110"}]},{"type":"S","expr":"S","branches":[{"type":"A","expr":"10135"},{"type":"A","expr":"10128"}]}]},{"type":"D","expr":"D","branches":[{"type":"J","expr":"J","rule":{"type":"R","expr":"10021","name":"命中监管敏感主题","negative":false},"action":{"type":"S","expr":"S","branches":[{"type":"A","expr":"10130"},{"type":"A","expr":"10128"}]}},{"type":"J","expr":"J","rule":{"type":"R","expr":"10022","name":"命中高影响发布","negative":false},"action":{"type":"S","expr":"S","branches":[{"type":"A","expr":"10131"},{"type":"A","expr":"10128"}]}}],"action":{"type":"S","expr":"S","branches":[{"type":"A","expr":"10133"},{"type":"A","expr":"10115"}]}}]}},{"type":"J","expr":"J","rule":{"type":"L","expr":"&&","name":"具备素材补全基础","negative":false,"rules":[{"type":"R","expr":"10002","name":"素材数量达标","negative":false},{"type":"R","expr":"10003","name":"可信素材占比达标","negative":false}]},"action":{"type":"S","expr":"S","branches":[{"type":"A","expr":"10129"},{"type":"A","expr":"10128"}]}}],"action":{"type":"A","expr":"10109"}}]}}],"action":{"type":"A","expr":"10109"}}}',
     1, 0)
ON CONFLICT(id) DO UPDATE SET
    name=excluded.name, description=excluded.description, rule_tree=excluded.rule_tree,
    status=excluded.status, version=rule_flow.version + 1, updated_at=datetime('now','localtime')
WHERE rule_flow.name <> excluded.name OR rule_flow.description <> excluded.description
   OR rule_flow.rule_tree <> excluded.rule_tree OR rule_flow.status <> excluded.status;

-- 20004 营销文案生成：活动准入、品牌知识、候选优选、渠道适配和高影响审核完整串联。
INSERT INTO rule_flow (id, name, description, rule_tree, status, version)
VALUES
    (20004, '营销文案生成', '以深层递归树表达活动与品牌准入、证据分流、双路准备、候选优选和高影响审核',
     '{"type":"T","expr":"","next":{"type":"D","expr":"D","branches":[{"type":"J","expr":"J","rule":{"type":"L","expr":"&&","name":"营销文案生产准入","negative":false,"rules":[{"type":"L","expr":"&&","name":"业务准入","negative":false,"rules":[{"type":"L","expr":"&&","name":"内容与活动","negative":false,"rules":[{"type":"R","expr":"10013","name":"命中营销文案模式","negative":false},{"type":"R","expr":"10024","name":"营销活动有效","negative":false}]},{"type":"L","expr":"&&","name":"品牌与版权","negative":false,"rules":[{"type":"R","expr":"10009","name":"品牌语调已配置","negative":false},{"type":"R","expr":"10005","name":"版权风险可接受","negative":false}]}]},{"type":"L","expr":"&&","name":"运行准入","negative":false,"rules":[{"type":"L","expr":"&&","name":"请求与渠道","negative":false,"rules":[{"type":"R","expr":"10001","name":"创作请求完整","negative":false},{"type":"R","expr":"10015","name":"发布渠道受支持","negative":false}]},{"type":"L","expr":"&&","name":"配置与预算","negative":false,"rules":[{"type":"R","expr":"10016","name":"生成配置就绪","negative":false},{"type":"R","expr":"10017","name":"生成预算充足","negative":false}]}]}]},"action":{"type":"S","expr":"S","branches":[{"type":"A","expr":"10132"},{"type":"D","expr":"D","branches":[{"type":"J","expr":"J","rule":{"type":"L","expr":"&&","name":"营销证据链完整","negative":false,"rules":[{"type":"R","expr":"10018","name":"素材时效达标","negative":false},{"type":"R","expr":"10019","name":"证据覆盖达标","negative":false}]},"action":{"type":"S","expr":"S","branches":[{"type":"P","expr":"P","branches":[{"type":"S","expr":"S","branches":[{"type":"A","expr":"10136"},{"type":"A","expr":"10111"}]},{"type":"S","expr":"S","branches":[{"type":"A","expr":"10120"},{"type":"A","expr":"10121"}]}]},{"type":"A","expr":"10102"},{"type":"A","expr":"10122"},{"type":"A","expr":"10123"},{"type":"D","expr":"D","branches":[{"type":"J","expr":"J","rule":{"type":"R","expr":"10022","name":"命中高影响发布","negative":false},"action":{"type":"S","expr":"S","branches":[{"type":"A","expr":"10131"},{"type":"A","expr":"10128"}]}}],"action":{"type":"S","expr":"S","branches":[{"type":"A","expr":"10133"},{"type":"A","expr":"10115"}]}}]}},{"type":"J","expr":"J","rule":{"type":"L","expr":"&&","name":"具备素材补全基础","negative":false,"rules":[{"type":"R","expr":"10002","name":"素材数量达标","negative":false},{"type":"R","expr":"10003","name":"可信素材占比达标","negative":false}]},"action":{"type":"S","expr":"S","branches":[{"type":"A","expr":"10129"},{"type":"A","expr":"10128"}]}}],"action":{"type":"A","expr":"10109"}}]}}],"action":{"type":"A","expr":"10109"}}}',
     1, 0)
ON CONFLICT(id) DO UPDATE SET
    name=excluded.name, description=excluded.description, rule_tree=excluded.rule_tree,
    status=excluded.status, version=rule_flow.version + 1, updated_at=datetime('now','localtime')
WHERE rule_flow.name <> excluded.name OR rule_flow.description <> excluded.description
   OR rule_flow.rule_tree <> excluded.rule_tree OR rule_flow.status <> excluded.status;

-- 20005 发布前质量门禁：监管、高影响、自动发布、普通复核和驳回有序决策。
INSERT INTO rule_flow (id, name, description, rule_tree, status, version)
VALUES
    (20005, '发布前质量门禁', '以深层递归树表达并行检测、分级审核、嵌套自动发布资格和发布后审计',
     '{"type":"T","expr":"","next":{"type":"S","expr":"S","branches":[{"type":"P","expr":"P","branches":[{"type":"A","expr":"10104"},{"type":"A","expr":"10105"},{"type":"A","expr":"10106"}]},{"type":"D","expr":"D","branches":[{"type":"J","expr":"J","rule":{"type":"R","expr":"10021","name":"命中监管敏感主题","negative":false},"action":{"type":"S","expr":"S","branches":[{"type":"A","expr":"10130"},{"type":"A","expr":"10128"}]}},{"type":"J","expr":"J","rule":{"type":"R","expr":"10022","name":"命中高影响发布","negative":false},"action":{"type":"S","expr":"S","branches":[{"type":"A","expr":"10131"},{"type":"A","expr":"10128"}]}},{"type":"J","expr":"J","rule":{"type":"L","expr":"&&","name":"自动发布门禁","negative":false,"rules":[{"type":"L","expr":"&&","name":"内容可信","negative":false,"rules":[{"type":"L","expr":"&&","name":"事实与证据","negative":false,"rules":[{"type":"R","expr":"10007","name":"事实置信度达标","negative":false},{"type":"R","expr":"10019","name":"证据覆盖达标","negative":false}]},{"type":"L","expr":"&&","name":"版权与敏感","negative":false,"rules":[{"type":"R","expr":"10005","name":"版权风险可接受","negative":false},{"type":"R","expr":"10006","name":"敏感风险可接受","negative":false}]}]},{"type":"L","expr":"&&","name":"发布资格","negative":false,"rules":[{"type":"L","expr":"&&","name":"质量与原创","negative":false,"rules":[{"type":"R","expr":"10008","name":"内容质量达标","negative":false},{"type":"R","expr":"10020","name":"原创性达标","negative":false}]},{"type":"L","expr":"&&","name":"自动发布策略","negative":false,"rules":[{"type":"R","expr":"10025","name":"允许自动发布","negative":false},{"type":"R","expr":"10010","name":"无需人工复核","negative":true}]}]}]},"action":{"type":"D","expr":"D","branches":[{"type":"J","expr":"J","rule":{"type":"R","expr":"10015","name":"发布渠道受支持","negative":false},"action":{"type":"S","expr":"S","branches":[{"type":"A","expr":"10108"},{"type":"A","expr":"10128"}]}}],"action":{"type":"A","expr":"10134"}}},{"type":"J","expr":"J","rule":{"type":"L","expr":"||","name":"普通人工复核门禁","negative":false,"rules":[{"type":"R","expr":"10010","name":"需要人工复核","negative":false},{"type":"L","expr":"||","name":"可信度不足","negative":false,"rules":[{"type":"R","expr":"10019","name":"证据覆盖不足","negative":true},{"type":"R","expr":"10020","name":"原创性不足","negative":true}]}]},"action":{"type":"S","expr":"S","branches":[{"type":"A","expr":"10107"},{"type":"A","expr":"10128"}]}}],"action":{"type":"S","expr":"S","branches":[{"type":"A","expr":"10109"},{"type":"A","expr":"10128"}]}}]}}',
     1, 0)
ON CONFLICT(id) DO UPDATE SET
    name=excluded.name, description=excluded.description, rule_tree=excluded.rule_tree,
    status=excluded.status, version=rule_flow.version + 1, updated_at=datetime('now','localtime')
WHERE rule_flow.name <> excluded.name OR rule_flow.description <> excluded.description
   OR rule_flow.rule_tree <> excluded.rule_tree OR rule_flow.status <> excluded.status;

-- 20006 素材证据链构建：清洗、检索、主张提取、引用绑定和证据质量分流。
INSERT INTO rule_flow (id, name, description, rule_tree, status, version)
VALUES
    (20006, '素材证据链构建', '完成素材清洗与检索、核心主张提取、引用绑定，并将证据充分、待补全和不可生成任务分流',
     '{"type":"T","expr":"","next":{"type":"S","expr":"S","branches":[{"type":"P","expr":"P","branches":[{"type":"A","expr":"10101"},{"type":"A","expr":"10117"}]},{"type":"A","expr":"10118"},{"type":"A","expr":"10119"},{"type":"D","expr":"D","branches":[{"type":"J","expr":"J","rule":{"type":"L","expr":"&&","name":"证据链完整","negative":false,"rules":[{"type":"R","expr":"10018","name":"素材时效达标","negative":false},{"type":"R","expr":"10019","name":"证据覆盖达标","negative":false}]},"action":{"type":"A","expr":"10128"}},{"type":"J","expr":"J","rule":{"type":"L","expr":"&&","name":"具备素材补全基础","negative":false,"rules":[{"type":"R","expr":"10002","name":"素材数量达标","negative":false},{"type":"R","expr":"10003","name":"可信素材占比达标","negative":false}]},"action":{"type":"A","expr":"10129"}}],"action":{"type":"A","expr":"10109"}}]}}',
     1, 0)
ON CONFLICT(id) DO UPDATE SET
    name=excluded.name, description=excluded.description, rule_tree=excluded.rule_tree,
    status=excluded.status, version=rule_flow.version + 1, updated_at=datetime('now','localtime')
WHERE rule_flow.name <> excluded.name OR rule_flow.description <> excluded.description
   OR rule_flow.rule_tree <> excluded.rule_tree OR rule_flow.status <> excluded.status;

-- 20007 多渠道内容适配：按渠道执行专属转换，并统一记录生成审计。
INSERT INTO rule_flow (id, name, description, rule_tree, status, version)
VALUES
    (20007, '多渠道内容适配', '按微信、客户端和 Web 三类渠道执行不同内容投影，并记录可追溯的生成审计',
     '{"type":"T","expr":"","next":{"type":"D","expr":"D","branches":[{"type":"J","expr":"J","rule":{"type":"R","expr":"10026","name":"目标渠道为微信","negative":false},"action":{"type":"S","expr":"S","branches":[{"type":"A","expr":"10125"},{"type":"A","expr":"10128"}]}},{"type":"J","expr":"J","rule":{"type":"R","expr":"10027","name":"目标渠道为客户端","negative":false},"action":{"type":"S","expr":"S","branches":[{"type":"A","expr":"10126"},{"type":"A","expr":"10128"}]}},{"type":"J","expr":"J","rule":{"type":"R","expr":"10028","name":"目标渠道为网站","negative":false},"action":{"type":"S","expr":"S","branches":[{"type":"A","expr":"10127"},{"type":"A","expr":"10128"}]}}],"action":{"type":"A","expr":"10134"}}}',
     1, 0)
ON CONFLICT(id) DO UPDATE SET
    name=excluded.name, description=excluded.description, rule_tree=excluded.rule_tree,
    status=excluded.status, version=rule_flow.version + 1, updated_at=datetime('now','localtime')
WHERE rule_flow.name <> excluded.name OR rule_flow.description <> excluded.description
   OR rule_flow.rule_tree <> excluded.rule_tree OR rule_flow.status <> excluded.status;

-- ============================================================
-- 派生引用：与 RuleFlowService 保存流程时生成的 flow_rule_ref 保持一致
-- ============================================================

INSERT OR IGNORE INTO flow_rule_ref (flow_id, rule_id) VALUES
    (20001, 10011), (20001, 10113), (20001, 10013), (20001, 10114),
    (20001, 10012), (20001, 10112), (20001, 10116),

    (20002, 10012), (20002, 10001), (20002, 10004), (20002, 10015),
    (20002, 10016), (20002, 10017), (20002, 10132), (20002, 10018),
    (20002, 10019), (20002, 10120), (20002, 10121), (20002, 10102),
    (20002, 10103), (20002, 10122), (20002, 10123), (20002, 10135),
    (20002, 10023), (20002, 10124), (20002, 10128), (20002, 10021),
    (20002, 10130), (20002, 10022), (20002, 10131), (20002, 10133),
    (20002, 10115), (20002, 10002), (20002, 10003), (20002, 10129),
    (20002, 10109),

    (20003, 10011), (20003, 10001), (20003, 10014), (20003, 10015),
    (20003, 10016), (20003, 10017), (20003, 10132), (20003, 10018),
    (20003, 10019), (20003, 10120), (20003, 10121), (20003, 10110),
    (20003, 10135), (20003, 10128), (20003, 10021), (20003, 10130),
    (20003, 10022), (20003, 10131), (20003, 10133), (20003, 10115),
    (20003, 10002), (20003, 10003), (20003, 10129), (20003, 10109),

    (20004, 10013), (20004, 10001), (20004, 10009), (20004, 10005),
    (20004, 10015), (20004, 10016), (20004, 10017), (20004, 10024),
    (20004, 10132), (20004, 10018), (20004, 10019), (20004, 10136),
    (20004, 10111), (20004, 10120), (20004, 10121), (20004, 10102),
    (20004, 10122), (20004, 10123), (20004, 10022), (20004, 10131),
    (20004, 10128), (20004, 10133), (20004, 10115), (20004, 10002),
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

COMMIT;
