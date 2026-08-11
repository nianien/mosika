package com.skyfalling.mosika.ui.web.common;

import com.skyfalling.mosika.exception.RuleEvalException;
import com.skyfalling.mosika.exception.RuleNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * REST 接口全局异常转换器
 * <p>
 * 把业务异常、请求绑定异常和未处理异常映射为稳定的 HTTP 状态与 {@link ApiResponse}
 * 未处理异常只向客户端返回通用信息，完整堆栈保留在服务端日志
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 把显式业务异常映射为对应的 4xx HTTP 响应
     *
     * @param e 业务异常
     * @return 带业务错误码和消息的响应
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
        log.warn("business error: code={}, msg={}", e.getCode(), e.getMessage());
        return ResponseEntity.status(toHttpStatus(e.getCode()))
                .body(ApiResponse.fail(e.getCode(), e.getMessage()));
    }

    /**
     * 把参数和编译校验失败统一映射为 HTTP 400
     *
     * @param e 参数异常
     * @return 参数错误响应
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArg(IllegalArgumentException e) {
        log.warn("illegal argument: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(400, e.getMessage()));
    }

    /**
     * 把唯一键或其他数据库约束冲突映射为 HTTP 409
     *
     * @param e 数据完整性异常
     * @return 数据冲突响应
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataConflict(DataIntegrityViolationException e) {
        log.warn("data conflict: {}", e.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.fail(409, "definition already exists or violates a data constraint"));
    }

    /**
     * 把缺少必填查询参数映射为 HTTP 400
     *
     * @param e 缺少请求参数异常
     * @return 包含参数名称的错误响应
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParam(MissingServletRequestParameterException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(400, "missing parameter: " + e.getParameterName()));
    }

    /**
     * 把无法反序列化的请求体映射为 HTTP 400
     *
     * @param e 请求体读取异常
     * @return 不暴露内部反序列化细节的错误响应
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(400, "request body is malformed or has invalid field types"));
    }

    /**
     * 把路径或查询参数类型不匹配映射为 HTTP 400
     *
     * @param e 参数类型不匹配异常
     * @return 包含参数名称的错误响应
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(400, "invalid parameter: " + e.getName()));
    }

    /**
     * 把不存在的静态资源或路径映射为 HTTP 404，不记录异常堆栈
     *
     * @param e 资源不存在异常
     * @return 资源不存在响应
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail(404, "resource not found"));
    }

    /**
     * 把引用不存在的规则映射为 HTTP 404
     * <p>
     * 表达式引用了当前命名空间中未注册的规则属于调用方引用错误，
     * 与规则执行失败区分处理
     *
     * @param e 规则未注册异常
     * @return 包含规则标识的资源不存在响应
     */
    @ExceptionHandler(RuleNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleRuleNotFound(RuleNotFoundException e) {
        log.warn("rule not found: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail(404, e.getMessage()));
    }

    /**
     * 把规则执行失败映射为 HTTP 400
     *
     * @param e 规则评估异常
     * @return 包含失败规则标识的错误响应
     */
    @ExceptionHandler(RuleEvalException.class)
    public ResponseEntity<ApiResponse<Void>> handleRuleEval(RuleEvalException e) {
        log.warn("rule eval failed: ruleId={}, msg={}", e.getRuleId(), e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(400, "rule evaluation failed: " + e.getRuleId()));
    }

    /**
     * 处理未被其他分支识别的服务端异常
     *
     * @param e 未处理异常
     * @return 不包含内部实现细节的 HTTP 500 响应
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleOther(Exception e) {
        // 服务端记录完整异常，客户端只返回通用信息，避免泄露异常类名和内部实现细节
        log.error("unhandled error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail(500, "internal server error"));
    }

    /**
     * 把 4xx 业务错误码映射为 HTTP 状态，其他错误码回退到 HTTP 400
     *
     * @param code 业务错误码
     * @return 对应 HTTP 状态
     */
    private static HttpStatus toHttpStatus(int code) {
        HttpStatus mapped = HttpStatus.resolve(code);
        if (mapped != null && mapped.is4xxClientError()) {
            return mapped;
        }
        return HttpStatus.BAD_REQUEST;
    }
}
