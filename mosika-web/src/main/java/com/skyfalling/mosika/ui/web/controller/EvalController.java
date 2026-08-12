package com.skyfalling.mosika.ui.web.controller;

import com.skyfalling.mosika.eval.result.NodeResult;
import com.skyfalling.mosika.ui.web.common.ApiResponse;
import com.skyfalling.mosika.ui.web.service.RuleSuiteManager;
import com.skyfalling.mosika.utils.JsonUtils;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 求值相关接口：按 flow / rule / expression 三种入口触发规则套件求值。
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
@RestController
@RequestMapping("/api/eval")
@RequiredArgsConstructor
public class EvalController {

    private final RuleSuiteManager suiteManager;

    @Data
    public static class EvalRequest {
        /** 求值参数对象（对应 core 里的 $）。 */
        private Object target;
        /** 附加上下文（对应 core 里的 $$），可为空。 */
        private Map<String, Object> context;
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    public static class ExprEvalRequest extends EvalRequest {
        private String namespace;
        private String expression;
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    public static class TryEvalRequest extends EvalRequest {
        private String namespace;
        /** 待试运行的原子规则表达式（JavaScript），取编辑器当前内容，规则可以尚未保存。 */
        private String expression;
        /** 模板参数取值（对应 core 里的 $args），可为空。 */
        private Map<String, Object> args;
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    public static class TryFlowRequest extends EvalRequest {
        private String namespace;
        private String ruleTree;
    }

    @PostMapping("/flow/try")
    public ApiResponse<RuleSuiteManager.FlowTryResult> tryFlow(@RequestBody TryFlowRequest req) {
        if (req.getNamespace() == null || req.getNamespace().isBlank()) {
            throw new IllegalArgumentException("namespace is required");
        }
        return ApiResponse.ok(suiteManager.tryFlow(
                req.getNamespace(), req.getRuleTree(), req.getTarget(), req.getContext()));
    }

    /** 执行一条规则流：POST /api/eval/flow/{flowId} */
    @PostMapping("/flow/{flowId}")
    public ApiResponse<NodeResult> evalFlow(@PathVariable String flowId,
                                            @RequestBody(required = false) EvalRequest req) {
        EvalRequest r = req == null ? new EvalRequest() : req;
        return ApiResponse.ok(suiteManager.evalFlow(flowId, r.getTarget(), r.getContext()));
    }

    /** 执行一条原子规则：POST /api/eval/rule/{ruleId} */
    @PostMapping("/rule/{ruleId}")
    public ApiResponse<NodeResult> evalRule(@PathVariable String ruleId,
                                            @RequestBody(required = false) EvalRequest req) {
        EvalRequest r = req == null ? new EvalRequest() : req;
        return ApiResponse.ok(suiteManager.evalRule(ruleId, r.getTarget()));
    }

    /** 执行任意规则 DSL 表达式：POST /api/eval/expr，body 包含 namespace/expression/target。 */
    @PostMapping("/expr")
    public ApiResponse<NodeResult> evalExpr(@RequestBody ExprEvalRequest req) {
        if (req.getNamespace() == null || req.getNamespace().isBlank()) {
            throw new IllegalArgumentException("namespace is required");
        }
        return ApiResponse.ok(suiteManager.evalExpr(
                req.getNamespace(), req.getExpression(), req.getTarget()));
    }

    /**
     * 试运行编辑中的原子规则：POST /api/eval/try
     * <p>
     * body 包含 namespace/expression/args/target/context，规则不必已保存或已启用。
     * 传了 context 时 core 不返回规则详情，结果里的 details 会为空。
     */
    @PostMapping("/try")
    public ApiResponse<NodeResult> tryRule(@RequestBody TryEvalRequest req) {
        if (req.getNamespace() == null || req.getNamespace().isBlank()) {
            throw new IllegalArgumentException("namespace is required");
        }
        String argsJson = req.getArgs() == null || req.getArgs().isEmpty()
                ? null : JsonUtils.toJson(req.getArgs());
        return ApiResponse.ok(suiteManager.tryRule(req.getNamespace(), req.getExpression(),
                argsJson, req.getTarget(), req.getContext()));
    }
}
