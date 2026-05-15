package xyz.winhok.nettap.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import xyz.winhok.nettap.R;
import xyz.winhok.nettap.ui.MainActivity;
import xyz.winhok.nettap.ui.SectionViews;

public final class ConfigFragment extends Fragment {
    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        View view = inflater.inflate(R.layout.fragment_config, container, false);
        LinearLayout root = view.findViewById(R.id.config_root);

        LinearLayout section = SectionViews.addSection(inflater, root);
        SectionViews.tile(inflater, section, R.drawable.ic_filter_alt, R.string.nav_url_filter,
                R.string.config_url_filter_subtitle, v -> ((MainActivity) requireActivity())
                        .showPage("url-filter", new UrlFilterFragment()));
        SectionViews.tile(inflater, section, R.drawable.ic_lock, R.string.nav_tls_keylog,
                R.string.config_tls_subtitle, v -> ((MainActivity) requireActivity())
                        .showPage("tls", new TlsKeylogFragment()));
        return view;
    }
}
