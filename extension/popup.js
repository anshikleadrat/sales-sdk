const msg = document.getElementById('msg');
const authSection = document.getElementById('auth');
const askSection = document.getElementById('ask');
const statusBadge = document.getElementById('status');

function show(text, kind) {
    msg.textContent = text;
    msg.className = 'msg show ' + (kind || 'ok');
}

function hide() {
    msg.className = 'msg';
}

const copyBtn = document.getElementById('copy-answer');
let lastAnswer = '';

function renderAnswer(text) {
    const answer = document.getElementById('answer');
    lastAnswer = text || '';
    try {
        answer.innerHTML = AiSdkMarkdown.render(lastAnswer);
        answer.className = 'answer answer-md';
    } catch (e) {
        answer.textContent = lastAnswer;
        answer.className = 'answer';
    }
    copyBtn.className = lastAnswer ? 'secondary' : 'secondary hidden';
    copyBtn.textContent = 'Copy';
}

copyBtn.addEventListener('click', async () => {
    try {
        await navigator.clipboard.writeText(lastAnswer);
        copyBtn.textContent = 'Copied';
    } catch (e) {
        copyBtn.textContent = 'Failed';
    }
    setTimeout(() => { copyBtn.textContent = 'Copy'; }, 1500);
});

function send(message) {
    return chrome.runtime.sendMessage(message);
}

async function detectFromPage() {
    try {
        const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
        if (!tab || !tab.id) return [];
        const response = await chrome.tabs.sendMessage(tab.id, { type: 'detectTargets' });
        return (response && response.targets) || [];
    } catch (e) {
        return [];
    }
}

async function loadSchema() {
    const result = await send({ type: 'schema' });
    if (!result.ok) {
        if (result.needsAuth || result.needsConfig) return showAuth(result.error);
        return show(result.error, 'err');
    }
    const enabled = result.body.entities.filter(e => e.enabled && e.present).map(e => e.entityName);
    const select = document.getElementById('entity');
    select.innerHTML = enabled.map(e => `<option value="${e}">${e}</option>`).join('');
    if (enabled.length === 0) {
        show('No entities are enabled in that deployment yet.', 'warn');
    }

    const config = await send({ type: 'config' });
    if (config.ok && config.body.lastEntity && enabled.includes(config.body.lastEntity)) {
        select.value = config.body.lastEntity;
    }

    const detected = await detectFromPage();
    if (detected.length > 0) {
        const first = detected.find(t => enabled.includes(t.entity)) || detected[0];
        if (enabled.includes(first.entity)) select.value = first.entity;
        document.getElementById('record-id').value = first.id;
        const note = document.getElementById('detected');
        note.textContent = 'Detected on this page: ' + detected.map(t => t.entity + ' ' + t.id).join(', ');
        note.classList.remove('hidden');
    }
}

function showAuth(reason) {
    authSection.classList.remove('hidden');
    askSection.classList.add('hidden');
    statusBadge.textContent = 'signed out';
    statusBadge.className = 'badge off';
    if (reason) show(reason, 'warn');
}

async function showAsk() {
    authSection.classList.add('hidden');
    askSection.classList.remove('hidden');
    statusBadge.textContent = 'authenticated';
    statusBadge.className = 'badge on';
    await loadSchema();
}

async function init() {
    const status = await send({ type: 'status' });
    const config = await send({ type: 'config' });
    if (config.ok && config.body.baseUrl) {
        document.getElementById('base-url').value = config.body.baseUrl;
    }
    if (status.ok && status.body.authenticated) {
        await showAsk();
    } else {
        showAuth(status.ok && !status.body.configured ? 'Set the deployment URL and sign in.' : null);
    }
}

document.getElementById('sign-in').addEventListener('click', async () => {
    hide();
    const result = await send({
        type: 'auth',
        baseUrl: document.getElementById('base-url').value,
        password: document.getElementById('password').value
    });
    if (!result.ok) return show(result.error, 'err');
    document.getElementById('password').value = '';
    show('Signed in for ' + result.body.expiresIn + ' seconds.', 'ok');
    await showAsk();
});

document.getElementById('sign-out').addEventListener('click', async () => {
    await send({ type: 'signOut' });
    showAuth('Signed out.');
});

document.getElementById('options').addEventListener('click', () => chrome.runtime.openOptionsPage());

document.getElementById('run').addEventListener('click', async () => {
    hide();
    const entity = document.getElementById('entity').value;
    const id = document.getElementById('record-id').value.trim();
    const question = document.getElementById('question').value.trim();
    if (!entity || !id) return show('Choose an entity and a record id.', 'err');
    if (!question) return show('A question is required.', 'err');

    const button = document.getElementById('run');
    button.disabled = true;
    document.getElementById('meta').textContent = 'thinking…';
    const result = await send({ type: 'query', payload: { question, targets: [{ entity, id }] } });
    button.disabled = false;

    if (!result.ok) {
        document.getElementById('meta').textContent = '';
        if (result.needsAuth) return showAuth(result.error);
        return show(result.error, 'err');
    }
    renderAnswer(result.body.answer);
    const records = document.getElementById('records');
    records.querySelector('pre').textContent = JSON.stringify(result.body.data, null, 2);
    records.classList.remove('hidden');
    document.getElementById('meta').textContent =
        `${result.body.meta.cached ? 'cached' : 'fresh'} · parents ${result.body.meta.parentDepth} · children ${result.body.meta.childDepth} · ${result.body.meta.latencyMs} ms`;
});

init();
