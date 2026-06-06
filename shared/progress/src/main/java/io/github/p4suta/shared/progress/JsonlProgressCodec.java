package io.github.p4suta.shared.progress;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The JSONL wire codec for {@link ProgressEvent}: one event per line as a flat JSON object with a
 * {@code "type"} discriminator. {@link #write(ProgressEvent)} renders the line a pdfbook subprocess
 * appends to its {@code --progress-file}; {@link #read(String)} parses one such line back.
 *
 * <p>This is a closed, dependency-free codec for exactly the shape this module emits — flat objects
 * whose values are strings (with the standard JSON escapes) or non-negative integers. It is not a
 * general JSON library: anything outside that shape, an unknown event type, or a missing/mistyped
 * field is rejected with an {@link IllegalArgumentException}. Producer and consumer ship together
 * in one repository, so the format is unversioned.
 */
public final class JsonlProgressCodec {

    private JsonlProgressCodec() {}

    /**
     * Renders one event as a single JSONL line (no trailing newline).
     *
     * @param event the event to render
     * @return the JSON object line
     */
    public static String write(ProgressEvent event) {
        return switch (event) {
            case ProgressEvent.RunStarted e ->
                    obj().str("type", "runStarted").num("stageCount", e.stageCount()).end();
            case ProgressEvent.StageStarted e ->
                    obj().str("type", "stageStarted")
                            .str("stage", e.stage())
                            .num("index", e.index())
                            .num("stageCount", e.stageCount())
                            .end();
            case ProgressEvent.PageProcessed e ->
                    obj().str("type", "pageProcessed")
                            .str("stage", e.stage())
                            .num("done", e.done())
                            .num("total", e.total())
                            .end();
            case ProgressEvent.StageCompleted e ->
                    obj().str("type", "stageCompleted").str("stage", e.stage()).end();
            case ProgressEvent.RunCompleted e -> obj().str("type", "runCompleted").end();
            case ProgressEvent.RunFailed e ->
                    obj().str("type", "runFailed")
                            .str("kind", e.kind())
                            .str("message", e.message())
                            .end();
        };
    }

    /**
     * Parses one JSONL line back into an event.
     *
     * @param line a single JSON object line produced by {@link #write(ProgressEvent)}
     * @return the decoded event
     * @throws IllegalArgumentException if the line is not a well-formed event object
     */
    public static ProgressEvent read(String line) {
        Map<String, Object> o = new Reader(line).parseObject();
        String type = string(o, "type");
        return switch (type) {
            case "runStarted" -> new ProgressEvent.RunStarted(integer(o, "stageCount"));
            case "stageStarted" ->
                    new ProgressEvent.StageStarted(
                            string(o, "stage"), integer(o, "index"), integer(o, "stageCount"));
            case "pageProcessed" ->
                    new ProgressEvent.PageProcessed(
                            string(o, "stage"), integer(o, "done"), integer(o, "total"));
            case "stageCompleted" -> new ProgressEvent.StageCompleted(string(o, "stage"));
            case "runCompleted" -> new ProgressEvent.RunCompleted();
            case "runFailed" ->
                    new ProgressEvent.RunFailed(string(o, "kind"), string(o, "message"));
            default -> throw new IllegalArgumentException("unknown progress event type: " + type);
        };
    }

    private static String string(Map<String, Object> o, String key) {
        Object v = o.get(key);
        if (v instanceof String s) {
            return s;
        }
        throw new IllegalArgumentException("missing or non-string field: " + key);
    }

    private static int integer(Map<String, Object> o, String key) {
        Object v = o.get(key);
        if (v instanceof Integer n) {
            return n;
        }
        throw new IllegalArgumentException("missing or non-integer field: " + key);
    }

    private static Writer obj() {
        return new Writer();
    }

    /** A minimal JSON object writer, escaping strings exactly as the diagnostic JSONL does. */
    private static final class Writer {

        private final StringBuilder sb = new StringBuilder("{");
        private boolean empty = true;

        Writer str(String key, String value) {
            key(key);
            quote(value);
            return this;
        }

        Writer num(String key, int value) {
            key(key);
            sb.append(value);
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
    }

    /** A strict cursor parser for the flat string/integer objects this codec emits. */
    private static final class Reader {

        private final String s;
        private int i;

        Reader(String s) {
            this.s = s;
        }

        Map<String, Object> parseObject() {
            Map<String, Object> out = new LinkedHashMap<>();
            skipWs();
            expect('{');
            skipWs();
            if (peek() == '}') {
                i++;
                skipWs();
                requireEnd();
                return out;
            }
            while (true) {
                skipWs();
                String key = parseString();
                skipWs();
                expect(':');
                skipWs();
                out.put(key, parseValue());
                skipWs();
                char c = next();
                if (c == '}') {
                    break;
                }
                if (c != ',') {
                    throw err("expected ',' or '}'");
                }
            }
            skipWs();
            requireEnd();
            return out;
        }

        private Object parseValue() {
            char c = peek();
            if (c == '"') {
                return parseString();
            }
            if (c == '-' || (c >= '0' && c <= '9')) {
                return parseInt();
            }
            throw err("expected a string or integer value");
        }

        private Integer parseInt() {
            int start = i;
            if (peek() == '-') {
                i++;
            }
            while (i < s.length() && Character.isDigit(s.charAt(i))) {
                i++;
            }
            String token = s.substring(start, i);
            if (token.isEmpty() || "-".equals(token)) {
                throw err("invalid number");
            }
            try {
                return Integer.valueOf(token);
            } catch (NumberFormatException e) {
                throw err("number out of range: " + token);
            }
        }

        private String parseString() {
            expect('"');
            StringBuilder b = new StringBuilder();
            while (true) {
                if (i >= s.length()) {
                    throw err("unterminated string");
                }
                char c = s.charAt(i++);
                if (c == '"') {
                    return b.toString();
                }
                if (c == '\\') {
                    b.append(unescape());
                } else {
                    b.append(c);
                }
            }
        }

        private char unescape() {
            if (i >= s.length()) {
                throw err("dangling escape");
            }
            char e = s.charAt(i++);
            return switch (e) {
                case '"' -> '"';
                case '\\' -> '\\';
                case '/' -> '/';
                case 'n' -> '\n';
                case 'r' -> '\r';
                case 't' -> '\t';
                case 'b' -> '\b';
                case 'f' -> '\f';
                case 'u' -> parseUnicode();
                default -> throw err("invalid escape: \\" + e);
            };
        }

        private char parseUnicode() {
            if (i + 4 > s.length()) {
                throw err("truncated \\u escape");
            }
            String hex = s.substring(i, i + 4);
            i += 4;
            try {
                return (char) Integer.parseInt(hex, 16);
            } catch (NumberFormatException e) {
                throw err("invalid \\u escape: " + hex);
            }
        }

        private void skipWs() {
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    i++;
                } else {
                    break;
                }
            }
        }

        private char peek() {
            if (i >= s.length()) {
                throw err("unexpected end of line");
            }
            return s.charAt(i);
        }

        private char next() {
            char c = peek();
            i++;
            return c;
        }

        private void expect(char c) {
            if (next() != c) {
                throw err("expected '" + c + "'");
            }
        }

        private void requireEnd() {
            if (i != s.length()) {
                throw err("trailing characters after object");
            }
        }

        private IllegalArgumentException err(String why) {
            return new IllegalArgumentException(why + " (at index " + i + " in: " + s + ")");
        }
    }
}
