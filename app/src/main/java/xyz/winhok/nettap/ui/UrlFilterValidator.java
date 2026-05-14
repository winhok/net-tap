package xyz.winhok.nettap.ui;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class UrlFilterValidator {
    private UrlFilterValidator() {
    }

    public static Result validate(String regex) {
        if (regex == null || regex.trim().isEmpty()) {
            return Result.valid();
        }
        for (String line : regex.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                Pattern.compile(trimmed);
            } catch (PatternSyntaxException e) {
                return new Result(false, e.getDescription());
            }
        }
        return Result.valid();
    }

    public static final class Result {
        private final boolean valid;
        private final String message;

        private Result(boolean valid, String message) {
            this.valid = valid;
            this.message = message == null ? "" : message;
        }

        static Result valid() {
            return new Result(true, "");
        }

        public boolean isValid() {
            return valid;
        }

        public String getMessage() {
            return message;
        }
    }
}
