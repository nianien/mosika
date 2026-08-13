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

    root.MosikaUi = {
        $: query,
        escapeHtml,
        debounce,
        createToast,
        setBusy
    };
})(window);
