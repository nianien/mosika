// Mousika 性能基准 CDP 驱动（真实墙钟计时）
//
// 用法：
//   node run.mjs                      # 无头运行 ./index.html?auto=1，打印 {env, results} JSON
//   node run.mjs > baseline.json      # 保存结果（建议连同 env 一并留档）
//   CHROME_BIN=/path/to/chrome node run.mjs
//
// 依赖：Node ≥ 21（内置 global WebSocket / fetch）；本机 Chrome/Chromium。
// 说明：
//   - 计时全部同步进行（performance.now），不依赖 rAF/setTimeout，避免 offscreen iframe
//     的后台定时器/rAF 节流。因此覆盖“JS 重建 + 首次强制布局”，不含 paint/composite 与真实逐帧间隔。
//   - --virtual-time-budget 会冻结 performance.now，故此处不用；用真实墙钟。
//   - 页面内 iframe 加载 ../index.html?bench=1，app.js 在 ?bench=1 下暴露只读/测试用 window.__mousikaBench
//     （正常页面路径不启用；不是“生产 inert”，而是“正常路径不加载的开发接口”）。
//   - 绝对毫秒值随机器/构建波动，请勿作为 CI 失败阈值；仅用于相对趋势与拐点判断。
import { spawn } from 'node:child_process';
import os from 'node:os';

const CHROME = process.env.CHROME_BIN || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
const PORT = Number(process.env.PORT || 9333);
const HERE = new URL('.', import.meta.url).pathname;
const URL_ = process.env.BENCH_URL || ('file://' + HERE + 'index.html?auto=1');
const sleep = ms => new Promise(r => setTimeout(r, ms));

const proc = spawn(CHROME, [
  '--headless=new', '--disable-gpu', '--no-sandbox', '--allow-file-access-from-files',
  `--remote-debugging-port=${PORT}`, '--user-data-dir=/tmp/mousika-bench-cdp', '--window-size=1600,1200', URL_
], { stdio: 'ignore' });

async function fetchJson(path) { return (await fetch(`http://127.0.0.1:${PORT}${path}`)).json(); }
async function getPageWs() {
  for (let i = 0; i < 40; i++) {
    try {
      const list = await fetchJson('/json');
      const page = list.find(t => t.type === 'page' && t.webSocketDebuggerUrl);
      if (page) return page.webSocketDebuggerUrl;
    } catch (e) { /* not ready */ }
    await sleep(250);
  }
  throw new Error('no page target');
}

let chromeVersion = 'unknown';
for (let i = 0; i < 20; i++) {
  try { const v = await fetchJson('/json/version'); if (v && v.Browser) { chromeVersion = v.Browser; break; } } catch (e) { /* retry */ }
  await sleep(200);
}

const ws = new WebSocket(await getPageWs());
let idc = 0; const pending = new Map();
const send = (method, params = {}) => new Promise((res, rej) => { const id = ++idc; pending.set(id, { res, rej }); ws.send(JSON.stringify({ id, method, params })); });
ws.addEventListener('message', ev => { const m = JSON.parse(ev.data); if (m.id && pending.has(m.id)) { const { res, rej } = pending.get(m.id); pending.delete(m.id); m.error ? rej(new Error(JSON.stringify(m.error))) : res(m.result); } });
await new Promise((res, rej) => { ws.addEventListener('open', res); ws.addEventListener('error', rej); });
await send('Runtime.enable');

const started = Date.now();
let out = null;
while (Date.now() - started < 300000) {
  const r = await send('Runtime.evaluate', { expression: "window.__BENCH_RESULTS||''", returnByValue: true });
  const v = r.result && r.result.value;
  if (v) { out = v; break; }
  await sleep(1000);
}
try { ws.close(); } catch (e) {}
proc.kill('SIGKILL');

if (!out) { console.error('TIMEOUT: no results'); process.exit(1); }
const env = {
  chrome: chromeVersion,
  node: process.version,
  os: `${os.platform()} ${os.release()} ${os.arch()}`,
  when: new Date().toISOString(),
  note: 'synchronous JS + first forced-layout timing; excludes paint/composite & real per-frame; absolute ms are machine-specific, do NOT use as CI thresholds',
  iterations: { render: '3 warmup + 20', snapshot: '10 warmup + 50', drag: '12 moves', ruleTree: '3 warmup + 20', fitTree: '3 warmup + 20' }
};
console.log(JSON.stringify({ env, results: JSON.parse(out) }, null, 2));
process.exit(0);
