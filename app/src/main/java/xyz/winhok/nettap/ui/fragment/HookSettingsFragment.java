package xyz.winhok.nettap.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import xyz.winhok.nettap.R;
import xyz.winhok.nettap.ui.HookSettingStatusFormatter;
import xyz.winhok.nettap.ui.NetTapUiState;
import xyz.winhok.nettap.ui.UiPreferences;

import com.google.android.material.switchmaterial.SwitchMaterial;

public final class HookSettingsFragment extends Fragment {
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        UiPreferences prefs = new UiPreferences(requireContext());
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(18, 18, 18, 18);

        SwitchMaterial transport = switchRow(R.string.settings_realtime_transport, prefs.isTransportEnabled());
        SwitchMaterial builder = switchRow(R.string.settings_builder_hook, prefs.isBuilderHookEnabled());
        SwitchMaterial tls = switchRow(R.string.settings_tls_keylog, prefs.isTlsKeylogEnabled());
        SwitchMaterial cronet = switchRow(R.string.settings_cronet_keylog, prefs.isCronetKeylogEnabled());
        EditText port = input(R.string.settings_port_hint, String.valueOf(prefs.getTransportPort()));
        EditText maxRecords = input(R.string.settings_max_records_hint, String.valueOf(prefs.getMaxRecords()));
        EditText maxMemory = input(R.string.settings_max_memory_hint, String.valueOf(prefs.getMaxMemoryMb()));
        root.addView(transport);
        root.addView(realtimeDiagnostics());
        root.addView(builder);
        root.addView(helper(builder));
        root.addView(tls);
        root.addView(helper(tls));
        root.addView(cronet);
        root.addView(helper(cronet));
        root.addView(label(R.string.settings_realtime_port));
        root.addView(port);
        root.addView(label(R.string.settings_ui_max_records));
        root.addView(maxRecords);
        root.addView(label(R.string.settings_ui_max_memory));
        root.addView(maxMemory);

        Button save = new Button(requireContext());
        save.setText(R.string.action_save);
        save.setAllCaps(false);
        save.setOnClickListener(view -> {
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
            if (transport.isChecked()) {
                NetTapUiState.startRealtimeServer(prefs.getTransportPort());
            } else {
                NetTapUiState.stopRealtimeServer();
            }
            Toast.makeText(requireContext(), R.string.settings_saved, Toast.LENGTH_SHORT).show();
        });
        root.addView(save);
        Button reset = new Button(requireContext());
        reset.setText(R.string.action_reset_defaults);
        reset.setAllCaps(false);
        reset.setOnClickListener(view -> {
            prefs.resetDefaults();
            transport.setChecked(prefs.isTransportEnabled());
            builder.setChecked(prefs.isBuilderHookEnabled());
            tls.setChecked(prefs.isTlsKeylogEnabled());
            cronet.setChecked(prefs.isCronetKeylogEnabled());
            port.setText(String.valueOf(prefs.getTransportPort()));
            maxRecords.setText(String.valueOf(prefs.getMaxRecords()));
            maxMemory.setText(String.valueOf(prefs.getMaxMemoryMb()));
            NetTapUiState.startRealtimeServer(prefs.getTransportPort());
            Toast.makeText(requireContext(), R.string.settings_saved, Toast.LENGTH_SHORT).show();
        });
        root.addView(reset);
        return root;
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
        switchMaterial.setOnCheckedChangeListener((buttonView, checked) ->
                helper.setText(HookSettingStatusFormatter.format(checked)));
        return helper;
    }

    private TextView label(int text) {
        TextView label = new TextView(requireContext());
        label.setText(text);
        return label;
    }

    private TextView realtimeDiagnostics() {
        TextView diagnostics = new TextView(requireContext());
        diagnostics.setText(getString(
                R.string.realtime_diagnostics,
                NetTapUiState.acceptedLineCount(),
                NetTapUiState.corruptLineCount(),
                NetTapUiState.oversizedLineCount()
        ));
        return diagnostics;
    }

    private EditText input(int hint, String text) {
        EditText input = new EditText(requireContext());
        input.setHint(hint);
        input.setSingleLine(true);
        input.setText(text);
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
