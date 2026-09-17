package com.leadrat.aisdk.config;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ReadOnlySqlGuard {

    private static final Pattern WORD = Pattern.compile("[A-Za-z_][A-Za-z0-9_$]*");

    private static final Set<String> FORBIDDEN = Set.of(
            "insert", "update", "delete", "merge", "upsert", "truncate", "drop", "alter", "create",
            "rename", "grant", "revoke", "copy", "call", "do", "perform", "execute", "prepare",
            "deallocate", "vacuum", "reindex", "refresh", "checkpoint", "discard", "lock",
            "listen", "unlisten", "notify", "begin", "commit", "rollback", "savepoint",
            "set", "reset", "into", "returning", "nextval", "setval", "dblink", "pg_sleep",
            "pg_terminate_backend", "pg_cancel_backend", "pg_read_file", "pg_read_binary_file",
            "pg_ls_dir", "pg_logical_emit_message", "lo_import", "lo_export", "lo_unlink");

    private ReadOnlySqlGuard() {
    }

    public static void verify(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new ReadOnlyViolationException("ai-sdk read-only guardrail: empty statement rejected");
        }
        String stripped = strip(sql).strip();
        while (stripped.endsWith(";")) {
            stripped = stripped.substring(0, stripped.length() - 1).strip();
        }
        if (stripped.indexOf(';') >= 0) {
            throw new ReadOnlyViolationException("ai-sdk read-only guardrail: multi-statement SQL rejected");
        }
        Matcher matcher = WORD.matcher(stripped);
        boolean first = true;
        while (matcher.find()) {
            String word = matcher.group().toLowerCase();
            if (first) {
                first = false;
                if (!word.equals("select") && !word.equals("with") && !word.equals("table") && !word.equals("values")) {
                    throw new ReadOnlyViolationException(
                            "ai-sdk read-only guardrail: only SELECT statements are allowed, rejected '" + word + "'");
                }
                continue;
            }
            if (FORBIDDEN.contains(word)) {
                throw new ReadOnlyViolationException(
                        "ai-sdk read-only guardrail: forbidden keyword '" + word + "' in generated SQL");
            }
        }
        if (first) {
            throw new ReadOnlyViolationException("ai-sdk read-only guardrail: unrecognised statement rejected");
        }
    }

    static String strip(String sql) {
        StringBuilder out = new StringBuilder(sql.length());
        int i = 0;
        int length = sql.length();
        while (i < length) {
            char c = sql.charAt(i);
            if (c == '-' && i + 1 < length && sql.charAt(i + 1) == '-') {
                while (i < length && sql.charAt(i) != '\n') {
                    i++;
                }
                out.append(' ');
            } else if (c == '/' && i + 1 < length && sql.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < length && !(sql.charAt(i) == '*' && sql.charAt(i + 1) == '/')) {
                    i++;
                }
                i = Math.min(length, i + 2);
                out.append(' ');
            } else if (c == '\'' || c == '"' || c == '`') {
                char quote = c;
                i++;
                while (i < length) {
                    if (sql.charAt(i) == quote) {
                        if (i + 1 < length && sql.charAt(i + 1) == quote) {
                            i += 2;
                            continue;
                        }
                        i++;
                        break;
                    }
                    i++;
                }
                out.append(' ');
            } else if (c == '$' && i + 1 < length && sql.charAt(i + 1) == '$') {
                i += 2;
                while (i + 1 < length && !(sql.charAt(i) == '$' && sql.charAt(i + 1) == '$')) {
                    i++;
                }
                i = Math.min(length, i + 2);
                out.append(' ');
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }
}
