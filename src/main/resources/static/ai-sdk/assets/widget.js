(function () {
    'use strict';

    const STYLES = `
        :host { all: initial; }
        * { box-sizing: border-box; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; }
        .launcher {
            position: fixed; z-index: 2147483000; border: none; cursor: pointer;
            background: #4f8cff; color: #fff; font-size: 14px; font-weight: 600;
            padding: 12px 18px; border-radius: 999px; box-shadow: 0 8px 24px rgba(0,0,0,.28);
        }
        .launcher:hover { background: #3f7bea; }
        .panel {
            position: fixed; z-index: 2147483000; width: 420px; max-width: calc(100vw - 32px);
            max-height: min(680px, calc(100vh - 32px)); display: none; flex-direction: column;
            background: #171b21; color: #e6eaef; border: 1px solid #2a323c; border-radius: 12px;
            box-shadow: 0 18px 50px rgba(0,0,0,.45); overflow: hidden; font-size: 14px;
        }
        .panel.open { display: flex; }
        .bottom-right .launcher, .bottom-right .panel { right: 20px; bottom: 20px; }
        .bottom-left .launcher, .bottom-left .panel { left: 20px; bottom: 20px; }
        .top-right .launcher, .top-right .panel { right: 20px; top: 20px; }
        .top-left .launcher, .top-left .panel { left: 20px; top: 20px; }
        .head { display: flex; align-items: center; gap: 10px; padding: 14px 16px; border-bottom: 1px solid #2a323c; background: #1e242c; }
        .head strong { font-size: 14px; }
        .head .spacer { flex: 1; }
        .icon-button { background: transparent; border: none; color: #9aa5b1; cursor: pointer; font-size: 18px; line-height: 1; padding: 4px 6px; }
        .icon-button:hover { color: #e6eaef; }
        .body { padding: 14px 16px; overflow: auto; flex: 1; }
        .targets { display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 12px; }
        .chip { background: #1e242c; border: 1px solid #2a323c; border-radius: 999px; padding: 4px 10px; font-size: 12px; color: #9aa5b1; }
        .chip strong { color: #e6eaef; font-weight: 600; }
        textarea {
            width: 100%; min-height: 72px; resize: vertical; background: #1e242c; color: #e6eaef;
            border: 1px solid #2a323c; border-radius: 8px; padding: 10px 12px; font-size: 14px;
        }
        .suggestions { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 10px; }
        .suggestion { background: #1e242c; border: 1px solid #2a323c; color: #9aa5b1; border-radius: 999px; padding: 5px 11px; font-size: 12px; cursor: pointer; }
        .suggestion:hover { color: #e6eaef; border-color: #4f8cff; }
        .actions { display: flex; align-items: center; gap: 10px; margin-top: 12px; }
        .primary { background: #4f8cff; border: none; color: #fff; font-weight: 600; font-size: 14px; padding: 9px 16px; border-radius: 8px; cursor: pointer; }
        .primary:disabled { opacity: .55; cursor: not-allowed; }
        .meta { font-size: 12px; color: #9aa5b1; }
        .answer { white-space: pre-wrap; line-height: 1.55; margin-top: 14px; padding: 14px; background: #1e242c; border: 1px solid #2a323c; border-radius: 8px; }
        .error { margin-top: 12px; padding: 10px 12px; border-radius: 8px; background: rgba(248,81,73,.12); color: #f85149; font-size: 13px; }
        details { margin-top: 12px; border: 1px solid #2a323c; border-radius: 8px; background: #1e242c; }
        summary { cursor: pointer; padding: 10px 12px; font-size: 13px; color: #9aa5b1; }
        pre { margin: 0; padding: 12px; overflow: auto; max-height: 260px; font-size: 12px; color: #c9d1d9; }
        .meeting { margin-top: 12px; padding: 12px; border: 1px solid #2a323c; border-radius: 8px; background: #1e242c; }
        .meeting a { color: #4f8cff; word-break: break-all; }
        .meeting .copy { margin-top: 8px; background: transparent; border: 1px solid #2a323c; color: #9aa5b1; border-radius: 6px; padding: 5px 10px; font-size: 12px; cursor: pointer; }
        .meeting .copy:hover { color: #e6eaef; border-color: #4f8cff; }
        .secondary-button { background: transparent; border: 1px solid #2a323c; color: #9aa5b1; font-size: 14px; padding: 9px 14px; border-radius: 8px; cursor: pointer; }
        .secondary-button:hover { color: #e6eaef; border-color: #4f8cff; }
        .secondary-button:disabled { opacity: .55; cursor: not-allowed; }
        .auth { margin-top: 12px; }
        .auth input { width: 100%; background: #1e242c; color: #e6eaef; border: 1px solid #2a323c; border-radius: 8px; padding: 9px 12px; font-size: 14px; }
        .hidden { display: none; }
    `;

    function resolveScriptConfig() {
        const script = document.currentScript || document.querySelector('script[data-ai-sdk-base]');
        if (!script) return null;
        const data = script.dataset;
        if (!data.aiSdkBase && !data.aiSdkEntity) return null;
        const config = {
            baseUrl: data.aiSdkBase || '/ai-sdk',
            tokenUrl: data.aiSdkTokenUrl || null,
            token: data.aiSdkToken || null,
            position: data.aiSdkPosition || 'bottom-right',
            title: data.aiSdkTitle || 'Ask about this record',
            question: data.aiSdkQuestion || '',
            launcherLabel: data.aiSdkLabel || 'Ask AI',
            open: data.aiSdkOpen === 'true',
            meetings: data.aiSdkMeetings === 'true',
            meetingLabel: data.aiSdkMeetingLabel || 'Get meeting link',
            meetingTitle: data.aiSdkMeetingTitle || null,
            meetingMinutes: data.aiSdkMeetingMinutes ? Number(data.aiSdkMeetingMinutes) : null
        };
        if (data.aiSdkEntity && data.aiSdkId) {
            config.targets = [{ entity: data.aiSdkEntity, id: data.aiSdkId }];
        }
        if (data.aiSdkSuggestions) {
            config.suggestions = data.aiSdkSuggestions.split('|').map(s => s.trim()).filter(Boolean);
        }
        if (data.aiSdkParentDepth || data.aiSdkChildDepth || data.aiSdkMaxChildren) {
            config.options = {
                parentDepth: data.aiSdkParentDepth ? Number(data.aiSdkParentDepth) : null,
                childDepth: data.aiSdkChildDepth ? Number(data.aiSdkChildDepth) : null,
                maxChildrenPerRelation: data.aiSdkMaxChildren ? Number(data.aiSdkMaxChildren) : null
            };
        }
        return config;
    }

    function scanDomTargets() {
        return Array.from(document.querySelectorAll('[data-ai-sdk-entity][data-ai-sdk-id]'))
            .map(el => ({ entity: el.dataset.aiSdkEntity, id: el.dataset.aiSdkId }))
            .filter(t => t.entity && t.id);
    }

    function escapeHtml(value) {
        return (value === null || value === undefined ? '' : String(value))
            .replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;');
    }

    class AiSdkClient {
        constructor(config) {
            this.config = config;
            this.token = config.token || null;
        }

        base() {
            return (this.config.baseUrl || '/ai-sdk').replace(/\/$/, '');
        }

        setToken(token) {
            this.token = token;
        }

        async resolveToken(force) {
            if (this.token && !force) return this.token;
            if (typeof this.config.getToken === 'function') {
                this.token = await this.config.getToken();
                return this.token;
            }
            if (this.config.tokenUrl) {
                const response = await fetch(this.config.tokenUrl, { credentials: 'include' });
                if (!response.ok) throw new Error('Token endpoint returned ' + response.status);
                const body = await response.json();
                this.token = body.token;
                return this.token;
            }
            if (this.config.useSessionStorage !== false) {
                try {
                    const stored = sessionStorage.getItem('aiSdkToken');
                    if (stored) {
                        this.token = stored;
                        return stored;
                    }
                } catch (e) { }
            }
            return this.token;
        }

        async authenticate(password) {
            const response = await fetch(this.base() + '/auth/token', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ password })
            });
            const body = await response.json().catch(() => null);
            if (!response.ok) throw new Error((body && body.error) || 'Authentication failed');
            this.token = body.token;
            try { sessionStorage.setItem('aiSdkToken', body.token); } catch (e) { }
            return body;
        }

        async request(path, init, retried) {
            const token = await this.resolveToken(false);
            if (!token) throw new Error('No token available');
            const response = await fetch(this.base() + path, Object.assign({}, init, {
                headers: Object.assign({ 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + token }, (init && init.headers) || {})
            }));
            const text = await response.text();
            let body = null;
            if (text) {
                try { body = JSON.parse(text); } catch (e) { body = { raw: text }; }
            }
            if (response.status === 401 && !retried) {
                this.token = null;
                const refreshed = await this.resolveToken(true);
                if (refreshed) return this.request(path, init, true);
            }
            if (!response.ok) throw new Error((body && body.error) || ('Request failed with status ' + response.status));
            return body;
        }

        schema() {
            return this.request('/configure/schema', { method: 'GET' });
        }

        query(payload) {
            return this.request('/query', { method: 'POST', body: JSON.stringify(payload) });
        }

        createMeeting(payload) {
            return this.request('/meetings', { method: 'POST', body: JSON.stringify(payload) });
        }

        meetingStatus() {
            return this.request('/meetings/status', { method: 'GET' });
        }

        meetingDiscussions(leadId) {
            return this.request('/meetings/discussions?leadId=' + encodeURIComponent(leadId), { method: 'GET' });
        }
    }

    class AiSdkWidgetInstance {
        constructor(config) {
            this.config = Object.assign({
                baseUrl: '/ai-sdk',
                position: 'bottom-right',
                title: 'Ask about this record',
                launcherLabel: 'Ask AI',
                question: '',
                suggestions: [],
                targets: null,
                options: null,
                open: false,
                meetings: false,
                meetingLabel: 'Get meeting link',
                meetingTitle: null,
                meetingMinutes: null
            }, config || {});
            this.client = new AiSdkClient(this.config);
            this.mount();
            if (this.config.open) this.open();
        }

        targets() {
            if (typeof this.config.resolveTargets === 'function') return this.config.resolveTargets() || [];
            if (Array.isArray(this.config.targets) && this.config.targets.length) return this.config.targets;
            return scanDomTargets();
        }

        mount() {
            this.host = document.createElement('div');
            this.host.setAttribute('data-ai-sdk-widget', '');
            document.body.appendChild(this.host);
            this.root = this.host.attachShadow({ mode: 'open' });

            const style = document.createElement('style');
            style.textContent = STYLES;
            this.root.appendChild(style);

            const wrapper = document.createElement('div');
            wrapper.className = this.config.position;
            wrapper.innerHTML = `
                <button class="launcher" part="launcher">${escapeHtml(this.config.launcherLabel)}</button>
                <div class="panel">
                    <div class="head">
                        <strong>${escapeHtml(this.config.title)}</strong>
                        <span class="spacer"></span>
                        <button class="icon-button close" title="Close">&times;</button>
                    </div>
                    <div class="body">
                        <div class="targets"></div>
                        <textarea class="question" placeholder="Ask a question about these records">${escapeHtml(this.config.question)}</textarea>
                        <div class="suggestions"></div>
                        <div class="actions">
                            <button class="primary ask">Ask</button>
                            <button class="secondary-button meeting-link ${this.config.meetings ? '' : 'hidden'}">${escapeHtml(this.config.meetingLabel)}</button>
                            <span class="meta"></span>
                        </div>
                        <div class="meeting hidden"></div>
                        <div class="auth hidden">
                            <input class="password" type="password" placeholder="Admin password"/>
                            <div class="actions"><button class="primary sign-in">Sign in</button></div>
                        </div>
                        <div class="error hidden"></div>
                        <div class="answer hidden"></div>
                        <details class="records hidden">
                            <summary>Retrieved records</summary>
                            <pre></pre>
                        </details>
                    </div>
                </div>`;
            this.root.appendChild(wrapper);

            this.wrapper = wrapper;
            this.panel = wrapper.querySelector('.panel');
            this.questionEl = wrapper.querySelector('.question');
            this.askButton = wrapper.querySelector('.ask');
            this.metaEl = wrapper.querySelector('.meta');
            this.errorEl = wrapper.querySelector('.error');
            this.answerEl = wrapper.querySelector('.answer');
            this.recordsEl = wrapper.querySelector('.records');
            this.authEl = wrapper.querySelector('.auth');
            this.meetingEl = wrapper.querySelector('.meeting');
            this.meetingButton = wrapper.querySelector('.meeting-link');

            wrapper.querySelector('.launcher').addEventListener('click', () => this.toggle());
            wrapper.querySelector('.close').addEventListener('click', () => this.close());
            this.askButton.addEventListener('click', () => this.run());
            this.meetingButton.addEventListener('click', () => this.generateMeetingLink());
            wrapper.querySelector('.sign-in').addEventListener('click', () => this.signIn());
            this.questionEl.addEventListener('keydown', event => {
                if (event.key === 'Enter' && (event.metaKey || event.ctrlKey)) this.run();
            });

            this.renderTargets();
            this.renderSuggestions();
        }

        renderTargets() {
            const container = this.wrapper.querySelector('.targets');
            const targets = this.targets();
            container.innerHTML = targets.length === 0
                ? '<span class="chip">no targets configured</span>'
                : targets.map(t => `<span class="chip"><strong>${escapeHtml(t.entity)}</strong> ${escapeHtml(t.id)}</span>`).join('');
        }

        renderSuggestions() {
            const container = this.wrapper.querySelector('.suggestions');
            container.innerHTML = (this.config.suggestions || [])
                .map(s => `<button class="suggestion">${escapeHtml(s)}</button>`).join('');
            container.querySelectorAll('.suggestion').forEach((button, index) => {
                button.addEventListener('click', () => {
                    this.questionEl.value = this.config.suggestions[index];
                    this.run();
                });
            });
        }

        showError(message) {
            this.errorEl.textContent = message;
            this.errorEl.classList.remove('hidden');
            if (/token|Authentication|expired|401/i.test(message)) this.authEl.classList.remove('hidden');
        }

        clearError() {
            this.errorEl.classList.add('hidden');
            this.errorEl.textContent = '';
        }

        async signIn() {
            const input = this.wrapper.querySelector('.password');
            this.clearError();
            try {
                await this.client.authenticate(input.value);
                input.value = '';
                this.authEl.classList.add('hidden');
                this.run();
            } catch (e) {
                this.showError(e.message);
            }
        }

        async ask(payload) {
            const targets = (payload && payload.targets) || this.targets();
            if (!targets.length) throw new Error('No targets to ask about');
            const question = (payload && payload.question) || this.questionEl.value;
            if (!question || !question.trim()) throw new Error('A question is required');
            return this.client.query({
                question: question.trim(),
                targets,
                options: (payload && payload.options) || this.config.options || null
            });
        }

        async run() {
            this.clearError();
            this.askButton.disabled = true;
            this.metaEl.textContent = 'thinking…';
            try {
                const response = await this.ask(null);
                this.answerEl.textContent = response.answer;
                this.answerEl.classList.remove('hidden');
                this.recordsEl.querySelector('pre').textContent = JSON.stringify(response.data, null, 2);
                this.recordsEl.classList.remove('hidden');
                this.metaEl.textContent = `${response.meta.cached ? 'cached' : 'fresh'} · ${response.meta.latencyMs} ms`;
                if (typeof this.config.onAnswer === 'function') this.config.onAnswer(response);
            } catch (e) {
                this.metaEl.textContent = '';
                this.showError(e.message);
                if (typeof this.config.onError === 'function') this.config.onError(e);
            } finally {
                this.askButton.disabled = false;
            }
        }

        async createMeeting(payload) {
            const target = (payload && payload.target)
                || (payload && payload.leadId ? { entity: payload.leadEntity, id: payload.leadId } : null)
                || this.targets()[0];
            if (!target || !target.id) throw new Error('No lead to create a meeting for');
            return this.client.createMeeting({
                leadId: String(target.id),
                leadEntity: target.entity || null,
                title: (payload && payload.title) || this.config.meetingTitle || null,
                agenda: (payload && payload.agenda) || null,
                scheduledAt: (payload && payload.scheduledAt) || null,
                durationMinutes: (payload && payload.durationMinutes) || this.config.meetingMinutes || null
            });
        }

        async generateMeetingLink(payload) {
            this.clearError();
            this.meetingButton.disabled = true;
            this.metaEl.textContent = 'creating meeting…';
            try {
                const meeting = await this.createMeeting(payload);
                this.meetingEl.innerHTML = `<a href="${escapeHtml(meeting.meetingLink)}" target="_blank" rel="noopener">${escapeHtml(meeting.meetingLink)}</a>
                    <button class="copy">Copy link</button>`;
                this.meetingEl.classList.remove('hidden');
                this.meetingEl.querySelector('.copy').addEventListener('click', () => {
                    navigator.clipboard.writeText(meeting.meetingLink).then(() => {
                        this.meetingEl.querySelector('.copy').textContent = 'Copied';
                    }).catch(() => { });
                });
                this.metaEl.textContent = 'meeting link ready';
                if (typeof this.config.onMeeting === 'function') this.config.onMeeting(meeting);
                return meeting;
            } catch (e) {
                this.metaEl.textContent = '';
                this.showError(e.message);
                if (typeof this.config.onError === 'function') this.config.onError(e);
                throw e;
            } finally {
                this.meetingButton.disabled = false;
            }
        }

        setTargets(targets) {
            this.config.targets = targets;
            this.renderTargets();
        }

        setToken(token) {
            this.client.setToken(token);
        }

        open() {
            this.renderTargets();
            this.panel.classList.add('open');
            this.questionEl.focus();
        }

        close() {
            this.panel.classList.remove('open');
        }

        toggle() {
            this.panel.classList.contains('open') ? this.close() : this.open();
        }

        destroy() {
            this.host.remove();
        }
    }

    const api = {
        instance: null,
        Client: AiSdkClient,
        init(config) {
            if (api.instance) api.instance.destroy();
            api.instance = new AiSdkWidgetInstance(config);
            return api.instance;
        },
        client(config) {
            return new AiSdkClient(config);
        },
        ask(payload) {
            if (!api.instance) throw new Error('AiSdkWidget.init must be called first');
            return api.instance.ask(payload);
        },
        meetingLink(payload) {
            if (!api.instance) throw new Error('AiSdkWidget.init must be called first');
            return api.instance.generateMeetingLink(payload);
        },
        open() { if (api.instance) api.instance.open(); },
        close() { if (api.instance) api.instance.close(); },
        setTargets(targets) { if (api.instance) api.instance.setTargets(targets); },
        setToken(token) { if (api.instance) api.instance.setToken(token); },
        destroy() { if (api.instance) { api.instance.destroy(); api.instance = null; } }
    };

    window.AiSdkWidget = api;

    const scriptConfig = resolveScriptConfig();
    if (scriptConfig) {
        if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', () => api.init(scriptConfig));
        } else {
            api.init(scriptConfig);
        }
    }
})();
