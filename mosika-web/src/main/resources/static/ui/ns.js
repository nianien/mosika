/**
 * Mosika 命名空间上下文。
 *
 * 命名空间是运行态隔离边界：规则、场景、UDF 都归属于某个命名空间，跨命名空间不可引用、
 * 不共享 UDF。因此所有列表页在发起第一个数据请求之前，必须先 await MosikaNs.boot()
 * 拿到当前命名空间；未选择时统一跳转门禁页 /namespaces。
 *
 * 解析优先级：URL 的 ?ns= → localStorage → 都没有则跳门禁页。
 * 选择器样式由 ui-common.css 统一提供。
 */
(function (root) {
    "use strict";

    const STORE_KEY = "mosika.ns";
    const GATE_PATH = "/namespaces";
    const MANAGE_VALUE = "__manage__";

    const state = { code: null, list: [] };

    const readUrl = () => new URLSearchParams(location.search).get("ns");

    function readStore() {
        try {
            return localStorage.getItem(STORE_KEY);
        } catch (_) {
            return null;
        }
    }

    function writeStore(code) {
        try {
            localStorage.setItem(STORE_KEY, code);
        } catch (_) {
            /* 隐私模式下禁写 localStorage 时退化为仅依赖 URL */
        }
    }

    /** 把当前命名空间同步进地址栏，保持链接可分享、可刷新 */
    function syncUrl(code) {
        const usp = new URLSearchParams(location.search);
        if (usp.get("ns") === code) {
            return;
        }
        usp.set("ns", code);
        history.replaceState(null, "", `${location.pathname}?${usp.toString()}`);
    }

    /** 给站内路径拼上当前命名空间 */
    function link(path) {
        if (!state.code) {
            return path;
        }
        return `${path}${path.includes("?") ? "&" : "?"}ns=${encodeURIComponent(state.code)}`;
    }

    /** 切换命名空间：整页重载，确保列表、筛选、分页、引用名缓存全部重置 */
    function switchTo(code) {
        writeStore(code);
        const usp = new URLSearchParams(location.search);
        usp.set("ns", code);
        location.href = `${location.pathname}?${usp.toString()}`;
    }

    /** 画布页由所属场景反向确定命名空间，不经过门禁 */
    function adopt(code) {
        if (!code) {
            return;
        }
        state.code = code;
        writeStore(code);
        syncUrl(code);
    }

    function gate() {
        location.replace(GATE_PATH);
    }

    /**
     * 解析当前命名空间。命中返回 {code, list}；未命中直接跳门禁页并返回 null，
     * 调用方拿到 null 后应立即停止后续渲染。
     */
    async function boot() {
        let all;
        try {
            all = await root.MosikaApi.listNamespaces();
        } catch (e) {
            return null;
        }
        state.list = (all || []).filter((n) => n.status === 1);
        const wanted = readUrl() || readStore();
        const hit = state.list.find((n) => n.code === wanted);
        if (!hit) {
            gate();
            return null;
        }
        state.code = hit.code;
        writeStore(hit.code);
        syncUrl(hit.code);
        return { code: state.code, list: state.list };
    }

    /** 在指定容器内渲染命名空间选择器，末项为管理入口 */
    function mountSelector(container) {
        if (!container || !state.code) {
            return;
        }
        const box = document.createElement("div");
        box.className = "ns-picker";
        const sep = document.createElement("span");
        sep.className = "ns-sep";
        sep.textContent = "/";
        const select = document.createElement("select");
        select.id = "nsSelect";
        state.list.forEach((n) => {
            const opt = document.createElement("option");
            opt.value = n.code;
            opt.textContent = n.code;
            opt.title = n.name || n.code;
            if (n.code === state.code) {
                opt.selected = true;
                select.title = n.name ? `当前命名空间：${n.name}（${n.code}）` : `当前命名空间：${n.code}`;
            }
            select.appendChild(opt);
        });
        const manage = document.createElement("option");
        manage.value = MANAGE_VALUE;
        manage.textContent = "管理…";
        select.appendChild(manage);
        select.addEventListener("change", () => {
            if (select.value === MANAGE_VALUE) {
                location.href = GATE_PATH;
                return;
            }
            if (select.value !== state.code) {
                switchTo(select.value);
            }
        });
        box.appendChild(sep);
        box.appendChild(select);
        container.appendChild(box);
    }

    /** 让站内 tab 链接携带当前命名空间 */
    function applyTabs() {
        if (!state.code) {
            return;
        }
        document.querySelectorAll("a.tab[href]").forEach((a) => {
            const raw = a.getAttribute("href");
            if (raw && raw.startsWith("/")) {
                a.setAttribute("href", link(raw));
            }
        });
    }

    /** 渲染只读命名空间标签，用于新建弹窗内展示归属 */
    function tagHtml() {
        return `<span class="ns-tag">${state.code || ""}</span>`;
    }

    root.MosikaNs = {
        boot,
        adopt,
        link,
        switchTo,
        mountSelector,
        applyTabs,
        tagHtml,
        gatePath: GATE_PATH,
        code: () => state.code,
        list: () => state.list
    };
})(window);
