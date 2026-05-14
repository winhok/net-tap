package xyz.winhok.nettap.ui.fragment;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
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
import xyz.winhok.nettap.RuntimeCaptureConfig;
import xyz.winhok.nettap.ui.UiPreferences;
import xyz.winhok.nettap.ui.data.TlsKeylogPath;

import com.google.android.material.switchmaterial.SwitchMaterial;

public final class TlsKeylogFragment extends Fragment {
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(18, 18, 18, 18);
        UiPreferences prefs = new UiPreferences(requireContext());
        SwitchMaterial tlsSwitch = new SwitchMaterial(requireContext());
        tlsSwitch.setText(R.string.settings_tls_keylog);
        tlsSwitch.setChecked(prefs.isTlsKeylogEnabled());
        tlsSwitch.setOnCheckedChangeListener((buttonView, checked) -> prefs.save(
                prefs.isTransportEnabled(),
                prefs.getTransportPort(),
                prefs.getMaxRecords(),
                prefs.getMaxMemoryMb(),
                prefs.isBuilderHookEnabled(),
                checked,
                prefs.isCronetKeylogEnabled()
        ));
        EditText hostPackage = new EditText(requireContext());
        hostPackage.setSingleLine(true);
        hostPackage.setHint(R.string.tls_host_package_hint);
        TextView text = new TextView(requireContext());
        text.setText(getString(R.string.tls_status_prefix) + "\n" + TlsKeylogPath.template()
                + "\n\n" + getString(
                        R.string.tls_hook_status,
                        String.valueOf(RuntimeCaptureConfig.isTlsKeylogEnabled())
                )
                + "\n" + getString(
                        R.string.tls_cronet_status,
                        String.valueOf(RuntimeCaptureConfig.isCronetQuicKeylogEnabled())
                )
                + "\n\n" + getString(R.string.tls_root_helper_note));
        Button copyPath = new Button(requireContext());
        copyPath.setText(R.string.tls_copy_path);
        copyPath.setAllCaps(false);
        copyPath.setOnClickListener(view -> {
            String path = TlsKeylogPath.forPackage(hostPackage.getText().toString());
            ClipboardManager clipboard = (ClipboardManager) requireContext()
                    .getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText(
                        getString(R.string.tls_clip_label),
                        path
                ));
                Toast.makeText(requireContext(), R.string.clipboard_copied, Toast.LENGTH_SHORT).show();
            }
        });
        root.addView(tlsSwitch);
        root.addView(hostPackage);
        root.addView(text);
        root.addView(copyPath);
        Button share = new Button(requireContext());
        share.setText(R.string.tls_share_disabled);
        share.setEnabled(false);
        Button clear = new Button(requireContext());
        clear.setText(R.string.tls_clear_disabled);
        clear.setEnabled(false);
        root.addView(share);
        root.addView(clear);
        return root;
    }
}
