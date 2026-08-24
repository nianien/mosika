# Mosika 文档

根目录 [`readme.md`](../readme.md) 用于项目概览和快速开始；实现设计、编辑器契约与开发运行说明统一维护在本目录。

| 文档 | 内容 | 主要读者 |
| --- | --- | --- |
| [Core 核心设计](./核心设计.md) | DSL、`RuleNode`、结果契约、Rhino、UDF、`RuleSuite` 与命名规则 | 内核开发者、规则平台接入方 |
| [Spring Boot Starter](./Spring-Boot-Starter.md) | `@Udf` Bean 自动扫描、`@UdfMethod` 方法暴露、Core 装配和 Web 合并语义 | Spring Boot 接入方、Web 开发者 |
| [模板参数化规则](./模板参数化规则.md) | `ruleId("""{...}""")` 语法、`$args` 运行时绑定、调用缓存与详情语义 | 规则作者、内核开发者、平台接入方 |
| [UI 树与 Web 编辑器](./规则编辑器.md) | UI AST 类型与字段、遍历校验、单向编译、画布交互和版本生命周期 | UI/Web 开发者、产品设计者 |
| [开发与运行](./开发与运行.md) | 工程结构、构建测试、Web 服务脚本、运行目录和变更验证 | 项目开发者、运维人员 |

## 文档边界

- `AGENTS.md` 是仓库协作约束，不属于产品文档，不移动到本目录
- `mosika-web/src/main/resources/static/ui/*.html` 是 Web 产品页面，不是静态说明页
- 文档图片统一放在 [`images/`](./images/)，不放入模块运行时资源目录
- 本目录只保留随当前代码维护的设计与使用说明；历史方案、一次性审查报告和临时性能基线由 Git 历史保存
