package xyz.winhok.nettap.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import xyz.winhok.nettap.R;
import xyz.winhok.nettap.ui.NetTapUiState;
import xyz.winhok.nettap.ui.TextHighlighter;
import xyz.winhok.nettap.ui.adapter.HeaderListAdapter;
import xyz.winhok.nettap.ui.adapter.QueryParamListAdapter;
import xyz.winhok.nettap.ui.data.CaptureUiEvent;
import xyz.winhok.nettap.ui.data.QueryParam;
import xyz.winhok.nettap.ui.data.QueryParamParser;
import xyz.winhok.nettap.ui.widget.JsonHighlightView;
import xyz.winhok.nettap.ui.widget.SpacedRecyclerView;

public final class DetailRequestFragment extends Fragment {
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        CaptureUiEvent event = DetailGeneralFragment.findEvent();
        if (event == null) {
            TextView text = new TextView(requireContext());
            text.setText(R.string.empty_request_selected);
            return text;
        }
        String query = NetTapUiState.getDetailQuery();
        TextView requestLine = new TextView(requireContext());
        requestLine.setText(TextHighlighter.highlight(
                requireContext(),
                event.getMethod() + " " + event.getUrl(),
                query
        ));
        root.addView(requestLine);
        TextView path = new TextView(requireContext());
        path.setText(TextHighlighter.highlight(
                requireContext(),
                getString(R.string.detail_path) + ": " + event.getPath(),
                query
        ));
        root.addView(path);
        java.util.List<QueryParam> params = QueryParamParser.parse(event.getQuery());
        if (!params.isEmpty()) {
            TextView queryTitle = new TextView(requireContext());
            queryTitle.setText(R.string.detail_query_params);
            root.addView(queryTitle);
            QueryParamListAdapter queryParams = new QueryParamListAdapter();
            queryParams.submit(params, query);
            SpacedRecyclerView queryList = new SpacedRecyclerView(requireContext());
            queryList.setLayoutManager(new LinearLayoutManager(requireContext()));
            queryList.setAdapter(queryParams);
            root.addView(queryList, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));
        }
        TextView headerTitle = new TextView(requireContext());
        headerTitle.setText(R.string.detail_headers);
        root.addView(headerTitle);
        HeaderListAdapter headers = new HeaderListAdapter();
        headers.submit(event.getRequestHeaders(), query);
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
        JsonHighlightView body = new JsonHighlightView(requireContext());
        body.setJsonText(event.getRequestBody().getText(), query);
        root.addView(body);
        return root;
    }
}
