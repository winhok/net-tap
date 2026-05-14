package xyz.winhok.nettap.ui.adapter;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import xyz.winhok.nettap.ui.UiTabs;
import xyz.winhok.nettap.ui.fragment.CaptureDetailFragment;
import xyz.winhok.nettap.ui.fragment.DetailCookiesFragment;
import xyz.winhok.nettap.ui.fragment.DetailGeneralFragment;
import xyz.winhok.nettap.ui.fragment.DetailRequestFragment;
import xyz.winhok.nettap.ui.fragment.DetailResponseFragment;

public final class DetailPagerAdapter extends FragmentStateAdapter {
    public DetailPagerAdapter(@NonNull CaptureDetailFragment fragment) {
        super(fragment);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 1:
                return new DetailRequestFragment();
            case 2:
                return new DetailResponseFragment();
            case 3:
                return new DetailCookiesFragment();
            case 0:
            default:
                return new DetailGeneralFragment();
        }
    }

    @Override
    public int getItemCount() {
        return UiTabs.detailCount();
    }
}
