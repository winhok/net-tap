package xyz.winhok.nettap.ui;

public final class UrlFilterSaveGuard {
    private UrlFilterSaveGuard() {
    }

    public static Decision evaluate(String regex) {
        UrlFilterValidator.Result result = UrlFilterValidator.validate(regex);
        if (result.isValid()) {
            return new Decision(true, "");
        }
        return new Decision(false, result.getMessage());
    }

    public static final class Decision {
        private final boolean shouldSave;
        private final String message;

        private Decision(boolean shouldSave, String message) {
            this.shouldSave = shouldSave;
            this.message = message == null ? "" : message;
        }

        public boolean shouldSave() {
            return shouldSave;
        }

        public String message() {
            return message;
        }
    }
}
