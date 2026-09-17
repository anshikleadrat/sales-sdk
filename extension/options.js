const msg = document.getElementById('msg');
const statusBadge = document.getElementById('status');

function show(text, kind) {
    msg.textContent = text;
    msg.className = 'msg show ' + (kind || 'ok');
}

function send(message) {
    return chrome.runtime.sendMessage(message);
}

async function refresh() {
    const status = await send({ type: 'status' });
    if (!status.ok) return;
    document.getElementById('base-url').value = status.body.baseUrl || '';
    statusBadge.textContent = status.body.authenticated ? 'authenticated' : (status.body.configured ? 'signed out' : 'not configured');
    statusBadge.className = 'badge ' + (status.body.authenticated ? 'on' : 'off');
}

document.getElementById('sign-in').addEventListener('click', async () => {
    const result = await send({
        type: 'auth',
        baseUrl: document.getElementById('base-url').value,
        password: document.getElementById('password').value
    });
    document.getElementById('password').value = '';
    if (!result.ok) return show(result.error, 'err');
    show('Signed in for ' + result.body.expiresIn + ' seconds.', 'ok');
    refresh();
});

document.getElementById('sign-out').addEventListener('click', async () => {
    await send({ type: 'signOut' });
    show('Token cleared.', 'ok');
    refresh();
});

document.getElementById('test').addEventListener('click', async () => {
    const result = await send({ type: 'schema' });
    if (!result.ok) return show(result.error, 'err');
    const enabled = result.body.entities.filter(e => e.enabled && e.present).length;
    show('Connected. ' + enabled + ' entities enabled, config version ' + result.body.configVersion + '.', 'ok');
});

refresh();
