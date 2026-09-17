const STORAGE_KEYS = ['baseUrl', 'token', 'expiresAt', 'lastEntity'];

async function config() {
    const stored = await chrome.storage.local.get(STORAGE_KEYS);
    return {
        baseUrl: (stored.baseUrl || '').replace(/\/$/, ''),
        token: stored.token || null,
        expiresAt: stored.expiresAt || 0,
        lastEntity: stored.lastEntity || null
    };
}

function tokenValid(current) {
    return Boolean(current.token) && (!current.expiresAt || current.expiresAt > Date.now() + 5000);
}

async function call(path, init) {
    const current = await config();
    if (!current.baseUrl) {
        return { ok: false, needsConfig: true, error: 'No deployment URL configured' };
    }
    if (!tokenValid(current)) {
        return { ok: false, needsAuth: true, error: 'Sign in to the deployment first' };
    }
    let response;
    try {
        response = await fetch(current.baseUrl + path, Object.assign({}, init, {
            headers: Object.assign({
                'Content-Type': 'application/json',
                'Authorization': 'Bearer ' + current.token
            }, (init && init.headers) || {})
        }));
    } catch (e) {
        return { ok: false, error: 'Could not reach ' + current.baseUrl + ' (' + e.message + ')' };
    }
    const text = await response.text();
    let body = null;
    if (text) {
        try { body = JSON.parse(text); } catch (e) { body = { raw: text }; }
    }
    if (response.status === 401) {
        await chrome.storage.local.remove(['token', 'expiresAt']);
        return { ok: false, needsAuth: true, error: (body && body.error) || 'Session expired' };
    }
    if (!response.ok) {
        return { ok: false, error: (body && body.error) || ('Request failed with status ' + response.status) };
    }
    return { ok: true, body };
}

async function authenticate(baseUrl, password) {
    const normalized = (baseUrl || '').replace(/\/$/, '');
    let response;
    try {
        response = await fetch(normalized + '/auth/token', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ password })
        });
    } catch (e) {
        return { ok: false, error: 'Could not reach ' + normalized + ' (' + e.message + ')' };
    }
    const body = await response.json().catch(() => null);
    if (!response.ok) {
        return { ok: false, error: (body && body.error) || ('Authentication failed with status ' + response.status) };
    }
    await chrome.storage.local.set({
        baseUrl: normalized,
        token: body.token,
        expiresAt: Date.now() + (body.expiresIn || 3600) * 1000
    });
    return { ok: true, body: { expiresIn: body.expiresIn } };
}

chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
    (async () => {
        switch (message.type) {
            case 'config':
                sendResponse({ ok: true, body: await config() });
                break;
            case 'status': {
                const current = await config();
                sendResponse({ ok: true, body: { configured: Boolean(current.baseUrl), authenticated: tokenValid(current), baseUrl: current.baseUrl } });
                break;
            }
            case 'auth':
                sendResponse(await authenticate(message.baseUrl, message.password));
                break;
            case 'signOut':
                await chrome.storage.local.remove(['token', 'expiresAt']);
                sendResponse({ ok: true });
                break;
            case 'schema':
                sendResponse(await call('/configure/schema', { method: 'GET' }));
                break;
            case 'query':
                if (message.payload && message.payload.targets && message.payload.targets[0]) {
                    await chrome.storage.local.set({ lastEntity: message.payload.targets[0].entity });
                }
                sendResponse(await call('/query', { method: 'POST', body: JSON.stringify(message.payload) }));
                break;
            default:
                sendResponse({ ok: false, error: 'Unknown message type' });
        }
    })();
    return true;
});
