package xyz.winhok.nettap.ui.fragment;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

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
    private static final int MENU_SHARE_RAW_JSON = 1;
    private static final int MENU_COPY_URL = 2;
    private static final int MENU_OPEN_BODY = 3;
    private static final int MENU_EXPORT_ENTRY = 4;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(18, 18, 18, 18);
        CaptureUiEvent event = DetailGeneralFragment.findEvent();
        root.addView(createTopBar(event));

        TabLayout tabs = new TabLayout(requireContext());
        root.addView(tabs);
        ViewPager2 pager = new ViewPager2(requireContext());
        pager.setId(View.generateViewId());
        pager.setAdapter(new DetailPagerAdapter(this));
        root.addView(pager, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));
        new TabLayoutMediator(tabs, pager, (tab, position) -> {
            tab.setText(UiTabs.detailTitle(position));
        }).attach();
        return root;
    }

    private View createTopBar(CaptureUiEvent event) {
        LinearLayout topBar = new LinearLayout(requireContext());
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        Button close = new Button(requireContext());
        close.setText(R.string.action_close);
        close.setAllCaps(false);
        close.setOnClickListener(view -> getParentFragmentManager().popBackStack());
        topBar.addView(close);

        TextView title = new TextView(requireContext());
        title.setText(DetailTitleFormatter.title(event));
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        topBar.addView(title, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        ));

        Button more = new Button(requireContext());
        more.setText(R.string.action_more);
        more.setAllCaps(false);
        more.setEnabled(event != null);
        more.setOnClickListener(view -> showDetailMenu(event, view));
        topBar.addView(more);
        return topBar;
    }

    private void showDetailMenu(CaptureUiEvent event, View anchor) {
        if (event == null) {
            return;
        }
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        menu.getMenu().add(0, MENU_SHARE_RAW_JSON, 0, R.string.action_share_raw_json);
        menu.getMenu().add(0, MENU_COPY_URL, 1, R.string.action_copy_url);
        menu.getMenu().add(0, MENU_OPEN_BODY, 2, R.string.action_open_response_body);
        menu.getMenu().add(0, MENU_EXPORT_ENTRY, 3, R.string.action_export_entry);
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == MENU_SHARE_RAW_JSON) {
                shareRawJson(event);
                return true;
            }
            if (item.getItemId() == MENU_COPY_URL) {
                copyUrl(event);
                return true;
            }
            if (item.getItemId() == MENU_OPEN_BODY) {
                NetTapUiState.setSelectedBody(event.getResponseBody());
                ((MainActivity) requireActivity()).showBodyViewer();
                return true;
            }
            if (item.getItemId() == MENU_EXPORT_ENTRY) {
                ((MainActivity) requireActivity()).exportHar(Collections.singletonList(event));
                return true;
            }
            return false;
        });
        menu.show();
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
