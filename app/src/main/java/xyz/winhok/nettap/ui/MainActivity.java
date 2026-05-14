package xyz.winhok.nettap.ui;

import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.view.GravityCompat;
import androidx.core.content.FileProvider;
import androidx.core.splashscreen.SplashScreen;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.color.DynamicColors;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.io.File;
import java.io.IOException;

import xyz.winhok.nettap.R;
import xyz.winhok.nettap.ui.data.CaptureUiEvent;
import xyz.winhok.nettap.ui.data.HarExportResult;
import xyz.winhok.nettap.ui.data.HarExportRequest;
import xyz.winhok.nettap.ui.data.HarExportWorker;
import xyz.winhok.nettap.ui.data.RawJsonFileExporter;
import xyz.winhok.nettap.ui.adapter.DrawerAdapter;
import xyz.winhok.nettap.ui.fragment.AboutFragment;
import xyz.winhok.nettap.ui.fragment.BodyViewerFragment;
import xyz.winhok.nettap.ui.fragment.CaptureDetailFragment;
import xyz.winhok.nettap.ui.fragment.CaptureListFragment;
import xyz.winhok.nettap.ui.fragment.DetailHostFragment;
import xyz.winhok.nettap.ui.fragment.HookSettingsFragment;
import xyz.winhok.nettap.ui.fragment.TlsKeylogFragment;
import xyz.winhok.nettap.ui.fragment.UrlFilterFragment;

public final class MainActivity extends AppCompatActivity {
    private static final String TAG = "NetTapMain";

    private NavigationController navigationController;
    private UiPreferences preferences;
    private int containerId;
    private DrawerLayout drawerLayout;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService harExportExecutor = Executors.newSingleThreadExecutor();
    private final HarExportWorker harExportWorker = new HarExportWorker(
            harExportExecutor,
            mainHandler::post
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        preferences = new UiPreferences(this);
        applyThemeMode(preferences.getThemeMode());
        preferences.applyToRuntime();
        if (preferences.isTransportEnabled()) {
            NetTapUiState.startRealtimeServer(preferences.getTransportPort());
        }
        containerId = View.generateViewId();
        setContentView(createLayout(containerId));
        navigationController = new NavigationController(getSupportFragmentManager(), containerId);
        if (savedInstanceState == null) {
            navigationController.show("capture", new CaptureListFragment(), false);
        }
    }

    public void showDetail(String eventId) {
        DetailHostFragment.setSelectedEventId(eventId);
        navigationController.show("detail", new CaptureDetailFragment(), true);
    }

    public void showBodyViewer() {
        navigationController.show("body", new BodyViewerFragment(), true);
    }

    public void exportHar(List<CaptureUiEvent> events) {
        exportHar(HarExportRequest.from(events));
    }

    public void shareRawJson(CaptureUiEvent event) {
        harExportExecutor.execute(() -> {
            try {
                File file = RawJsonFileExporter.write(getExternalFilesDir("export"), event);
                mainHandler.post(() -> showRawJsonShareResult(file));
            } catch (IOException | IllegalArgumentException e) {
                Log.w(TAG, "Raw JSON share failed", e);
                mainHandler.post(this::showRawJsonShareFailure);
            }
        });
    }

    private void exportHar(HarExportRequest request) {
        showSnackbar(R.string.har_export_started);
        harExportWorker.export(getExternalFilesDir("export"), request.events(), new HarExportWorker.Callback() {
            @Override
            public void onSuccess(HarExportResult result) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                showHarExportResult(result);
            }

            @Override
            public void onFailure(Exception exception) {
                Log.w(TAG, "HAR export failed", exception);
                if (!isFinishing() && !isDestroyed()) {
                    showHarExportFailure(request);
                }
            }
        });
    }

    private void showHarExportFailure(HarExportRequest request) {
        if (drawerLayout == null) {
            Toast.makeText(this, R.string.har_export_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        Snackbar.make(drawerLayout, R.string.har_export_failed, Snackbar.LENGTH_LONG)
                .setAction(R.string.action_retry, view -> exportHar(request))
                .show();
    }

    private void showHarExportResult(HarExportResult result) {
        Snackbar.make(
                drawerLayout,
                getString(R.string.har_export_success, result.getEntryCount()),
                Snackbar.LENGTH_LONG
        ).setAction(R.string.action_open, view -> openHar(result)).show();
    }

    private void openHar(HarExportResult result) {
        try {
            Intent open = new Intent(Intent.ACTION_VIEW);
            open.setDataAndType(harUri(result), "application/json");
            open.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(open);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.har_open_unavailable, Toast.LENGTH_LONG).show();
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "HAR open failed", e);
            showSnackbar(R.string.har_export_failed);
        }
    }

    private Uri harUri(HarExportResult result) {
        return FileProvider.getUriForFile(
                this,
                getPackageName() + ".fileprovider",
                result.getFile()
        );
    }

    private void showRawJsonShareResult(File file) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        try {
            Uri uri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider",
                    file
            );
            Intent send = new Intent(Intent.ACTION_SEND)
                    .setType("application/json")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(send, getString(R.string.action_share_raw_json)));
        } catch (ActivityNotFoundException | IllegalArgumentException e) {
            Log.w(TAG, "Raw JSON share failed", e);
            showRawJsonShareFailure();
        }
    }

    private void showRawJsonShareFailure() {
        Toast.makeText(this, R.string.raw_json_share_failed, Toast.LENGTH_SHORT).show();
    }

    private void showSnackbar(int message) {
        if (drawerLayout == null) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            return;
        }
        Snackbar.make(drawerLayout, message, Snackbar.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isFinishing()) {
            NetTapUiState.stopRealtimeServer();
        }
        harExportExecutor.shutdownNow();
    }

    private View createLayout(int containerId) {
        drawerLayout = new DrawerLayout(this);
        drawerLayout.setId(R.id.drawer_layout);
        drawerLayout.setBackgroundColor(getColor(R.color.nettap_surface));

        LinearLayout contentRoot = new LinearLayout(this);
        contentRoot.setOrientation(LinearLayout.VERTICAL);
        Button menuButton = new Button(this);
        menuButton.setText(R.string.nav_menu);
        menuButton.setAllCaps(false);
        menuButton.setOnClickListener(view -> drawerLayout.openDrawer(GravityCompat.START));
        contentRoot.addView(menuButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        FrameLayout content = new FrameLayout(this);
        content.setId(containerId);
        contentRoot.addView(content, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));
        drawerLayout.addView(contentRoot, new DrawerLayout.LayoutParams(
                DrawerLayout.LayoutParams.MATCH_PARENT,
                DrawerLayout.LayoutParams.MATCH_PARENT
        ));

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.VERTICAL);
        nav.setGravity(Gravity.TOP);
        nav.setPadding(12, 18, 12, 12);
        DrawerLayout.LayoutParams navParams = new DrawerLayout.LayoutParams(
                dp(280),
                DrawerLayout.LayoutParams.MATCH_PARENT
        );
        navParams.gravity = GravityCompat.START;
        drawerLayout.addView(nav, navParams);

        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextSize(20);
        title.setTextColor(getColor(R.color.nettap_on_surface));
        nav.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        DrawerAdapter drawerAdapter = new DrawerAdapter();
        if (drawerAdapter.size() != 5) {
            throw new IllegalStateException("unexpected drawer item count");
        }
        addNavButton(nav, R.string.nav_capture, () -> navigationController.show("capture", new CaptureListFragment(), false));
        addNavButton(nav, R.string.nav_hooks, () -> navigationController.show("hooks", new HookSettingsFragment(), false));
        addNavButton(nav, R.string.nav_url_filter, () -> navigationController.show("url-filter", new UrlFilterFragment(), false));
        addNavButton(nav, R.string.nav_tls_keylog, () -> navigationController.show("tls", new TlsKeylogFragment(), false));
        addNavButton(nav, R.string.nav_about, () -> navigationController.show("about", new AboutFragment(), false));
        return drawerLayout;
    }

    private void addNavButton(LinearLayout nav, int label, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setOnClickListener(view -> {
            action.run();
            if (drawerLayout != null) {
                drawerLayout.closeDrawer(GravityCompat.START);
            }
        });
        nav.addView(button, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    static void applyThemeMode(String themeMode) {
        if (UiPreferences.THEME_LIGHT.equals(themeMode)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            return;
        }
        if (UiPreferences.THEME_DARK.equals(themeMode)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            return;
        }
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }
}
