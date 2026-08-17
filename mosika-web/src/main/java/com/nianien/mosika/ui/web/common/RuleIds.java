package com.nianien.mosika.ui.web.common;

/**
 * 规则和规则流对外 ID 的格式化与解析工具
 * <p>
 * 对外 ID 由类型前缀和对应数据表的自增主键组成，不单独持久化
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
public final class RuleIds {

    /** 原子规则 ID 前缀 */
    private static final char RULE_PREFIX = 'r';

    /** 规则流 ID 前缀 */
    private static final char FLOW_PREFIX = 'f';

    private RuleIds() {
    }

    /** 根据原子规则数据库主键生成 ruleId */
    public static String ruleId(Long id) {
        return format(RULE_PREFIX, id);
    }

    /** 根据规则流数据库主键生成 flowId */
    public static String flowId(Long id) {
        return format(FLOW_PREFIX, id);
    }

    /** 严格解析 ruleId 并返回原子规则数据库主键 */
    public static long parseRuleId(String ruleId) {
        return parse(RULE_PREFIX, "ruleId", ruleId);
    }

    /** 严格解析 flowId 并返回规则流数据库主键 */
    public static long parseFlowId(String flowId) {
        return parse(FLOW_PREFIX, "flowId", flowId);
    }

    /** 判断字符串是否为规范 ruleId */
    public static boolean isRuleId(String value) {
        return isTypedId(RULE_PREFIX, value);
    }

    /** 判断字符串是否为规范 flowId */
    public static boolean isFlowId(String value) {
        return isTypedId(FLOW_PREFIX, value);
    }

    /** 格式化带类型前缀的对外 ID */
    private static String format(char prefix, Long id) {
        if (id == null) {
            return null;
        }
        if (id <= 0) {
            throw new IllegalArgumentException("database id must be positive: " + id);
        }
        return prefix + Long.toString(id);
    }

    /** 严格解析并校验前缀、正整数和规范文本形式 */
    private static long parse(char prefix, String type, String value) {
        if (!isTypedId(prefix, value)) {
            throw new IllegalArgumentException("invalid " + type + ": [" + value + "]");
        }
        try {
            return Long.parseLong(value.substring(1));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid " + type + ": [" + value + "]", e);
        }
    }

    /** 判断字符串是否满足指定类型 ID 的规范格式 */
    private static boolean isTypedId(char prefix, String value) {
        if (value == null || value.length() < 2 || value.charAt(0) != prefix || value.charAt(1) == '0') {
            return false;
        }
        for (int i = 1; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        try {
            return Long.parseLong(value.substring(1)) > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
