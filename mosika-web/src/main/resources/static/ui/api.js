/**
 * Mosika 后端 API 轻客户端。统一走 /api，解包 ApiResponse（code=0 视为成功）。
 * 供画布(open/save)、规则流列表页、原子规则库页共用。
 */
(function (root) {
    "use strict";
    const BASE = "/api";

    async function request(method, path, body) {
        const res = await fetch(BASE + path, {
            method,
            headers: body != null ? { "Content-Type": "application/json" } : undefined,
            body: body != null ? JSON.stringify(body) : undefined
        });
        let payload = null;
        try { payload = await res.json(); } catch (_) { payload = null; }
        if (!res.ok) {
            const msg = payload && payload.message ? payload.message : `HTTP ${res.status}`;
            throw new Error(msg);
        }
        if (payload && typeof payload.code === "number" && payload.code !== 0) {
            throw new Error(payload.message || `业务错误 ${payload.code}`);
        }
        return payload ? payload.data : null;
    }

    const qs = (params) => {
        const usp = new URLSearchParams();
        Object.entries(params || {}).forEach(([k, v]) => { if (v !== undefined && v !== null && v !== "") usp.set(k, v); });
        const s = usp.toString();
        return s ? `?${s}` : "";
    };

    root.MosikaApi = {
        // flows
        listFlows: (params) => request("GET", `/flows${qs(params)}`),
        getFlow: (flowId) => request("GET", `/flows/${flowId}`),
        createFlow: (flow) => request("POST", "/flows", flow),
        updateFlow: (flowId, flow) => request("PUT", `/flows/${flowId}`, flow),
        publishFlow: (flowId, flow) => request("POST", `/flows/${flowId}/publish`, flow),
        updateFlowMeta: (flowId, meta) => request("PUT", `/flows/${flowId}/meta`, meta),
        disableFlow: (flowId, version) => request("DELETE", `/flows/${flowId}${qs({ version })}`),
        listFlowReferences: (namespace) => request("GET", `/flows/references/active${qs({ namespace })}`),
        // rules
        listRules: (params) => request("GET", `/rules${qs(params)}`),
        listRuleReferences: (namespace) => request("GET", `/rules/references${qs({ namespace })}`),
        getRuleRefCounts: (namespace) => request("GET", `/rules/ref-counts${qs({ namespace })}`),
        createRule: (rule) => request("POST", "/rules", rule),
        updateRule: (ruleId, rule) => request("PUT", `/rules/${ruleId}`, rule),
        enableRule: (ruleId, version) => request("POST", `/rules/${ruleId}/enable${qs({ version })}`),
        disableRule: (ruleId, version) => request("DELETE", `/rules/${ruleId}${qs({ version })}`),
        // namespaces
        listNamespaces: () => request("GET", "/namespaces"),
        createNamespace: (namespace) => request("POST", "/namespaces", namespace),
        updateNamespace: (code, meta) => request("PUT", `/namespaces/${encodeURIComponent(code)}`, meta),
        disableNamespace: (code) => request("POST", `/namespaces/${encodeURIComponent(code)}/disable`),
        enableNamespace: (code) => request("POST", `/namespaces/${encodeURIComponent(code)}/enable`),
        // user-registered JavaScript UDFs
        listUdfs: (params) => request("GET", `/udfs${qs(params)}`),
        createUdf: (udf) => request("POST", "/udfs", udf),
        updateUdf: (id, udf) => request("PUT", `/udfs/${id}`, udf),
        enableUdf: (id, version) => request("POST", `/udfs/${id}/enable${qs({ version })}`),
        disableUdf: (id, version) => request("DELETE", `/udfs/${id}${qs({ version })}`),
        // eval
        tryRule: (body) => request("POST", "/eval/try", body),
        tryFlow: (body) => request("POST", "/eval/flow/try", body)
    };
})(window);
