package xyz.winhok.nettap.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import xyz.winhok.nettap.R;
import xyz.winhok.nettap.ui.NetTapUiState;
import xyz.winhok.nettap.ui.TextHighlighter;
import xyz.winhok.nettap.ui.data.CaptureUiEvent;

public final class DetailGeneralFragment extends Fragment {
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        TextView text = new TextView(requireContext());
        CaptureUiEvent event = findEvent();
        if (event == null) {
            text.setText(R.string.empty_event_selected);
            return text;
        }
        String summary = getString(
                        R.string.detail_general_format,
                        event.getUrl(),
                        event.getMethod(),
                        event.getDisplayStatus(),
                        event.getHook(),
                        event.getPackageName(),
                        event.getDurationMs()
                );
        text.setText(TextHighlighter.highlight(requireContext(), summary, NetTapUiState.getDetailQuery()));
        return text;
    }

    static CaptureUiEvent findEvent() {
        String id = DetailHostFragment.selectedEventId();
        if (id == null) {
            return null;
        }
        for (CaptureUiEvent event : NetTapUiState.store().sequenceOldestFirst()) {
            if (id.equals(event.getId())) {
                return event;
            }
        }
        return null;
    }
}
