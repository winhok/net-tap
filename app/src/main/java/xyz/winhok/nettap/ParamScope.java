package xyz.winhok.nettap;

import de.robv.android.xposed.XC_MethodHook;

/** MethodScopeState.Scope adapter over Xposed {@code MethodHookParam} extras. */
final class ParamScope implements MethodScopeState.Scope {
    private final XC_MethodHook.MethodHookParam param;

    ParamScope(XC_MethodHook.MethodHookParam param) {
        this.param = param;
    }

    @Override
    public Object getExtra(String key) {
        return param.getObjectExtra(key);
    }

    @Override
    public void setExtra(String key, Object value) {
        param.setObjectExtra(key, value);
    }
}
