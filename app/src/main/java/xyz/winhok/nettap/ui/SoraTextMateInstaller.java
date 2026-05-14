package xyz.winhok.nettap.ui;

import android.content.Context;
import android.content.res.Configuration;
import android.util.Log;

import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme;
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage;
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel;
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver;
import io.github.rosemoe.sora.widget.CodeEditor;
import org.eclipse.tm4e.core.registry.IThemeSource;

public final class SoraTextMateInstaller {
    private static final String TAG = "NetTapTextMate";
    private static final String LIGHT_THEME = "nettap-light";
    private static final String DARK_THEME = "nettap-dark";
    private static final AtomicBoolean ASSETS_REGISTERED = new AtomicBoolean();
    private static final AtomicBoolean GRAMMARS_LOADED = new AtomicBoolean();

    private SoraTextMateInstaller() {
    }

    public static boolean apply(Context context, CodeEditor editor, String language) {
        String scope = BodyLanguageScope.textMateScope(language);
        if (context == null || editor == null || scope == null) {
            return false;
        }
        try {
            Context appContext = context.getApplicationContext();
            registerAssets(appContext);
            String theme = themeName(context);
            loadTheme(appContext, theme);
            loadGrammars();
            ThemeRegistry registry = ThemeRegistry.getInstance();
            registry.setTheme(theme);
            editor.setColorScheme(TextMateColorScheme.create(registry));
            editor.setEditorLanguage(TextMateLanguage.create(scope, false));
            return true;
        } catch (Exception e) {
            Log.w(TAG, "TextMate setup failed; using plain editor", e);
            return false;
        }
    }

    private static void registerAssets(Context context) {
        if (ASSETS_REGISTERED.compareAndSet(false, true)) {
            FileProviderRegistry.getInstance().addFileProvider(
                    new AssetsFileResolver(context.getAssets())
            );
        }
    }

    private static void loadTheme(Context context, String name) throws Exception {
        ThemeRegistry registry = ThemeRegistry.getInstance();
        if (registry.findThemeByThemeName(name) != null) {
            return;
        }
        String path = "textmate/" + name + ".json";
        try (InputStream input = context.getAssets().open(path)) {
            ThemeModel model = new ThemeModel(
                    IThemeSource.fromInputStream(input, path, null),
                    name
            );
            model.setDark(DARK_THEME.equals(name));
            registry.loadTheme(model);
        }
    }

    private static void loadGrammars() {
        if (!GRAMMARS_LOADED.get()) {
            GrammarRegistry.getInstance().loadGrammars("textmate/languages.json");
            GRAMMARS_LOADED.set(true);
        }
    }

    private static String themeName(Context context) {
        int mode = context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES ? DARK_THEME : LIGHT_THEME;
    }
}
