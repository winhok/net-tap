package xyz.winhok.nettap.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import xyz.winhok.nettap.R;
import xyz.winhok.nettap.ui.MainActivity;
import xyz.winhok.nettap.ui.NetTapUiState;
import xyz.winhok.nettap.ui.TextHighlighter;
import xyz.winhok.nettap.ui.adapter.HeaderListAdapter;
import xyz.winhok.nettap.ui.data.CaptureUiEvent;
import xyz.winhok.nettap.ui.data.BodyDisplayState;
import xyz.winhok.nettap.ui.widget.JsonHighlightView;
import xyz.winhok.nettap.ui.widget.SpacedRecyclerView;

public final class DetailResponseFragment extends Fragment {
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        LinearLayout controls = new LinearLayout(requireContext());
        controls.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(controls);
        TextView text = new TextView(requireContext());
        CaptureUiEvent event = DetailGeneralFragment.findEvent();
        if (event == null) {
            text.setText(R.string.empty_response_selected);
            root.addView(text);
            return root;
        }
        String query = NetTapUiState.getDetailQuery();
        Button openBody = new Button(requireContext());
        openBody.setText(R.string.action_open_body);
        openBody.setAllCaps(false);
        openBody.setOnClickListener(view -> {
            NetTapUiState.setSelectedBody(event.getResponseBody());
            ((MainActivity) requireActivity()).showBodyViewer();
        });
        controls.addView(openBody);
        Button exportEntry = new Button(requireContext());
        exportEntry.setText(R.string.action_export_entry);
        exportEntry.setAllCaps(false);
        exportEntry.setOnClickListener(view -> ((MainActivity) requireActivity()).exportHar(java.util.Collections.singletonList(event)));
        controls.addView(exportEntry);
        TextView responseLine = new TextView(requireContext());
        responseLine.setText(TextHighlighter.highlight(
                requireContext(),
                event.getResponseCode() + " " + event.getResponseMessage(),
                query
        ));
        root.addView(responseLine);
        TextView headerTitle = new TextView(requireContext());
        headerTitle.setText(R.string.detail_headers);
        root.addView(headerTitle);
        HeaderListAdapter headers = new HeaderListAdapter();
        headers.submit(event.getResponseHeaders(), query);
        SpacedRecyclerView headerList = new SpacedRecyclerView(requireContext());
        headerList.setLayoutManager(new LinearLayoutManager(requireContext()));
        headerList.setAdapter(headers);
        root.addView(headerList, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));
        TextView bodyTitle = new TextView(requireContext());
        bodyTitle.setText(R.string.detail_body);
        root.addView(bodyTitle);
        BodyDisplayState bodyState = BodyDisplayState.from(event.getResponseBody());
        if (bodyState.requiresLargeBodyConfirmation()) {
            TextView bodyStatus = new TextView(requireContext());
            bodyStatus.setText(bodyState.getStatus());
            root.addView(bodyStatus);
        } else {
            if (!"OK".equals(bodyState.getStatus())) {
                TextView bodyStatus = new TextView(requireContext());
                bodyStatus.setText(bodyState.getStatus());
                root.addView(bodyStatus);
            }
            JsonHighlightView body = new JsonHighlightView(requireContext());
            body.setJsonText(bodyState.getText(), query);
            root.addView(body);
        }
        return root;
    }
}
