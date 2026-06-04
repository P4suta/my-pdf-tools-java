package io.github.p4suta.register.infrastructure.diag;

import org.jspecify.annotations.Nullable;

/**
 * A tiny dependency-free JSON object writer for the diagnostic reports: enough to emit one object
 * per line (JSONL) with strings, finite numbers, booleans and pre-rendered nested fragments. Not a
 * general JSON library — keys are assumed well-formed and NaN/Infinity are written as {@code null}
 * (JSON has no literal for them).
 */
final class Json {

    private final StringBuilder sb = new StringBuilder();
    private boolean empty = true;

    Json() {
        sb.append('{');
    }

    Json field(String key, long value) {
        key(key);
        sb.append(value);
        return this;
    }

    Json field(String key, double value) {
        key(key);
        sb.append(number(value));
        return this;
    }

    Json field(String key, boolean value) {
        key(key);
        sb.append(value);
        return this;
    }

    Json field(String key, @Nullable String value) {
        key(key);
        if (value == null) {
            sb.append("null");
        } else {
            quote(value);
        }
        return this;
    }

    /** A field whose value is an already-rendered JSON fragment (object/array), or {@code null}. */
    Json fieldRaw(String key, @Nullable String json) {
        key(key);
        sb.append(json == null ? "null" : json);
        return this;
    }

    String end() {
        sb.append('}');
        return sb.toString();
    }

    private void key(String key) {
        if (!empty) {
            sb.append(',');
        }
        empty = false;
        quote(key);
        sb.append(':');
    }

    private void quote(String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    /** A finite number rendered without a trailing {@code .0} when integral; null if non-finite. */
    private static String number(double v) {
        if (!Double.isFinite(v)) {
            return "null";
        }
        if (v == Math.rint(v) && Math.abs(v) < 1e15) {
            return Long.toString((long) v);
        }
        return Double.toString(v);
    }
}
