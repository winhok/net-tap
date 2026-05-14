package xyz.winhok.nettap.ui;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

public final class NavigationController {
    private final FragmentManager fragmentManager;
    private final int containerId;

    public NavigationController(FragmentManager fragmentManager, int containerId) {
        this.fragmentManager = fragmentManager;
        this.containerId = containerId;
    }

    public void show(String tag, Fragment fragment, boolean addToBackStack) {
        androidx.fragment.app.FragmentTransaction transaction = fragmentManager.beginTransaction()
                .replace(containerId, fragment, tag);
        if (addToBackStack) {
            transaction.addToBackStack(tag);
        }
        transaction.commit();
    }
}
