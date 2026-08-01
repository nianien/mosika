# Mousika UI-Tree 性能基准

演示编辑器（`demo/ui-tree`）的可复现性能基准。测的是**同步 JS 重建 + 首次强制布局**耗时，用于观察随规模变化的相对趋势与拐点。

> 绝对毫秒值随机器、Chrome/Node 版本、构建波动，**不要**作为 CI 失败阈值；只看相对趋势。
> 本基准**不含** paint/composite 与真实逐帧间隔（那需要在可见窗口里另测）。

## 组成

- `index.html` —— 基准页面。内嵌 iframe 加载 `../index.html?bench=1`，通过 `window.__mousikaBench` 桥驱动真实编辑器代码。同步计时（`performance.now`），避开 offscreen iframe 的后台定时器/rAF 节流。
- `run.mjs` —— CDP 驱动。用真实墙钟无头跑 `index.html?auto=1`，回收结果并附带环境信息打印 `{env, results}` JSON。
- `../app.js` 里的 `window.__mousikaBench` 桥 —— **仅当 URL 带 `?bench=1` 时暴露**的只读/测试接口（正常页面路径不加载、不自动运行 benchmark）。生产打开 `index.html`（无 `?bench=1`）时它不存在。

## 运行

浏览器（配合 IntelliJ 内置服务器等 http 托管，或直接 file://）：

```
打开 bench/index.html            # 点“运行全部”看表格，或“导出 JSON”
打开 bench/index.html?auto=1     # 自动跑
```

命令行（无头，产出带环境信息的 JSON）：

```
node bench/run.mjs > bench/baseline.json
CHROME_BIN=/path/to/chromium node bench/run.mjs
```

## 覆盖场景

- `render()` 主画布全树重建：深链 A×50/200/1000、宽分支 P×20/100/500、以及 J（规则只投影为一个节点）。
- `renderRuleTree()` 弹窗内真实规则树：扁平 L(R…)、平衡二叉、深嵌套，各 20/100/500。
- 快照 `JSON.parse(JSON.stringify(judge.children))`：含 J 小规则 + 大 action 子树。
- `fitTree()` 单独 + `render()+fitTree()`。
- 拖拽 `pointermove` 单次事件耗时（宽分支 20/100/500）。
- 43 节点样例作为真实基线。

迭代：render/ruleTree/fitTree 为 3 warmup + 20 采样，snapshot 10 warmup + 50，drag 12 次连续 move；报 median/p95/max。

## 结果格式

`run.mjs` 输出：

```json
{
  "env": { "chrome": "...", "node": "...", "os": "...", "when": "...", "iterations": { ... }, "note": "..." },
  "results": {
    "render":   [{ "name", "metrics": { flowCount, ruleCount, width, height }, "jsMedian", "jsP95", "jsMax", "layMedian" }],
    "snapshot": [{ "name", "metrics", "median", "p95", "max" }],
    "drag":     [{ "name", "siblings", "median", "p95", "max", "over16" }],
    "ruleTree": [{ "name", "ruleNodes", "width", "height", "jsMedian", "jsP95", "layMedian" }],
    "fitTree":  [{ "name", "metrics", "fitMedian", "comboMedian" }],
    "errors":   [{ "at", "msg", "stack" }]
  }
}
```

## 已知结论（基线，仅供参考）

仓库中的 `baseline.json` 记录于 2026-07-30，是安全预算和宽分支渲染优化之前的历史对照，不代表当前代码性能；修改渲染逻辑后应在可见浏览器中重新生成并保留环境信息。

- 现实规模（几十~低百节点、规则嵌套个位数）下无需性能优化。
- `render()` 在数百个**可见流程节点**时含布局接近一帧；宽树把画布宽度撑得很大是主因之一。
- 快照即便千节点仍 <1ms。
- 拖拽 `pointermove` 在本机同步基准下未见瓶颈（暂缓优化，但因不含 paint/composite，不等于“无意义”）。
- `renderRuleTree()` **深嵌套**在极深（数百层）时爆炸（JS + 布局 ~百 ms，宽度被逐层缩进撑爆），现实嵌套个位数层不受影响。
- 深链 ~1000 会在 `countModalRuleNodes` 等**业务递归**触发栈溢出——已由 `app.js` 的 `TREE_LIMITS`（深度/节点上限）在导入/结构变更边界拦截。
