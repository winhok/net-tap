package xyz.winhok.nettap.ui.fragment;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.util.Collections;

import xyz.winhok.nettap.R;
import xyz.winhok.nettap.ui.DetailTitleFormatter;
import xyz.winhok.nettap.ui.MainActivity;
import xyz.winhok.nettap.ui.NetTapUiState;
import xyz.winhok.nettap.ui.UiTabs;
import xyz.winhok.nettap.ui.adapter.DetailPagerAdapter;
import xyz.winhok.nettap.ui.data.CaptureUiEvent;

public final class CaptureDetailFragment extends Fragment {
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_capture_detail, container, false);
        CaptureUiEvent event = DetailGeneralFragment.findEvent();
        MaterialToolbar toolbar = root.findViewById(R.id.toolbar);
        toolbar.inflateMenu(R.menu.menu_capture_detail);
        toolbar.setTitle(DetailTitleFormatter.title(event));
        toolbar.setNavigationOnClickListener(view -> getParentFragmentManager().popBackStack());
        toolbar.setOnMenuItemClickListener(item -> handleMenuItem(event, item));

        TabLayout tabs = root.findViewById(R.id.tabs);
        ViewPager2 pager = root.findViewById(R.id.pager);
        pager.setAdapter(new DetailPagerAdapter(this));
        new TabLayoutMediator(tabs, pager, (tab, position) -> {
            tab.setText(UiTabs.detailTitle(position));
        }).attach();
        return root;
    }

    private boolean handleMenuItem(CaptureUiEvent event, MenuItem item) {
        if (event == null) {
            return true;
        }
        if (item.getItemId() == R.id.action_share_raw_json) {
            shareRawJson(event);
            return true;
        } else if (item.getItemId() == R.id.action_copy_url) {
            copyUrl(event);
            return true;
        } else if (item.getItemId() == R.id.action_open_body) {
            NetTapUiState.setSelectedBody(event.getResponseBody());
            ((MainActivity) requireActivity()).showBodyViewer();
            return true;
        } else if (item.getItemId() == R.id.action_export_entry) {
            ((MainActivity) requireActivity()).exportHar(Collections.singletonList(event));
            return true;
        }
        return false;
    }

    private void shareRawJson(CaptureUiEvent event) {
        ((MainActivity) requireActivity()).shareRawJson(event);
    }

    private void copyUrl(CaptureUiEvent event) {
        ClipboardManager clipboard = (ClipboardManager) requireContext()
                .getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText(
                    getString(R.string.action_copy_url),
                    event.getUrl()
            ));
            Toast.makeText(requireContext(), R.string.clipboard_copied, Toast.LENGTH_SHORT).show();
        }
    }
}
