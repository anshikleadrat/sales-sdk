const AiSdk = (() => {
    const TOKEN_KEY = 'aiSdkToken';
    const EXPIRY_KEY = 'aiSdkTokenExpiresAt';
    const base = document.body.dataset.base || '/ai-sdk';

    function store() {
        try { return window.localStorage; } catch (e) { return null; }
    }

    function token() {
        const s = store();
        if (!s) return null;
        try {
            const value = s.getItem(TOKEN_KEY);
            if (!value) return null;
            const expiresAt = Number(s.getItem(EXPIRY_KEY) || 0);
            if (expiresAt && Date.now() >= expiresAt) {
                clearToken();
                return null;
            }
            return value;
        } catch (e) {
            return null;
        }
    }

    function setToken(value, expiresInSeconds) {
        const s = store();
        if (s) {
            try {
                s.setItem(TOKEN_KEY, value);
                const seconds = Number(expiresInSeconds);
                if (seconds > 0) {
                    s.setItem(EXPIRY_KEY, String(Date.now() + seconds * 1000));
                } else {
                    s.removeItem(EXPIRY_KEY);
                }
            } catch (e) { }
        }
        renderAuthBadge();
    }

    function clearToken() {
        const s = store();
        if (s) {
            try {
                s.removeItem(TOKEN_KEY);
                s.removeItem(EXPIRY_KEY);
            } catch (e) { }
        }
        renderAuthBadge();
    }

    function expiresInSeconds() {
        const s = store();
        if (!s) return 0;
        try {
            const expiresAt = Number(s.getItem(EXPIRY_KEY) || 0);
            if (!expiresAt) return 0;
            return Math.max(0, Math.round((expiresAt - Date.now()) / 1000));
        } catch (e) {
            return 0;
        }
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
        const signOut = document.getElementById('sign-out');
        const signedIn = !!token();
        if (signOut) signOut.style.display = signedIn ? '' : 'none';
        if (!badge) return;
        if (signedIn) {
            badge.textContent = 'Signed in';
            badge.className = 'badge on';
        } else {
            badge.textContent = 'Signed out';
            badge.className = 'badge off';
        }
    }

    function requireToken(redirect = true) {
        if (token()) return true;
        if (redirect) {
            window.location.href = base + '/auth?next=' + encodeURIComponent(window.location.pathname);
        }
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

    return { base, token, setToken, clearToken, expiresInSeconds, request, message, hide, requireToken, renderAuthBadge };
})();
