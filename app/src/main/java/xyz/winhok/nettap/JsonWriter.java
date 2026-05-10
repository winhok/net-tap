package xyz.winhok.nettap;

public final class JsonWriter {
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private JsonWriter() {
    }

    public static String number(long value) {
        return Long.toString(value);
    }

    public static String number(int value) {
        return Integer.toString(value);
    }

    public static String bool(boolean value) {
        return Boolean.toString(value);
    }

    public static String object(java.util.Map<String, String> values) {
        if (values == null) {
            return "null";
        }

        StringBuilder result = new StringBuilder();
        result.append('{');
        boolean first = true;
        for (java.util.Map.Entry<String, String> entry : values.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            if (!first) {
                result.append(',');
            }
            first = false;
            result.append(string(entry.getKey()));
            result.append(':');
            result.append(string(entry.getValue()));
        }
        result.append('}');
        return result.toString();
    }

    public static String string(String value) {
        if (value == null) {
            return "null";
        }

        StringBuilder result = new StringBuilder(value.length() + 2);
        result.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"':
                    result.append("\\\"");
                    break;
                case '\\':
                    result.append("\\\\");
                    break;
                case '\n':
                    result.append("\\n");
                    break;
                case '\r':
                    result.append("\\r");
                    break;
                case '\t':
                    result.append("\\t");
                    break;
                default:
                    if (character < 0x20) {
                        result.append("\\u00")
                                .append(HEX[(character >> 4) & 0xF])
                                .append(HEX[character & 0xF]);
                    } else {
                        result.append(character);
                    }
                    break;
            }
        }
        result.append('"');
        return result.toString();
    }
}
