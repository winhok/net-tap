package de.robv.android.xposed;

import java.lang.reflect.Member;
import java.util.HashMap;
import java.util.Map;

public abstract class XC_MethodHook {
    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
    }

    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
    }

    public final class Unhook {
        public Member getHookedMethod() {
            return null;
        }

        public XC_MethodHook getCallback() {
            return XC_MethodHook.this;
        }

        public void unhook() {
        }
    }

    public static final class MethodHookParam {
        public Member method;
        public Object thisObject;
        public Object[] args;
        private Object result;
        private Throwable throwable;
        private final Map<String, Object> extras = new HashMap<>();

        public Object getResult() {
            return result;
        }

        public void setResult(Object result) {
            this.result = result;
        }

        public Throwable getThrowable() {
            return throwable;
        }

        public void setThrowable(Throwable throwable) {
            this.throwable = throwable;
        }

        public Object getObjectExtra(String key) {
            return extras.get(key);
        }

        public void setObjectExtra(String key, Object value) {
            extras.put(key, value);
        }
    }
}
