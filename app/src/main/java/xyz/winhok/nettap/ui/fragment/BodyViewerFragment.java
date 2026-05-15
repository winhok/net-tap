package xyz.winhok.nettap.ui.fragment;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import java.util.Locale;

import xyz.winhok.nettap.R;
import xyz.winhok.nettap.ui.Dimens;
import xyz.winhok.nettap.ui.NetTapUiState;
import xyz.winhok.nettap.ui.SoraTextMateInstaller;
import xyz.winhok.nettap.ui.data.BodyDisplayState;

import com.google.android.material.card.MaterialCardView;

import io.github.rosemoe.sora.widget.CodeEditor;

public final class BodyViewerFragment extends Fragment {
    private CodeEditor editor;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        Dimens.setPaddingDp(root, 12, 12);
        BodyDisplayState state = NetTapUiState.getSelectedBody();

        MaterialCardView topBar = new MaterialCardView(requireContext());
        LinearLayout topBarContent = new LinearLayout(requireContext());
        topBarContent.setOrientation(LinearLayout.HORIZONTAL);
        Button close = new Button(requireContext());
        close.setText(R.string.action_close);
        close.setAllCaps(false);
        close.setOnClickListener(view -> getParentFragmentManager().popBackStack());
        topBarContent.addView(close);
        TextView language = new TextView(requireContext());
        language.setText(getString(R.string.body_language_format, state.getLanguage()));
        language.setPadding(Dimens.dp(requireContext(), 12), 0, 0, 0);
        topBarContent.addView(language);
        topBar.addView(topBarContent);
        root.addView(topBar);

        MaterialCardView statusCard = new MaterialCardView(requireContext());
        statusCard.setVisibility("OK".equals(state.getStatus()) ? View.GONE : View.VISIBLE);
        TextView status = new TextView(requireContext());
        status.setText(state.getStatus());
        Dimens.setPaddingDp(status, 12, 10);
        statusCard.addView(status);
        root.addView(statusCard);
        if (state.isBinaryBase64()) {
            Button decode = new Button(requireContext());
            decode.setText(R.string.body_decode_to_file);
            decode.setAllCaps(false);
            decode.setEnabled(false);
            root.addView(decode);
        }

        editor = new CodeEditor(requireContext());
        editor.setEditable(false);
        editor.setTypefaceText(Typeface.MONOSPACE);
        editor.setWordwrap(true);
        SoraTextMateInstaller.apply(requireContext(), editor, state.getLanguage());
        editor.setText(state.requiresLargeBodyConfirmation()
                ? getString(R.string.body_large_placeholder)
                : state.getText());
        root.addView(editor, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));
        if (state.requiresLargeBodyConfirmation()) {
            root.post(() -> showLargeBodyDialog(state));
        }
        return root;
    }

    @Override
    public void onDestroyView() {
        if (editor != null) {
            editor.release();
            editor = null;
        }
        super.onDestroyView();
    }

    private void showLargeBodyDialog(BodyDisplayState state) {
        if (!isAdded() || getView() == null || editor == null) {
            return;
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.detail_body)
                .setMessage(getString(R.string.body_large_warning, formatBytes(state.getContentLength())))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    if (editor != null) {
                        editor.setText(state.getText());
                    }
                })
                .show();
    }

    private static String formatBytes(long bytes) {
        if (bytes <= 0L) {
            return "unknown size";
        }
        double mib = bytes / (1024D * 1024D);
        if (mib >= 1D) {
            return String.format(Locale.US, "%.1f MB", mib);
        }
        double kib = bytes / 1024D;
        return String.format(Locale.US, "%.1f KB", kib);
    }
}
