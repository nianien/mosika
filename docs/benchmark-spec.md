# 规则引擎压测规格(Rhino 基线 → GraalJS 对照)

自包含的压测规格。目的:让不同 JS 后端(Rhino / GraalJS)在**同一套规则、同一份输入、同一组指标**下可比。
参考实现见 `mosika-core/src/test/java/com/nianien/mosika/RuleEngineBenchmark.java`(含 `main()`)。

## 1. 目标

端到端度量**吞吐、延迟分布、并发扩展、每次 eval 分配字节**。测的是**执行路径**——`RuleSuite` 构建一次,不含 DSL 解析(用已注册 ruleId 复用预编译节点)。

## 2. 规则集(原样照搬)

**6 个原子 JS 规则**(`useType=0`,表达式即 JS):

```
isAdult      : $.age >= 18
isVip        : $.level >= 3
hasBalance   : $.balance >= $args.min
discount     : $.amount * 0.9
greet        : 'hi ' + $.name
recordVisit  : ($$.put('n', ($$.get('n')||0)+1), true)
```

**4 个组合规则**(`useType=2`,规则 DSL):

```
gate     : isAdult && isVip
body     : hasBalance("""{"min":50}""") -> (discount => greet) -> recordVisit    // 变体A:含并行 =>
body     : hasBalance("""{"min":50}""") -> discount -> greet -> recordVisit       // 变体B:全串行 ->
fallback : recordVisit
flow     : gate ? body : fallback
```

覆盖:条件 `?:`、串行 `->`、并行 `=>`、`&&`、模板参 `$args`、上下文写 `$$`、输入读 `$`、布尔/数值/字符串多种返回。
**跑两个变体(A/B)**,用来归因并行节点开销。

## 3. 输入与上下文

- `$`(输入,只读、跨线程共享):`{age:20, level:5, balance:100, amount:200, name:"tom"}` → 使 `gate` 为真,走 `body` 分支。
- `$$`(上下文):**每次 eval 新建 `HashMap`**(因为 `recordVisit` 会写 `n`)。
- 执行入口:`suite.eval("flow", input, newContext)`,用**已注册的 ruleId**(复用预编译节点)。

## 4. 度量项与方法

### (a) 每次 eval 分配字节

```
com.sun.management.ThreadMXBean.getThreadAllocatedBytes() 对所有 getAllThreadIds() 求和
预热后,测 50,000 次 eval 前后的分配差 / 50,000
```

> 汇总所有线程,才能把并行 `=>` 派发到池线程上的分配也算进去。

### (b) 单线程吞吐 + 延迟分布

```
预热 20,000 次;然后 100,000 次,每次 System.nanoTime() 记单点延迟
排序取 avg / p50 / p99 / max;吞吐 = iters / 总墙钟
```

### (c) 并发扩展

```
线程数 {1, 2, 4, availableProcessors};每线程 60,000 次 eval
CountDownLatch 对齐起跑;吞吐 = 总次数 / 墙钟;加速比 = ops / 单线程ops
```

**关键约束**:套件只 `build` 一次;所有线程共享只读 `$`;各线程各用各的 `$$`;预热务必充分。

## 5. 指标解读

- **分配速率** = 单线程 ops/s × 字节/eval。若逼近内存带宽(数十 GB/s),即 allocation-bandwidth-bound → 加吞吐要**降分配**,换 GC 无效。
- **尾延迟毛刺**(几十 ms 的 max):多为 GC STW,可用 ZGC 验证(降到亚毫秒即坐实)。
- **加速比远低于线程数**:先看是并行节点池争用(比 A/B 变体)还是分配带宽(看分配速率)。

## 6. Rhino 基线(参考机:10 逻辑核,JDK 21,默认 G1)

| 变体 | 单线程 ops/s | p50 | 10线程 ops/s / 加速比 | 字节/eval | max 延迟 |
| --- | --- | --- | --- | --- | --- |
| A 并行 `=>` | ~48–54k | ~18µs | ~186–192k / 3.6–3.8x | 40,694 B | ~7ms |
| B 全串行 `->` | ~120–140k | ~7µs | ~320–347k / 2.5x | 36,650 B | ~40ms(GC) |

- G1 GC:62 次 young,总停顿 1.3s,最长 107ms。
- ZGC:变体 B 的 max 从 49ms → 1.6ms(尾延迟证实是 GC),但 10 线程吞吐 320k → 228k(读屏障更慢)。

**结论**:allocation-bandwidth-bound,每次 eval ~36–40KB;GC 停顿只解释尾延迟,不解释吞吐天花板。
`=>` 对廉价叶子是净亏(单线程 ~2.5x 慢 + 独立的线程池调度尾延迟)。

## 7. GraalJS 移植要点(重要)

1. **别每次 eval 新建 Context**:`org.graalvm.polyglot.Context` 创建极贵。应共享一个 `Engine`(存 JIT 代码),用**复用的 Context + 每次注入 bindings**(等价 Rhino 的 sharedScope + per-eval local);每次建 Context 会压垮结果、不公平。
2. **预热要长得多**:Truffle 部分求值需要上万~十万次迭代才到峰值。预热至少 **100k**,最好画一条 warmup 曲线;冷态 GraalJS 远慢于 Rhino,热态可能反超。
3. **并发模型不同**:polyglot Context **默认非线程安全**。并发压测要么**每线程一个 Context**(共享 Engine),要么显式开多线程支持;**绝不能多线程共享同一 Context**。这和 Rhino 的线程安全共享作用域不一样,harness 要改。
4. **宿主访问 / 数据袋语义**:`$.name` 走 `HostAccess`;注意 `readMember`(读→key)与 `invokeMember`(调→方法)分流。要对齐 Rhino 的"数据袋"语义,需限制 host 方法访问。
5. **返回值转换**:结果是 polyglot `Value`,需 `as(...)` 转 Java,等价 `toJava`。
6. **同口径**:同规则集、同输入、同 A/B 变体、同三项指标(分配 / 延迟 / 扩展),才可比。

## 8. 运行配方

```bash
# 默认 G1(吞吐 + 尾延迟)
java -cp <cp> com.nianien.mosika.RuleEngineBenchmark
# 隔离 GC 停顿贡献
java -XX:+UseZGC -Xmx4g -cp <cp> com.nianien.mosika.RuleEngineBenchmark
# GC 停顿账
java -Xlog:gc:file=gc.log:time,uptime -cp <cp> com.nianien.mosika.RuleEngineBenchmark
```

参考实现的类路径(mosika-core 已 build 后):

```bash
mvn -q -pl mosika-core test-compile
mvn -q -pl mosika-core dependency:build-classpath -Dmdep.outputFile=cp.txt
CP="mosika-core/target/test-classes:mosika-core/target/classes:$(cat cp.txt)"
java -cp "$CP" com.nianien.mosika.RuleEngineBenchmark
```
