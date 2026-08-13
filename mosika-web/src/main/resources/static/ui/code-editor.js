/**
 * Shared JavaScript expression parsing and formatting for rule and UDF editors.
 */
(function (root) {
    "use strict";

    const ECMA_VERSION = 2020;
    const DEFAULT_FORMAT_OPTIONS = {
        indent_size: 2,
        brace_style: "collapse",
        end_with_newline: false,
        wrap_line_length: 0
    };

    function parseExpression(value) {
        const source = String(value ?? "").trim();
        if (!source) {
            return { ok: true, empty: true, source, ast: null, expression: null };
        }
        if (!root.acorn) {
            return {
                ok: false,
                empty: false,
                source,
                message: "语法解析组件未加载",
                line: 0,
                column: 0
            };
        }
        try {
            const ast = root.acorn.parse(`(\n${source}\n)`, { ecmaVersion: ECMA_VERSION });
            return {
                ok: true,
                empty: false,
                source,
                ast,
                expression: ast.body[0] && ast.body[0].expression
            };
        } catch (error) {
            const location = error.loc || { line: 2, column: 0 };
            return {
                ok: false,
                empty: false,
                source,
                error,
                message: String(error.message || "语法错误").replace(/\s*\(\d+:\d+\)\s*$/, ""),
                line: Math.max(0, location.line - 2),
                column: Math.max(0, location.column | 0)
            };
        }
    }

    function toLintAnnotations(result, codeMirror) {
        if (!result || result.ok || !codeMirror) {
            return [];
        }
        return [{
            message: result.message,
            severity: "error",
            from: codeMirror.Pos(result.line, result.column),
            to: codeMirror.Pos(result.line, result.column + 1)
        }];
    }

    function formatExpression(value, options) {
        const parsed = parseExpression(value);
        if (!parsed.ok) {
            throw new Error(parsed.message);
        }
        const beautify = root.js_beautify || (root.beautify && root.beautify.js);
        if (typeof beautify !== "function") {
            throw new Error("格式化组件未加载");
        }
        return beautify(parsed.source, Object.assign({}, DEFAULT_FORMAT_OPTIONS, options || {}));
    }

    root.MosikaCode = {
        ecmaVersion: ECMA_VERSION,
        parseExpression,
        toLintAnnotations,
        formatExpression
    };
})(window);
