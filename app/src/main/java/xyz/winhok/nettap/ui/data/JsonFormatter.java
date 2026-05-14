package xyz.winhok.nettap.ui.data;

public final class JsonFormatter {
    private JsonFormatter() {
    }

    public static String format(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "";
        }
        validateJson(value);
        return formatTrusted(value);
    }

    static String formatTrusted(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "";
        }
        StringBuilder result = new StringBuilder(value.length() + 16);
        int indent = 0;
        boolean inString = false;
        for (int index = 0; index < value.length(); index++) {
            char c = value.charAt(index);
            if (c == '"' && !isEscaped(value, index)) {
                inString = !inString;
            }
            if (!inString && (c == '{' || c == '[')) {
                result.append(c).append('\n');
                indent++;
                appendIndent(result, indent);
            } else if (!inString && (c == '}' || c == ']')) {
                result.append('\n');
                indent = Math.max(0, indent - 1);
                appendIndent(result, indent);
                result.append(c);
            } else if (!inString && c == ',') {
                result.append(c).append('\n');
                appendIndent(result, indent);
            } else if (!inString && c == ':') {
                result.append(": ");
            } else if (inString || !Character.isWhitespace(c)) {
                result.append(c);
            }
        }
        return result.toString();
    }

    static boolean isJsonCandidate(String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim();
        return (trimmed.startsWith("{") && trimmed.endsWith("}"))
                || (trimmed.startsWith("[") && trimmed.endsWith("]"));
    }

    private static void validateJson(String value) {
        if (!isJsonCandidate(value)) {
            throw new IllegalArgumentException("not json");
        }
        SimpleJsonParser.parseObjectOrArray(value);
    }

    private static boolean isEscaped(String value, int index) {
        int slashCount = 0;
        int cursor = index - 1;
        while (cursor >= 0 && value.charAt(cursor) == '\\') {
            slashCount++;
            cursor--;
        }
        return slashCount % 2 == 1;
    }

    private static void appendIndent(StringBuilder result, int indent) {
        for (int i = 0; i < indent; i++) {
            result.append("  ");
        }
    }
}
