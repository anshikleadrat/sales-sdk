const AiSdk = (() => {
    const TOKEN_KEY = 'aiSdkToken';
    const base = document.body.dataset.base || '/ai-sdk';

    function token() {
        try { return sessionStorage.getItem(TOKEN_KEY); } catch (e) { return null; }
    }

    function setToken(value) {
        try { sessionStorage.setItem(TOKEN_KEY, value); } catch (e) { }
        renderAuthBadge();
    }

    function clearToken() {
        try { sessionStorage.removeItem(TOKEN_KEY); } catch (e) { }
        renderAuthBadge();
    }

    async function request(path, options = {}) {
        const headers = Object.assign({ 'Content-Type': 'application/json' }, options.headers || {});
        const current = token();
        if (current) headers['Authorization'] = 'Bearer ' + current;
        const response = await fetch(base + path, Object.assign({}, options, { headers }));
        const text = await response.text();
        let body = null;
        if (text) {
            try { body = JSON.parse(text); } catch (e) { body = { raw: text }; }
        }
        if (response.status === 401) {
            clearToken();
            throw new Error((body && body.error) || 'Session expired, sign in again');
        }
        if (!response.ok) {
            throw new Error((body && body.error) || ('Request failed with status ' + response.status));
        }
        return body;
    }

    function message(el, text, kind) {
        el.textContent = text;
        el.className = 'msg show ' + (kind || 'ok');
    }

    function hide(el) {
        el.className = 'msg';
    }

    function renderAuthBadge() {
        const badge = document.getElementById('auth-badge');
        if (!badge) return;
        if (token()) {
            badge.textContent = 'Authenticated';
            badge.className = 'badge on';
        } else {
            badge.textContent = 'No token';
            badge.className = 'badge off';
        }
    }

    function requireToken(redirect = true) {
        if (token()) return true;
        if (redirect) window.location.href = base + '/auth';
        return false;
    }

    function markActiveNav() {
        const path = window.location.pathname;
        document.querySelectorAll('header.topbar nav a').forEach(link => {
            if (link.getAttribute('href') === path) link.classList.add('active');
        });
    }

    function initNav() {
        markActiveNav();
        renderAuthBadge();
        const signOut = document.getElementById('sign-out');
        if (signOut) {
            signOut.addEventListener('click', () => {
                clearToken();
                window.location.href = base + '/auth';
            });
        }
    }

    document.addEventListener('DOMContentLoaded', initNav);

    return { base, token, setToken, clearToken, request, message, hide, requireToken, renderAuthBadge };
})();
