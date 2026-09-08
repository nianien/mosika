/**
 * Shared DOM and interaction helpers for Mosika's static control-plane pages.
 */
(function (root) {
    "use strict";

    function query(selector, scope) {
        return (scope || document).querySelector(selector);
    }

    function escapeHtml(value) {
        return String(value ?? "").replace(/[&<>'"]/g, (character) => ({
            "&": "&amp;",
            "<": "&lt;",
            ">": "&gt;",
            "'": "&#39;",
            '"': "&quot;"
        })[character]);
    }

    function debounce(fn, wait) {
        let timer = null;
        return function debounced(...args) {
            clearTimeout(timer);
            timer = setTimeout(() => fn.apply(this, args), wait ?? 250);
        };
    }

    function createToast(selector, options) {
        const settings = options || {};
        const duration = settings.duration ?? 2200;
        let timer = null;
        return function showToast(message, type) {
            const element = typeof selector === "string" ? query(selector) : selector;
            if (!element) {
                return;
            }
            element.textContent = String(message ?? "");
            element.classList.remove("is-error", "is-success");
            if (type === "error" || type === "success") {
                element.classList.add(`is-${type}`);
            }
            element.classList.add("show");
            clearTimeout(timer);
            timer = setTimeout(() => {
                element.classList.remove("show", "is-error", "is-success");
            }, duration);
        };
    }

    function setBusy(button, busy, busyText) {
        if (!button) {
            return;
        }
        if (busy) {
            button.dataset.idleText = button.textContent;
            if (busyText) {
                button.textContent = busyText;
            }
            button.disabled = true;
            button.setAttribute("aria-busy", "true");
            return;
        }
        if (button.dataset.idleText !== undefined) {
            button.textContent = button.dataset.idleText;
            delete button.dataset.idleText;
        }
        button.disabled = false;
        button.removeAttribute("aria-busy");
    }

    function createPager(selector, onChange, pageSize) {
        const element = typeof selector === "string" ? query(selector) : selector;
        element.innerHTML = '<span data-page-summary></span><span><button type="button" data-page-previous>上一页</button> <button type="button" data-page-next>下一页</button></span>';
        const summary = query("[data-page-summary]", element);
        const previous = query("[data-page-previous]", element);
        const next = query("[data-page-next]", element);
        const size = pageSize ?? 20;
        let pageNumber = 1;
        let total = 0;

        function pageCount() {
            return Math.max(1, Math.ceil(total / size));
        }

        function render() {
            const pages = pageCount();
            element.hidden = false;
            summary.textContent = `第 ${pageNumber} / ${pages} 页 · 共 ${total} 条`;
            previous.disabled = pageNumber <= 1;
            next.disabled = pageNumber >= pages;
        }

        function change(page) {
            if (page < 1 || page > pageCount() || page === pageNumber) {
                return;
            }
            pageNumber = page;
            render();
            onChange();
        }

        previous.addEventListener("click", () => change(pageNumber - 1));
        next.addEventListener("click", () => change(pageNumber + 1));
        render();

        return {
            get pageNumber() {
                return pageNumber;
            },
            get pageSize() {
                return size;
            },
            reset() {
                pageNumber = 1;
                render();
            },
            update(nextTotal) {
                const previousPage = pageNumber;
                total = nextTotal;
                pageNumber = Math.min(pageNumber, pageCount());
                render();
                return pageNumber !== previousPage;
            },
            hide() {
                element.hidden = true;
            }
        };
    }

    function createPagedTable(options) {
        const rows = typeof options.rows === "string" ? query(options.rows) : options.rows;
        const columns = options.columns;
        let sequence = 0;
        let pager;

        async function load() {
            const current = ++sequence;
            rows.innerHTML = `<tr><td colspan="${columns}"><div class="loading">加载中…</div></td></tr>`;
            try {
                const page = await options.fetchPage({
                    pageNumber: pager.pageNumber,
                    pageSize: pager.pageSize
                }) || {};
                if (current !== sequence) return;
                if (pager.update(page.total || 0)) return load();
                options.renderRows(page.items || [], page);
            } catch (error) {
                if (current !== sequence) return;
                rows.innerHTML = `<tr><td colspan="${columns}"><div class="empty">加载失败：${escapeHtml(error.message)}</div></td></tr>`;
                pager.hide();
            }
        }

        pager = createPager(options.pager, load, options.pageSize);
        return {
            pager,
            load,
            reset() {
                pager.reset();
                return load();
            }
        };
    }

    let confirmDialog;
    let confirmPromise;

    function createConfirmDialog() {
        const dialog = document.createElement("dialog");
        dialog.className = "ui-confirm-dialog";
        dialog.innerHTML = `
            <form method="dialog">
                <div class="dlg-head"><div class="dlg-title" data-confirm-title></div></div>
                <div class="dlg-body"><p class="confirm-message" data-confirm-message></p></div>
                <div class="dlg-foot">
                    <button class="btn" value="cancel">取消</button>
                    <button class="btn" value="confirm" data-confirm-button></button>
                </div>
            </form>`;
        document.body.appendChild(dialog);
        return dialog;
    }

    function confirmAction(message, options) {
        const settings = options || {};
        confirmDialog ||= createConfirmDialog();
        if (confirmDialog.open) return confirmPromise;
        query("[data-confirm-title]", confirmDialog).textContent = settings.title || "确认操作";
        query("[data-confirm-message]", confirmDialog).textContent = String(message ?? "");
        const button = query("[data-confirm-button]", confirmDialog);
        button.textContent = settings.confirmLabel || "确认";
        button.className = `btn ${settings.danger ? "danger" : "primary"}`;
        confirmDialog.returnValue = "";
        confirmPromise = new Promise((resolve) => {
            confirmDialog.addEventListener("close", () => {
                const confirmed = confirmDialog.returnValue === "confirm";
                confirmPromise = null;
                resolve(confirmed);
            }, { once: true });
            confirmDialog.showModal();
        });
        return confirmPromise;
    }

    root.MosikaUi = {
        $: query,
        escapeHtml,
        debounce,
        createToast,
        setBusy,
        createPager,
        createPagedTable,
        confirmAction
    };
})(window);
