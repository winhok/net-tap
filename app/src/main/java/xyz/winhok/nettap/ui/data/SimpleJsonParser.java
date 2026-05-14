package xyz.winhok.nettap.ui.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class SimpleJsonParser {
    private final String source;
    private int index;

    private SimpleJsonParser(String source) {
        this.source = source == null ? "" : source;
    }

    static Map<String, Object> parseObject(String source) {
        SimpleJsonParser parser = new SimpleJsonParser(source);
        Object value = parser.parseRootValue();
        parser.skipWhitespace();
        if (parser.index != parser.source.length()) {
            throw parser.error("trailing content");
        }
        if (!(value instanceof Map)) {
            throw parser.error("root is not an object");
        }
        return castMap(value);
    }

    static Object parseObjectOrArray(String source) {
        SimpleJsonParser parser = new SimpleJsonParser(source);
        Object value = parser.parseRootValue();
        parser.skipWhitespace();
        if (parser.index != parser.source.length()) {
            throw parser.error("trailing content");
        }
        if (!(value instanceof Map) && !(value instanceof List)) {
            throw parser.error("root is not an object or array");
        }
        return value;
    }

    private Object parseRootValue() {
        skipWhitespace();
        return parseValue();
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> castMap(Object value) {
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return new LinkedHashMap<>();
    }

    private Object parseValue() {
        skipWhitespace();
        if (index >= source.length()) {
            throw error("unexpected end");
        }
        char c = source.charAt(index);
        if (c == '{') {
            return parseObjectValue();
        }
        if (c == '[') {
            return parseArrayValue();
        }
        if (c == '"') {
            return parseString();
        }
        if (c == 't' && source.startsWith("true", index)) {
            index += 4;
            return Boolean.TRUE;
        }
        if (c == 'f' && source.startsWith("false", index)) {
            index += 5;
            return Boolean.FALSE;
        }
        if (c == 'n' && source.startsWith("null", index)) {
            index += 4;
            return null;
        }
        return parseNumber();
    }

    private Map<String, Object> parseObjectValue() {
        expect('{');
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        skipWhitespace();
        if (peek('}')) {
            index++;
            return result;
        }
        while (true) {
            String key = parseString();
            skipWhitespace();
            expect(':');
            result.put(key, parseValue());
            skipWhitespace();
            if (peek('}')) {
                index++;
                return result;
            }
            expect(',');
            skipWhitespace();
        }
    }

    private List<Object> parseArrayValue() {
        expect('[');
        ArrayList<Object> result = new ArrayList<>();
        skipWhitespace();
        if (peek(']')) {
            index++;
            return result;
        }
        while (true) {
            result.add(parseValue());
            skipWhitespace();
            if (peek(']')) {
                index++;
                return result;
            }
            expect(',');
        }
    }

    private String parseString() {
        expect('"');
        StringBuilder result = new StringBuilder();
        while (index < source.length()) {
            char c = source.charAt(index++);
            if (c == '"') {
                return result.toString();
            }
            if (c != '\\') {
                result.append(c);
                continue;
            }
            if (index >= source.length()) {
                throw error("unterminated escape");
            }
            char escaped = source.charAt(index++);
            switch (escaped) {
                case '"':
                case '\\':
                case '/':
                    result.append(escaped);
                    break;
                case 'b':
                    result.append('\b');
                    break;
                case 'f':
                    result.append('\f');
                    break;
                case 'n':
                    result.append('\n');
                    break;
                case 'r':
                    result.append('\r');
                    break;
                case 't':
                    result.append('\t');
                    break;
                case 'u':
                    result.append(parseUnicodeEscape());
                    break;
                default:
                    throw error("invalid escape");
            }
        }
        throw error("unterminated string");
    }

    private char parseUnicodeEscape() {
        if (index + 4 > source.length()) {
            throw error("short unicode escape");
        }
        int value = 0;
        for (int i = 0; i < 4; i++) {
            char c = source.charAt(index++);
            int digit = Character.digit(c, 16);
            if (digit < 0) {
                throw error("invalid unicode escape");
            }
            value = (value << 4) + digit;
        }
        return (char) value;
    }

    private Number parseNumber() {
        int start = index;
        if (peek('-')) {
            index++;
        }
        while (index < source.length() && Character.isDigit(source.charAt(index))) {
            index++;
        }
        boolean fractional = false;
        if (peek('.')) {
            fractional = true;
            index++;
            int fractionStart = index;
            while (index < source.length() && Character.isDigit(source.charAt(index))) {
                index++;
            }
            if (fractionStart == index) {
                throw error("invalid number");
            }
        }
        if (peek('e') || peek('E')) {
            fractional = true;
            index++;
            if (peek('+') || peek('-')) {
                index++;
            }
            int exponentStart = index;
            while (index < source.length() && Character.isDigit(source.charAt(index))) {
                index++;
            }
            if (exponentStart == index) {
                throw error("invalid number");
            }
        }
        if (start == index) {
            throw error("expected value");
        }
        String token = source.substring(start, index);
        try {
            if (fractional) {
                return Double.valueOf(token);
            }
            return Long.valueOf(token);
        } catch (NumberFormatException e) {
            throw error("invalid number");
        }
    }

    private void skipWhitespace() {
        while (index < source.length()) {
            char c = source.charAt(index);
            if (c != ' ' && c != '\n' && c != '\r' && c != '\t') {
                return;
            }
            index++;
        }
    }

    private boolean peek(char c) {
        return index < source.length() && source.charAt(index) == c;
    }

    private void expect(char c) {
        if (!peek(c)) {
            throw error("expected " + c);
        }
        index++;
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException(message + " at " + index);
    }
}
