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
import xyz.winhok.nettap.ui.adapter.CookieListAdapter;
import xyz.winhok.nettap.ui.data.CookieParser;
import xyz.winhok.nettap.ui.data.CaptureUiEvent;
import xyz.winhok.nettap.ui.widget.SpacedRecyclerView;

public final class DetailCookiesFragment extends Fragment {
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        CaptureUiEvent event = DetailGeneralFragment.findEvent();
        if (event == null) {
            TextView text = new TextView(requireContext());
            text.setText(R.string.empty_cookies_selected);
            return text;
        }
        CookieListAdapter adapter = new CookieListAdapter();
        adapter.submit(CookieParser.parse(event), NetTapUiState.getDetailQuery());
        if (adapter.getItemCount() == 0) {
            TextView empty = new TextView(requireContext());
            empty.setText(R.string.empty_cookies);
            return empty;
        }
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        SpacedRecyclerView list = new SpacedRecyclerView(requireContext());
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(adapter);
        root.addView(list, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
        ));
        return root;
    }
}
