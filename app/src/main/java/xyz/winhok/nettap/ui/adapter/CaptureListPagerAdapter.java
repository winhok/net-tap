package xyz.winhok.nettap.ui.adapter;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import xyz.winhok.nettap.ui.UiTabs;
import xyz.winhok.nettap.ui.fragment.CaptureDomainsFragment;
import xyz.winhok.nettap.ui.fragment.CaptureListFragment;
import xyz.winhok.nettap.ui.fragment.CaptureSequenceFragment;

public final class CaptureListPagerAdapter extends FragmentStateAdapter {
    public CaptureListPagerAdapter(@NonNull CaptureListFragment fragment) {
        super(fragment);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        if (position == 1) {
            return new CaptureDomainsFragment();
        }
        return new CaptureSequenceFragment();
    }

    @Override
    public int getItemCount() {
        return UiTabs.captureCount();
    }
}
