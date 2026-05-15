package xyz.winhok.nettap.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import xyz.winhok.nettap.R;
import xyz.winhok.nettap.ui.MainActivity;
import xyz.winhok.nettap.ui.SectionViews;
import xyz.winhok.nettap.ui.UiPreferences;

public final class SettingsFragment extends Fragment {
    private UiPreferences preferences;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        preferences = new UiPreferences(requireContext());
        View view = inflater.inflate(R.layout.fragment_settings, container, false);
        LinearLayout root = view.findViewById(R.id.settings_root);

        LinearLayout appearance = SectionViews.addSection(inflater, root);
        SectionViews.tile(inflater, appearance, R.drawable.ic_settings_outlined,
                R.string.settings_theme_mode, 0, v -> showThemeDialog());

        LinearLayout about = SectionViews.addSection(inflater, root);
        SectionViews.tile(inflater, about, R.drawable.ic_info_outline, R.string.nav_about,
                R.string.settings_about_subtitle, v -> ((MainActivity) requireActivity())
                        .showPage("about", new AboutFragment()));
        SectionViews.tile(inflater, about, R.drawable.ic_delete_outline,
                R.string.action_reset_defaults, 0, v -> resetDefaults());
        return view;
    }

    private void showThemeDialog() {
        String[] labels = new String[]{
                getString(R.string.settings_theme_system),
                getString(R.string.settings_theme_light),
                getString(R.string.settings_theme_dark)
        };
        String[] values = new String[]{
                UiPreferences.THEME_SYSTEM,
                UiPreferences.THEME_LIGHT,
                UiPreferences.THEME_DARK
        };
        int checked = 0;
        String current = preferences.getThemeMode();
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(current)) {
                checked = i;
            }
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.settings_theme_mode)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    preferences.saveThemeMode(values[which]);
                    MainActivity.applyThemeMode(values[which]);
                    dialog.dismiss();
                })
                .show();
    }

    private void resetDefaults() {
        preferences.resetDefaults();
        MainActivity.applyThemeMode(preferences.getThemeMode());
        preferences.applyRealtimeTransport();
        Toast.makeText(requireContext(), R.string.settings_saved, Toast.LENGTH_SHORT).show();
    }
}
