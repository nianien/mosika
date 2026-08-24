# Spring Boot Starter

> 适用模块：`mosika-udf`、`mosika-spring-boot-starter`、`mosika-web`

## 1. 模块边界

`mosika-core` 保持无 Spring 依赖。`@Udf` 声明 UDF 类、名称和分组，`@UdfMethod` 声明规则可调用的方法及其说明。`mosika-spring-boot-starter` 使用 Spring Boot 自动配置发现应用基础包中的 `@Udf` 类型，将其注册为 Spring Bean，并提供统一的 Core 定义加载入口。

Starter 只处理 Java UDF Bean，不决定规则从文件、数据库还是其他配置中心获取，也不自动创建或刷新 `RuleSuite`。规则来源和套件生命周期仍由应用负责。

## 2. 引入与声明

```xml
<dependency>
    <groupId>io.github.nianien</groupId>
    <artifactId>mosika-spring-boot-starter</artifactId>
    <version>${mosika.version}</version>
</dependency>
```

UDF 类声明 `@Udf`，需要暴露给规则的方法声明 `@UdfMethod`，不需要再添加 `@Component`：

```java
@Udf(group = "account", value = "profile")
public class NormalizeAccountUdf {

    private final AccountService accountService;

    public NormalizeAccountUdf(AccountService accountService) {
        this.accountService = accountService;
    }

    @UdfMethod(description = "标准化账户信息")
    public Account normalize(Account account) {
        return accountService.normalize(account);
    }
}
```

普通 Bean 不需要实现 `Function` 接口，上述方法通过 `account.profile.normalize(...)` 调用。如果需要通过 `account.profile(...)` 直接调用，则暴露一个带 `@UdfMethod` 的 `apply` 方法。未标注方法不会被规则访问。

未显式设置 `value` 时，名称取类的短名称并按 JavaBean 规则转换首字母，例如 `NormalizeAccountUdf` 对应 `normalizeAccountUdf`。`group` 保持 `@Udf` 中声明的点分隔路径。

默认扫描范围使用 Spring Boot 的自动配置基础包，通常就是 `@SpringBootApplication` 所在包及其子包。外部依赖中的 UDF 可以通过 `mosika.udf.scan-packages` 增加扫描包：

```yaml
mosika:
  udf:
    scan-packages:
      - com.example.risk.udf
      - com.example.marketing.udf
```

配置包与默认包会合并扫描，允许父子包重叠。抽象类和接口不会被注册。外部 UDF 只需添加 `@Udf`，不需要由外部依赖提供自动配置。

如果需要显式控制 UDF 的创建过程，可以同时使用 `@Bean`：

```java
@Bean
NormalizeAccountUdf normalizeAccountUdf(AccountService accountService) {
    return new NormalizeAccountUdf(accountService);
}
```

Starter 在 Spring 完成配置类解析后扫描 UDF。若同一类型已经通过 `@Bean`、`@Component` 等方式存在于容器中，Starter 直接使用已有 Bean，不再创建第二个实例；否则将扫描到的 `@Udf` 类型注册为 Spring Bean。两种方式都使用主容器完成依赖注入和 Bean 后置处理。

## 3. 装配 RuleSuite

`SpringUdfLoader` 每次返回当前 Spring 容器中全部 `@Udf` Bean 对应的 `UdfDefinition`：

```java
@Bean
RuleSuite ruleSuite(RuleRepository repository, SpringUdfLoader udfLoader) {
    return new RuleSuite(repository.loadRules(), udfLoader.loadUdfs());
}
```

Loader 将 Spring 管理的 Bean 包装为 `UdfDelegate` 后放入现有 `UdfDefinition.udf` 字段。方法注解、参数和返回类型从 AOP 目标类读取，真正调用仍落在 Spring 代理 Bean 上，因此构造器注入、Bean 生命周期和 AOP 拦截都会保留。最终的 UDF 路径唯一性、分组冲突和函数签名校验仍由 Core 在 `RuleSuite` 构造阶段统一处理。

## 4. Web 中的 Java 与 JavaScript UDF

参考 Web 将两类 UDF 合并到同一份查询结果和运行时 `RuleSuite`：

| 属性 | Java UDF | JavaScript UDF |
| --- | --- | --- |
| 来源 | Spring 容器中的 `@Udf` Bean | SQLite `udf_definition` |
| 命名空间 | 对所有启用命名空间可用 | 按数据库命名空间隔离 |
| 运行状态 | 随应用启动始终启用 | 可启用或停用 |
| 修改方式 | 只能修改 Java 代码和 Spring 配置 | 可通过 Web 创建、编辑、停用和启用 |
| 查询标记 | `type=java`、`editable=false` | `type=javascript`、`editable=true` |
| 数据库字段 | 没有 ID、版本和时间 | 包含 ID、乐观锁版本和时间 |

查询时 Java UDF 排在数据库 UDF 之前，并共同参与 `total`、`pageNumber` 和 `pageSize` 分页。`status=0` 不返回 Java UDF；关键字可以匹配 Java UDF 完整路径、实现类名、方法名、方法说明和参数或返回类型。

创建、更新或启用 JavaScript UDF 时，如果完整路径与任一 Java UDF 相同，接口返回 `409`，数据库记录和运行时套件都不会被替换。页面根据 `editable` 隐藏 Java UDF 的编辑和启停操作，并在只读详情中展示实现类、暴露方法签名和说明。

运行时先加载全部 Java UDF，再追加当前命名空间已启用的 JavaScript UDF。两类定义最终一起交给 Core，因此规则表达式可以直接组合调用：

```javascript
account.normalize(databaseFns.enrich($))
```

## 5. 实现与验证位置

| 位置 | 当前职责 |
| --- | --- |
| `mosika-udf` | 集中存放由 Starter 扫描注册的 Java UDF 实现 |
| `MosikaUdfAutoConfiguration` | 导入扫描注册器并提供 `SpringUdfLoader` Bean |
| `UdfBeanDefinitionRegistrar` | 扫描默认包和配置的扩展包，并注册尚不存在的 `@Udf` Bean |
| `SpringUdfLoader` | 从目标类生成受控代理并映射为 Core `UdfDefinition` |
| `RuleSuiteManager` | 合并 Java UDF 与当前命名空间已启用的数据库 UDF |
| `UdfDefinitionService` | 合并分页查询并阻止数据库定义覆盖 Java UDF |
| `udfs.html` | 同表展示两类 UDF，并提供 Java UDF 只读方法详情 |
| `MosikaUdfAutoConfigurationTest` | 验证默认/扩展包扫描、构造器注入、JDK/CGLIB 代理调用、`@Bean` 复用和重复注册规避 |
| `MosikaJavaUdfIntegrationTest` | 验证 Web 合并分页、方法元数据、只读标记、Java/JavaScript 联合执行和路径冲突回滚 |
