/**
 * Mousika 后端 API 轻客户端。统一走 /api，解包 ApiResponse（code=0 视为成功）。
 * 供画布(open/save)、场景列表页、原子规则库页共用。
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

    root.MousikaApi = {
        // flows
        listFlows: (params) => request("GET", `/flows${qs(params)}`),
        getFlow: (id) => request("GET", `/flows/${id}`),
        createFlow: (flow) => request("POST", "/flows", flow),
        updateFlow: (id, flow) => request("PUT", `/flows/${id}`, flow),
        publishFlow: (id, flow) => request("POST", `/flows/${id}/publish`, flow),
        updateFlowMeta: (id, meta) => request("PUT", `/flows/${id}/meta`, meta),
        disableFlow: (id, version) => request("DELETE", `/flows/${id}${qs({ version })}`),
        validateFlow: (flow) => request("POST", "/flows/validate", flow),
        // rules
        listRules: (params) => request("GET", `/rules${qs(params)}`),
        getRuleRefCounts: () => request("GET", "/rules/ref-counts"),
        getRule: (id) => request("GET", `/rules/${id}`),
        createRule: (rule) => request("POST", "/rules", rule),
        updateRule: (id, rule) => request("PUT", `/rules/${id}`, rule),
        enableRule: (id, version) => request("POST", `/rules/${id}/enable${qs({ version })}`),
        disableRule: (id, version) => request("DELETE", `/rules/${id}${qs({ version })}`)
    };
})(window);
