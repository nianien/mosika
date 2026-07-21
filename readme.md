# Mousika

Mousika 要求 JDK 21，使用 Maven 构建：

```shell
mvn clean test
```

规则中的 JavaScript 由 GraalJS Polyglot 执行。普通 JDK 21 可以正常运行；高频 JavaScript 场景建议使用支持 GraalJS 优化运行时的 GraalVM。

## 1. 串行执行

![img.png](mousika-ui/src/main/resources/img/serial.png)

```text
 a->b->c->d
```

## 2. 并行执行
![img.png](mousika-ui/src/main/resources/img/parallel.png)
```text
 a->(b=>c)->d
```

## 3.条件执行
![img.png](mousika-ui/src/main/resources/img/conditional.png)
```text
 (a?b:c)->d
```

## 4.半条件执行
![img.png](mousika-ui/src/main/resources/img/half-conditional.png)
```text
 a?(b->d)
```

## 5.多分支执行
![img.png](mousika-ui/src/main/resources/img/multi-branch.png)
```text
(a?b)->d
```

## 6.UI树交互
![ui-tree.png](mousika-ui/src/main/resources/img/ui-tree.png)
```text
 (∅->(∅=>a11=>a12=>a13)->((c1||(c2&&c3))?(∅->(c1?a1:((c2&&c3)?a3:∅))->(c4?a4)):(c5?(c6?a6:(c7?a7:a5)):(∅->(c8?a8)->(c9?a9)))))
```
上述表达式执行逻辑如下:
```shell
 a11 &
 a12 &
 a13 &
 wait
 
 if [c1||c2&&c3];then
    if [c1];then
      a1;
    elif [c2&&c3];then
      a3;
    fi
    
    if [c4];then
      a4;
    fi
    
 elif [c5];then
    if [c6];then
      a6;
    elif [c7];then  
      a7;
    else
      a5;
    fi
    
 else
    if [c8];then
      a8;
    fi;
    
    if [c9];then
      a9;
    fi
 fi
```
注：`∅` 不参与规则计算，仅作为 `SNode`、`PNode` 的结构标记，确保单分支结构也能稳定反序列化。

## 7. 命中数量

`hits(min, max, rules...)` 用于判断子规则的命中数是否位于指定区间，`_` 表示该边界不限制。

```text
hits(2, _, a, b, c)  // 至少命中 2 个
hits(_, 2, a, b, c)  // 至多命中 2 个
hits(2, 2, a, b, c)  // 恰好命中 2 个
hits(1, 2, a, b, c)  // 命中 1～2 个
```
