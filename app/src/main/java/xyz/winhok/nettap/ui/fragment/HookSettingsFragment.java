package xyz.winhok.nettap.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import xyz.winhok.nettap.R;
import xyz.winhok.nettap.ui.Dimens;
import xyz.winhok.nettap.ui.HookSettingStatusFormatter;
import xyz.winhok.nettap.ui.NetTapUiState;
import xyz.winhok.nettap.ui.SectionViews;
import xyz.winhok.nettap.ui.UiPreferences;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.switchmaterial.SwitchMaterial;

public final class HookSettingsFragment extends Fragment {
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        UiPreferences prefs = new UiPreferences(requireContext());
        View view = inflater.inflate(R.layout.fragment_hook_settings, container, false);
        LinearLayout root = view.findViewById(R.id.hook_settings_root);

        SwitchMaterial transport = switchRow(R.string.settings_realtime_transport, prefs.isTransportEnabled());
        SwitchMaterial builder = switchRow(R.string.settings_builder_hook, prefs.isBuilderHookEnabled());
        SwitchMaterial tls = switchRow(R.string.settings_tls_keylog, prefs.isTlsKeylogEnabled());
        SwitchMaterial cronet = switchRow(R.string.settings_cronet_keylog, prefs.isCronetKeylogEnabled());
        LinearLayout realtime = SectionViews.addSection(inflater, root);
        SectionViews.header(realtime, R.string.settings_realtime_section);
        realtime.addView(transport);
        realtime.addView(realtimeDiagnostics());
        EditText port = input(realtime, R.string.settings_port_hint, String.valueOf(prefs.getTransportPort()));

        LinearLayout hooks = SectionViews.addSection(inflater, root);
        SectionViews.header(hooks, R.string.settings_hooks_section);
        hooks.addView(builder);
        hooks.addView(helper(builder));
        hooks.addView(tls);
        hooks.addView(helper(tls));
        hooks.addView(cronet);
        hooks.addView(helper(cronet));

        LinearLayout buffers = SectionViews.addSection(inflater, root);
        SectionViews.header(buffers, R.string.settings_buffers_section);
        EditText maxRecords = input(buffers, R.string.settings_max_records_hint, String.valueOf(prefs.getMaxRecords()));
        EditText maxMemory = input(buffers, R.string.settings_max_memory_hint, String.valueOf(prefs.getMaxMemoryMb()));

        MaterialButton save = new MaterialButton(requireContext());
        save.setText(R.string.action_save);
        save.setAllCaps(false);
        save.setOnClickListener(v -> {
            int parsedPort = parseInt(port.getText().toString(), prefs.getTransportPort());
            prefs.save(
                    transport.isChecked(),
                    parsedPort,
                    parseInt(maxRecords.getText().toString(), prefs.getMaxRecords()),
                    parseInt(maxMemory.getText().toString(), prefs.getMaxMemoryMb()),
                    builder.isChecked(),
                    tls.isChecked(),
                    cronet.isChecked()
            );
            port.setText(String.valueOf(prefs.getTransportPort()));
            maxRecords.setText(String.valueOf(prefs.getMaxRecords()));
            maxMemory.setText(String.valueOf(prefs.getMaxMemoryMb()));
            prefs.applyRealtimeTransport();
            Toast.makeText(requireContext(), R.string.settings_saved, Toast.LENGTH_SHORT).show();
        });
        root.addView(save);
        MaterialButton reset = new MaterialButton(requireContext());
        reset.setText(R.string.action_reset_defaults);
        reset.setAllCaps(false);
        reset.setOnClickListener(v -> {
            prefs.resetDefaults();
            transport.setChecked(prefs.isTransportEnabled());
            builder.setChecked(prefs.isBuilderHookEnabled());
            tls.setChecked(prefs.isTlsKeylogEnabled());
            cronet.setChecked(prefs.isCronetKeylogEnabled());
            port.setText(String.valueOf(prefs.getTransportPort()));
            maxRecords.setText(String.valueOf(prefs.getMaxRecords()));
            maxMemory.setText(String.valueOf(prefs.getMaxMemoryMb()));
            prefs.applyRealtimeTransport();
            Toast.makeText(requireContext(), R.string.settings_saved, Toast.LENGTH_SHORT).show();
        });
        root.addView(reset);
        return view;
    }

    private SwitchMaterial switchRow(int text, boolean checked) {
        SwitchMaterial switchMaterial = new SwitchMaterial(requireContext());
        switchMaterial.setText(text);
        switchMaterial.setChecked(checked);
        return switchMaterial;
    }

    private TextView helper(SwitchMaterial switchMaterial) {
        TextView helper = new TextView(requireContext());
        helper.setText(HookSettingStatusFormatter.format(switchMaterial.isChecked()));
        helper.setTextSize(11f);
        Dimens.setPaddingDp(helper, 12, 4);
        switchMaterial.setOnCheckedChangeListener((buttonView, checked) ->
                helper.setText(HookSettingStatusFormatter.format(checked)));
        return helper;
    }

    private TextView realtimeDiagnostics() {
        TextView diagnostics = new TextView(requireContext());
        diagnostics.setText(getString(
                R.string.realtime_diagnostics,
                NetTapUiState.acceptedLineCount(),
                NetTapUiState.corruptLineCount(),
                NetTapUiState.oversizedLineCount()
        ));
        diagnostics.setTextSize(11f);
        Dimens.setPaddingDp(diagnostics, 12, 4);
        return diagnostics;
    }

    private EditText input(LinearLayout parent, int hint, String text) {
        TextInputLayout layout = new TextInputLayout(requireContext());
        layout.setHint(getString(hint));
        layout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        TextInputEditText input = new TextInputEditText(requireContext());
        input.setHint(hint);
        input.setSingleLine(true);
        input.setText(text);
        layout.addView(input);
        parent.addView(layout);
        return input;
    }

    private int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
