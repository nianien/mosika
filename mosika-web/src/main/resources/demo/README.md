# 内容生成领域演示数据

`content-generation.sql` 提供一套可以直接导入 `mosika-web` 的内容生成领域数据：

- 28 条条件规则：覆盖任务准入、素材可信度、证据覆盖、生成预算、模型配置、风险、原创性、活动和渠道策略；
- 36 条动作规则：覆盖检索、清洗、主张提取、引用绑定、上下文组装、模型选择、候选生成、渠道适配、审核和发布；
- 7 条已生效规则流：智能路由、三类内容生产、发布前质量门禁、素材证据链和多渠道适配。

演示数据使用 `10001~10136` 的原子规则 ID 和 `20001~20007` 的规则流 ID。原子规则使用稳定 ID 且不会覆盖同 ID 的已有定义；演示规则流由本文件维护，重复导入会升级流程树并重建引用。

复杂度主要来自递归层级，不来自同级节点堆叠。三条主生产流都达到 11 层 UI AST 深度，并把准入规则拆成嵌套规则子树；流程侧继续沿“准入判断 → 证据决策 → 命中流程 → 串/并行生成子树 → 风险决策 → 命中流程”向下递归。任一结构仍是可独立替换的 `FlowNode` 或 `RNode` 子树。

| 规则流 | AST 深度 | 最大同级宽度 | 引用规则 |
|---|---:|---:|---:|
| 标准深度文章生成 | 11 | 5 | 29 |
| 突发快讯生成 | 11 | 3 | 24 |
| 营销文案生成 | 11 | 5 | 27 |
| 发布前质量门禁 | 8 | 4 | 21 |

深度文章包含嵌套 SEO 与风险决策，突发快讯在证据决策下继续展开双路并行生成，营销文案在证据命中分支内再展开品牌/上下文并行准备和高影响审核。三者共同复用素材证据链、多渠道适配和质量门禁。

## 导入

在仓库根目录执行：

```bash
./scripts/mosika.sh stop
sqlite3 mosika-web/data/mosika.db ".read mosika-web/src/main/resources/demo/content-generation.sql"
./scripts/mosika.sh start
```

仓库已提供包含该演示场景的 `mosika-web/data/mosika.db`，克隆后可直接启动体验；上述命令用于重建或刷新演示数据。SQLite 运行时产生的 WAL、SHM 和 journal 文件不纳入版本管理。

## 执行示例

下面的请求会由 `20001` 路由到标准深度文章生成流程，并继续调用统一的发布前质量门禁：

```bash
curl -sS -X POST http://127.0.0.1:8080/api/eval/flow/20001 \
  -H 'Content-Type: application/json' \
  -d '{
    "target": {
      "topic": "生成式 AI 如何改变知识生产",
      "contentType": "ARTICLE",
      "channel": "WECHAT",
      "sourceCount": 6,
      "verifiedSourceCount": 5,
      "topicRelevance": 0.92,
      "copyrightRisk": 0.05,
      "sensitiveRisk": 0.08,
      "factConfidence": 0.94,
      "qualityScore": 0.88,
      "brandToneReady": true,
      "requiresHumanReview": false,
      "eventAgeMinutes": 10,
      "promptTemplateVersion": "content-v3",
      "modelPolicy": "QUALITY_BALANCED",
      "remainingTokenBudget": 50000,
      "estimatedTokenCost": 12000,
      "sourceFreshnessHours": 4,
      "maxSourceAgeHours": 72,
      "claimCount": 10,
      "citedClaimCount": 10,
      "originalityScore": 0.91,
      "regulatedTopic": false,
      "exposureLevel": "NORMAL",
      "seoRequired": true,
      "campaignActive": true,
      "campaignBudgetRemaining": 10000,
      "autoPublishEnabled": true
    },
    "context": {
      "tenant": "demo",
      "operator": "content-platform"
    }
  }'
```

将 `contentType` 改为 `NEWS_FLASH` 或 `MARKETING`，可以分别验证快讯和营销文案分支；将 `regulatedTopic` 设为 `true`、将 `exposureLevel` 设为 `HIGH`，或者降低证据覆盖与质量分，可以观察不同审核路径。
