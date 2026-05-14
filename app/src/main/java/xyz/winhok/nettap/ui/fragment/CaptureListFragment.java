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
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

import java.util.List;

import xyz.winhok.nettap.R;
import xyz.winhok.nettap.ui.CaptureSessionActions;
import xyz.winhok.nettap.ui.MainActivity;
import xyz.winhok.nettap.ui.NetTapUiState;
import xyz.winhok.nettap.ui.RealtimeStatusFormatter;
import xyz.winhok.nettap.ui.SortOrder;
import xyz.winhok.nettap.ui.UiTabs;
import xyz.winhok.nettap.ui.UiPreferences;
import xyz.winhok.nettap.ui.adapter.CaptureListPagerAdapter;
import xyz.winhok.nettap.ui.data.CaptureUiEvent;
import xyz.winhok.nettap.ui.data.CaptureSessionStore;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

public final class CaptureListFragment extends Fragment {
    private SearchView search;
    private UiPreferences preferences;
    private SortOrder sortOrder = SortOrder.NEWEST_FIRST;
    private ViewPager2 pager;
    private TextView status;
    private Button freezeButton;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        preferences = new UiPreferences(requireContext());
        sortOrder = preferences.getDefaultSort();
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(18, 18, 18, 18);

        status = new TextView(requireContext());
        root.addView(status);

        search = new SearchView(requireContext());
        search.setIconifiedByDefault(false);
        search.setQueryHint(getString(R.string.search_hint));
        root.addView(search);

        LinearLayout controls = new LinearLayout(requireContext());
        controls.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(controls);
        addFreezeButton(controls);
        updateSessionControls();
        freezeButton.setOnClickListener(view -> {
            if (NetTapUiState.store().getState() == CaptureSessionStore.State.FROZEN) {
                NetTapUiState.store().resume();
            } else {
                NetTapUiState.store().freeze();
            }
            updateSessionControls();
            refreshCurrentPage();
        });
        addButton(controls, R.string.action_clear, () -> {
            new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.clear_confirm_title)
                    .setMessage(R.string.clear_confirm_message)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.action_clear, (dialog, which) -> {
                        CaptureSessionActions.clearCurrentSession(this::clearQuery);
                        updateSessionControls();
                        refreshCurrentPage();
                    })
                    .show();
        });
        addSortButton(controls);
        addButton(controls, R.string.action_export_visible, () -> {
            List<CaptureUiEvent> events = NetTapUiState.filteredEvents(
                    currentQuery(),
                    sortOrder.isNewestFirst()
            );
            ((MainActivity) requireActivity()).exportHar(events);
        });
        addButton(controls, R.string.action_export_all, () -> {
            List<CaptureUiEvent> events = NetTapUiState.store().sequenceOldestFirst();
            ((MainActivity) requireActivity()).exportHar(events);
        });

        TabLayout tabs = new TabLayout(requireContext());
        root.addView(tabs);
        pager = new ViewPager2(requireContext());
        pager.setId(View.generateViewId());
        pager.setAdapter(new CaptureListPagerAdapter(this));
        root.addView(pager, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));
        new TabLayoutMediator(tabs, pager, (tab, position) -> {
            tab.setText(UiTabs.captureTitle(position));
        }).attach();
        search.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                refreshCurrentPage();
                search.clearFocus();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                refreshCurrentPage();
                return true;
            }
        });
        return root;
    }

    public String currentQuery() {
        return search == null ? "" : search.getQuery().toString();
    }

    public void clearQuery() {
        if (search != null && search.getQuery().length() > 0) {
            search.setQuery("", false);
        }
    }

    public boolean isNewestFirst() {
        return sortOrder.isNewestFirst();
    }

    public void refreshSessionStatus() {
        updateSessionControls();
    }

    public void showSequenceTab() {
        if (pager != null) {
            pager.setCurrentItem(0, true);
        }
        refreshCurrentPage();
    }

    private void addButton(LinearLayout controls, int label, Runnable action) {
        addRawButton(controls, getString(label), action);
    }

    private void addRawButton(LinearLayout controls, String label, Runnable action) {
        Button button = new Button(requireContext());
        button.setText(label);
        button.setAllCaps(false);
        button.setOnClickListener(view -> action.run());
        controls.addView(button);
    }

    private void addFreezeButton(LinearLayout controls) {
        freezeButton = new Button(requireContext());
        freezeButton.setAllCaps(false);
        controls.addView(freezeButton);
    }

    private void addSortButton(LinearLayout controls) {
        Button button = new Button(requireContext());
        button.setText(R.string.action_sort);
        button.setAllCaps(false);
        button.setOnClickListener(this::showSortMenu);
        controls.addView(button);
    }

    private void showSortMenu(View anchor) {
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        menu.getMenu().setGroupCheckable(0, true, true);
        for (SortOrder order : SortOrder.values()) {
            menu.getMenu()
                    .add(0, order.ordinal(), order.ordinal(), order.labelResId())
                    .setCheckable(true)
                    .setChecked(order == sortOrder);
        }
        menu.setOnMenuItemClickListener(item -> {
            SortOrder selected = SortOrder.fromMenuId(item.getItemId());
            setSortOrder(selected);
            return true;
        });
        menu.show();
    }

    private void setSortOrder(SortOrder selected) {
        sortOrder = selected == null ? SortOrder.NEWEST_FIRST : selected;
        preferences.saveDefaultSort(sortOrder);
        refreshCurrentPage();
    }

    private void updateSessionControls() {
        CaptureSessionStore.State state = NetTapUiState.store().getState();
        if (freezeButton != null) {
            freezeButton.setText(state == CaptureSessionStore.State.FROZEN
                    ? R.string.action_resume
                    : R.string.action_freeze);
        }
        if (status != null) {
            status.setText(getString(
                    R.string.capture_session_status,
                    RealtimeStatusFormatter.format(
                            NetTapUiState.realtimePort(),
                            NetTapUiState.lastServerError(),
                            preferences == null || preferences.isTransportEnabled()
                    ),
                    stateLabel(state)
            ));
        }
    }

    private String stateLabel(CaptureSessionStore.State state) {
        if (state == CaptureSessionStore.State.FROZEN) {
            return getString(R.string.session_state_frozen);
        }
        if (state == CaptureSessionStore.State.CLEARED) {
            return getString(R.string.session_state_cleared);
        }
        return getString(R.string.session_state_running);
    }

    private void refreshCurrentPage() {
        for (Fragment fragment : getChildFragmentManager().getFragments()) {
            if (fragment instanceof CapturePage) {
                ((CapturePage) fragment).refresh();
            }
        }
    }
}
