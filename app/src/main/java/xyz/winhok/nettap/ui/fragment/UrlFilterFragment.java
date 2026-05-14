package xyz.winhok.nettap.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import xyz.winhok.nettap.R;
import xyz.winhok.nettap.ui.UiPreferences;
import xyz.winhok.nettap.ui.UrlFilterSaveGuard;
import xyz.winhok.nettap.ui.UrlFilterValidator;

public final class UrlFilterFragment extends Fragment {
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        UiPreferences prefs = new UiPreferences(requireContext());
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(18, 18, 18, 18);
        TextView status = new TextView(requireContext());
        status.setText(R.string.url_filter_status);
        TextView validation = new TextView(requireContext());
        root.addView(status);

        root.addView(label(R.string.url_filter_regex_label));
        EditText regex = new EditText(requireContext());
        regex.setSingleLine(false);
        regex.setMinLines(3);
        regex.setHint(R.string.url_filter_hint);
        regex.setText(prefs.getUrlRegex());
        root.addView(regex);

        Button test = new Button(requireContext());
        test.setText(R.string.action_test);
        test.setAllCaps(false);
        test.setOnClickListener(view -> {
            UrlFilterValidator.Result result = UrlFilterValidator.validate(regex.getText().toString());
            validation.setText(result.isValid() ? getString(R.string.url_filter_valid) : result.getMessage());
        });
        root.addView(test);
        root.addView(validation);

        root.addView(label(R.string.url_filter_packages_label));
        EditText packages = new EditText(requireContext());
        packages.setMinLines(6);
        packages.setHint(R.string.url_filter_packages_hint);
        packages.setText(prefs.getPackageAllowlistText());
        root.addView(packages);

        Button save = new Button(requireContext());
        save.setText(R.string.action_save);
        save.setAllCaps(false);
        save.setOnClickListener(view -> {
            UrlFilterSaveGuard.Decision decision = UrlFilterSaveGuard.evaluate(
                    regex.getText().toString()
            );
            if (!decision.shouldSave()) {
                validation.setText(decision.message());
                return;
            }
            prefs.saveFilters(
                    regex.getText().toString(),
                    packages.getText().toString()
            );
            validation.setText(R.string.settings_saved);
        });
        root.addView(save);
        Button clearAll = new Button(requireContext());
        clearAll.setText(R.string.action_clear_all);
        clearAll.setAllCaps(false);
        clearAll.setOnClickListener(view -> {
            regex.setText("");
            packages.setText("");
            validation.setText("");
            prefs.saveFilters("", "");
        });
        root.addView(clearAll);
        return root;
    }

    private TextView label(int text) {
        TextView label = new TextView(requireContext());
        label.setText(text);
        return label;
    }
}
