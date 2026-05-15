package xyz.winhok.nettap.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SearchView;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
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

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

public final class CaptureListFragment extends Fragment {
    private SearchView search;
    private UiPreferences preferences;
    private SortOrder sortOrder = SortOrder.NEWEST_FIRST;
    private ViewPager2 pager;
    private TextView status;
    private ExtendedFloatingActionButton freezeButton;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        preferences = new UiPreferences(requireContext());
        sortOrder = preferences.getDefaultSort();
        View root = inflater.inflate(R.layout.fragment_capture_list, container, false);
        status = root.findViewById(R.id.status);
        freezeButton = root.findViewById(R.id.fab_freeze);
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
        TabLayout tabs = root.findViewById(R.id.tabs);
        pager = root.findViewById(R.id.pager);
        pager.setAdapter(new CaptureListPagerAdapter(this));
        new TabLayoutMediator(tabs, pager, (tab, position) -> {
            tab.setText(UiTabs.captureTitle(position));
        }).attach();
        installMenuProvider();
        return root;
    }

    private void installMenuProvider() {
        requireActivity().addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
                inflater.inflate(R.menu.menu_capture, menu);
                MenuItem searchItem = menu.findItem(R.id.action_search);
                search = (SearchView) searchItem.getActionView();
                if (search == null) {
                    return;
                }
                search.setIconifiedByDefault(false);
                search.setQueryHint(getString(R.string.search_hint));
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
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem item) {
                if (item.getItemId() == R.id.action_clear) {
                    clearSession();
                    return true;
                }
                if (item.getItemId() == R.id.action_sort) {
                    showSortDialog();
                    return true;
                }
                if (item.getItemId() == R.id.action_export_visible) {
                    exportVisible();
                    return true;
                }
                if (item.getItemId() == R.id.action_export_all) {
                    exportAll();
                    return true;
                }
                return false;
            }
        }, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
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

    private void clearSession() {
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
    }

    private void showSortDialog() {
        SortOrder[] orders = SortOrder.values();
        String[] labels = new String[orders.length];
        int checked = 0;
        for (int i = 0; i < orders.length; i++) {
            labels[i] = getString(orders[i].labelResId());
            if (orders[i] == sortOrder) {
                checked = i;
            }
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.action_sort)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    setSortOrder(orders[which]);
                    dialog.dismiss();
                })
                .show();
    }

    private void exportVisible() {
        List<CaptureUiEvent> events = NetTapUiState.filteredEvents(
                currentQuery(),
                sortOrder.isNewestFirst()
        );
        ((MainActivity) requireActivity()).exportHar(events);
    }

    private void exportAll() {
        List<CaptureUiEvent> events = NetTapUiState.store().sequenceOldestFirst();
        ((MainActivity) requireActivity()).exportHar(events);
    }

    private void setSortOrder(SortOrder selected) {
        sortOrder = selected == null ? SortOrder.NEWEST_FIRST : selected;
        preferences.saveDefaultSort(sortOrder);
        refreshCurrentPage();
    }

    private void updateSessionControls() {
        CaptureSessionStore.State state = NetTapUiState.store().getState();
        if (freezeButton != null) {
            boolean frozen = state == CaptureSessionStore.State.FROZEN;
            freezeButton.setText(frozen ? R.string.action_resume : R.string.action_freeze);
            freezeButton.setIconResource(frozen ? R.drawable.ic_play_arrow : R.drawable.ic_pause);
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

    @Override
    public void onDestroyView() {
        search = null;
        freezeButton = null;
        pager = null;
        status = null;
        super.onDestroyView();
    }
}
