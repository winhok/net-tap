package xyz.winhok.nettap.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import xyz.winhok.nettap.R;
import xyz.winhok.nettap.ui.Dimens;
import xyz.winhok.nettap.ui.SectionViews;
import xyz.winhok.nettap.ui.UiPreferences;
import xyz.winhok.nettap.ui.UrlFilterSaveGuard;
import xyz.winhok.nettap.ui.UrlFilterValidator;

import com.google.android.material.button.MaterialButton;

public final class UrlFilterFragment extends Fragment {
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        UiPreferences prefs = new UiPreferences(requireContext());
        ScrollView scrollView = new ScrollView(requireContext());
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        Dimens.setPaddingDp(root, 12, 12);
        scrollView.addView(root);
        LinearLayout section = SectionViews.addSection(inflater, root);
        TextView status = new TextView(requireContext());
        status.setText(R.string.url_filter_status);
        TextView validation = new TextView(requireContext());
        Dimens.setPaddingDp(status, 12, 8);
        section.addView(status);

        section.addView(label(R.string.url_filter_regex_label));
        EditText regex = new EditText(requireContext());
        regex.setSingleLine(false);
        regex.setMinLines(3);
        regex.setHint(R.string.url_filter_hint);
        regex.setText(prefs.getUrlRegex());
        section.addView(regex);

        MaterialButton test = new MaterialButton(requireContext());
        test.setText(R.string.action_test);
        test.setAllCaps(false);
        test.setOnClickListener(view -> {
            UrlFilterValidator.Result result = UrlFilterValidator.validate(regex.getText().toString());
            validation.setText(result.isValid() ? getString(R.string.url_filter_valid) : result.getMessage());
        });
        section.addView(test);
        section.addView(validation);

        section.addView(label(R.string.url_filter_packages_label));
        EditText packages = new EditText(requireContext());
        packages.setMinLines(6);
        packages.setHint(R.string.url_filter_packages_hint);
        packages.setText(prefs.getPackageAllowlistText());
        section.addView(packages);

        MaterialButton save = new MaterialButton(requireContext());
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
        section.addView(save);
        MaterialButton clearAll = new MaterialButton(requireContext());
        clearAll.setText(R.string.action_clear_all);
        clearAll.setAllCaps(false);
        clearAll.setOnClickListener(view -> {
            regex.setText("");
            packages.setText("");
            validation.setText("");
            prefs.saveFilters("", "");
        });
        section.addView(clearAll);
        return scrollView;
    }

    private TextView label(int text) {
        TextView label = new TextView(requireContext());
        label.setText(text);
        Dimens.setPaddingDp(label, 12, 4);
        return label;
    }
}
