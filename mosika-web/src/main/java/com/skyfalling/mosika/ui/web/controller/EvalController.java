package com.skyfalling.mosika.ui.web.controller;

import com.skyfalling.mosika.eval.result.NodeResult;
import com.skyfalling.mosika.ui.web.common.ApiResponse;
import com.skyfalling.mosika.ui.web.service.RuleSuiteManager;
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
        private String expression;
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

    /** 执行任意 DSL 表达式：POST /api/eval/expr，body 包含 expression/target。 */
    @PostMapping("/expr")
    public ApiResponse<NodeResult> evalExpr(@RequestBody ExprEvalRequest req) {
        return ApiResponse.ok(suiteManager.evalExpr(req.getExpression(), req.getTarget()));
    }
}
