(function () {
    'use strict';

    const AiSdkMarkdown = (() => {
        const MAX_DEPTH = 4;
        const LIST_ITEM = /^(\s*)([-*+]|\d+[.)])\s+(.*)$/;
        const HEADING = /^(#{1,6})\s+(.+?)\s*#*$/;
        const RULE = /^(-{3,}|\*{3,}|_{3,})$/;
        const SETEXT = /^(=+|-{3,})$/;
        const TASK = /^\[([ xX])\]\s+(.*)$/;
        const FENCE_SLOT = /^\uE000(\d+)\uE000$/;

        let codeBlocks = [];

        function escapeHtml(value) {
            return (value === null || value === undefined ? '' : String(value))
                .replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;');
        }

        function inline(text) {
            const spans = [];
            let out = escapeHtml(text).replace(/`([^`]+)`/g, (match, body) => {
                spans.push(body);
                return '\uE001' + (spans.length - 1) + '\uE001';
            });
            out = out.replace(/!\[([^\]]*)\]\(([^()]*(?:\([^()]*\)[^()]*)*)\)/g, '$1');
            out = out.replace(/\[([^\]]*)\]\(([^()]*(?:\([^()]*\)[^()]*)*)\)/g, (match, label, target) => {
                const url = target.trim().split(/\s+/)[0];
                return /^(https?:|mailto:)/i.test(url) ? label + ' <code>' + url + '</code>' : label;
            });
            out = out.replace(/\*\*\*([^*]+)\*\*\*/g, '<strong><em>$1</em></strong>');
            out = out.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>');
            out = out.replace(/(^|[^*\w])\*([^*\s](?:[^*]*[^*\s])?)\*(?!\w)/g, '$1<em>$2</em>');
            out = out.replace(/~~([^~]+)~~/g, '<del>$1</del>');
            out = out.replace(/\uE001(\d+)\uE001/g, (match, index) => '<code>' + spans[Number(index)] + '</code>');
            return out.replace(/\uE000(\d+)\uE000/g, (match, index) =>
                '<code>' + escapeHtml(codeBlocks[Number(index)]) + '</code>');
        }

        function splitRow(line) {
            let text = line.trim();
            if (text.startsWith('|')) text = text.slice(1);
            if (text.endsWith('|')) text = text.slice(0, -1);
            return text.split('|').map(cell => cell.trim());
        }

        function isDivider(line) {
            return line !== undefined && /^\s*\|?\s*:?-{1,}:?\s*(\|\s*:?-{1,}:?\s*)*\|?\s*$/.test(line);
        }

        function alignAttribute(cell) {
            const left = cell.startsWith(':');
            const right = cell.endsWith(':');
            if (left && right) return ' class="md-center"';
            if (right) return ' class="md-right"';
            return '';
        }

        function renderTable(lines, start) {
            const header = splitRow(lines[start]);
            const aligns = splitRow(lines[start + 1]).map(alignAttribute);
            const rows = [];
            let i = start + 2;
            while (i < lines.length && lines[i].trim() !== '' && lines[i].includes('|')) {
                rows.push(splitRow(lines[i]));
                i++;
            }
            const head = header
                .map((cell, index) => '<th' + (aligns[index] || '') + '>' + inline(cell) + '</th>')
                .join('');
            const body = rows
                .map(row => '<tr>' + header
                    .map((ignored, index) => '<td' + (aligns[index] || '') + '>'
                        + inline(row[index] === undefined ? '' : row[index]) + '</td>')
                    .join('') + '</tr>')
                .join('');
            return {
                html: '<div class="md-table"><table><thead><tr>' + head + '</tr></thead><tbody>'
                    + body + '</tbody></table></div>',
                next: i
            };
        }

        function collectItems(lines, start) {
            const items = [];
            let i = start;
            while (i < lines.length) {
                const match = lines[i].match(LIST_ITEM);
                if (match) {
                    items.push({
                        indent: match[1].replaceAll('\t', '    ').length,
                        ordered: /\d/.test(match[2]),
                        text: match[3]
                    });
                    i++;
                    continue;
                }
                if (lines[i].trim() === '') {
                    let next = i + 1;
                    while (next < lines.length && lines[next].trim() === '') next++;
                    if (next < lines.length && LIST_ITEM.test(lines[next])) {
                        i = next;
                        continue;
                    }
                    break;
                }
                if (items.length > 0 && /^\s+\S/.test(lines[i])) {
                    items[items.length - 1].text += ' ' + lines[i].trim();
                    i++;
                    continue;
                }
                break;
            }
            return { items, next: i };
        }

        function buildList(items, state, indent, depth) {
            const ordered = items[state.index].ordered;
            let html = ordered ? '<ol>' : '<ul>';
            while (state.index < items.length) {
                const item = items[state.index];
                if (item.indent < indent) break;
                if (item.indent > indent && depth >= MAX_DEPTH) item.indent = indent;
                if (item.indent > indent) {
                    html += buildList(items, state, item.indent, depth + 1);
                    continue;
                }
                state.index++;
                let text = item.text;
                const task = text.match(TASK);
                if (task) {
                    text = (task[1] === ' ' ? '\u2610 ' : '\u2611 ') + task[2];
                }
                let entry = '<li>' + inline(text);
                while (state.index < items.length && items[state.index].indent > indent) {
                    if (depth >= MAX_DEPTH) {
                        items[state.index].indent = indent;
                        break;
                    }
                    entry += buildList(items, state, items[state.index].indent, depth + 1);
                }
                html += entry + '</li>';
            }
            return html + (ordered ? '</ol>' : '</ul>');
        }

        function startsBlock(lines, index) {
            const line = lines[index];
            const trimmed = line.trim();
            return trimmed === ''
                || LIST_ITEM.test(line)
                || /^\s*>/.test(line)
                || HEADING.test(trimmed)
                || RULE.test(trimmed)
                || FENCE_SLOT.test(trimmed)
                || SETEXT.test((lines[index + 1] === undefined ? '' : lines[index + 1]).trim())
                || (line.includes('|') && isDivider(lines[index + 1]));
        }

        function renderBlocks(source, depth) {
            const lines = source.split('\n');
            const out = [];
            let i = 0;
            while (i < lines.length) {
                const line = lines[i];
                const trimmed = line.trim();
                if (trimmed === '') {
                    i++;
                    continue;
                }
                const fence = trimmed.match(FENCE_SLOT);
                if (fence) {
                    out.push('<pre><code>' + escapeHtml(codeBlocks[Number(fence[1])]) + '</code></pre>');
                    i++;
                    continue;
                }
                if (RULE.test(trimmed)) {
                    out.push('<hr/>');
                    i++;
                    continue;
                }
                const heading = trimmed.match(HEADING);
                if (heading) {
                    const level = Math.min(4, Math.max(2, heading[1].length));
                    out.push('<h' + level + '>' + inline(heading[2]) + '</h' + level + '>');
                    i++;
                    continue;
                }
                if (line.includes('|') && isDivider(lines[i + 1])) {
                    const table = renderTable(lines, i);
                    out.push(table.html);
                    i = table.next;
                    continue;
                }
                if (/^\s*>/.test(line)) {
                    const quoted = [];
                    while (i < lines.length && /^\s*>/.test(lines[i])) {
                        quoted.push(lines[i].replace(/^\s*>\s?/, ''));
                        i++;
                    }
                    const body = depth >= MAX_DEPTH
                        ? '<p>' + quoted.map(inline).join('<br/>') + '</p>'
                        : renderBlocks(quoted.join('\n'), depth + 1);
                    out.push('<blockquote>' + body + '</blockquote>');
                    continue;
                }
                if (LIST_ITEM.test(line)) {
                    const collected = collectItems(lines, i);
                    if (collected.items.length > 0 && collected.next > i) {
                        out.push(buildList(collected.items, { index: 0 }, collected.items[0].indent, depth));
                        i = collected.next;
                        continue;
                    }
                }
                if (SETEXT.test((lines[i + 1] === undefined ? '' : lines[i + 1]).trim())) {
                    out.push('<h2>' + inline(trimmed) + '</h2>');
                    i += 2;
                    continue;
                }
                const paragraph = [];
                while (i < lines.length && !startsBlock(lines, i)) {
                    paragraph.push(lines[i]);
                    i++;
                }
                if (paragraph.length === 0) {
                    i++;
                    continue;
                }
                out.push('<p>' + paragraph.map(inline).join('<br/>') + '</p>');
            }
            return out.join('');
        }

        function render(value) {
            if (value === null || value === undefined) return '';
            codeBlocks = [];
            const source = String(value).replaceAll('\r\n', '\n').replaceAll('\r', '\n');
            const slot = body => {
                codeBlocks.push(body.replace(/\n$/, ''));
                return '\n\uE000' + (codeBlocks.length - 1) + '\uE000\n';
            };
            const stripped = source
                .replace(/```[^\n]*\n([\s\S]*?)```[ \t]*/g, (match, body) => slot(body))
                .replace(/```[^\n]*\n([\s\S]*)$/, (match, body) => slot(body));
            return renderBlocks(stripped, 0);
        }

        return { render };
    })();

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
        .answer-md { white-space: normal; }
        .answer-md > :first-child { margin-top: 0; }
        .answer-md > :last-child { margin-bottom: 0; }
        .answer-md p { margin: 0 0 10px; }
        .answer-md h2 { font-size: 14px; font-weight: 600; margin: 16px 0 7px; }
        .answer-md h3, .answer-md h4 { font-size: 13px; font-weight: 600; margin: 14px 0 6px; }
        .answer-md h4 { color: #9aa5b1; }
        .answer-md ul, .answer-md ol { margin: 0 0 10px; padding-left: 20px; }
        .answer-md li { margin: 3px 0; }
        .answer-md li > ul, .answer-md li > ol { margin: 3px 0 0; }
        .answer-md strong { font-weight: 600; color: #fff; }
        .answer-md hr { border: none; border-top: 1px solid #2a323c; margin: 14px 0; }
        .answer-md blockquote { margin: 0 0 10px; padding: 2px 0 2px 12px; border-left: 3px solid #2a323c; color: #9aa5b1; }
        .answer-md blockquote > :last-child { margin-bottom: 0; }
        .answer-md code { background: #171b21; border: 1px solid #2a323c; border-radius: 4px; padding: 0 5px; font-family: ui-monospace, SFMono-Regular, Consolas, Menlo, monospace; font-size: .92em; }
        .answer-md pre { margin: 0 0 10px; padding: 10px 12px; background: #12161b; border: 1px solid #2a323c; border-radius: 6px; overflow-x: auto; max-height: 260px; }
        .answer-md pre code { background: none; border: none; padding: 0; font-size: 12px; color: #c9d1d9; }
        .answer-md .md-table { overflow-x: auto; margin: 0 0 12px; }
        .answer-md table { border-collapse: collapse; width: 100%; font-size: 12px; }
        .answer-md th, .answer-md td { border: 1px solid #2a323c; padding: 6px 8px; text-align: left; vertical-align: top; }
        .answer-md th { background: #171b21; font-weight: 600; white-space: nowrap; }
        .answer-md .md-center { text-align: center; }
        .answer-md .md-right { text-align: right; }
        .copy-answer { font-size: 12px; margin-left: auto; }
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
            config.targets = [{ entity: data.aiSdkEntity, id: data.aiSdkId, phone: data.aiSdkPhone || null }];
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
            .map(el => ({ entity: el.dataset.aiSdkEntity, id: el.dataset.aiSdkId, phone: el.dataset.aiSdkPhone || null }))
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
                            <button class="icon-button copy-answer hidden" title="Copy answer markdown">Copy</button>
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
            this.copyButton = wrapper.querySelector('.copy-answer');
            this.recordsEl = wrapper.querySelector('.records');
            this.authEl = wrapper.querySelector('.auth');
            this.meetingEl = wrapper.querySelector('.meeting');
            this.meetingButton = wrapper.querySelector('.meeting-link');

            wrapper.querySelector('.launcher').addEventListener('click', () => this.toggle());
            wrapper.querySelector('.close').addEventListener('click', () => this.close());
            this.askButton.addEventListener('click', () => this.run());
            this.meetingButton.addEventListener('click', () => this.generateMeetingLink());
            wrapper.querySelector('.sign-in').addEventListener('click', () => this.signIn());
            this.copyButton.addEventListener('click', () => this.copyAnswer());
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
                this.renderAnswer(response.answer);
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

        renderAnswer(text) {
            this.lastAnswer = text || '';
            try {
                this.answerEl.innerHTML = AiSdkMarkdown.render(this.lastAnswer);
                this.answerEl.className = 'answer answer-md';
            } catch (e) {
                this.answerEl.textContent = this.lastAnswer;
                this.answerEl.className = 'answer';
            }
            this.copyButton.className = this.lastAnswer ? 'icon-button copy-answer' : 'icon-button copy-answer hidden';
            this.copyButton.textContent = 'Copy';
        }

        async copyAnswer() {
            try {
                await navigator.clipboard.writeText(this.lastAnswer || '');
                this.copyButton.textContent = 'Copied';
            } catch (e) {
                this.copyButton.textContent = 'Failed';
            }
            setTimeout(() => { this.copyButton.textContent = 'Copy'; }, 1500);
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
