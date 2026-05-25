package dev.xsuite.holograms.web;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Json {

    private Json() {
    }

    public static @NotNull String encode(@Nullable Object value) {
        StringBuilder sb = new StringBuilder();
        write(sb, value);
        return sb.toString();
    }

    public static @NotNull Object decode(@NotNull String src) {
        Parser parser = new Parser(src);
        Object value = parser.readValue();
        parser.skipWhitespace();
        if (parser.pos < src.length()) throw parser.error("Unexpected trailing content");
        return value;
    }

    public static @NotNull Map<String, Object> decodeObject(@NotNull String src) {
        Object root = decode(src);
        if (!(root instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("Expected JSON object");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> casted = (Map<String, Object>) map;
        return casted;
    }

    public static @Nullable String string(@NotNull Map<String, Object> obj, @NotNull String key) {
        Object value = obj.get(key);
        return value == null ? null : value.toString();
    }

    public static @Nullable Number number(@NotNull Map<String, Object> obj, @NotNull String key) {
        Object value = obj.get(key);
        return value instanceof Number n ? n : null;
    }

    public static @Nullable Boolean bool(@NotNull Map<String, Object> obj, @NotNull String key) {
        Object value = obj.get(key);
        return value instanceof Boolean b ? b : null;
    }

    @SuppressWarnings("unchecked")
    public static @Nullable List<Object> list(@NotNull Map<String, Object> obj, @NotNull String key) {
        Object value = obj.get(key);
        return value instanceof List<?> l ? (List<Object>) l : null;
    }

    @SuppressWarnings("unchecked")
    public static @Nullable Map<String, Object> object(@NotNull Map<String, Object> obj, @NotNull String key) {
        Object value = obj.get(key);
        return value instanceof Map<?, ?> m ? (Map<String, Object>) m : null;
    }

    private static void write(StringBuilder sb, @Nullable Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof Boolean b) {
            sb.append(b.booleanValue());
        } else if (value instanceof Number n) {
            writeNumber(sb, n);
        } else if (value instanceof CharSequence s) {
            writeString(sb, s);
        } else if (value instanceof Map<?, ?> m) {
            writeObject(sb, m);
        } else if (value instanceof Iterable<?> it) {
            writeArray(sb, it);
        } else {
            writeString(sb, value.toString());
        }
    }

    private static void writeNumber(StringBuilder sb, Number n) {
        double d = n.doubleValue();
        if (Double.isNaN(d) || Double.isInfinite(d)) {
            sb.append("null");
            return;
        }
        if (n instanceof Integer || n instanceof Long || n instanceof Short || n instanceof Byte) {
            sb.append(n);
        } else if (d == Math.floor(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15) {
            sb.append(Long.toString((long) d));
        } else {
            sb.append(n);
        }
    }

    private static void writeString(StringBuilder sb, CharSequence s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
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

    private static void writeObject(StringBuilder sb, Map<?, ?> m) {
        sb.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> entry : m.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            writeString(sb, entry.getKey().toString());
            sb.append(':');
            write(sb, entry.getValue());
        }
        sb.append('}');
    }

    private static void writeArray(StringBuilder sb, Iterable<?> it) {
        sb.append('[');
        boolean first = true;
        for (Object item : it) {
            if (!first) sb.append(',');
            first = false;
            write(sb, item);
        }
        sb.append(']');
    }

    private static final class Parser {
        final String src;
        int pos;

        Parser(String src) {
            this.src = src;
        }

        Object readValue() {
            skipWhitespace();
            if (pos >= src.length()) throw error("Unexpected end of input");
            char c = src.charAt(pos);
            return switch (c) {
                case '{' -> readObject();
                case '[' -> readArray();
                case '"' -> readString();
                case 't', 'f' -> readBoolean();
                case 'n' -> readNull();
                default -> readNumber();
            };
        }

        Map<String, Object> readObject() {
            expect('{');
            Map<String, Object> map = new LinkedHashMap<>();
            skipWhitespace();
            if (peek() == '}') {
                pos++;
                return map;
            }
            while (true) {
                skipWhitespace();
                String key = readString();
                skipWhitespace();
                expect(':');
                Object value = readValue();
                map.put(key, value);
                skipWhitespace();
                char c = peek();
                if (c == ',') {
                    pos++;
                    continue;
                }
                if (c == '}') {
                    pos++;
                    return map;
                }
                throw error("Expected ',' or '}'");
            }
        }

        List<Object> readArray() {
            expect('[');
            List<Object> list = new java.util.ArrayList<>();
            skipWhitespace();
            if (peek() == ']') {
                pos++;
                return list;
            }
            while (true) {
                list.add(readValue());
                skipWhitespace();
                char c = peek();
                if (c == ',') {
                    pos++;
                    continue;
                }
                if (c == ']') {
                    pos++;
                    return list;
                }
                throw error("Expected ',' or ']'");
            }
        }

        String readString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (pos < src.length()) {
                char c = src.charAt(pos++);
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    if (pos >= src.length()) throw error("Unterminated escape");
                    char esc = src.charAt(pos++);
                    switch (esc) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> {
                            if (pos + 4 > src.length()) throw error("Bad unicode escape");
                            sb.append((char) Integer.parseInt(src.substring(pos, pos + 4), 16));
                            pos += 4;
                        }
                        default -> throw error("Bad escape: \\" + esc);
                    }
                } else {
                    sb.append(c);
                }
            }
            throw error("Unterminated string");
        }

        Boolean readBoolean() {
            if (src.startsWith("true", pos)) {
                pos += 4;
                return Boolean.TRUE;
            }
            if (src.startsWith("false", pos)) {
                pos += 5;
                return Boolean.FALSE;
            }
            throw error("Expected boolean");
        }

        Object readNull() {
            if (src.startsWith("null", pos)) {
                pos += 4;
                return null;
            }
            throw error("Expected null");
        }

        Number readNumber() {
            int start = pos;
            if (peek() == '-') pos++;
            while (pos < src.length()) {
                char c = src.charAt(pos);
                if ((c >= '0' && c <= '9') || c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
                    pos++;
                } else break;
            }
            String slice = src.substring(start, pos);
            if (slice.contains(".") || slice.contains("e") || slice.contains("E")) {
                return Double.parseDouble(slice);
            }
            try {
                long l = Long.parseLong(slice);
                if (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) return (int) l;
                return l;
            } catch (NumberFormatException e) {
                return Double.parseDouble(slice);
            }
        }

        void skipWhitespace() {
            while (pos < src.length()) {
                char c = src.charAt(pos);
                if (c == ' ' || c == '\n' || c == '\r' || c == '\t') pos++;
                else break;
            }
        }

        char peek() {
            if (pos >= src.length()) throw error("Unexpected end of input");
            return src.charAt(pos);
        }

        void expect(char c) {
            skipWhitespace();
            if (pos >= src.length() || src.charAt(pos) != c) throw error("Expected '" + c + "'");
            pos++;
        }

        IllegalArgumentException error(String msg) {
            return new IllegalArgumentException(msg + " at position " + pos);
        }
    }
}
