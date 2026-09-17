(function () {
    'use strict';

    const STYLES = `
        * { box-sizing: border-box; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; }
        .launcher {
            position: fixed; right: 20px; bottom: 20px; z-index: 2147483000;
            background: #4f8cff; color: #fff; border: none; border-radius: 999px;
            padding: 11px 17px; font-size: 13px; font-weight: 600; cursor: pointer;
            box-shadow: 0 8px 24px rgba(0,0,0,.28);
        }
        .panel {
            position: fixed; right: 20px; bottom: 20px; z-index: 2147483000; width: 380px;
            max-width: calc(100vw - 32px); max-height: calc(100vh - 40px); display: none;
            flex-direction: column; background: #171b21; color: #e6eaef; border: 1px solid #2a323c;
            border-radius: 12px; box-shadow: 0 18px 50px rgba(0,0,0,.45); overflow: hidden; font-size: 13px;
        }
        .panel.open { display: flex; }
        .head { display: flex; align-items: center; gap: 8px; padding: 12px 14px; background: #1e242c; border-bottom: 1px solid #2a323c; }
        .head strong { flex: 1; font-size: 13px; }
        .close { background: none; border: none; color: #9aa5b1; font-size: 17px; cursor: pointer; }
        .body { padding: 12px 14px; overflow: auto; }
        .chip { display: inline-block; background: #1e242c; border: 1px solid #2a323c; border-radius: 999px; padding: 3px 9px; font-size: 11px; color: #9aa5b1; margin: 0 4px 8px 0; }
        .chip strong { color: #e6eaef; }
        textarea { width: 100%; min-height: 62px; background: #1e242c; color: #e6eaef; border: 1px solid #2a323c; border-radius: 8px; padding: 9px 11px; font-size: 13px; resize: vertical; }
        .ask { margin-top: 10px; background: #4f8cff; border: none; color: #fff; font-weight: 600; padding: 8px 15px; border-radius: 8px; cursor: pointer; }
        .ask:disabled { opacity: .55; cursor: not-allowed; }
        .meta { margin-left: 10px; font-size: 11px; color: #9aa5b1; }
        .answer { white-space: pre-wrap; margin-top: 12px; padding: 11px; background: #1e242c; border: 1px solid #2a323c; border-radius: 8px; line-height: 1.5; }
        .error { margin-top: 10px; padding: 9px 11px; border-radius: 8px; background: rgba(248,81,73,.12); color: #f85149; font-size: 12px; }
        .hidden { display: none; }
    `;

    function detectTargets() {
        return Array.from(document.querySelectorAll('[data-ai-sdk-entity][data-ai-sdk-id]'))
            .map(el => ({ entity: el.dataset.aiSdkEntity, id: el.dataset.aiSdkId }))
            .filter(t => t.entity && t.id);
    }

    function escapeHtml(value) {
        return (value === null || value === undefined ? '' : String(value))
            .replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;');
    }

    let ui = null;

    function mount(targets) {
        if (ui) return ui;
        const host = document.createElement('div');
        host.className = 'ai-sdk-ext-host';
        document.body.appendChild(host);
        const root = host.attachShadow({ mode: 'open' });

        const style = document.createElement('style');
        style.textContent = STYLES;
        root.appendChild(style);

        const wrapper = document.createElement('div');
        wrapper.innerHTML = `
            <button class="launcher">Ask AI</button>
            <div class="panel">
                <div class="head"><strong>Ask about this record</strong><button class="close">&times;</button></div>
                <div class="body">
                    <div class="targets">${targets.map(t => `<span class="chip"><strong>${escapeHtml(t.entity)}</strong> ${escapeHtml(t.id)}</span>`).join('')}</div>
                    <textarea placeholder="Summarize this record and flag anything unusual"></textarea>
                    <div><button class="ask">Ask</button><span class="meta"></span></div>
                    <div class="error hidden"></div>
                    <div class="answer hidden"></div>
                </div>
            </div>`;
        root.appendChild(wrapper);

        ui = {
            host,
            targets,
            panel: wrapper.querySelector('.panel'),
            question: wrapper.querySelector('textarea'),
            askButton: wrapper.querySelector('.ask'),
            meta: wrapper.querySelector('.meta'),
            error: wrapper.querySelector('.error'),
            answer: wrapper.querySelector('.answer')
        };

        wrapper.querySelector('.launcher').addEventListener('click', () => ui.panel.classList.toggle('open'));
        wrapper.querySelector('.close').addEventListener('click', () => ui.panel.classList.remove('open'));
        ui.askButton.addEventListener('click', run);
        return ui;
    }

    async function run() {
        const question = ui.question.value.trim();
        ui.error.classList.add('hidden');
        if (!question) {
            ui.error.textContent = 'A question is required.';
            ui.error.classList.remove('hidden');
            return;
        }
        ui.askButton.disabled = true;
        ui.meta.textContent = 'thinking…';
        const result = await chrome.runtime.sendMessage({
            type: 'query',
            payload: { question, targets: ui.targets }
        });
        ui.askButton.disabled = false;
        if (!result || !result.ok) {
            ui.meta.textContent = '';
            ui.error.textContent = (result && result.error) || 'Request failed';
            if (result && (result.needsAuth || result.needsConfig)) {
                ui.error.textContent += ' — open the extension popup to sign in.';
            }
            ui.error.classList.remove('hidden');
            return;
        }
        ui.answer.textContent = result.body.answer;
        ui.answer.classList.remove('hidden');
        ui.meta.textContent = `${result.body.meta.cached ? 'cached' : 'fresh'} · ${result.body.meta.latencyMs} ms`;
    }

    chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
        if (message.type === 'detectTargets') {
            sendResponse({ targets: detectTargets() });
        }
        return true;
    });

    const found = detectTargets();
    if (found.length > 0) {
        chrome.runtime.sendMessage({ type: 'status' }).then(status => {
            if (status && status.ok && status.body.authenticated) mount(found);
        }).catch(() => { });
    }
})();
