package com.skyfalling.mosika.ui.web.service;

import com.skyfalling.mosika.udf.JsUdf;
import com.skyfalling.mosika.ui.web.common.BusinessException;
import com.skyfalling.mosika.ui.web.dao.RuleNamespaceDao;
import com.skyfalling.mosika.ui.web.dao.UdfDefinitionDao;
import com.skyfalling.mosika.ui.web.entity.RuleNamespaceEntity;
import com.skyfalling.mosika.ui.web.entity.UdfDefinitionEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * JavaScript UDF 的注册、版本管理和运行态发布服务
 * <p>
 * 持久化前使用 Core 的 {@link JsUdf} 完成真实编译校验，并限制用户定义覆盖 JavaScript
 * 关键字、规则上下文变量和 {@code sys} 内置命名空间
 */
@Service
@RequiredArgsConstructor
public class UdfDefinitionService {

    /** 默认命名空间编码 */
    private static final String DEFAULT_NAMESPACE = "default";

    /** JavaScript 标识符格式，不允许通过点号表达层级 */
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");

    /** 不允许作为顶级 UDF 名称或命名空间的 JavaScript 保留字 */
    private static final Set<String> RESERVED_TOP_LEVEL_NAMES = Set.of(
            "await", "break", "case", "catch", "class", "const", "continue", "debugger", "default",
            "delete", "do", "else", "enum", "export", "extends", "false", "finally", "for", "function",
            "if", "import", "in", "instanceof", "let", "new", "null", "return", "static", "super",
            "switch", "this", "throw", "true", "try", "typeof", "var", "void", "while", "with", "yield");

    /** UDF 定义持久化访问对象 */
    private final UdfDefinitionDao udfDao;

    /** 命名空间持久化访问对象 */
    private final RuleNamespaceDao namespaceDao;

    /** 规则套件候选快照构造与发布入口 */
    private final RuleSuiteManager suiteManager;

    /**
     * 创建并默认启用一个 JavaScript UDF
     * <p>
     * UDF 写入和完整规则套件预编译位于同一事务中，命名空间冲突、函数编译失败或
     * 存量规则无法装配时都会回滚新增记录
     *
     * @param request 待创建的 UDF 定义
     * @return 已落库并带自增 ID 和版本号的 UDF 定义
     * @throws IllegalArgumentException UDF 路径或源码无效时抛出
     * @throws BusinessException         UDF 完整路径已经存在时抛出
     */
    @Transactional
    public UdfDefinitionEntity create(UdfDefinitionEntity request) {
        normalizeAndValidate(request);
        RuleNamespaceEntity namespace = requireNamespace(request.getNamespace());
        request.setNamespaceId(namespace.getId());
        request.setNamespace(namespace.getCode());
        ensurePathAvailable(namespace.getCode(), request.getGroup(), request.getName(), null);
        request.setStatus(1);
        long id = udfDao.insert(request);
        // 事务内构造完整候选快照：函数、命名空间或存量规则失败则整体回滚
        suiteManager.refreshAfterCommit();
        return udfDao.findById(id);
    }

    /**
     * 按乐观锁更新 UDF 定义，不允许通过更新接口直接改变启停状态
     * <p>
     * 已启用 UDF 更新后会重新构造运行态套件，停用 UDF 只执行自身编译与路径校验
     *
     * @param id      UDF 数据库 ID
     * @param request 包含期望版本号和新定义的更新请求
     * @return 更新后的 UDF 定义
     * @throws IllegalArgumentException UDF 路径、源码或版本参数无效时抛出
     * @throws BusinessException         UDF 不存在、路径冲突或版本冲突时抛出
     */
    @Transactional
    public UdfDefinitionEntity update(long id, UdfDefinitionEntity request) {
        UdfDefinitionEntity existing = requireUdf(id);
        if (request.getVersion() == null) {
            throw new IllegalArgumentException(
                    "version is required for update (expected " + existing.getVersion() + ")");
        }
        request.setId(id);
        request.setStatus(existing.getStatus());
        normalizeAndValidate(request);
        RuleNamespaceEntity namespace = requireNamespace(request.getNamespace());
        assertNamespaceUnchanged(namespace.getCode(), existing.getNamespace());
        request.setNamespaceId(existing.getNamespaceId());
        request.setNamespace(existing.getNamespace());
        ensurePathAvailable(existing.getNamespace(), request.getGroup(), request.getName(), id);
        int rows = udfDao.update(request);
        if (rows == 0) {
            throw new BusinessException(409,
                    "udf updated by others, please retry (expected version=" + request.getVersion() + ")");
        }
        if (existing.getStatus() != null && existing.getStatus() == 1) {
            suiteManager.refreshAfterCommit();
        }
        return udfDao.findById(id);
    }

    /**
     * 启用 UDF 并刷新运行态套件
     *
     * @param id              UDF 数据库 ID
     * @param expectedVersion 客户端持有的期望版本号
     * @return 启用后的 UDF 定义
     * @throws BusinessException UDF 不存在、路径冲突、版本冲突或套件编译失败时抛出
     */
    @Transactional
    public UdfDefinitionEntity enable(long id, long expectedVersion) {
        UdfDefinitionEntity existing = requireUdf(id);
        normalizeAndValidate(existing);
        requireNamespace(existing.getNamespace());
        ensurePathAvailable(existing.getNamespace(), existing.getGroup(), existing.getName(), id);
        int rows = udfDao.enable(id, expectedVersion);
        if (rows == 0) {
            throw new BusinessException(409, "udf updated by others, please retry");
        }
        suiteManager.refreshAfterCommit();
        return udfDao.findById(id);
    }

    /**
     * 停用 UDF 并刷新运行态套件
     *
     * @param id              UDF 数据库 ID
     * @param expectedVersion 客户端持有的期望版本号
     * @throws BusinessException UDF 不存在、版本冲突或套件编译失败时抛出
     */
    @Transactional
    public void disable(long id, long expectedVersion) {
        requireUdf(id);
        int rows = udfDao.disable(id, expectedVersion);
        if (rows == 0) {
            throw new BusinessException(409, "udf updated by others, please retry");
        }
        suiteManager.refreshAfterCommit();
    }

    /**
     * 按 ID 查询 UDF 定义
     *
     * @param id UDF 数据库 ID
     * @return UDF 定义，不存在时返回 {@code null}
     */
    public UdfDefinitionEntity findById(long id) {
        return udfDao.findById(id);
    }

    /**
     * 分页查询 UDF 定义
     *
     * @param status     启停状态过滤条件，传 {@code null} 表示不过滤
     * @param namespace  规则命名空间，传空值表示默认命名空间
     * @param keyword    UDF 分组、名称、描述或源码关键字，传空值表示不过滤
     * @param pageNumber 从 1 开始的页码，小于 1 时按 1 处理
     * @param pageSize   每页条数，范围限制为 1 到 200
     * @return 包含数据项、总数和规范化分页参数的结果
     */
    public Map<String, Object> page(Integer status, String namespace, String keyword,
                                    int pageNumber, int pageSize) {
        int page = Math.max(pageNumber, 1);
        int size = Math.max(1, Math.min(pageSize, 200));
        long offset = (long) (page - 1) * size;
        String normalizedNamespace = normalizeNamespace(namespace);
        List<UdfDefinitionEntity> rows = udfDao.list(
                status, normalizedNamespace, keyword, offset, size);
        int total = udfDao.count(status, normalizedNamespace, keyword);
        return Map.of(
                "items", rows,
                "total", total,
                "pageNumber", page,
                "pageSize", size);
    }

    /**
     * 查询必须存在的 UDF 定义
     *
     * @param id UDF 数据库 ID
     * @return 对应 UDF 定义
     * @throws BusinessException UDF 不存在时抛出 404 业务异常
     */
    private UdfDefinitionEntity requireUdf(long id) {
        UdfDefinitionEntity entity = udfDao.findById(id);
        if (entity == null) {
            throw new BusinessException(404, "udf not found: " + id);
        }
        return entity;
    }

    /** 查询必须存在且启用的命名空间 */
    private RuleNamespaceEntity requireNamespace(String code) {
        String normalized = normalizeNamespace(code);
        RuleNamespaceEntity namespace = namespaceDao.findByCode(normalized);
        if (namespace == null || namespace.getStatus() == null || namespace.getStatus() != 1) {
            throw new BusinessException(400, "unknown or disabled namespace: " + normalized);
        }
        return namespace;
    }

    /**
     * 规范化 UDF 字段并使用真实 JavaScript 运行时完成编译校验
     *
     * @param request 待规范化和校验的 UDF 定义
     * @throws IllegalArgumentException 命名空间、名称或源码无效时抛出
     */
    private void normalizeAndValidate(UdfDefinitionEntity request) {
        String group = request.getGroup() == null ? "" : request.getGroup().trim();
        String name = request.getName() == null ? "" : request.getName().trim();
        String source = request.getSource() == null ? "" : request.getSource().trim();
        request.setGroup(group);
        request.setName(name);
        request.setSource(source);

        if (!group.isEmpty()) {
            for (String segment : group.split("\\.", -1)) {
                requireIdentifier(segment, "udf group");
            }
            if ("sys".equals(group) || group.startsWith("sys.")) {
                throw new IllegalArgumentException("udf group 'sys' is reserved for built-in functions");
            }
        }
        requireIdentifier(name, "udf name");
        String topLevel = group.isEmpty() ? name : group.substring(0, group.indexOf('.') < 0
                ? group.length() : group.indexOf('.'));
        validateTopLevelName(topLevel);
        if (source.isEmpty()) {
            throw new IllegalArgumentException("udf source is required");
        }
        // 与内核使用同一个编译器，确保保存时就拒绝语法错误、非函数和命名不一致
        new JsUdf(name, source);
    }

    /**
     * 校验单个 JavaScript 标识符片段
     *
     * @param value 待校验的标识符
     * @param label 错误信息中的字段名称
     * @throws IllegalArgumentException 标识符格式无效时抛出
     */
    private static void requireIdentifier(String value, String label) {
        if ("__proto__".equals(value)) {
            throw new IllegalArgumentException(label + " is reserved: " + value);
        }
        if (!IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException(label + " must be a JavaScript identifier: " + value);
        }
    }

    /**
     * 防止用户 UDF 覆盖运行时保留的顶级名称
     *
     * @param name 顶级函数名称或命名空间片段
     * @throws IllegalArgumentException 名称属于保留字时抛出
     */
    private static void validateTopLevelName(String name) {
        if ("$".equals(name) || "$$".equals(name) || "$args".equals(name) || "sys".equals(name)
                || RESERVED_TOP_LEVEL_NAMES.contains(name)) {
            throw new IllegalArgumentException("udf top-level name is reserved: " + name);
        }
    }

    /**
     * 校验 UDF 的完整注册路径是否唯一
     *
     * @param namespace 规则命名空间
     * @param group     点分隔 UDF 分组
     * @param name      函数名称
     * @param selfId    更新场景中允许忽略的当前记录 ID，创建时传 {@code null}
     * @throws BusinessException 完整路径已被其他记录占用时抛出
     */
    private void ensurePathAvailable(String namespace, String group, String name, Long selfId) {
        UdfDefinitionEntity conflict = udfDao.findByPath(namespace, group, name);
        if (conflict != null && (selfId == null || !selfId.equals(conflict.getId()))) {
            throw new BusinessException(409, "udf already exists: " + fullName(group, name));
        }
    }

    /** UDF 创建后不允许改变命名空间 */
    private static void assertNamespaceUnchanged(String requested, String existing) {
        if (!existing.equals(requested)) {
            throw new BusinessException(400, "namespace cannot be changed after creation");
        }
    }

    /** 空命名空间归一为默认命名空间 */
    private static String normalizeNamespace(String namespace) {
        return namespace == null || namespace.isBlank() ? DEFAULT_NAMESPACE : namespace.trim();
    }

    /**
     * 拼接 UDF 的完整注册路径
     *
     * @param group 点分隔命名空间
     * @param name  函数名称
     * @return 顶级函数名称或命名空间与函数名称的点分隔路径
     */
    private static String fullName(String group, String name) {
        return group == null || group.isEmpty() ? name : group + "." + name;
    }
}
