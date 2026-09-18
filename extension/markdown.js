(function () {
    if (typeof window !== 'undefined' && window.AiSdkMarkdown) return;

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

    if (typeof window !== 'undefined') {
        window.AiSdkMarkdown = AiSdkMarkdown;
    }
})();
